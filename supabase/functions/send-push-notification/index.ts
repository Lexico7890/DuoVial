import { serve } from 'https://deno.land/std@0.168.0/http/server.ts'

const ONESIGNAL_APP_ID = Deno.env.get('ONESIGNAL_APP_ID')!
const ONESIGNAL_REST_API_KEY = Deno.env.get('ONESIGNAL_REST_API_KEY')!

interface PushRequest {
  playerIds: string[]        // IDs de los dispositivos destino
  title: string              // Título de la notificación
  message: string            // Cuerpo de la notificación
  data?: Record<string, string>  // Datos adicionales (deep link, etc.)
  segment?: string           // Alternativa: enviar a un segmento en vez de playerIds
}

serve(async (req) => {
  // Verificar que la request tiene autorización JWT (verify_jwt = true en config)
  const body: PushRequest = await req.json()

  if (!body.playerIds && !body.segment) {
    return new Response(
      JSON.stringify({ error: 'Debe proporcionar playerIds o segment' }),
      { headers: { 'Content-Type': 'application/json' }, status: 400 }
    )
  }

  if (!body.title || !body.message) {
    return new Response(
      JSON.stringify({ error: 'title y message son requeridos' }),
      { headers: { 'Content-Type': 'application/json' }, status: 400 }
    )
  }

  // Construir payload para OneSignal API
  const payload: Record<string, unknown> = {
    app_id: ONESIGNAL_APP_ID,
    headings: { en: body.title, es: body.title },
    contents: { en: body.message, es: body.message },
    data: body.data || {},
  }

  // Usar playerIds o segment según lo que venga en la request
  if (body.playerIds && body.playerIds.length > 0) {
    payload.include_player_ids = body.playerIds
  }
  if (body.segment) {
    payload.included_segments = [body.segment]
  }

  console.log(`[send-push-notification] Enviando notificación: "${body.title}"`)

  const response = await fetch('https://api.onesignal.com/notifications', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Basic ${ONESIGNAL_REST_API_KEY}`,
    },
    body: JSON.stringify(payload),
  })

  const result = await response.json()

  if (!response.ok) {
    console.error(`[send-push-notification] Error OneSignal: ${JSON.stringify(result)}`)
    return new Response(JSON.stringify({ success: false, error: result }), {
      headers: { 'Content-Type': 'application/json' },
      status: response.status,
    })
  }

  console.log(`[send-push-notification] Enviada exitosamente: ${result.id}`)

  return new Response(JSON.stringify({ success: true, notificationId: result.id, result }), {
    headers: { 'Content-Type': 'application/json' },
    status: 200,
  })
})
