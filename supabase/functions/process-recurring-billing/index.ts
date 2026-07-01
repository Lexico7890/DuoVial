import { serve } from 'https://deno.land/std@0.168.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const WOMPI_PRIVATE_KEY = Deno.env.get('WOMPI_PRIVATE_KEY')!
const WOMPI_BASE_URL = Deno.env.get('WOMPI_BASE_URL') || 'https://sandbox.wompi.co'

/**
 * Process Recurring Billing (Scheduled Function)
 *
 * Se ejecuta periódicamente (via pg_cron) para procesar cobros recurrentes
 * de suscripciones Wompi. Como Wompi NO tiene suscripciones nativas, esta
 * función implementa la lógica de cobro recurrente usando tokenización de
 * tarjetas.
 *
 * Seguridad: N/A (scheduled function, se ejecuta internamente).
 * Trigger: pg_cron schedule (ej: cada día a las 2:00 AM).
 *
 * Flujo:
 * 1. Encontrar suscripciones activas Wompi que necesitan renovación
 * 2. Para cada una, obtener el token de tarjeta guardado
 * 3. Crear una transacción Wompi para el cobro
 * 4. Esperar confirmación (polling)
 * 5. Actualizar estado de la suscripción
 * 6. Si falla: notificar al usuario y marcar para reintento
 * 7. Después de 3 intentos fallidos: cancelar suscripción
 *
 * Documentación Wompi Transacciones:
 *   https://docs.wompi.co/reference/crear-transaccion
 */

interface WompiCardToken {
  id: string
  subscription_id: string
  user_id: string
  org_id: string | null
  wompi_token: string
  last_four: string
  brand: string
  amount_cop: number
  product_id: string
  external_id: string
}

serve(async (_req) => {
  console.log('[process-recurring-billing] Iniciando procesamiento de cobros recurrentes')
  console.log(`  Fecha: ${new Date().toISOString()}`)

  const supabase = createClient(
    Deno.env.get('SB_URL')!,
    Deno.env.get('SB_SERVICE_ROLE_KEY')!
  )

  // 1. Encontrar suscripciones activas Wompi que necesitan renovación
  const now = new Date().toISOString()
  const graceEnd = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString() // 3 días de gracia

  const { data: subscriptions, error: subError } = await supabase
    .from('subscriptions')
    .select(`
      id,
      user_id,
      org_id,
      product_id,
      external_id,
      status,
      current_period_end,
      next_billing_date,
      billing_attempts,
      wompi_card_token_id,
      amount_cop,
      wompi_card_tokens!inner(
        id,
        wompi_token,
        last_four,
        brand
      )
    `)
    .eq('channel', 'wompi')
    .eq('status', 'active')
    .lte('next_billing_date', graceEnd)
    .order('next_billing_date', { ascending: true })

  if (subError) {
    console.error(`[process-recurring-billing] Error consultando suscripciones: ${subError.message}`)
    return new Response(JSON.stringify({ success: false, error: subError.message }), {
      headers: { 'Content-Type': 'application/json' },
      status: 500,
    })
  }

  if (!subscriptions || subscriptions.length === 0) {
    console.log('[process-recurring-billing] No hay suscripciones para renovar hoy')
    return new Response(JSON.stringify({
      success: true,
      processed: 0,
      message: 'No hay suscripciones para renovar',
    }), {
      headers: { 'Content-Type': 'application/json' },
      status: 200,
    })
  }

  console.log(`[process-recurring-billing] Encontradas ${subscriptions.length} suscripciones para procesar`)

  const results = {
    total: subscriptions.length,
    approved: 0,
    declined: 0,
    failed: 0,
    details: [] as Array<{
      subscription_id: string
      user_id: string
      status: string
      error?: string
    }>,
  }

  // 2. Procesar cada suscripción
  for (const sub of subscriptions) {
    const cardToken = Array.isArray(sub.wompi_card_tokens)
      ? sub.wompi_card_tokens[0]
      : sub.wompi_card_tokens

    if (!cardToken || !cardToken.wompi_token) {
      console.warn(`[process-recurring-billing] Sin token de tarjeta para suscripción ${sub.id}`)
      results.failed++
      results.details.push({
        subscription_id: sub.id,
        user_id: sub.user_id,
        status: 'no_token',
        error: 'No hay token de tarjeta guardado',
      })

      // Notificar al usuario que necesita actualizar método de pago
      await sendNoTokenNotification(supabase, sub.user_id, sub.id)
      continue
    }

    console.log(`[process-recurring-billing] Procesando: ${sub.id} (${cardToken.brand} ****${cardToken.last_four})`)

    try {
      // 3. Crear transacción Wompi
      const reference = `duovial_renewal_${sub.id}_${Date.now()}`
      const wompiResponse = await fetch(`${WOMPI_BASE_URL}/v1/transactions`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${WOMPI_PRIVATE_KEY}`,
        },
        body: JSON.stringify({
          amount_in_cents: sub.amount_cop,
          currency: 'COP',
          customer_email: '', // Se obtendrá del perfil
          reference: reference,
          payment_method: {
            type: 'CARD',
            token: cardToken.wompi_token,
            installments: 1,
          },
        }),
      })

      const transactionData = await wompiResponse.json()

      if (!wompiResponse.ok) {
        console.error(`[process-recurring-billing] Error Wompi: ${JSON.stringify(transactionData)}`)
        results.declined++
        results.details.push({
          subscription_id: sub.id,
          user_id: sub.user_id,
          status: 'declined',
          error: transactionData.error?.message || 'Error en transacción',
        })

        await handleFailedPayment(supabase, sub.id, sub.billing_attempts || 0)
        continue
      }

      const txId = transactionData.data.id
      console.log(`[process-recurring-billing] Transacción creada: ${txId}, estado: ${transactionData.data.status}`)

      // 4. Polling para confirmar estado
      let finalStatus = transactionData.data.status
      let pollCount = 0
      while (finalStatus === 'PENDING' && pollCount < 10) { // Máximo 5 minutos
        await new Promise(resolve => setTimeout(resolve, 30000)) // Esperar 30s
        const pollResponse = await fetch(`${WOMPI_BASE_URL}/v1/transactions/${txId}`, {
          headers: { 'Authorization': `Bearer ${WOMPI_PRIVATE_KEY}` },
        })
        const pollData = await pollResponse.json()
        finalStatus = pollData.data.status
        pollCount++
        console.log(`[process-recurring-billing] Poll ${pollCount}: ${txId} → ${finalStatus}`)
      }

      // 5. Actualizar suscripción según resultado
      if (finalStatus === 'APPROVED') {
        const newPeriodEnd = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000)
        await supabase
          .from('subscriptions')
          .update({
            status: 'active',
            current_period_start: new Date().toISOString(),
            current_period_end: newPeriodEnd.toISOString(),
            next_billing_date: newPeriodEnd.toISOString(),
            billing_attempts: 0,
            updated_at: new Date().toISOString(),
          })
          .eq('id', sub.id)

        await supabase
          .from('purchases')
          .insert({
            user_id: sub.user_id,
            org_id: sub.org_id,
            product_id: sub.product_id,
            channel: 'wompi',
            external_id: reference,
            status: 'approved',
            amount_cop: sub.amount_cop,
            metadata: {
              transaction_id: txId,
              renewal: true,
              subscription_id: sub.id,
            },
          })

        results.approved++
        results.details.push({
          subscription_id: sub.id,
          user_id: sub.user_id,
          status: 'approved',
        })
        console.log(`[process-recurring-billing] ✅ Renovación aprobada: ${sub.id}`)
      } else {
        results.declined++
        results.details.push({
          subscription_id: sub.id,
          user_id: sub.user_id,
          status: finalStatus.toLowerCase(),
          error: `Transacción ${finalStatus}`,
        })
        await handleFailedPayment(supabase, sub.id, sub.billing_attempts || 0)
      }
    } catch (err) {
      console.error(`[process-recurring-billing] Error procesando suscripción ${sub.id}: ${err.message}`)
      results.failed++
      results.details.push({
        subscription_id: sub.id,
        user_id: sub.user_id,
        status: 'error',
        error: err.message,
      })
    }
  }

  console.log(`[process-recurring-billing] Resumen: ${results.approved} aprobadas, ${results.declined} declinadas, ${results.failed} errores`)

  return new Response(JSON.stringify({
    success: true,
    processed_at: new Date().toISOString(),
    results,
  }), {
    headers: { 'Content-Type': 'application/json' },
    status: 200,
  })
})

/**
 * Manejar pago fallido: incrementar contador de intentos,
 * mover a grace_period o cancelar.
 */
async function handleFailedPayment(
  supabase: ReturnType<typeof createClient>,
  subscriptionId: string,
  currentAttempts: number,
) {
  const newAttempts = currentAttempts + 1

  if (newAttempts >= 3) {
    // Después de 3 intentos, cancelar suscripción
    await supabase
      .from('subscriptions')
      .update({
        status: 'cancelled',
        billing_attempts: newAttempts,
        updated_at: new Date().toISOString(),
      })
      .eq('id', subscriptionId)
    console.log(`[process-recurring-billing] Suscripción cancelada tras ${newAttempts} intentos: ${subscriptionId}`)
  } else {
    // Mover a grace_period y reintentar en 3 días
    const retryDate = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000)
    await supabase
      .from('subscriptions')
      .update({
        status: 'grace_period',
        billing_attempts: newAttempts,
        next_billing_date: retryDate.toISOString(),
        updated_at: new Date().toISOString(),
      })
      .eq('id', subscriptionId)
    console.log(`[process-recurring-billing] Intento ${newAttempts}/3 fallido, reintento el ${retryDate.toISOString()}`)
  }
}

/**
 * Notificar al usuario que necesita actualizar su método de pago.
 */
async function sendNoTokenNotification(
  supabase: ReturnType<typeof createClient>,
  userId: string,
  subscriptionId: string,
) {
  try {
    const { data: profile } = await supabase
      .from('profiles')
      .select('push_token')
      .eq('id', userId)
      .single()

    if (profile?.push_token) {
      const onesignalAppId = Deno.env.get('ONESIGNAL_APP_ID')!
      const onesignalKey = Deno.env.get('ONESIGNAL_REST_API_KEY')!

      await fetch('https://api.onesignal.com/notifications', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Basic ${onesignalKey}`,
        },
        body: JSON.stringify({
          app_id: onesignalAppId,
          include_player_ids: [profile.push_token],
          headings: { es: 'Método de pago requerido' },
          contents: { es: 'Tu suscripción no tiene un método de pago. Actualízalo para continuar.' },
          data: { type: 'billing', subscription_id: subscriptionId },
        }),
      })
    }
  } catch (err) {
    console.error(`[process-recurring-billing] Error enviando notificación: ${err.message}`)
  }
}
