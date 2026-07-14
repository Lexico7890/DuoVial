/**
 * ============================================
 * TEST HELPERS — Validación de Fixtures
 * ============================================
 *
 * Verifica que los fixtures y helpers del Paso 1
 * están correctamente construidos.
 */

import { assertEquals, assertExists } from "https://deno.land/std@0.168.0/testing/asserts.ts"
import {
  WOMPI_EVENT_APPROVED,
  WOMPI_EVENT_DECLINED,
  WOMPI_STATUS_MAP,
  MUX_EVENT_ASSET_READY,
  MUX_EVENT_IGNORED,
  calculateWompiChecksum,
  calculateMuxHmac,
  randomId,
  generateWompiReference,
  isValidUUID,
  isValidUrl,
  PUSH_REQUEST_WITH_PLAYER_IDS,
  PUSH_REQUEST_WITH_SEGMENT,
  CREATE_LINK_REQUEST_VALID,
  SUBSCRIPTION_WITH_TOKEN,
} from "./helpers.ts"

// ============================================
// TESTS: Fixtures Wompi
// ============================================

Deno.test("helpers: WOMPI_EVENT_APPROVED tiene estructura correcta", () => {
  assertExists(WOMPI_EVENT_APPROVED.event)
  assertExists(WOMPI_EVENT_APPROVED.data)
  assertExists(WOMPI_EVENT_APPROVED.data.transaction)
  assertExists(WOMPI_EVENT_APPROVED.data.signature)
  assertEquals(WOMPI_EVENT_APPROVED.event, "transaction.updated")
  assertEquals(WOMPI_EVENT_APPROVED.data.transaction.status, "APPROVED")
})

Deno.test("helpers: WOMPI_EVENT_DECLINED tiene status DECLINED", () => {
  assertEquals(WOMPI_EVENT_DECLINED.data.transaction.status, "DECLINED")
})

Deno.test("helpers: WOMPI_STATUS_MAP mapea correctamente", () => {
  assertEquals(WOMPI_STATUS_MAP["APPROVED"], "approved")
  assertEquals(WOMPI_STATUS_MAP["DECLINED"], "declined")
  assertEquals(WOMPI_STATUS_MAP["PENDING"], "pending")
  assertEquals(WOMPI_STATUS_MAP["VOIDED"], "refunded")
  assertEquals(WOMPI_STATUS_MAP["ERROR"], "declined")
})

// ============================================
// TESTS: Fixtures Mux
// ============================================

Deno.test("helpers: MUX_EVENT_ASSET_READY tiene playback_id", () => {
  assertExists(MUX_EVENT_ASSET_READY.data.playback_ids)
  assertEquals(MUX_EVENT_ASSET_READY.data.playback_ids!.length > 0, true)
  assertEquals(MUX_EVENT_ASSET_READY.data.playback_ids![0].id, "playback_xyz789")
})

Deno.test("helpers: MUX_EVENT_IGNORED tiene tipo diferente a video.asset.ready", () => {
  assertEquals(MUX_EVENT_IGNORED.type !== "video.asset.ready", true)
})

// ============================================
// TESTS: Fixtures Push Notification
// ============================================

Deno.test("helpers: PUSH_REQUEST_WITH_PLAYER_IDS tiene playerIds", () => {
  assertExists(PUSH_REQUEST_WITH_PLAYER_IDS.playerIds)
  assertEquals(PUSH_REQUEST_WITH_PLAYER_IDS.playerIds!.length, 2)
})

Deno.test("helpers: PUSH_REQUEST_WITH_SEGMENT tiene segment", () => {
  assertExists(PUSH_REQUEST_WITH_SEGMENT.segment)
  assertEquals(PUSH_REQUEST_WITH_SEGMENT.segment, "drivers")
})

// ============================================
// TESTS: Fixtures Create Wompi Link
// ============================================

Deno.test("helpers: CREATE_LINK_REQUEST_VALID tiene todos los campos", () => {
  assertExists(CREATE_LINK_REQUEST_VALID.productId)
  assertExists(CREATE_LINK_REQUEST_VALID.name)
  assertExists(CREATE_LINK_REQUEST_VALID.amountInCents)
  assertEquals(CREATE_LINK_REQUEST_VALID.amountInCents, 1090000)
})

// ============================================
// TESTS: Fixtures Recurring Billing
// ============================================

Deno.test("helpers: SUBSCRIPTION_WITH_TOKEN tiene token", () => {
  assertExists(SUBSCRIPTION_WITH_TOKEN.wompi_card_tokens)
  assertExists(SUBSCRIPTION_WITH_TOKEN.wompi_card_tokens.wompi_token)
  assertEquals(SUBSCRIPTION_WITH_TOKEN.wompi_card_tokens.brand, "VISA")
})

// ============================================
// TESTS: Funciones utilitarias
// ============================================

Deno.test("helpers: randomId genera ID con prefijo", () => {
  const id = randomId("test")
  assertEquals(id.startsWith("test_"), true)
  assertEquals(id.length > 5, true)
})

Deno.test("helpers: randomId genera IDs únicos", () => {
  const id1 = randomId()
  const id2 = randomId()
  assertEquals(id1 !== id2, true)
})

Deno.test("helpers: generateWompiReference genera referencia válida", () => {
  const ref = generateWompiReference("sub_001")
  assertEquals(ref.startsWith("duovial_renewal_sub_001_"), true)
})

Deno.test("helpers: generateWompiReference sin ID usa random", () => {
  const ref = generateWompiReference()
  assertEquals(ref.startsWith("duovial_renewal_"), true)
})

Deno.test("helpers: calculateWompiChecksum retorna hash SHA256", async () => {
  const checksum = await calculateWompiChecksum("test_data")
  assertEquals(typeof checksum, "string")
  assertEquals(checksum.length, 64) // SHA256 hex = 64 chars
})

Deno.test("helpers: calculateWompiChecksum es determinista", async () => {
  const checksum1 = await calculateWompiChecksum("same_input")
  const checksum2 = await calculateWompiChecksum("same_input")
  assertEquals(checksum1, checksum2)
})

Deno.test("helpers: calculateMuxHmac retorna hash HMAC", async () => {
  const hmac = await calculateMuxHmac("secret", "12345", "body")
  assertEquals(typeof hmac, "string")
  assertEquals(hmac.length, 64)
})

Deno.test("helpers: isValidUUID valida UUIDs correctamente", () => {
  assertEquals(isValidUUID("550e8400-e29b-41d4-a716-446655440000"), true)
  assertEquals(isValidUUID("not-a-uuid"), false)
  assertEquals(isValidUUID(""), false)
})

Deno.test("helpers: isValidUrl valida URLs correctamente", () => {
  assertEquals(isValidUrl("https://supabase.co"), true)
  assertEquals(isValidUrl("http://localhost:8000"), true)
  assertEquals(isValidUrl("not-a-url"), false)
  assertEquals(isValidUrl(""), false)
})
