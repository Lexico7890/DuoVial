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
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Implementación de AuthService usando Supabase Auth.
 */
class SupabaseAuthService(
    private val context: Context
) : AuthService {

    private val TAG = "DuoVial_SupabaseAuth"
    private val supabase get() = SupabaseClientProvider.getClient()

    init {
        checkExistingSession()
    }

    private fun checkExistingSession() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = supabase.auth.currentSessionOrNull()
                if (session != null && !session.isExpired()) {
                    val user = session.user?.toAuthUser()
                    if (user != null) {
                        AuthStateManager.setUser(user)
                        Log.i(TAG, "Sesión restaurada: ${user.email}")
                    } else {
                        AuthStateManager.setLoggedOut()
                    }
                } else if (session != null && session.isExpired()) {
                    try {
                        supabase.auth.refreshCurrentSession()
                        val refreshedSession = supabase.auth.currentSessionOrNull()
                        val user = refreshedSession?.user?.toAuthUser()
                        if (user != null) {
                            AuthStateManager.setUser(user)
                        } else {
                            AuthStateManager.setLoggedOut()
                        }
                    } catch (e: Exception) {
                        AuthStateManager.setLoggedOut()
                    }
                } else {
                    AuthStateManager.setLoggedOut()
                }
            } catch (e: Exception) {
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
                val user = supabase.auth.currentUserOrNull()
                if (user != null && user.emailConfirmedAt != null) {
                    val authUser = user.toAuthUser()
                    if (authUser != null) {
                        AuthStateManager.setUser(authUser)
                    }
                } else {
                    AuthStateManager.setNeedsConfirmation(email)
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
                // Verificar OTP de email
                supabase.auth.verifyEmailOtp(
                    email = email,
                    token = code
                )
                val user = supabase.auth.currentUserOrNull()?.toAuthUser()
                if (user != null) {
                    AuthStateManager.setUser(user)
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
                // Reenviar email de verificación
                supabase.auth.sendVerificationEmail(email)
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
            } catch (e: Exception) {
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
            } catch (e: Exception) {
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
            } catch (e: Exception) {
                AuthStateManager.setError(SupabaseErrorHandler.mapError(e))
            }
        }
    }

    override suspend fun linkToEmail(email: String, password: String) {
        withContext(Dispatchers.IO) {
            try {
                AuthStateManager.setLoading(true)
                supabase.auth.updateUser {
                    this.email = email
                    this.password = password
                }
                val user = supabase.auth.currentUserOrNull()?.toAuthUser()
                if (user != null) {
                    AuthStateManager.setUser(user)
                }
            } catch (e: Exception) {
                AuthStateManager.setError(SupabaseErrorHandler.mapError(e))
            }
        }
    }

    override fun isAnonymous(): Boolean {
        return try {
            val user = supabase.auth.currentUserOrNull()
            user?.email == null
        } catch (e: Exception) {
            false
        }
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

    private fun UserInfo.toAuthUser(): AuthUser? {
        val isUserAnonymous = email == null
        return AuthUser(
            id = id,
            email = email ?: "",
            username = email ?: "",
            displayName = userMetadata?.get("full_name")?.toString(),
            avatarUrl = userMetadata?.get("avatar_url")?.toString(),
            isLoggedIn = true,
            isAnonymous = isUserAnonymous
        )
    }

    private fun UserSession.isExpired(): Boolean {
        return expiresAt?.let {
            kotlinx.datetime.Clock.System.now() > it
        } ?: true
    }
}
