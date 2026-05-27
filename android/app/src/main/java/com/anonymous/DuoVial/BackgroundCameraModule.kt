package com.anonymous.DuoVial

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule

/**
 * Módulo puente nativo que escucha los eventos del servicio y los retransmite a la UI en JavaScript en tiempo real.
 */
class BackgroundCameraModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private val TAG = "DuoVial_CameraModule"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null && intent.action == "com.anonymous.DuoVial.CAMERA_STATUS_CHANGED") {
                val status = intent.getStringExtra("status") ?: "INACTIVO"
                sendStatusEventToJS(status)
            }
        }
    }

    init {
        // Registrar el local receiver para escuchar el estado del servicio nativo
        val filter = IntentFilter("com.anonymous.DuoVial.CAMERA_STATUS_CHANGED")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                reactContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                reactContext.registerReceiver(receiver, filter)
            }
            Log.d(TAG, "BroadcastReceiver registrado para escuchar estado de la cámara.")
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

    @ReactMethod
    fun startRecording() {
        Log.d(TAG, "Iniciando grabación en segundo plano desde JS...")
        val context = reactApplicationContext
        val intent = Intent(context, BackgroundCameraService::class.java)
        try {
            ContextCompat.startForegroundService(context, intent)
            sendStatusEventToJS("VIGILANDO")
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
}
