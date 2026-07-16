package com.duovial.auth

/**
 * Interfaz que define el contrato de autenticación.
 * Implementada en androidMain por SupabaseAuthService.
 *
 * Patrón: Interface en commonMain, implementación en androidMain.
 * Esto permite usar la misma interfaz en tests y en otras plataformas.
 */
interface AuthService {
    // ── Operaciones principales ──────────────────────────────────────

    /** Iniciar sesión con email y contraseña */
    suspend fun login(email: String, password: String)

    /** Registrar nuevo usuario con email y contraseña */
    suspend fun signUp(email: String, password: String)

    /** Confirmar registro con código de verificación (si confirmación está habilitada) */
    suspend fun confirmSignUp(email: String, code: String)

    /** Reenviar código de confirmación */
    suspend fun resendConfirmationCode(email: String)

    /** Cerrar sesión y limpiar tokens */
    suspend fun logout()

    // ── Google OAuth ─────────────────────────────────────────────────

    /** Iniciar sesión con Google usando el ID token obtenido de Google Sign-In */
    suspend fun signInWithGoogle(idToken: String)

    // ── Recuperación de contraseña ──────────────────────────────────

    /** Enviar email de recuperación de contraseña */
    suspend fun resetPassword(email: String)

    // ── Sesión anónima ──────────────────────────────────────────────

    /** Iniciar sesión anónima (sin email) */
    suspend fun signInAnonymously()

    /** Vincular cuenta anónima a email+contraseña */
    suspend fun linkToEmail(email: String, password: String)

    /** Verificar si la sesión actual es anónima */
    fun isAnonymous(): Boolean

    // ── Información del usuario ─────────────────────────────────────

    /** Obtener el usuario actual (null si no hay sesión) */
    suspend fun getCurrentUser(): AuthUser?

    /** Verificar si hay una sesión válida */
    suspend fun hasValidSession(): Boolean

    /** Refrescar la sesión actual */
    suspend fun refreshSession()
}
