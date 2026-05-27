package com.anonymous.DuoVial

import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * Módulo puente para comunicar React Native JavaScript con el servicio nativo de cámara.
 */
class BackgroundCameraModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private val TAG = "DuoVial_CameraModule"

    override fun getName(): String {
        return "BackgroundCameraModule"
    }

    @ReactMethod
    fun startRecording() {
        Log.d(TAG, "Iniciando grabación en segundo plano desde JS...")
        val context = reactApplicationContext
        val intent = Intent(context, BackgroundCameraService::class.java)
        try {
            ContextCompat.startForegroundService(context, intent)
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
