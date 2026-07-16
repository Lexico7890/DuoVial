package com.duovial.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.functions.Functions

/**
 * Singleton que gestiona la conexión al cliente de Supabase.
 * Se inicializa una sola vez al arrancar la app y se reutiliza en todo el proyecto.
 *
 * Configuración:
 * - Auth: scheme "duovial", host "callback" (deep links para OAuth)
 * - Realtime: habilitado para suscripciones WebSocket
 * - Storage: habilitado para subida de videos
 * - Postgrest: habilitado para consultas a la BD
 * - Functions: habilitado para llamar Edge Functions
 */
object SupabaseClientProvider {

    private var client: SupabaseClient? = null

    /**
     * Inicializa el cliente de Supabase. Debe llamarse UNA vez al arrancar la app.
     * @param url URL del proyecto Supabase (ej: "https://xyz.supabase.co")
     * @param key Anon key del proyecto Supabase
     */
    fun initialize(url: String, key: String) {
        if (client != null) return // Ya inicializado

        client = createSupabaseClient(url, key) {
            install(Auth) {
                scheme = "duovial"
                host = "callback"
            }
            install(Realtime)
            install(Storage)
            install(Postgrest)
            install(Functions)
        }
    }

    /**
     * Retorna el cliente de Supabase.
     * @throws IllegalStateException si no se ha llamado initialize() primero
     */
    fun getClient(): SupabaseClient {
        return client ?: throw IllegalStateException(
            "SupabaseClient no inicializado. Llama a SupabaseClientProvider.initialize() primero."
        )
    }

    /**
     * Verifica si el cliente ha sido inicializado.
     */
    fun isInitialized(): Boolean = client != null
}
