package com.duovial.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.duovial.services.BackgroundCameraService
import com.duovial.services.CameraStatusListener
import com.duovial.state.AppStateManager
import com.duovial.state.CameraServiceManager
import com.duovial.state.CameraStatus
import com.duovial.state.FaceStatus
import com.duovial.state.FatigueConfig

class CameraServiceManagerAndroid(private val context: Context) : CameraServiceManager {

    init {
        BackgroundCameraService.statusListener = object : CameraStatusListener {
            override fun onStatusChanged(status: String) {
                val mapped = when {
                    status.contains("DUOVIAL ACTIVO") || status.contains("GRABANDO") -> CameraStatus.ACTIVO
                    status.contains("GENERANDO") || status.contains("GUARDANDO") -> CameraStatus.GUARDANDO
                    status.contains("INICIANDO") -> CameraStatus.INICIANDO
                    status.contains("ERROR") -> CameraStatus.ERROR
                    else -> CameraStatus.INACTIVO
                }
                AppStateManager.updateCameraStatus(mapped)
            }

            override fun onAccelChanged(gForce: Double) {
                AppStateManager.updateGForce(gForce)
            }

            override fun onSpeedChanged(speed: Double) {
                AppStateManager.updateSpeed(speed)
            }

            override fun onFaceStatusChanged(
                enabled: Boolean, faceDetected: Boolean, earValue: Double, closedEyeDuration: Double
            ) {
                AppStateManager.updateFaceStatus(
                    FaceStatus(enabled = enabled, faceDetected = faceDetected,
                        earValue = earValue, closedEyeDurationMs = closedEyeDuration.toLong())
                )
            }

            override fun onDrowsinessDetected(timestamp: Long, earValue: Double) { }
        }
    }

    private fun sendIntent(action: String) {
        val intent = Intent(context, BackgroundCameraService::class.java).apply { this.action = action }
        ContextCompat.startForegroundService(context, intent)
    }

    override fun startStandby() { sendIntent(BackgroundCameraService.ACTION_START_STANDBY) }
    override fun startRecording() { sendIntent(BackgroundCameraService.ACTION_START_RECORDING) }
    override fun stopRecording() { sendIntent(BackgroundCameraService.ACTION_STOP_AND_SAVE) }
    override fun triggerPanic() { sendIntent(BackgroundCameraService.ACTION_TRIGGER_PANIC) }

    override fun forceReset() {
        BackgroundCameraService.instance?.forceResetToStandby()
    }

    override fun setGForceThreshold(threshold: Double) {
        BackgroundCameraService.instance?.setGForceThreshold(threshold)
            ?: run { BackgroundCameraService.pendingGForceThreshold = threshold }
        AppStateManager.updateGForceThreshold(threshold)
    }

    override fun getGForceThreshold(): Double {
        return BackgroundCameraService.instance?.gForceThreshold
            ?: BackgroundCameraService.pendingGForceThreshold ?: 2.5
    }

    override fun enableFatigueDetection(enable: Boolean) {
        val action = if (enable) BackgroundCameraService.ACTION_ENABLE_FATIGUE
        else BackgroundCameraService.ACTION_DISABLE_FATIGUE
        sendIntent(action)
    }

    override fun setEarThreshold(threshold: Double) {
        val service = BackgroundCameraService.instance
        if (service != null) {
            service.setEarThreshold(threshold)
        } else {
            BackgroundCameraService.pendingEarThreshold = threshold
        }
    }

    override fun setDurationThreshold(ms: Long) {
        val intent = Intent(context, BackgroundCameraService::class.java).apply {
            action = BackgroundCameraService.ACTION_SET_DURATION_THRESHOLD
            putExtra("duration_ms", ms)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    override fun setMaxAlertsPerHour(max: Int) {
        val intent = Intent(context, BackgroundCameraService::class.java).apply {
            action = BackgroundCameraService.ACTION_SET_MAX_ALERTS
            putExtra("max_alerts", max)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    override fun snoozeFatigueAlert(minutes: Int) {
        sendIntent(BackgroundCameraService.ACTION_SNOOZE_FATIGUE)
        AppStateManager.updateFatigueConfig(
            AppStateManager.fatigueConfig.value.copy(isSnoozed = true)
        )
    }

    override fun getFatigueStatus(): FatigueConfig {
        val service = BackgroundCameraService.instance
        if (service != null) {
            val status = service.getFatigueStatus()
            return FatigueConfig(
                earThreshold = (status["earThreshold"] as? Double) ?: 0.2,
                durationThresholdMs = (status["closedEyeDuration"] as? Double)?.toLong() ?: 2000L,
                maxAlertsPerHour = (status["maxAlertsPerHour"] as? Int) ?: 3,
                isSnoozed = (status["isSnoozed"] as? Boolean) ?: false,
                alertCount = (status["alertCount"] as? Int) ?: 0
            )
        }
        return FatigueConfig()
    }

    override fun requestOverlayPermission() {
        Permissions.openOverlaySettings(context)
    }

    override fun watchCameraState() = AppStateManager.cameraState.value
    override fun watchFaceStatus() = AppStateManager.faceStatus.value
}
