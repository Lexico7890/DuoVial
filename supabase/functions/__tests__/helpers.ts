/**
 * ============================================
 * HELPERS COMPARTIDOS — FASE 0 TESTS
 * ============================================
 *
 * Utilidades, fixtures y tipos reutilizables para
 * todos los tests de Edge Functions.
 *
 * Uso:
 *   import { WOMPI_WEBHOOK_EVENT, createMockRequest } from "./helpers.ts"
 */

// ============================================
// TYPES
// ============================================

export interface WompiTransaction {
  id: string
  reference: string
  status: string
  amount_in_cents: number
  created_at: string
  finalized_at: string | null
  payment_method_type: string
  currency: string
}

export interface WompiSignature {
  checksum: string
  properties: string[]
}

export interface WompiWebhookEvent {
  event: string
  data: {
    transaction: WompiTransaction
    signature: WompiSignature
    timestamp: number
  }
  sent_at: string
}

export interface MuxAsset {
  id: string
  playback_ids?: Array<{ id: string }>
  status: string
}

export interface MuxWebhookEvent {
  type: string
  data: MuxAsset
}

// ============================================
// FIXTURES — WOMPI
// ============================================

/**
 * Evento de webhook de Wompi con estado APPROVED
 */
export const WOMPI_EVENT_APPROVED: WompiWebhookEvent = {
  event: "transaction.updated",
  data: {
    transaction: {
      id: "txn_123456",
      reference: "duovial_1234567890_abc12345",
      status: "APPROVED",
      amount_in_cents: 1090000,
      created_at: "2026-07-01T10:00:00Z",
      finalized_at: "2026-07-01T10:00:05Z",
      payment_method_type: "CARD",
      currency: "COP",
    },
    signature: {
      checksum: "abc123def456ghi789",
      properties: [
        "transaction.id",
        "transaction.status",
        "transaction.amount_in_cents",
        "timestamp",
      ],
    },
    timestamp: Date.now(),
  },
  sent_at: "2026-07-01T10:00:10Z",
}

/**
 * Evento de webhook de Wompi con estado DECLINED
 */
export const WOMPI_EVENT_DECLINED: WompiWebhookEvent = {
  ...WOMPI_EVENT_APPROVED,
  data: {
    ...WOMPI_EVENT_APPROVED.data,
    transaction: {
      ...WOMPI_EVENT_APPROVED.data.transaction,
      id: "txn_789012",
      status: "DECLINED",
    },
  },
}

/**
 * Evento de webhook de Wompi con estado PENDING
 */
export const WOMPI_EVENT_PENDING: WompiWebhookEvent = {
  ...WOMPI_EVENT_APPROVED,
  data: {
    ...WOMPI_EVENT_APPROVED.data,
    transaction: {
      ...WOMPI_EVENT_APPROVED.data.transaction,
      id: "txn_345678",
      status: "PENDING",
    },
  },
}

/**
 * Mapa de estados Wompi → estados internos
 */
export const WOMPI_STATUS_MAP: Record<string, string> = {
  APPROVED: "approved",
  DECLINED: "declined",
  PENDING: "pending",
  VOIDED: "refunded",
  ERROR: "declined",
}

// ============================================
// FIXTURES — MUX
// ============================================

/**
 * Evento de webhook de Mux: video.asset.ready
 */
export const MUX_EVENT_ASSET_READY: MuxWebhookEvent = {
  type: "video.asset.ready",
  data: {
    id: "asset_abc123def456",
    playback_ids: [{ id: "playback_xyz789" }],
    status: "ready",
  },
}

/**
 * Evento de webhook de Mux: video.asset.errored
 */
export const MUX_EVENT_ASSET_ERRORED: MuxWebhookEvent = {
  type: "video.asset.errored",
  data: {
    id: "asset_error_001",
    playback_ids: [],
    status: "errored",
  },
}

/**
 * Evento de webhook de Mux ignorado
 */
export const MUX_EVENT_IGNORED: MuxWebhookEvent = {
  type: "video.asset.created",
  data: {
    id: "asset_created_001",
    status: "created",
  },
}

// ============================================
// FIXTURES — GOOGLE PLAY
// ============================================

export const GOOGLE_PURCHASE_SUBSCRIPTION_ACTIVE = {
  startTimeMillis: "1688169600000",
  expiryTimeMillis: "1690761600000",
  paymentState: 2, // 2 = Received
  cancelReason: 0,
  paymentDetails: {
    priceChangeDetails: null,
  },
}

export const GOOGLE_PURCHASE_SUBSCRIPTION_CANCELLED = {
  startTimeMillis: "1688169600000",
  expiryTimeMillis: "1690761600000",
  paymentState: 2,
  cancelReason: 1, // 1 = User cancelled
}

export const GOOGLE_PURCHASE_ONE_TIME = {
  startTimeMillis: "1688169600000",
  paymentState: 1, // 1 = Received
}

// ============================================
// FIXTURES — SEND PUSH NOTIFICATION
// ============================================

export const PUSH_REQUEST_WITH_PLAYER_IDS = {
  playerIds: ["player_abc123", "player_xyz789"],
  title: "Alerta de Incidente",
  message: "Se detectó una colisión en tu vehículo",
  data: { incidentId: "inc_001", type: "collision" },
}

export const PUSH_REQUEST_WITH_SEGMENT = {
  segment: "drivers",
  title: "Mantenimiento Preventivo",
  message: "Tu vehículo necesita servicio de aceite",
}

export const PUSH_REQUEST_INVALID_NO_TARGET = {
  title: "Test",
  message: "Test message",
  // Falta playerIds y segment
}

export const PUSH_REQUEST_INVALID_NO_TITLE = {
  playerIds: ["player_001"],
  message: "Test message",
  // Falta title
}

export const PUSH_REQUEST_INVALID_NO_MESSAGE = {
  playerIds: ["player_001"],
  title: "Test Title",
  // Falta message
}

// ============================================
// FIXTURES — CREATE WOMPI LINK
// ============================================

export const CREATE_LINK_REQUEST_VALID = {
  productId: "premium_monthly",
  name: "DuoVial Premium - Mensual",
  amountInCents: 1090000,
  currency: "COP",
  redirectUrl: "https://duovial.app/billing/result",
  orgId: "org_abc123",
}

export const CREATE_LINK_REQUEST_NO_PRODUCT_ID = {
  name: "DuoVial Premium",
  amountInCents: 1090000,
}

export const CREATE_LINK_REQUEST_NO_NAME = {
  productId: "premium_monthly",
  amountInCents: 1090000,
}

export const CREATE_LINK_REQUEST_NO_AMOUNT = {
  productId: "premium_monthly",
  name: "DuoVial Premium",
}

// ============================================
// FIXTURES — PROCESS RECURRING BILLING
// ============================================

export const SUBSCRIPTION_WITH_TOKEN = {
  id: "sub_001",
  user_id: "user_001",
  org_id: "org_001",
  product_id: "premium_monthly",
  external_id: "duovial_old_ref",
  status: "active",
  current_period_end: new Date(Date.now() - 86400000).toISOString(), // Ayer
  next_billing_date: new Date(Date.now() - 86400000).toISOString(),
  billing_attempts: 0,
  wompi_card_token_id: "token_001",
  amount_cop: 1090000,
  wompi_card_tokens: {
    id: "token_001",
    wompi_token: "tok_visa_1234",
    last_four: "1234",
    brand: "VISA",
  },
}

export const SUBSCRIPTION_NO_TOKEN = {
  ...SUBSCRIPTION_WITH_TOKEN,
  id: "sub_002",
  wompi_card_token_id: null,
  wompi_card_tokens: null,
}

export const SUBSCRIPTION_AFTER_2_ATTEMPTS = {
  ...SUBSCRIPTION_WITH_TOKEN,
  id: "sub_003",
  billing_attempts: 2,
}

export const SUBSCRIPTION_AFTER_3_ATTEMPTS = {
  ...SUBSCRIPTION_WITH_TOKEN,
  id: "sub_004",
  billing_attempts: 3,
}

// ============================================
// HELPERS — Funciones utilitarias
// ============================================

/**
 * Genera un ID único para tests
 */
export function randomId(prefix = "test"): string {
  return `${prefix}_${crypto.randomUUID().substring(0, 8)}`
}

/**
 * Genera una referencia Wompi
 */
export function generateWompiReference(subscriptionId?: string): string {
  const id = subscriptionId || randomId("sub")
  return `duovial_renewal_${id}_${Date.now()}`
}

/**
 * Calcula el checksum SHA256 para firma Wompi
 * (Para testing — NO usar en producción sin validación completa)
 */
export async function calculateWompiChecksum(
  data: string
): Promise<string> {
  const encoder = new TextEncoder()
  const hashBuffer = await crypto.subtle.digest("SHA-256", encoder.encode(data))
  const hashArray = Array.from(new Uint8Array(hashBuffer))
  return hashArray.map((b) => b.toString(16).padStart(2, "0")).join("")
}

/**
 * Calcula HMAC-SHA256 para firma Mux
 */
export async function calculateMuxHmac(
  secret: string,
  timestamp: string,
  body: string
): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  )

  const sig = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(`${timestamp}.${body}`)
  )

  return Array.from(new Uint8Array(sig))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("")
}

/**
 * Crea un Request mock para tests HTTP
 */
export function createMockRequest(
  method: string,
  body?: unknown,
  headers?: Record<string, string>
): Request {
  const url = "http://localhost:8000/functions/v1/test"
  return new Request(url, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
  })
}

/**
 * Extrae status y body de una Response
 */
export async function parseResponse(
  response: Response
): Promise<{ status: number; body: unknown }> {
  const body = await response.json()
  return { status: response.status, body }
}

/**
 * Espera un tiempo determinado (para simular delays)
 */
export function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/**
 * Valida que un string es un UUID válido
 */
export function isValidUUID(str: string): boolean {
  const uuidRegex =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
  return uuidRegex.test(str)
}

/**
 * Valida que un string es una URL válida
 */
export function isValidUrl(str: string): boolean {
  try {
    new URL(str)
    return true
  } catch {
    return false
  }
}
