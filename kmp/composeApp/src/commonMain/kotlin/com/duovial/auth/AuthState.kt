package com.duovial.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Representa un usuario autenticado en la app.
 */
data class AuthUser(
    val id: String = "",
    val email: String = "",
    val username: String = "",
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val isLoggedIn: Boolean = false,
    val isAnonymous: Boolean = false,
    val needsConfirmation: Boolean = false
)

/**
 * Estados posibles del flujo de autenticación.
 * Se usa para manejar la UI de login/registro de forma reactiva.
 */
sealed class AuthFlowState {
    /** Estado inicial, verificando sesión existente */
    data object Loading : AuthFlowState()

    /** Usuario no autenticado */
    data object Unauthenticated : AuthFlowState()

    /** Usuario autenticado con sesión válida */
    data class Authenticated(val user: AuthUser) : AuthFlowState()

    /** Error en la operación de auth */
    data class Error(val message: String) : AuthFlowState()

    /** Email de registro enviado, esperando confirmación */
    data class ConfirmationPending(val email: String) : AuthFlowState()
}

/**
 * Estado de la UI de autenticación (formularios, modos, etc).
 * Mantiene compatibilidad con el código existente.
 */
data class AuthState(
    val user: AuthUser? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val mode: AuthMode = AuthMode.LOGIN
)

enum class AuthMode { LOGIN, SIGNUP, CONFIRM, FORGOT_PASSWORD }

/**
 * Gestor singleton del estado de autenticación.
 * Mantiene compatibilidad con el código existente (LoginScreen, AccountScreen).
 */
object AuthStateManager {
    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _flowState = MutableStateFlow<AuthFlowState>(AuthFlowState.Loading)
    val flowState: StateFlow<AuthFlowState> = _flowState.asStateFlow()

    fun setLoading(loading: Boolean) {
        _authState.value = _authState.value.copy(isLoading = loading, error = null)
    }

    fun setError(message: String) {
        _authState.value = _authState.value.copy(isLoading = false, error = message)
        _flowState.value = AuthFlowState.Error(message)
    }

    fun setUser(user: AuthUser) {
        _authState.value = AuthState(user = user, isLoading = false)
        _flowState.value = AuthFlowState.Authenticated(user)
    }

    fun setLoggedOut() {
        _authState.value = AuthState(user = null, isLoading = false)
        _flowState.value = AuthFlowState.Unauthenticated
    }

    fun setNeedsConfirmation(email: String) {
        _authState.value = AuthState(
            user = AuthUser(email = email, needsConfirmation = true),
            mode = AuthMode.CONFIRM,
            isLoading = false
        )
        _flowState.value = AuthFlowState.ConfirmationPending(email)
    }

    fun setMode(mode: AuthMode) {
        _authState.value = _authState.value.copy(mode = mode, error = null)
    }

    fun clearError() {
        _authState.value = _authState.value.copy(error = null)
    }

    fun setFlowState(state: AuthFlowState) {
        _flowState.value = state
    }

    fun setAnonymous() {
        val anonUser = AuthUser(
            isLoggedIn = true,
            isAnonymous = true,
            username = "Anónimo"
        )
        _authState.value = AuthState(user = anonUser, isLoading = false)
        _flowState.value = AuthFlowState.Authenticated(anonUser)
    }
}
