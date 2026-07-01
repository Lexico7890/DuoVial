import { serve } from 'https://deno.land/std@0.168.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const MUX_TOKEN_ID = Deno.env.get('MUX_TOKEN_ID')!
const MUX_TOKEN_SECRET = Deno.env.get('MUX_TOKEN_SECRET')!

serve(async (req) => {
  // Trigger: Supabase Storage webhook (object.created)
  // Se dispara automáticamente cuando un video se sube a Supabase Storage
  const record = await req.json()

  const supabase = createClient(
    Deno.env.get('SB_URL')!,
    Deno.env.get('SB_SERVICE_ROLE_KEY')!
  )

  // Construir la URL pública del video recién subido
  const videoUrl = `https://${Deno.env.get('SB_PROJECT_REF')}.supabase.co/storage/v1/object/${record.bucket}/${record.key}`

  console.log(`[trigger-mux-transcode] Creando Mux Asset para: ${videoUrl}`)

  // Crear Mux Asset para transcodificación
  const muxResponse = await fetch('https://api.mux.com/video/v1/assets', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Basic ' + btoa(`${MUX_TOKEN_ID}:${MUX_TOKEN_SECRET}`),
    },
    body: JSON.stringify({
      input: [{
        url: videoUrl,
      }],
      playback_policy: ['public'],
      mp4_support: 'none', // Solo HLS para ahorrar encoding minutes
    }),
  })

  if (!muxResponse.ok) {
    const errorBody = await muxResponse.text()
    console.error(`[trigger-mux-transcode] Error creando Mux Asset: ${errorBody}`)
    return new Response(JSON.stringify({ success: false, error: errorBody }), {
      headers: { 'Content-Type': 'application/json' },
      status: 500,
    })
  }

  const muxAsset = await muxResponse.json()
  const assetId = muxAsset.data.id

  console.log(`[trigger-mux-transcode] Mux Asset creado: ${assetId}`)

  // Actualizar el incidente con el mux_asset_id
  const incidentId = record.metadata?.incident_id
  if (incidentId) {
    const { error: updateError } = await supabase
      .from('incidents')
      .update({
        mux_asset_id: assetId,
        status: 'processing',
      })
      .eq('id', incidentId)

    if (updateError) {
      console.error(`[trigger-mux-transcode] Error actualizando incidente: ${updateError.message}`)
    }
  }

  return new Response(JSON.stringify({ success: true, assetId }), {
    headers: { 'Content-Type': 'application/json' },
    status: 200,
  })
})
