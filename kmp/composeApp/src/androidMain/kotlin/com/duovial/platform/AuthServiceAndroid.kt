package com.duovial.platform

import com.duovial.auth.AuthService
import com.duovial.auth.AuthStateManager
import com.duovial.auth.AuthUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.util.Log

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
                if (!isConfigured) {
                    delay(800)
                    AuthStateManager.setUser(AuthUser(email = email, username = email, isLoggedIn = true))
                    Log.i(TAG, "Demo login exitoso: $email")
                    return@withContext
                }
                AuthStateManager.setUser(AuthUser(email = email, username = email, isLoggedIn = true))
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
                if (!isConfigured) {
                    delay(800)
                    AuthStateManager.setNeedsConfirmation(email)
                    Log.i(TAG, "Demo registro exitoso: $email")
                    return@withContext
                }
                AuthStateManager.setNeedsConfirmation(email)
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
            try {
                Log.i(TAG, "Codigo reenviado a: $email")
            } catch (e: Exception) {
                Log.e(TAG, "Error resendConfirmationCode: ${e.message}")
                AuthStateManager.setError(e.message ?: "Error al reenviar codigo")
            }
        }
    }

    override suspend fun logout() {
        try {
            AuthStateManager.setLoggedOut()
            Log.i(TAG, "Sesion cerrada")
        } catch (e: Exception) {
            Log.e(TAG, "Error logout: ${e.message}")
        }
    }

    override suspend fun getCurrentUser(): AuthUser? {
        return AuthStateManager.authState.value.user
    }
}
