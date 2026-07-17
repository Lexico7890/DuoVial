package com.duovial.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

/**
 * BootReceiver se ejecuta cuando el dispositivo se enciende.
 *
 * Su único propósito es iniciar el BackgroundCameraService en modo STANDBY
 * si el usuario habilitó la opción de "Auto-inicio" en Settings.
 *
 * Esto permite que el servicio esté corriendo y monitoreando la velocidad GPS
 * incluso si el usuario no ha abierto la app en la sesión actual.
 *
 * IMPORTANTE: El usuario debe haber abierto la app al menos UNA VEZ para que
 * las SharedPreferences existan y el auto-inicio esté configurado.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DuoVial_BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val receivedAction = intent?.action
        Log.i(TAG, "BootReceiver recibió acción: $receivedAction")

        if (receivedAction != Intent.ACTION_BOOT_COMPLETED && receivedAction != "android.intent.action.QUICKBOOT_POWERON") {
            Log.w(TAG, "Acción no reconocida: $receivedAction — ignorando.")
            return
        }

        // Verificar si el auto-inicio está habilitado en SharedPreferences
        val settings: Settings = SharedPreferencesSettings(
            context.getSharedPreferences("duovial_prefs", Context.MODE_PRIVATE)
        )
        val autoStartEnabled = settings.getBoolean("auto_start_enabled", false)

        if (!autoStartEnabled) {
            Log.i(TAG, "Auto-inicio deshabilitado. No se inicia el servicio.")
            return
        }

        Log.i(TAG, "Auto-inicio habilitado. Iniciando BackgroundCameraService en STANDBY...")

        try {
            val serviceIntent = Intent(context, BackgroundCameraService::class.java).apply {
                action = BackgroundCameraService.ACTION_START_STANDBY
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.i(TAG, "BackgroundCameraService iniciado correctamente.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar BackgroundCameraService: ${e.message}")
        }
    }
}
