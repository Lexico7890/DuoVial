package com.duovial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.duovial.auth.AuthService
import com.duovial.platform.AuthServiceAndroid
import com.duovial.platform.CameraServiceManagerAndroid
import com.duovial.platform.IncidentRepository
import com.duovial.platform.Permissions
import com.duovial.platform.SettingsManagerAndroid
import com.duovial.state.AppStateManager
import com.duovial.state.CameraServiceManager
import com.duovial.state.OnboardingManager
import com.duovial.theme.DuoVialTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var serviceManager: CameraServiceManagerAndroid
    private lateinit var settingsManager: SettingsManagerAndroid
    private lateinit var onboardingManager: OnboardingManager
    private var authService: AuthService? = null

    // Estado del onboarding (reactivo para Compose)
    private var showOnboarding by mutableStateOf(false)

    // Estado de permisos para el onboarding (reactivo para Compose)
    private val permissionStatuses = mutableStateMapOf<String, Boolean>()

    // Configura aquí tus credenciales de AWS Cognito, o déjalas vacías
    // para usar la app en modo demo (sin login).
    private val config = DuoVialConfig(
        cognitoUserPoolId = "",
        cognitoClientId = "",
        cognitoRegion = "us-east-1"
    )

    // Launcher para permisos iniciales (pre-onboarding)
    private val initialPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { (permission, granted) ->
            permissionStatuses[permission] = granted
        }
        // Actualizar estados completos después de la solicitud
        permissionStatuses.putAll(Permissions.getAllPermissionStatuses(this))

        val allGranted = Permissions.allRequired().all { results[it] == true }
        if (allGranted) {
            scope.launch {
                restoreSettings()
                serviceManager.startStandby()
            }
        }
    }

    // Launcher para permisos del onboarding
    private val onboardingPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { (permission, granted) ->
            permissionStatuses[permission] = granted
        }
        // Actualizar estados completos después de la solicitud
        permissionStatuses.putAll(Permissions.getAllPermissionStatuses(this))
    }
        val allGranted = Permissions.allRequired().all { results[it] == true }
        if (allGranted) {
            scope.launch {
                restoreSettings()
                serviceManager.startStandby()
            }
        }
    }

    // Launcher para permisos del onboarding
    private val onboardingPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { (permission, granted) ->
            permissionStatuses[permission] = granted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        settingsManager = SettingsManagerAndroid(this)
        onboardingManager = OnboardingManager(settingsManager)
        serviceManager = CameraServiceManagerAndroid(this, settingsManager)

        if (config.isAuthConfigured) {
            authService = AuthServiceAndroid(
                region = config.cognitoRegion,
                clientId = config.cognitoClientId,
                userPoolId = config.cognitoUserPoolId
            )
        }

        // Verificar si el onboarding fue completado
        scope.launch {
            // Cargar estados de permisos actuales
            permissionStatuses.putAll(Permissions.getAllPermissionStatuses(this@MainActivity))

            // TODO: TEMPORAL - Para testing, siempre mostrar onboarding
            // Cuando el diseño esté listo, restaurar la línea de abajo y eliminar showOnboarding = true
            // showOnboarding = !onboardingManager.isOnboardingCompleted()
            showOnboarding = true

            if (!showOnboarding) {
                // Onboarding ya completado, verificar permisos y arrancar servicio
                if (!Permissions.areAllGranted(this@MainActivity)) {
                    initialPermissionLauncher.launch(Permissions.allRequired())
                } else {
                    restoreSettings()
                    serviceManager.startStandby()
                }
            }
            // Si showOnboarding es true, el servicio NO se arranca hasta completar el onboarding
        }

        // Limpieza silenciosa de videos antiguos (>72 horas)
        scope.launch(Dispatchers.IO) {
            try {
                val deleted = IncidentRepository.cleanupOldIncidents(this@MainActivity)
                if (deleted > 0) {
                    android.util.Log.i("MainActivity", "Videos antiguos eliminados: $deleted")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error en limpieza de videos: ${e.message}")
            }
        }

        setContent {
            DuoVialTheme {
                DuoVialApp(
                    serviceManager = serviceManager,
                    authService = authService,
                    showOnboarding = showOnboarding,
                    onOnboardingCompleted = {
                        scope.launch {
                            onboardingManager.markAsCompleted()
                            showOnboarding = false

                            // Después del onboarding, verificar permisos y arrancar servicio
                            if (!Permissions.areAllGranted(this@MainActivity)) {
                                initialPermissionLauncher.launch(Permissions.allRequired())
                            } else {
                                restoreSettings()
                                serviceManager.startStandby()
                            }
                        }
                    },
                    onRequestPermissions = { permissions ->
                        // Mapear nombres de permisos de Android a nombres de manifesto
                        val androidPermissions = permissions.mapNotNull { perm ->
                            when (perm) {
                                "CAMERA" -> android.Manifest.permission.CAMERA
                                "ACCESS_FINE_LOCATION" -> android.Manifest.permission.ACCESS_FINE_LOCATION
                                "POST_NOTIFICATIONS" -> {
                                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                                        android.Manifest.permission.POST_NOTIFICATIONS
                                    } else null
                                }
                                "ACTIVITY_RECOGNITION" -> {
                                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                                        android.Manifest.permission.ACTIVITY_RECOGNITION
                                    } else null
                                }
                                "BLUETOOTH_CONNECT" -> {
                                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                                        android.Manifest.permission.BLUETOOTH_CONNECT
                                    } else null
                                }
                                else -> null
                            }
                        }.toTypedArray()

                        if (androidPermissions.isNotEmpty()) {
                            onboardingPermissionLauncher.launch(androidPermissions)
                        }

                        // SYSTEM_ALERT_WINDOW se maneja aparte (no es permiso de runtime)
                        if ("SYSTEM_ALERT_WINDOW" in permissions && !Permissions.canDrawOverlays(this@MainActivity)) {
                            Permissions.openOverlaySettings(this@MainActivity)
                        }
                    },
                    permissionStatuses = permissionStatuses.toMap(),
                    onOpenPermissionSettings = {
                        Permissions.openAppSettings(this@MainActivity)
                    },
                    onResetOnboarding = {
                        scope.launch {
                            onboardingManager.reset()
                            showOnboarding = true
                        }
                    }
                )
            }
        }
    }

    private suspend fun restoreSettings() {
        val gThreshold = settingsManager.getGForceThreshold()
        serviceManager.setGForceThreshold(gThreshold)

        val earThreshold = settingsManager.getEarThreshold()
        serviceManager.setEarThreshold(earThreshold)

        val durationThreshold = settingsManager.getDurationThresholdMs()
        serviceManager.setDurationThreshold(durationThreshold)

        val maxAlerts = settingsManager.getMaxAlertsPerHour()
        serviceManager.setMaxAlertsPerHour(maxAlerts)

        val autoStart = settingsManager.isAutoStartEnabled()
        serviceManager.setAutoStartEnabled(autoStart)

        // Auto-inicio inteligente
        val autoStartAsk = settingsManager.isAutoStartAskBeforeActivate()
        serviceManager.setAutoStartAskBeforeActivate(autoStartAsk)

        val autoStartCooldown = settingsManager.getAutoStartCooldownHours()
        serviceManager.setAutoStartCooldownHours(autoStartCooldown)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Service persists via foreground notification
    }
}
