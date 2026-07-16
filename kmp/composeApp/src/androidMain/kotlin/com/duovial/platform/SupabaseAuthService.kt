package com.duovial.platform

import android.content.Context
import android.util.Log
import com.duovial.auth.AuthService
import com.duovial.auth.AuthStateManager
import com.duovial.auth.AuthUser
import com.duovial.supabase.SupabaseClientProvider
import com.duovial.supabase.SupabaseErrorHandler
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementación de AuthService usando Supabase Auth.
 *
 * Reemplaza el AuthServiceAndroid (demo mode) con una integración real.
 * Maneja:
 * - Login/Registro con email+contraseña
 * - Google Sign-In via OAuth
 * - Recuperación de contraseña
 * - Sesiones anónimas
 * - Persistencia de sesión (Supabase SDK maneja tokens automáticamente)
 */
class SupabaseAuthService(
    private val context: Context
) : AuthService {

    private val TAG = "DuoVial_SupabaseAuth"
    private val supabase get() = SupabaseClientProvider.getClient()

    init {
        // Verificar sesión existente al inicializar
        checkExistingSession()
    }

    /**
     * Verifica si hay una sesión válida al arrancar la app.
     * Si hay refresh_token válido, refresca el access_token automáticamente.
     */
    private fun checkExistingSession() {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = supabase.auth.currentSessionOrNull()
                if (session != null && !session.isExpired()) {
                    val user = session.user?.toAuthUser()
                    if (user != null) {
                        AuthStateManager.setUser(user)
                        Log.i(TAG, "Sesión existente restaurada: ${user.email}")
                    } else {
                        AuthStateManager.setLoggedOut()
                    }
                } else if (session != null && session.isExpired()) {
                    // Intentar refrescar
                    try {
                        supabase.auth.refreshCurrentSession()
                        val refreshedSession = supabase.auth.currentSessionOrNull()
                        val user = refreshedSession?.user?.toAuthUser()
                        if (user != null) {
                            AuthStateManager.setUser(user)
                            Log.i(TAG, "Sesión refrescada: ${user.email}")
                        } else {
                            AuthStateManager.setLoggedOut()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "No se pudo refrescar sesión: ${e.message}")
                        AuthStateManager.setLoggedOut()
                    }
                } else {
                    AuthStateManager.setLoggedOut()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error verificando sesión: ${e.message}")
                AuthStateManager.setLoggedOut()
            }
        }
    }

    override suspend fun login(email: String, password: String) {
        withContext(Dispatchers.IO) {
            try {
                AuthStateManager.setLoading(true)
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                val user = supabase.auth.currentUserOrNull()?.toAuthUser()
                if (user != null) {
                    AuthStateManager.setUser(user)
                    Log.i(TAG, "Login exitoso: ${user.email}")
                } else {
                    AuthStateManager.setError("Error al obtener datos del usuario")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error login: ${e.message}")
                AuthStateManager.setError(SupabaseErrorHandler.mapError(e))
            }
        }
    }

    override suspend fun signUp(email: String, password: String) {
        withContext(Dispatchers.IO) {
            try {
                AuthStateManager.setLoading(true)
                supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                // Verificar si requiere confirmación
                val user = supabase.auth.currentUserOrNull()
                if (user != null && user.emailConfirmedAt != null) {
                    // Email confirmado automáticamente (confirmación deshabilitada)
                    val authUser = user.toAuthUser()
                    if (authUser != null) {
                        AuthStateManager.setUser(authUser)
                        Log.i(TAG, "Registro exitoso (sin confirmación): ${authUser.email}")
                    }
                } else {
                    // Requiere confirmación
                    AuthStateManager.setNeedsConfirmation(email)
                    Log.i(TAG, "Registro exitoso, confirmación pendiente: $email")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error signUp: ${e.message}")
                AuthStateManager.setError(SupabaseErrorHandler.mapError(e))
            }
        }
    }

    override suspend fun confirmSignUp(email: String, code: String) {
        withContext(Dispatchers.IO) {
            try {
                AuthStateManager.setLoading(true)
                supabase.auth.verifyEmailOtp(
                    email = email,
                    token = code,
                    type = io.github.jan.supabase.auth.models.EmailOtpType.SIGNUP
                )
                val user = supabase.auth.currentUserOrNull()?.toAuthUser()
                if (user != null) {
                    AuthStateManager.setUser(user)
                    Log.i(TAG, "Confirmación exitosa: ${user.email}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error confirmSignUp: ${e.message}")
                AuthStateManager.setError(SupabaseErrorHandler.mapError(e))
            }
        }
    }

    override suspend fun resendConfirmationCode(email: String) {
        withContext(Dispatchers.IO) {
            try {
                supabase.auth.sendEmailOTP(email = email)
                Log.i(TAG, "Código reenviado a: $email")
            } catch (e: Exception) {
                Log.e(TAG, "Error resendConfirmationCode: ${e.message}")
                AuthStateManager.setError(SupabaseErrorHandler.mapError(e))
            }
        }
    }

    override suspend fun logout() {
        withContext(Dispatchers.IO) {
            try {
                supabase.auth.signOut()
                AuthStateManager.setLoggedOut()
                Log.i(TAG, "Sesión cerrada correctamente")
            } catch (e: Exception) {
                Log.e(TAG, "Error logout: ${e.message}")
                // Cerrar sesión localmente aunque falle el remoto
                AuthStateManager.setLoggedOut()
            }
        }
    }

    override suspend fun signInWithGoogle(idToken: String) {
        withContext(Dispatchers.IO) {
            try {
                AuthStateManager.setLoading(true)
                supabase.auth.signInWith(IDToken) {
                    this.idToken = idToken
                    provider = Google
                }
                val user = supabase.auth.currentUserOrNull()?.toAuthUser()
                if (user != null) {
                    AuthStateManager.setUser(user)
                    Log.i(TAG, "Google Sign-In exitoso: ${user.email}")
                } else {
                    AuthStateManager.setError("Error al obtener datos del usuario")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error signInWithGoogle: ${e.message}")
                AuthStateManager.setError(SupabaseErrorHandler.mapError(e))
            }
        }
    }

    override suspend fun resetPassword(email: String) {
        withContext(Dispatchers.IO) {
            try {
                AuthStateManager.setLoading(true)
                supabase.auth.resetPasswordForEmail(email)
                AuthStateManager.setLoading(false)
                Log.i(TAG, "Email de recuperación enviado a: $email")
            } catch (e: Exception) {
                Log.e(TAG, "Error resetPassword: ${e.message}")
                AuthStateManager.setError(SupabaseErrorHandler.mapError(e))
            }
        }
    }

    override suspend fun signInAnonymously() {
        withContext(Dispatchers.IO) {
            try {
                AuthStateManager.setLoading(true)
                supabase.auth.signInAnonymously()
                AuthStateManager.setAnonymous()
                Log.i(TAG, "Sesión anónima iniciada")
            } catch (e: Exception) {
                Log.e(TAG, "Error signInAnonymously: ${e.message}")
                AuthStateManager.setError(SupabaseErrorHandler.mapError(e))
            }
        }
    }

    override suspend fun linkToEmail(email: String, password: String) {
        withContext(Dispatchers.IO) {
            try {
                AuthStateManager.setLoading(true)
                supabase.auth.linkIdentity(Email) {
                    this.email = email
                    this.password = password
                }
                val user = supabase.auth.currentUserOrNull()?.toAuthUser()
                if (user != null) {
                    AuthStateManager.setUser(user)
                    Log.i(TAG, "Cuenta vinculada a email: ${user.email}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error linkToEmail: ${e.message}")
                AuthStateManager.setError(SupabaseErrorHandler.mapError(e))
            }
        }
    }

    override fun isAnonymous(): Boolean {
        val user = supabase.auth.currentUserOrNull()
        return user?.isAnonymous == true
    }

    override suspend fun getCurrentUser(): AuthUser? {
        return supabase.auth.currentUserOrNull()?.toAuthUser()
    }

    override suspend fun hasValidSession(): Boolean {
        return try {
            val session = supabase.auth.currentSessionOrNull()
            session != null && !session.isExpired()
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun refreshSession() {
        withContext(Dispatchers.IO) {
            try {
                supabase.auth.refreshCurrentSession()
                val user = supabase.auth.currentUserOrNull()?.toAuthUser()
                if (user != null) {
                    AuthStateManager.setUser(user)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshSession: ${e.message}")
            }
        }
    }

    // ── Helpers privados ─────────────────────────────────────────────

    /**
     * Convierte un usuario de Supabase a nuestro modelo AuthUser.
     */
    private fun io.github.jan.supabase.auth.user.UserInfo.toAuthUser(): AuthUser? {
        return AuthUser(
            id = id,
            email = email ?: "",
            username = email ?: "",
            displayName = userMetadata?.get("full_name")?.toString(),
            avatarUrl = userMetadata?.get("avatar_url")?.toString(),
            isLoggedIn = true,
            isAnonymous = isAnonymous
        )
    }

    /**
     * Verifica si una sesión ha expirado.
     */
    private fun UserSession.isExpired(): Boolean {
        return expiresAt?.let { expiresAt ->
            kotlinx.datetime.Clock.System.now() > expiresAt
        } ?: true
    }
}
