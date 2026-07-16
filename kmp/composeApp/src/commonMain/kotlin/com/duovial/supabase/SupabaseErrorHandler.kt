package com.duovial.supabase

/**
 * Manejador global de errores de Supabase.
 * Mapea excepciones técnicas a mensajes amigables en español.
 */
object SupabaseErrorHandler {

    /**
     * Mapea una excepción a un mensaje de error legible para el usuario.
     * @param e Excepción capturada
     * @return Mensaje de error en español
     */
    fun mapError(e: Exception): String {
        val message = e.message ?: ""

        return when {
            // Errores de red
            message.contains("Unable to resolve host", ignoreCase = true) ->
                "Sin conexión a internet. Verifica tu red."
            message.contains("timeout", ignoreCase = true) ->
                "Tiempo de espera agotado. Intenta de nuevo."
            message.contains("CONNECT_TIMEOUT", ignoreCase = true) ->
                "Tiempo de espera agotado. Intenta de nuevo."
            message.contains("ECONNREFUSED", ignoreCase = true) ->
                "No se pudo conectar al servidor."

            // Errores de autenticación
            message.contains("JWT expired", ignoreCase = true) ->
                "Sesión expirada. Inicia sesión nuevamente."
            message.contains("Invalid login credentials", ignoreCase = true) ->
                "Email o contraseña incorrectos."
            message.contains("Email not confirmed", ignoreCase = true) ->
                "Email no confirmado. Revisa tu bandeja de entrada."
            message.contains("User already registered", ignoreCase = true) ->
                "Este email ya está registrado."
            message.contains("Password should be at least", ignoreCase = true) ->
                "La contraseña debe tener al menos 6 caracteres."
            message.contains("Unable to validate email address", ignoreCase = true) ->
                "Email no válido. Verifica el formato."
            message.contains("Signup is disabled", ignoreCase = true) ->
                "El registro está deshabilitado temporalmente."
            message.contains("Rate limit exceeded", ignoreCase = true) ->
                "Demasiados intentos. Espera unos minutos e intenta de nuevo."
            message.contains("Email rate limit exceeded", ignoreCase = true) ->
                "Demasiados emails enviados. Espera unos minutos."

            // Errores de base de datos
            message.contains("duplicate key", ignoreCase = true) ->
                "El registro ya existe."
            message.contains("foreign key", ignoreCase = true) ->
                "Referencia no válida. Verifica los datos."
            message.contains("new row violates row-level security", ignoreCase = true) ->
                "No tienes permisos para realizar esta acción."

            // Errores de storage
            message.contains("file size exceeds", ignoreCase = true) ->
                "El archivo es demasiado grande."
            message.contains("mime type not allowed", ignoreCase = true) ->
                "Tipo de archivo no permitido."
            message.contains("Object not found", ignoreCase = true) ->
                "Archivo no encontrado en el servidor."

            // Errores de realtime
            message.contains("WebSocket", ignoreCase = true) ->
                "Error de conexión en tiempo real. Reconectando..."
            message.contains("Channel error", ignoreCase = true) ->
                "Error en el canal de tiempo real."

            // Error genérico
            else -> "Error inesperado: ${e.message ?: "Desconocido"}"
        }
    }

    /**
     * Extrae el código de error de una excepción de Supabase si está disponible.
     */
    fun getErrorCode(e: Exception): String? {
        val message = e.message ?: return null
        val regex = Regex("""\((\w+)\)""")
        return regex.find(message)?.groupValues?.get(1)
    }
}
