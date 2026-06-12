package com.duovial.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duovial.auth.AuthMode
import com.duovial.auth.AuthService
import com.duovial.auth.AuthStateManager
import com.duovial.theme.DuoVialBackground
import com.duovial.theme.DuoVialBorder
import com.duovial.theme.DuoVialCardBackground
import com.duovial.theme.DuoVialNeonGreen
import com.duovial.theme.DuoVialNeonRed
import com.duovial.theme.DuoVialSurface
import com.duovial.theme.DuoVialTextPrimary
import com.duovial.theme.DuoVialTextSecondary
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authService: AuthService? = null,
    onClose: () -> Unit = {}
) {
    val authState by AuthStateManager.authState.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmCode by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val mode = authState.mode

    val title = when (mode) {
        AuthMode.LOGIN -> "Iniciar Sesión"
        AuthMode.SIGNUP -> "Crear Cuenta"
        AuthMode.CONFIRM -> "Confirmar Código"
    }

    val subtitle = when (mode) {
        AuthMode.LOGIN -> "Ingresa tus credenciales DuoVial"
        AuthMode.SIGNUP -> "Regístrate para funciones premium"
        AuthMode.CONFIRM -> "Revisa tu correo e ingresa el código"
    }

    val canSubmit = when (mode) {
        AuthMode.CONFIRM -> email.isNotBlank() && confirmCode.isNotBlank() && !authState.isLoading
        else -> email.isNotBlank() && password.isNotBlank() && !authState.isLoading
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoVialBackground.copy(alpha = 0.97f)),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.Close, "Cerrar", tint = DuoVialTextSecondary)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(20.dp))
                .background(DuoVialCardBackground)
                .border(1.dp, DuoVialBorder, RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = DuoVialTextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DuoVialTextSecondary)
            Spacer(Modifier.height(24.dp))

            // Email field
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; AuthStateManager.clearError() },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                colors = textFieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Password field (not shown in confirm mode)
            if (mode != AuthMode.CONFIRM) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; AuthStateManager.clearError() },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = DuoVialTextSecondary
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (canSubmit) scope.launch { submitAction(authService, mode, email, password, confirmCode) }
                        }
                    ),
                    colors = textFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Confirm code field
            if (mode == AuthMode.CONFIRM) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmCode,
                    onValueChange = { confirmCode = it; AuthStateManager.clearError() },
                    label = { Text("Código de confirmación") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    colors = textFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Error message
            if (authState.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    authState.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = DuoVialNeonRed,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(20.dp))

            // Submit button
            Button(
                onClick = {
                    scope.launch {
                        submitAction(authService, mode, email, password, confirmCode)
                    }
                },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DuoVialNeonGreen,
                    disabledContainerColor = DuoVialSurface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (authState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = DuoVialBackground,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        when (mode) {
                            AuthMode.LOGIN -> "INICIAR SESIÓN"
                            AuthMode.SIGNUP -> "CREAR CUENTA"
                            AuthMode.CONFIRM -> "CONFIRMAR CÓDIGO"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = DuoVialBackground
                    )
                }
            }

            // Mode switcher
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                if (mode == AuthMode.LOGIN) {
                    Text("¿No tienes cuenta? ", style = MaterialTheme.typography.bodySmall,
                        color = DuoVialTextSecondary)
                    Text("Regístrate",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W800),
                        color = DuoVialNeonGreen,
                        modifier = Modifier.clickable { AuthStateManager.setMode(AuthMode.SIGNUP) }
                    )
                } else if (mode == AuthMode.SIGNUP) {
                    Text("¿Ya tienes cuenta? ", style = MaterialTheme.typography.bodySmall,
                        color = DuoVialTextSecondary)
                    Text("Inicia sesión",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W800),
                        color = DuoVialNeonGreen,
                        modifier = Modifier.clickable { AuthStateManager.setMode(AuthMode.LOGIN) }
                    )
                }
            }

            // Resend code (confirm mode)
            if (mode == AuthMode.CONFIRM) {
                Spacer(Modifier.height(12.dp))
                Text("Reenviar código",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W800),
                    color = DuoVialNeonGreen,
                    modifier = Modifier.clickable {
                        scope.launch { authService?.resendConfirmationCode(email) }
                    }
                )
            }
        }
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = DuoVialTextPrimary,
    unfocusedTextColor = DuoVialTextPrimary,
    focusedBorderColor = DuoVialNeonGreen,
    unfocusedBorderColor = DuoVialBorder,
    focusedLabelColor = DuoVialNeonGreen,
    unfocusedLabelColor = DuoVialTextSecondary,
    cursorColor = DuoVialNeonGreen
)

private suspend fun submitAction(
    authService: AuthService?,
    mode: AuthMode,
    email: String,
    password: String,
    code: String
) {
    when (mode) {
        AuthMode.LOGIN -> authService?.login(email.trim(), password)
        AuthMode.SIGNUP -> authService?.signUp(email.trim(), password)
        AuthMode.CONFIRM -> authService?.confirmSignUp(email.trim(), code.trim())
    }
}
