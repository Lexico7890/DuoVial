package com.anonymous.DuoVial

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
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
            override fun onSpeedChanged(speed: Double) {
                sendSpeedEventToJS(speed)
            }
        }

        // Re-sincronizar el estado JS con el servicio inmediatamente después de
        // vincular el listener. Esto evita que la UI se quede con un estado obsoleto
        // (e.g. "INACTIVO") cuando el servicio ya está activo (e.g. "DUOVIAL ACTIVO")
        // tras un hot-reload, app reopen o crash recovery.
        BackgroundCameraService.instance?.resyncJsState()
        Log.d(TAG, "statusListener estático vinculado con éxito. Estado re-sincronizado.")
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

    private fun sendSpeedEventToJS(speed: Double) {
        try {
            val params = Arguments.createMap().apply {
                putDouble("speed", speed)
            }
            reactApplicationContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("onSpeedChanged", params)
        } catch (e: Exception) {
            // Ignorar
        }
    }

    @ReactMethod
    fun startStandby() {
        Log.d(TAG, "Iniciando cámara en modo Standby desde JS...")
        // Bandera anti-race: la subimos ANTES de enviar el intent. El Service
        // la baja síncronamente en onCreate. Mientras esté arriba, el
        // ViewManager NO crea un Preview local paralelo.
        BackgroundCameraService.markServiceStarting()
        val context = reactApplicationContext
        val intent = Intent(context, BackgroundCameraService::class.java).apply {
            action = "ACTION_START_STANDBY"
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            // Si startForegroundService falla, dejamos la bandera en true como
            // seguridad: el siguiente intento de startStandby/startRecording
            // podrá sobrescribirla. Pero también la bajamos para no dejar
            // al ViewManager bloqueado si el Service no va a llegar.
            BackgroundCameraService.clearServiceStarting()
            Log.e(TAG, "Error al arrancar el servicio de cámara en modo Standby: ${e.message}")
        }
    }

    @ReactMethod
    fun startRecording() {
        Log.d(TAG, "Iniciando grabación en segundo plano desde JS...")
        BackgroundCameraService.markServiceStarting()
        val context = reactApplicationContext
        val intent = Intent(context, BackgroundCameraService::class.java).apply {
            action = "ACTION_START_RECORDING"
        }
        try {
            ContextCompat.startForegroundService(context, intent)
            sendStatusEventToJS("INICIANDO DUOVIAL")
        } catch (e: Exception) {
            BackgroundCameraService.clearServiceStarting()
            Log.e(TAG, "Error al arrancar el servicio de grabación de cámara nativo: ${e.message}")
        }
    }

    @ReactMethod
    fun stopRecording() {
        Log.d(TAG, "Deteniendo grabación desde JS con guardado seguro...")
        val context = reactApplicationContext
        val intent = Intent(context, BackgroundCameraService::class.java).apply {
            action = "ACTION_STOP_AND_SAVE"
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar ACTION_STOP_AND_SAVE al servicio: ${e.message}")
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

    /**
     * Setter del umbral de G-Force. Acepta un Double desde JS. Si el Service
     * aún no está vivo, el valor se almacena en un pendingThreshold que se
     * aplicará en el primer onCreate.
     */
    @ReactMethod
    fun setGForceThreshold(threshold: Double) {
        val service = BackgroundCameraService.instance
        if (service != null) {
            service.setGForceThreshold(threshold)
        } else {
            // Guardar para aplicar cuando el Service arranque
            BackgroundCameraService.pendingGForceThreshold = threshold
            Log.d(TAG, "Service no vivo; umbral pendiente: $threshold G")
        }
    }

    /**
     * Getter del umbral actual. Útil para que la UI muestre el valor real
     * (no el hardcodeado en JS) tras un reinicio del Service.
     */
    @ReactMethod
    fun getGForceThreshold(promise: Promise) {
        val service = BackgroundCameraService.instance
        val value = service?.getGForceThreshold()
            ?: BackgroundCameraService.pendingGForceThreshold
            ?: 2.5
        promise.resolve(value)
    }
}
