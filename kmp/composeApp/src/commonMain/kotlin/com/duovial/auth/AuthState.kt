package com.duovial.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AuthUser(
    val email: String = "",
    val username: String = "",
    val isLoggedIn: Boolean = false,
    val needsConfirmation: Boolean = false
)

data class AuthState(
    val user: AuthUser? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val mode: AuthMode = AuthMode.LOGIN
)

enum class AuthMode { LOGIN, SIGNUP, CONFIRM }

object AuthStateManager {
    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun setLoading(loading: Boolean) {
        _authState.value = _authState.value.copy(isLoading = loading, error = null)
    }

    fun setError(message: String) {
        _authState.value = _authState.value.copy(isLoading = false, error = message)
    }

    fun setUser(user: AuthUser) {
        _authState.value = AuthState(user = user, isLoading = false)
    }

    fun setLoggedOut() {
        _authState.value = AuthState(user = null, isLoading = false)
    }

    fun setNeedsConfirmation(email: String) {
        _authState.value = AuthState(
            user = AuthUser(email = email, needsConfirmation = true),
            mode = AuthMode.CONFIRM,
            isLoading = false
        )
    }

    fun setMode(mode: AuthMode) {
        _authState.value = _authState.value.copy(mode = mode, error = null)
    }

    fun clearError() {
        _authState.value = _authState.value.copy(error = null)
    }
}
