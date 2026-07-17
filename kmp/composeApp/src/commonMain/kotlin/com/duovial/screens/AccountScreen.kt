package com.duovial.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
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
fun AccountScreen(
    authService: AuthService? = null,
    onGoogleSignIn: () -> Unit = {}
) {
    val authState by AuthStateManager.authState.collectAsState()
    val scope = rememberCoroutineScope()
    var showLogin by remember { mutableStateOf(false) }
    val user = authState.user
    val isLoggedIn = user?.isLoggedIn == true

    Box(modifier = Modifier.fillMaxSize().background(DuoVialBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(10.dp))

            Text(
                text = if (isLoggedIn) "Mi Cuenta" else "Cuenta",
                style = MaterialTheme.typography.titleLarge,
                color = DuoVialTextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isLoggedIn) "Informacion de tu perfil"
                else "Inicia sesion para sincronizar tus datos",
                style = MaterialTheme.typography.bodySmall,
                color = DuoVialTextSecondary
            )
            Spacer(Modifier.height(24.dp))

            if (isLoggedIn && user != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DuoVialCardBackground)
                        .border(1.dp, DuoVialBorder, RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AccountCircle, null,
                            tint = DuoVialNeonGreen, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(user.email, style = MaterialTheme.typography.titleSmall,
                            color = DuoVialTextPrimary)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Has iniciado sesion correctamente.",
                        style = MaterialTheme.typography.bodySmall, color = DuoVialTextSecondary)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            scope.launch { authService?.logout() }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DuoVialNeonRed.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CERRAR SESION", style = MaterialTheme.typography.labelMedium,
                            color = DuoVialNeonRed)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Spacer(Modifier.height(40.dp))
                    Icon(Icons.Outlined.Lock, null, tint = DuoVialSurface,
                        modifier = Modifier.size(60.dp))
                    Text("Sin sesion iniciada", style = MaterialTheme.typography.titleMedium,
                        color = DuoVialTextPrimary)
                    Text("Inicia sesion para acceder a funciones premium y sincronizacion en la nube.",
                        style = MaterialTheme.typography.bodySmall, color = DuoVialTextSecondary,
                        modifier = Modifier.padding(horizontal = 32.dp))
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { showLogin = true },
                        colors = ButtonDefaults.buttonColors(containerColor = DuoVialNeonGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(0.7f).height(48.dp)
                    ) {
                        Text("INICIAR SESION", style = MaterialTheme.typography.labelLarge,
                            color = DuoVialBackground)
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }

        if (showLogin && authService != null) {
            LoginScreen(
                authService = authService,
                onGoogleSignIn = onGoogleSignIn,
                onClose = { showLogin = false }
            )
        }
    }
}
