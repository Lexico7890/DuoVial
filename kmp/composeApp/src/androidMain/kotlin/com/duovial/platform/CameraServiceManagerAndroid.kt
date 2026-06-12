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
        val intent = Intent(context, BackgroundCameraService::class.java).apply {
            action = BackgroundCameraService.ACTION_SET_EAR_THRESHOLD
            putExtra("ear_threshold", threshold)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    override fun setDurationThreshold(ms: Long) {
        BackgroundCameraService.instance?.frontDetector?.closedEyeDurationMs = ms
    }

    override fun setMaxAlertsPerHour(max: Int) {
        BackgroundCameraService.instance?.frontDetector?.maxAlertsPerHour = max
    }

    override fun snoozeFatigueAlert(minutes: Int) {
        sendIntent(BackgroundCameraService.ACTION_SNOOZE_FATIGUE)
        AppStateManager.updateFatigueConfig(
            AppStateManager.fatigueConfig.value.copy(isSnoozed = true)
        )
    }

    override fun getFatigueStatus(): FatigueConfig {
        val detector = BackgroundCameraService.instance?.frontDetector
        return FatigueConfig(
            earThreshold = detector?.earThreshold ?: 0.2,
            durationThresholdMs = detector?.closedEyeDurationMs ?: 2000L,
            maxAlertsPerHour = detector?.maxAlertsPerHour ?: 3,
            isSnoozed = detector?.isSnoozed ?: false,
            alertCount = detector?.alertCount ?: 0
        )
    }

    override fun requestOverlayPermission() {
        Permissions.openOverlaySettings(context)
    }

    override fun watchCameraState() = AppStateManager.cameraState.value
    override fun watchFaceStatus() = AppStateManager.faceStatus.value
}
