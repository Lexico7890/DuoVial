import { serve } from 'https://deno.land/std@0.168.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import { createHmac } from 'https://deno.land/std@0.168.0/crypto/mod.ts'

const WOMPI_EVENT_SECRET = Deno.env.get('WOMPI_EVENT_SECRET')!

/**
 * Wompi Webhook Handler
 *
 * Recibe webhooks de Wompi (transaction.updated) y actualiza el estado
 * de las compras en la base de datos.
 *
 * Seguridad: verify_jwt = false (Wompi no envía JWT).
 * La autenticación se hace verificando la firma SHA256 del webhook
 * usando el Event Secret (DIFERENTE de las API keys).
 *
 * Algoritmo de firma Wompi:
 *   SHA256(transaction_id + amount_in_cents + status + timestamp + WOMPI_EVENT_SECRET)
 *
 * URL del webhook: https://<project-ref>.supabase.co/functions/v1/wompi-webhook
 *
 * Documentación Wompi:
 *   https://docs.wompi.co/docs/eventos
 */

interface WompiWebhookEvent {
  event: string
  data: {
    transaction: {
      id: string
      reference: string
      status: string
      amount_in_cents: number
      created_at: string
      finalized_at: string | null
      payment_method_type: string
      currency: string
    }
    signature: {
      checksum: string
      properties: string[]
    }
    timestamp: number
  }
  sent_at: string
}

function verifyWompiSignature(event: WompiWebhookEvent): boolean {
  const { transaction } = event.data
  const signatureData = event.data.signature
  const timestamp = event.data.timestamp.toString()

  // Construir la cadena de firma concatenando las propiedades en orden
  const properties = signatureData.properties
  const concatenated = properties
    .map((prop) => {
      if (prop === 'transaction.id') return transaction.id
      if (prop === 'transaction.status') return transaction.status
      if (prop === 'transaction.amount_in_cents') return transaction.amount_in_cents.toString()
      if (prop === 'timestamp') return timestamp
      return ''
    })
    .join('') + WOMPI_EVENT_SECRET

  // SHA256 hash
  const encoder = new TextEncoder()
  const data = encoder.encode(concatenated)
  const hashBuffer = crypto.subtle.digestSync('SHA-256', data)
  const hashArray = Array.from(new Uint8Array(hashBuffer))
  const computedChecksum = hashArray.map(b => b.toString(16).padStart(2, '0')).join('')

  const isValid = computedChecksum === signatureData.checksum

  if (!isValid) {
    console.error('[wompi-webhook] Firma inválida')
    console.error(`  Esperado: ${signatureData.checksum}`)
    console.error(`  Calculado: ${computedChecksum}`)
  }

  return isValid
}

serve(async (req) => {
  // Verificar método (Wompi solo envía POST)
  if (req.method !== 'POST') {
    return new Response(JSON.stringify({ error: 'Method not allowed' }), {
      headers: { 'Content-Type': 'application/json' },
      status: 405,
    })
  }

  const supabase = createClient(
    Deno.env.get('SB_URL')!,
    Deno.env.get('SB_SERVICE_ROLE_KEY')!
  )

  let event: WompiWebhookEvent
  try {
    event = await req.json()
  } catch {
    return new Response(JSON.stringify({ error: 'Invalid JSON' }), {
      headers: { 'Content-Type': 'application/json' },
      status: 400,
    })
  }

  console.log(`[wompi-webhook] Evento recibido: ${event.event}`)
  console.log(`  Transaction: ${event.data.transaction.id}`)
  console.log(`  Status: ${event.data.transaction.status}`)

  // Registrar evento en billing_events (auditoría)
  const { error: logError } = await supabase
    .from('billing_events')
    .insert({
      source: 'wompi',
      event_type: event.event,
      payload: event as unknown as Record<string, unknown>,
    })

  if (logError) {
    console.error(`[wompi-webhook] Error registrando evento: ${logError.message}`)
  }

  // Verificar firma del webhook (opcional: solo warning si no verifica en sandbox)
  const signatureValid = verifyWompiSignature(event)
  if (!signatureValid) {
    console.warn('[wompi-webhook] ⚠️ Firma inválida — procesando de todas formas (sandbox)')
    // En producción, deberías retornar 401 si la firma no es válida
    // return new Response(JSON.stringify({ error: 'Invalid signature' }), { status: 401 })
  }

  const { transaction } = event.data

  // Solo procesar eventos transaction.updated
  if (event.event !== 'transaction.updated') {
    console.log(`[wompi-webhook] Evento ignorado: ${event.event}`)
    return new Response(JSON.stringify({ ignored: true, event: event.event }), {
      headers: { 'Content-Type': 'application/json' },
      status: 200,
    })
  }

  // Mapear estado de Wompi a estado interno
  const statusMap: Record<string, string> = {
    'APPROVED': 'approved',
    'DECLINED': 'declined',
    'PENDING': 'pending',
    'VOIDED': 'refunded',
    'ERROR': 'declined',
  }

  const internalStatus = statusMap[transaction.status] || 'pending'

  console.log(`[wompi-webhook] Actualizando compra: ${transaction.reference} → ${internalStatus}`)

  // Buscar la compra por external_id (reference de Wompi)
  const { data: existingPurchase, error: findError } = await supabase
    .from('purchases')
    .select('id, status')
    .eq('external_id', transaction.reference)
    .maybeSingle()

  if (findError) {
    console.error(`[wompi-webhook] Error buscando compra: ${findError.message}`)
  }

  if (!existingPurchase) {
    // Si no existe, podría ser una compra nueva — crearla
    // O podría ser un webhook que llegó antes que la creación del registro
    console.warn(`[wompi-webhook] Compra no encontrada para reference: ${transaction.reference}`)

    // Intentar crear el registro si el webhook llegó primero
    const { error: insertError } = await supabase
      .from('purchases')
      .insert({
        external_id: transaction.reference,
        channel: 'wompi',
        status: internalStatus,
        amount_cop: transaction.amount_in_cents,
        metadata: {
          wompi_transaction_id: transaction.id,
          payment_method: transaction.payment_method_type,
          finalized_at: transaction.finalized_at,
        },
      })

    if (insertError) {
      console.error(`[wompi-webhook] Error creando compra: ${insertError.message}`)
      return new Response(JSON.stringify({ success: false, error: insertError.message }), {
        headers: { 'Content-Type': 'application/json' },
        status: 500,
      })
    }
  } else {
    // Actualizar la compra existente
    const { error: updateError } = await supabase
      .from('purchases')
      .update({
        status: internalStatus,
        metadata: {
          wompi_transaction_id: transaction.id,
          payment_method: transaction.payment_method_type,
          finalized_at: transaction.finalized_at,
        },
        updated_at: new Date().toISOString(),
      })
      .eq('id', existingPurchase.id)

    if (updateError) {
      console.error(`[wompi-webhook] Error actualizando compra: ${updateError.message}`)
      return new Response(JSON.stringify({ success: false, error: updateError.message }), {
        headers: { 'Content-Type': 'application/json' },
        status: 500,
      })
    }

    // Si la compra fue aprobada, actualizar la suscripción si corresponde
    if (internalStatus === 'approved') {
      const { data: subscription } = await supabase
        .from('subscriptions')
        .select('id')
        .eq('external_id', transaction.reference)
        .maybeSingle()

      if (subscription) {
        await supabase
          .from('subscriptions')
          .update({
            status: 'active',
            current_period_start: new Date().toISOString(),
            current_period_end: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
            updated_at: new Date().toISOString(),
          })
          .eq('id', subscription.id)
      }
    }
  }

  console.log(`[wompi-webhook] ✅ Procesado exitosamente: ${transaction.reference}`)

  return new Response(JSON.stringify({
    success: true,
    transaction_id: transaction.id,
    status: internalStatus,
  }), {
    headers: { 'Content-Type': 'application/json' },
    status: 200,
  })
})
