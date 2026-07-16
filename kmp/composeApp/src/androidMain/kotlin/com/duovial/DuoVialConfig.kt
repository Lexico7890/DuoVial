package com.duovial

import com.duovial.BuildConfig

/**
 * Configuración de DuoVial.
 *
 * Los valores se inyectan desde local.properties (gitignored) via BuildConfig.
 * NUNCA hardcodear credenciales en el código fuente.
 *
 * Para configurar, crea/edita kmp/local.properties:
 *   SUPABASE_URL=https://tu-proyecto.supabase.co
 *   SUPABASE_ANON_KEY=tu-anon-key
 *   GOOGLE_WEB_CLIENT_ID=tu-client-id.apps.googleusercontent.com
 */
data class DuoVialConfig(
    val supabaseUrl: String = BuildConfig.SUPABASE_URL,
    val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY,
    val googleWebClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID
) {
    /** Verifica si la configuración de Supabase está completa */
    val isSupabaseConfigured: Boolean
        get() = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()

    /** Verifica si Google Sign-In está configurado */
    val isGoogleConfigured: Boolean
        get() = googleWebClientId.isNotBlank()

    /** Verifica si la autenticación está configurada */
    val isAuthConfigured: Boolean
        get() = isSupabaseConfigured
}
