package com.anonymous.DuoVial

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
 * 1. Transmite transiciones de estado nativas a React Native JS.
 * 2. Transmite telemetría del acelerómetro en tiempo real rate-limitada para evitar lags en el JS bridge.
 * 3. Expone control de arranque, parada y pánico.
 * 4. Expone la solicitud interactiva de permisos de burbuja flotante (Draw Overlays).
 */
class BackgroundCameraModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private val TAG = "DuoVial_CameraModule"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                when (intent.action) {
                    "com.anonymous.DuoVial.CAMERA_STATUS_CHANGED" -> {
                        val status = intent.getStringExtra("status") ?: "INACTIVO"
                        sendStatusEventToJS(status)
                    }
                    "com.anonymous.DuoVial.ACCEL_CHANGED" -> {
                        val gForce = intent.getDoubleExtra("gForce", 1.0)
                        sendAccelEventToJS(gForce)
                    }
                }
            }
        }
    }

    init {
        // Registrar el local receiver para escuchar el estado del servicio y acelerómetro nativos
        val filter = IntentFilter().apply {
            addAction("com.anonymous.DuoVial.CAMERA_STATUS_CHANGED")
            addAction("com.anonymous.DuoVial.ACCEL_CHANGED")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                reactContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                reactContext.registerReceiver(receiver, filter)
            }
            Log.d(TAG, "BroadcastReceiver registrado para escuchar estado y aceleración.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al registrar BroadcastReceiver: ${e.message}")
        }
    }

    override fun getName(): String {
        return "BackgroundCameraModule"
    }

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        try {
            reactApplicationContext.unregisterReceiver(receiver)
            Log.d(TAG, "BroadcastReceiver desregistrado con éxito.")
        } catch (e: Exception) {
            // Ya desregistrado
        }
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
        Log.d(TAG, "Deteniendo grabación desde JS...")
        val context = reactApplicationContext
        val intent = Intent(context, BackgroundCameraService::class.java)
        try {
            context.stopService(intent)
            sendStatusEventToJS("INACTIVO")
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
