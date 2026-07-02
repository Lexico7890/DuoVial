import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const MUX_WEBHOOK_SIGNING_SECRET = Deno.env.get('MUX_WEBHOOK_SIGNING_SECRET') || ''

/**
 * Verifica la firma del webhook de Mux usando HMAC-SHA256.
 * Mux firma cada webhook con el Signing Secret configurado.
 * Formato del header: t=<timestamp>,v1=<firma>
 */
async function verifyMuxSignature(
  body: string,
  headerSignature: string | null
): Promise<boolean> {
  // Si no hay signing secret configurado, no verificar (desarrollo local)
  if (!headerSignature || !MUX_WEBHOOK_SIGNING_SECRET) {
    return !MUX_WEBHOOK_SIGNING_SECRET
  }

  try {
    const parts = headerSignature.split(',')
    const sigMap: Record<string, string> = {}
    for (const p of parts) {
      const [k, v] = p.split('=')
      sigMap[k] = v
    }
    const timestamp = sigMap['t']
    const signature = sigMap['v1']
    if (!timestamp || !signature) return false

    // Verificar que el timestamp no sea mayor a 5 minutos
    const timestampMs = parseInt(timestamp) * 1000
    if (Math.abs(Date.now() - timestampMs) > 5 * 60 * 1000) {
      console.warn('[mux-webhook-handler] Timestamp fuera de rango')
      return false
    }

    // Calcular HMAC-SHA256
    const key = await crypto.subtle.importKey(
      'raw',
      new TextEncoder().encode(MUX_WEBHOOK_SIGNING_SECRET),
      { name: 'HMAC', hash: 'SHA-256' },
      false,
      ['sign']
    )

    const sig = await crypto.subtle.sign(
      'HMAC',
      key,
      new TextEncoder().encode(`${timestamp}.${body}`)
    )

    const calc = Array.from(new Uint8Array(sig))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('')

    return calc === signature
  } catch (err) {
    console.error('[mux-webhook-handler] Error verificando firma:', err)
    return false
  }
}

Deno.serve(async (req) => {
  // Obtener firma del header
  const muxSignature = req.headers.get('mux-signature')
  const bodyText = await req.text()

  // Verificar firma si hay signing secret configurado
  if (MUX_WEBHOOK_SIGNING_SECRET) {
    const isValid = await verifyMuxSignature(bodyText, muxSignature)
    if (!isValid) {
      console.error('[mux-webhook-handler] Firma invalida')
      return new Response(JSON.stringify({ error: 'Invalid signature' }), {
        headers: { 'Content-Type': 'application/json' },
        status: 401,
      })
    }
  }

  const event = JSON.parse(bodyText)

  // Solo procesamos el evento video.asset.ready
  if (event.type !== 'video.asset.ready') {
    console.log(`[mux-webhook-handler] Evento ignorado: ${event.type}`)
    return new Response(JSON.stringify({ ignored: true, type: event.type }), {
      headers: { 'Content-Type': 'application/json' },
      status: 200,
    })
  }

  const supabase = createClient(
    Deno.env.get('SB_URL')!,
    Deno.env.get('SB_SERVICE_ROLE_KEY')!
  )

  const assetId = event.data.id
  const playbackId = event.data.playback_ids?.[0]?.id

  if (!playbackId) {
    console.error(`[mux-webhook-handler] No playback_id para asset: ${assetId}`)
    return new Response(JSON.stringify({ success: false, error: 'No playback_id' }), {
      headers: { 'Content-Type': 'application/json' },
      status: 400,
    })
  }

  console.log(`[mux-webhook-handler] Video listo: asset=${assetId}, playback=${playbackId}`)

  // Actualizar el incidente con la información de streaming
  const { error: updateError } = await supabase
    .from('incidents')
    .update({
      mux_playback_id: playbackId,
      streaming_url: `https://stream.mux.com/${playbackId}.m3u8`,
      status: 'ready',
      processed_at: new Date().toISOString(),
    })
    .eq('mux_asset_id', assetId)

  if (updateError) {
    console.error(`[mux-webhook-handler] Error actualizando incidente: ${updateError.message}`)
    return new Response(JSON.stringify({ success: false, error: updateError.message }), {
      headers: { 'Content-Type': 'application/json' },
      status: 500,
    })
  }

  return new Response(JSON.stringify({ success: true, assetId, playbackId }), {
    headers: { 'Content-Type': 'application/json' },
    status: 200,
  })
})
