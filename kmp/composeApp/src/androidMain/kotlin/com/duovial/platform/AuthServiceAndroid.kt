package com.duovial.platform

import com.duovial.auth.AuthService
import com.duovial.auth.AuthStateManager
import com.duovial.auth.AuthUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.util.Log

/**
 * Implementación DEMO de AuthService.
 * Solo se usa cuando Supabase no está configurado.
 * NO hace llamadas reales a ningún backend.
 *
 * NOTA: Este archivo está en modo legacy. Para producción,
 * usar SupabaseAuthService.
 */
class AuthServiceAndroid(
    private val region: String = "us-east-1",
    private val clientId: String = "",
    private val userPoolId: String = ""
) : AuthService {

    private val TAG = "DuoVial_Auth"
    private val isConfigured = clientId.isNotBlank() && userPoolId.isNotBlank()

    override suspend fun login(email: String, password: String) {
        withContext(Dispatchers.IO) {
            try {
                AuthStateManager.setLoading(true)
                delay(800)
                AuthStateManager.setUser(AuthUser(email = email, username = email, isLoggedIn = true))
                Log.i(TAG, "Demo login exitoso: $email")
            } catch (e: Exception) {
                Log.e(TAG, "Error login: ${e.message}")
                AuthStateManager.setError(e.message ?: "Error de conexion")
            }
        }
    }

    override suspend fun signUp(email: String, password: String) {
        withContext(Dispatchers.IO) {
            try {
                AuthStateManager.setLoading(true)
                delay(800)
                AuthStateManager.setNeedsConfirmation(email)
                Log.i(TAG, "Demo registro exitoso: $email")
            } catch (e: Exception) {
                Log.e(TAG, "Error signUp: ${e.message}")
                AuthStateManager.setError(e.message ?: "Error al registrar")
            }
        }
    }

    override suspend fun confirmSignUp(email: String, code: String) {
        withContext(Dispatchers.IO) {
            try {
                AuthStateManager.setLoading(true)
                delay(600)
                AuthStateManager.setUser(AuthUser(email = email, username = email, isLoggedIn = true))
                Log.i(TAG, "Confirmacion exitosa: $email")
            } catch (e: Exception) {
                Log.e(TAG, "Error confirmSignUp: ${e.message}")
                AuthStateManager.setError(e.message ?: "Codigo invalido")
            }
        }
    }

    override suspend fun resendConfirmationCode(email: String) {
        withContext(Dispatchers.IO) {
            Log.i(TAG, "Codigo reenviado a: $email")
        }
    }

    override suspend fun logout() {
        AuthStateManager.setLoggedOut()
        Log.i(TAG, "Sesion cerrada")
    }

    override suspend fun signInWithGoogle(idToken: String) {
        withContext(Dispatchers.IO) {
            AuthStateManager.setLoading(true)
            delay(800)
            AuthStateManager.setUser(AuthUser(email = "google@demo.com", username = "Google Demo", isLoggedIn = true))
            Log.i(TAG, "Demo Google Sign-In")
        }
    }

    override suspend fun resetPassword(email: String) {
        withContext(Dispatchers.IO) {
            Log.i(TAG, "Demo resetPassword: $email")
        }
    }

    override suspend fun signInAnonymously() {
        withContext(Dispatchers.IO) {
            AuthStateManager.setLoading(true)
            delay(500)
            AuthStateManager.setAnonymous()
            Log.i(TAG, "Demo sesion anonima")
        }
    }

    override suspend fun linkToEmail(email: String, password: String) {
        withContext(Dispatchers.IO) {
            Log.i(TAG, "Demo linkToEmail: $email")
        }
    }

    override fun isAnonymous(): Boolean = false

    override suspend fun getCurrentUser(): AuthUser? {
        return AuthStateManager.authState.value.user
    }

    override suspend fun hasValidSession(): Boolean {
        return AuthStateManager.authState.value.user?.isLoggedIn == true
    }

    override suspend fun refreshSession() {
        // No-op en modo demo
    }
}
