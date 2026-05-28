package com.anonymous.DuoVial

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule

/**
 * Módulo puente nativo de alto rendimiento que:
 * 1. Transmite transiciones de estado nativas a React Native JS mediante interface estática.
 * 2. Transmite telemetría del acelerómetro en tiempo real rate-limitada para evitar lags.
 * 3. Implementa arranque, detención con guardado (ACTION_STOP_AND_SAVE) y pánico.
 * 4. Expone la solicitud interactiva de permisos de burbuja flotante.
 */
class BackgroundCameraModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private val TAG = "DuoVial_CameraModule"

    init {
        // Registrar el statusListener estático para una comunicación 100% fiable
        BackgroundCameraService.statusListener = object : CameraStatusListener {
            override fun onStatusChanged(status: String) {
                sendStatusEventToJS(status)
            }
            override fun onAccelChanged(gForce: Double) {
                sendAccelEventToJS(gForce)
            }
        }
        Log.d(TAG, "statusListener estático vinculado con éxito.")
    }

    override fun getName(): String {
        return "BackgroundCameraModule"
    }

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        BackgroundCameraService.statusListener = null
        Log.d(TAG, "statusListener estático desvinculado con éxito.")
    }

    private fun sendStatusEventToJS(status: String) {
        try {
            val params = Arguments.createMap().apply {
                putString("status", status)
            }
            reactApplicationContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("onCameraStatusChanged", params)
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar evento de cámara a JS: ${e.message}")
        }
    }

    private fun sendAccelEventToJS(gForce: Double) {
        try {
            val params = Arguments.createMap().apply {
                putDouble("gForce", gForce)
            }
            reactApplicationContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("onAccelChanged", params)
        } catch (e: Exception) {
            // Ignorar para evitar spam
        }
    }

    @ReactMethod
    fun startRecording() {
        Log.d(TAG, "Iniciando grabación en segundo plano desde JS...")
        val context = reactApplicationContext
        val intent = Intent(context, BackgroundCameraService::class.java)
        try {
            ContextCompat.startForegroundService(context, intent)
            sendStatusEventToJS("INICIANDO DUOVIAL")
        } catch (e: Exception) {
            Log.e(TAG, "Error al arrancar el servicio de cámara nativo: ${e.message}")
        }
    }

    @ReactMethod
    fun stopRecording() {
        Log.d(TAG, "Deteniendo grabación desde JS con guardado seguro...")
        val context = reactApplicationContext
        // En lugar de matar el servicio directamente, enviamos el intent ACTION_STOP_AND_SAVE
        // para que guarde el buffer circular actual de pre-evento y luego se apague él mismo.
        val intent = Intent(context, BackgroundCameraService::class.java).apply {
            action = "ACTION_STOP_AND_SAVE"
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error al detener el servicio de cámara nativo: ${e.message}")
        }
    }

    @ReactMethod
    fun triggerPanic() {
        Log.d(TAG, "Disparando pánico manual desde JS...")
        val context = reactApplicationContext
        val intent = Intent(context, BackgroundCameraService::class.java).apply {
            action = "ACTION_TRIGGER_PANIC"
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar el intent de pánico al servicio: ${e.message}")
        }
    }

    @ReactMethod
    fun requestOverlayPermission() {
        val context = reactApplicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error al abrir pantalla de permisos de overlay: ${e.message}")
                }
            }
        }
    }
}
