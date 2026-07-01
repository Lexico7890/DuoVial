import { serve } from 'https://deno.land/std@0.168.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

serve(async (req) => {
  const event = await req.json()

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
    console.error(`[mux-webhook-handler] No playback_id encontrado para asset: ${assetId}`)
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

  // El cambio en Postgres dispara automáticamente Supabase Realtime
  // para notificar al Dashboard Web en tiempo real

  return new Response(JSON.stringify({ success: true, assetId, playbackId }), {
    headers: { 'Content-Type': 'application/json' },
    status: 200,
  })
})
