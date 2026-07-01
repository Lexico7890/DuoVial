import { serve } from 'https://deno.land/std@0.168.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const WOMPI_PRIVATE_KEY = Deno.env.get('WOMPI_PRIVATE_KEY')!
const WOMPI_BASE_URL = Deno.env.get('WOMPI_BASE_URL') || 'https://sandbox.wompi.co'

/**
 * Create Wompi Payment Link
 *
 * Crea un link de pago de Wompi para pagos desde el Dashboard Web (Fleet,
 * instalación OBD, etc.).
 *
 * Seguridad: verify_jwt = true (solo usuarios autenticados del Dashboard).
 *
 * Flujo:
 * 1. Usuario en Dashboard web selecciona un producto/servicio
 * 2. Dashboard llama a esta Edge Function
 * 3. Esta función crea un payment link en Wompi
 * 4. Crea un registro pending en purchases
 * 5. Retorna la URL de pago al Dashboard
 * 6. Usuario es redirigido a Wompi para completar el pago
 * 7. Wompi envía webhook (transaction.updated) a wompi-webhook
 *
 * Documentación Wompi API:
 *   https://docs.wompi.co/reference/crear-link-de-pago
 */

interface CreateLinkRequest {
  productId: string         // ID del producto en nuestro catálogo
  name: string              // Nombre descriptivo del pago
  amountInCents: number     // Monto en centavos ($10,900 COP = 1,090,000 centavos)
  currency?: string         // Moneda (default: COP)
  redirectUrl?: string      // URL a redirigir tras el pago
  orgId?: string            // Organización (para Fleet)
  metadata?: Record<string, string>  // Metadatos adicionales
}

serve(async (req) => {
  // verify_jwt = true
  const authHeader = req.headers.get('Authorization')!
  const token = authHeader.replace('Bearer ', '')

  const supabase = createClient(
    Deno.env.get('SUPABASE_URL')!,
    Deno.env.get('SUPABASE_ANON_KEY')!,
    { global: { headers: { Authorization: authHeader } } }
  )

  const { data: { user }, error: userError } = await supabase.auth.getUser(token)
  if (userError || !user) {
    return new Response(JSON.stringify({ error: 'Unauthorized' }), {
      headers: { 'Content-Type': 'application/json' },
      status: 401,
    })
  }

  const body: CreateLinkRequest = await req.json()

  if (!body.productId || !body.name || !body.amountInCents) {
    return new Response(JSON.stringify({
      error: 'productId, name, y amountInCents son requeridos',
    }), {
      headers: { 'Content-Type': 'application/json' },
      status: 400,
    })
  }

  console.log(`[create-wompi-link] Creando pago: ${body.name} (${body.amountInCents} COP)`)
  console.log(`  Usuario: ${user.id}, Producto: ${body.productId}`)

  // Generar referencia única (se usará como external_id para reconciliación)
  const reference = `duovial_${Date.now()}_${crypto.randomUUID().substring(0, 8)}`

  try {
    // Crear payment link en Wompi
    const wompiResponse = await fetch(`${WOMPI_BASE_URL}/v1/payment_links`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${WOMPI_PRIVATE_KEY}`,
      },
      body: JSON.stringify({
        name: body.name,
        amount_in_cents: body.amountInCents,
        currency: body.currency || 'COP',
        reference: reference,
        redirect_url: body.redirectUrl || `${Deno.env.get('APP_URL')}/billing/result`,
        single_use: true,
        expires_at: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(), // 24h
      }),
    })

    const linkData = await wompiResponse.json()

    if (!wompiResponse.ok) {
      console.error(`[create-wompi-link] Error Wompi: ${JSON.stringify(linkData)}`)
      return new Response(JSON.stringify({
        success: false,
        error: linkData.error?.message || 'Error creando link de pago',
        wompiError: linkData,
      }), {
        headers: { 'Content-Type': 'application/json' },
        status: wompiResponse.status,
      })
    }

    console.log(`[create-wompi-link] Link creado: ${linkData.data.id}`)

    // Crear registro de compra en estado pending
    const { error: insertError } = await supabase
      .from('purchases')
      .insert({
        user_id: user.id,
        org_id: body.orgId || null,
        product_id: body.productId,
        channel: 'wompi',
        external_id: reference,
        status: 'pending',
        amount_cop: body.amountInCents,
        metadata: {
          payment_link_id: linkData.data.id,
          payment_url: linkData.data.url,
          created_by: user.id,
          ...body.metadata,
        },
      })

    if (insertError) {
      console.error(`[create-wompi-link] Error registrando compra: ${insertError.message}`)
      // No retornamos error porque el link ya está creado
    }

    return new Response(JSON.stringify({
      success: true,
      paymentLinkId: linkData.data.id,
      url: linkData.data.url,
      reference: reference,
      expiresAt: linkData.data.expires_at,
    }), {
      headers: { 'Content-Type': 'application/json' },
      status: 200,
    })
  } catch (err) {
    console.error(`[create-wompi-link] Error: ${err.message}`)
    return new Response(JSON.stringify({
      success: false,
      error: err.message,
    }), {
      headers: { 'Content-Type': 'application/json' },
      status: 500,
    })
  }
})
