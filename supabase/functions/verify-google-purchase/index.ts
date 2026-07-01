import { serve } from 'https://deno.land/std@0.168.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const PACKAGE_NAME = Deno.env.get('GOOGLE_PLAY_PACKAGE_NAME')!

/**
 * Verify Google Play Purchase
 *
 * Recibe un purchaseToken de la app Android y verifica la compra
 * con la Google Play Developer API.
 *
 * Seguridad: verify_jwt = true (solo la app autenticada puede verificar compras).
 *
 * Flujo:
 * 1. App completa compra via Google Play Billing
 * 2. App recibe purchaseToken
 * 3. App llama a esta Edge Function con el token
 * 4. Esta función verifica con Google Play Developer API
 * 5. Si es válida, actualiza purchases/subscriptions en DB
 * 6. Retorna resultado a la app
 *
 * Documentación Google Play Developer API:
 *   https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptions/get
 */

interface VerifyRequest {
  purchaseToken: string
  productId: string
  productType: 'subscription' | 'one_time'
  orgId?: string
}

// Obtener access token para Google Play Developer API
async function getGoogleAccessToken(): Promise<string> {
  const serviceAccountJson = Deno.env.get('GOOGLE_PLAY_SERVICE_ACCOUNT_JSON')
  if (!serviceAccountJson) {
    throw new Error('GOOGLE_PLAY_SERVICE_ACCOUNT_JSON no configurado')
  }

  const credentials = JSON.parse(serviceAccountJson)
  const now = Math.floor(Date.now() / 1000)

  // Crear JWT para autenticación service account
  const header = {
    alg: 'RS256',
    typ: 'JWT',
    kid: credentials.private_key_id,
  }

  const claimSet = {
    iss: credentials.client_email,
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
    scope: 'https://www.googleapis.com/auth/androidpublisher',
  }

  // Codificar JWT manualmente (Deno)
  const encoder = new TextEncoder()
  const base64Header = btoa(JSON.stringify(header))
  const base64ClaimSet = btoa(JSON.stringify(claimSet))
  const signatureInput = `${base64Header}.${base64ClaimSet}`

  // Firmar con RSA private key
  const privateKey = credentials.private_key
  const keyData = encoder.encode(privateKey)

  // Importar la clave privada
  const cryptoKey = await crypto.subtle.importKey(
    'pkcs8',
    pemToArrayBuffer(privateKey),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign']
  )

  const signatureBytes = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    cryptoKey,
    encoder.encode(signatureInput)
  )

  const signature = arrayBufferToBase64(signatureBytes)
  const jwt = `${signatureInput}.${signature}`

  // Intercambiar JWT por access token
  const tokenResponse = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: jwt,
    }),
  })

  const tokenData = await tokenResponse.json()
  if (!tokenResponse.ok || !tokenData.access_token) {
    throw new Error(`Error obteniendo access token: ${JSON.stringify(tokenData)}`)
  }

  return tokenData.access_token
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const b64 = pem
    .replace('-----BEGIN PRIVATE KEY-----', '')
    .replace('-----END PRIVATE KEY-----', '')
    .replace(/\n/g, '')
    .replace(/\r/g, '')
  const binary = atob(b64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes.buffer
}

function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i])
  }
  return btoa(binary)
}

serve(async (req) => {
  // verify_jwt = true, el token ya fue validado por Supabase
  const authHeader = req.headers.get('Authorization')!
  const token = authHeader.replace('Bearer ', '')

  const supabase = createClient(
    Deno.env.get('SB_URL')!,
    Deno.env.get('SB_ANON_KEY')!,
    { global: { headers: { Authorization: authHeader } } }
  )

  const { data: { user }, error: userError } = await supabase.auth.getUser(token)
  if (userError || !user) {
    return new Response(JSON.stringify({ error: 'Unauthorized' }), {
      headers: { 'Content-Type': 'application/json' },
      status: 401,
    })
  }

  const body: VerifyRequest = await req.json()

  if (!body.purchaseToken || !body.productId) {
    return new Response(JSON.stringify({ error: 'purchaseToken y productId son requeridos' }), {
      headers: { 'Content-Type': 'application/json' },
      status: 400,
    })
  }

  console.log(`[verify-google-purchase] Verificando compra: ${body.productId}`)
  console.log(`  Usuario: ${user.id}, Token: ${body.purchaseToken.substring(0, 10)}...`)

  try {
    const accessToken = await getGoogleAccessToken()

    // Verificar compra con Google Play Developer API
    const endpoint = body.productType === 'subscription'
      ? `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE_NAME}/purchases/subscriptions/${body.productId}/tokens/${body.purchaseToken}`
      : `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE_NAME}/purchases/products/${body.productId}/tokens/${body.purchaseToken}`

    const googleResponse = await fetch(endpoint, {
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Accept': 'application/json',
      },
    })

    const purchaseData = await googleResponse.json()

    if (!googleResponse.ok) {
      console.error(`[verify-google-purchase] Error Google API: ${JSON.stringify(purchaseData)}`)
      return new Response(JSON.stringify({
        verified: false,
        error: purchaseData.error?.message || 'Error verificando compra',
      }), {
        headers: { 'Content-Type': 'application/json' },
        status: 400,
      })
    }

    console.log(`[verify-google-purchase] Compra verificada: ${body.productId}`)

    // Determinar estado de la compra
    let purchaseStatus = 'approved'
    if (body.productType === 'subscription') {
      const sub = purchaseData
      if (sub.cancelReason) purchaseStatus = 'cancelled'
      else if (sub.paymentState === 2) purchaseStatus = 'approved' // 2 = Received
      else if (sub.paymentState === 1) purchaseStatus = 'pending' // 1 = Payment pending
    }

    // Registrar en purchases
    const { error: insertError } = await supabase
      .from('purchases')
      .insert({
        user_id: user.id,
        org_id: body.orgId || null,
        product_id: body.productId,
        channel: 'google_play',
        external_id: body.purchaseToken,
        status: purchaseStatus,
        metadata: {
          google_response: purchaseData,
          verified_at: new Date().toISOString(),
        },
      })

    if (insertError) {
      console.error(`[verify-google-purchase] Error registrando compra: ${insertError.message}`)
    }

    // Si es suscripción aprobada, crear/actualizar registro en subscriptions
    if (body.productType === 'subscription' && purchaseStatus === 'approved') {
      const subData = purchaseData
      const now = new Date()
      const periodEnd = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000) // 30 días

      await supabase
        .from('subscriptions')
        .upsert({
          user_id: user.id,
          org_id: body.orgId || null,
          product_id: body.productId,
          channel: 'google_play',
          external_id: body.purchaseToken,
          status: 'active',
          current_period_start: now.toISOString(),
          current_period_end: periodEnd.toISOString(),
          next_billing_date: periodEnd.toISOString(),
        }, {
          onConflict: 'user_id,product_id',
        })
    }

    return new Response(JSON.stringify({
      verified: true,
      productId: body.productId,
      purchaseToken: body.purchaseToken,
      status: purchaseStatus,
      data: purchaseData,
    }), {
      headers: { 'Content-Type': 'application/json' },
      status: 200,
    })
  } catch (err) {
    console.error(`[verify-google-purchase] Error: ${err.message}`)
    return new Response(JSON.stringify({
      verified: false,
      error: err.message,
    }), {
      headers: { 'Content-Type': 'application/json' },
      status: 500,
    })
  }
})
