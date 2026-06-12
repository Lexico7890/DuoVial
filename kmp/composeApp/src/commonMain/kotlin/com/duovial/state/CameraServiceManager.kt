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

    fun watchCameraState(): CameraState
    fun watchFaceStatus(): FaceStatus
}
