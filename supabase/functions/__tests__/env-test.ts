/**
 * ============================================
 * TEST MÍNIMO — Validación de Entorno
 * ============================================
 *
 * Verifica que el entorno de testing está correctamente
 * configurado antes de ejecutar tests reales.
 *
 * Ejecutar: deno test env-test.ts
 */

import { assertEquals, assertExists } from "https://deno.land/std@0.168.0/testing/asserts.ts"

Deno.test("entorno: Deno está disponible", () => {
  assertExists(Deno.version)
  assertExists(Deno.version.deno)
})

Deno.test("entorno: crypto.subtle está disponible (SHA-256)", async () => {
  const encoder = new TextEncoder()
  const data = encoder.encode("test")
  const hash = await crypto.subtle.digest("SHA-256", data)
  assertExists(hash)
  assertEquals(hash.byteLength, 32) // SHA-256 = 32 bytes
})

Deno.test("entorno: HMAC-SHA256 funciona", async () => {
  const encoder = new TextEncoder()
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode("secret_key"),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  )
  const sig = await crypto.subtle.sign("HMAC", key, encoder.encode("message"))
  assertExists(sig)
  assertEquals(sig.byteLength, 32)
})

Deno.test("entorno: crypto.randomUUID funciona", () => {
  const uuid = crypto.randomUUID()
  assertExists(uuid)
  assertEquals(typeof uuid, "string")
  assertEquals(uuid.length, 36) // UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
})

Deno.test("entorno: TextEncoder/TextDecoder funcionan", () => {
  const encoder = new TextEncoder()
  const decoder = new TextDecoder()
  const original = "Hola DuoVial"
  const encoded = encoder.encode(original)
  const decoded = decoder.decode(encoded)
  assertEquals(decoded, original)
})

Deno.test("entorno: JSON parse/stringify funciona", () => {
  const obj = { test: true, value: 42 }
  const json = JSON.stringify(obj)
  const parsed = JSON.parse(json)
  assertEquals(parsed, obj)
})

Deno.test("entorno: Date.now() retorna timestamp válido", () => {
  const now = Date.now()
  assertEquals(typeof now, "number")
  assertEquals(now > 0, true)
  // Verificar que es un timestamp razonable (después de 2020)
  assertEquals(now > 1577836800000, true)
})
