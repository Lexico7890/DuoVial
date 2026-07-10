package com.duovial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import com.duovial.auth.AuthService
import com.duovial.platform.AuthServiceAndroid
import com.duovial.platform.CameraServiceManagerAndroid
import com.duovial.platform.IncidentRepository
import com.duovial.platform.Permissions
import com.duovial.platform.SettingsManagerAndroid
import com.duovial.state.AppStateManager
import com.duovial.state.CameraServiceManager
import com.duovial.state.SettingsManager
import com.duovial.theme.DuoVialTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var serviceManager: CameraServiceManagerAndroid
    private lateinit var settingsManager: SettingsManagerAndroid
    private var authService: AuthService? = null

    // Configura aquí tus credenciales de AWS Cognito, o déjalas vacías
    // para usar la app en modo demo (sin login).
    private val config = DuoVialConfig(
        cognitoUserPoolId = "",
        cognitoClientId = "",
        cognitoRegion = "us-east-1"
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = Permissions.allRequired().all { results[it] == true }
        if (allGranted) {
            scope.launch {
                restoreSettings()
                serviceManager.startStandby()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        settingsManager = SettingsManagerAndroid(this)
        serviceManager = CameraServiceManagerAndroid(this, settingsManager)

        if (config.isAuthConfigured) {
            authService = AuthServiceAndroid(
                region = config.cognitoRegion,
                clientId = config.cognitoClientId,
                userPoolId = config.cognitoUserPoolId
            )
        }

        if (!Permissions.areAllGranted(this)) {
            permissionLauncher.launch(Permissions.allRequired())
        } else {
            scope.launch {
                restoreSettings()
                serviceManager.startStandby()
            }
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
                    authService = authService
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
    }

    override fun onDestroy() {
        super.onDestroy()
        // Service persists via foreground notification
    }
}
