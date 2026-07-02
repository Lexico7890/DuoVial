package com.duovial.state

interface CameraServiceManager {
    fun startStandby()
    fun startRecording()
    fun stopRecording()
    fun triggerPanic()
    fun forceReset()
    fun setGForceThreshold(threshold: Double)
    fun getGForceThreshold(): Double
    fun enableFatigueDetection(enable: Boolean)
    fun setEarThreshold(threshold: Double)
    fun setDurationThreshold(ms: Long)
    fun setMaxAlertsPerHour(max: Int)
    fun snoozeFatigueAlert(minutes: Int)
    fun getFatigueStatus(): FatigueConfig
    fun requestOverlayPermission()
    fun getTemperature(): Float
    fun setAutoStartEnabled(enabled: Boolean)
    fun isAutoStartEnabled(): Boolean
    fun cancelAutoStart()

    fun loadIncidents(): List<Incident>

    fun watchCameraState(): CameraState
    fun watchFaceStatus(): FaceStatus
}
