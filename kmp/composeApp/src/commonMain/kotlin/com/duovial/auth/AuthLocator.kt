package com.duovial.auth

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal para inyectar AuthService en la jerarquía de Compose.
 * Permite acceder al servicio de auth desde cualquier composable sin pasar parámetros.
 */
val LocalAuthService = compositionLocalOf<AuthService?> { null }

/**
 * CompositionLocal para inyectar AuthViewModel en la jerarquía de Compose.
 * Útil para acceder a las acciones de auth desde cualquier composable.
 */
val LocalAuthViewModel = compositionLocalOf<AuthViewModel?> { null }
