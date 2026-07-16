package com.duovial.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duovial.supabase.SupabaseClientProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para la pantalla de autenticación.
 * Gestiona el estado del formulario (email, password, modo, errores).
 *
 * Patrón: ViewModel en commonMain, puede usar cualquier implementación de AuthService.
 */
class AuthViewModel : ViewModel() {

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _confirmCode = MutableStateFlow("")
    val confirmCode: StateFlow<String> = _confirmCode.asStateFlow()

    private val _showPassword = MutableStateFlow(false)
    val showPassword: StateFlow<Boolean> = _showPassword.asStateFlow()

    private val _resetPasswordEmail = MutableStateFlow("")
    val resetPasswordEmail: StateFlow<String> = _resetPasswordEmail.asStateFlow()

    // ── Acciones de formulario ──────────────────────────────────────

    fun onEmailChange(value: String) {
        _email.value = value
        AuthStateManager.clearError()
    }

    fun onPasswordChange(value: String) {
        _password.value = value
        AuthStateManager.clearError()
    }

    fun onConfirmCodeChange(value: String) {
        _confirmCode.value = value
        AuthStateManager.clearError()
    }

    fun onResetPasswordEmailChange(value: String) {
        _resetPasswordEmail.value = value
    }

    fun toggleShowPassword() {
        _showPassword.value = !_showPassword.value
    }

    // ── Acciones de auth ────────────────────────────────────────────

    fun login(authService: AuthService?) {
        val emailValue = _email.value.trim()
        val passwordValue = _password.value

        if (emailValue.isBlank() || passwordValue.isBlank()) {
            AuthStateManager.setError("Ingresa email y contraseña")
            return
        }

        viewModelScope.launch {
            authService?.login(emailValue, passwordValue)
        }
    }

    fun signUp(authService: AuthService?) {
        val emailValue = _email.value.trim()
        val passwordValue = _password.value

        if (emailValue.isBlank() || passwordValue.isBlank()) {
            AuthStateManager.setError("Ingresa email y contraseña")
            return
        }

        if (passwordValue.length < 6) {
            AuthStateManager.setError("La contraseña debe tener al menos 6 caracteres")
            return
        }

        viewModelScope.launch {
            authService?.signUp(emailValue, passwordValue)
        }
    }

    fun confirmSignUp(authService: AuthService?) {
        val emailValue = _email.value.trim()
        val code = _confirmCode.value.trim()

        if (emailValue.isBlank() || code.isBlank()) {
            AuthStateManager.setError("Ingresa el código de confirmación")
            return
        }

        viewModelScope.launch {
            authService?.confirmSignUp(emailValue, code)
        }
    }

    fun resendCode(authService: AuthService?) {
        val emailValue = _email.value.trim()
        if (emailValue.isBlank()) return

        viewModelScope.launch {
            authService?.resendConfirmationCode(emailValue)
        }
    }

    fun resetPassword(authService: AuthService?) {
        val emailValue = _email.value.trim()
        if (emailValue.isBlank()) {
            AuthStateManager.setError("Ingresa tu email")
            return
        }

        viewModelScope.launch {
            authService?.resetPassword(emailValue)
        }
    }

    fun signInWithGoogle(idToken: String, authService: AuthService?) {
        viewModelScope.launch {
            authService?.signInWithGoogle(idToken)
        }
    }

    fun signInAnonymously(authService: AuthService?) {
        viewModelScope.launch {
            authService?.signInAnonymously()
        }
    }

    // ── Navegación ──────────────────────────────────────────────────

    fun switchMode(mode: AuthMode) {
        AuthStateManager.setMode(mode)
        _confirmCode.value = ""
    }

    fun clearForm() {
        _email.value = ""
        _password.value = ""
        _confirmCode.value = ""
        _showPassword.value = false
        AuthStateManager.clearError()
    }
}
