package com.duovial.state

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CameraStatus {
    INACTIVO,
    INICIANDO,
    ACTIVO,
    GUARDANDO,
    ERROR
}

enum class TemperatureStatus {
    NORMAL, WARNING, DANGER, CRITICAL
}

data class CameraState(
    val status: CameraStatus = CameraStatus.INACTIVO,
    val gForce: Double = 1.0,
    val speedKph: Double = 0.0,
    val gForceThreshold: Double = 2.5,
    val bubbleActive: Boolean = false,
    val temperature: Float = 0f,
    val temperatureStatus: TemperatureStatus = TemperatureStatus.NORMAL
)

data class FaceStatus(
    val enabled: Boolean = false,
    val faceDetected: Boolean = false,
    val earValue: Double = 0.0,
    val closedEyeDurationMs: Long = 0L
)

data class FatigueConfig(
    val earThreshold: Double = 0.2,
    val durationThresholdMs: Long = 2000L,
    val maxAlertsPerHour: Int = 3,
    val isSnoozed: Boolean = false,
    val alertCount: Int = 0
)

data class Incident(
    val timestampSec: Long,
    val parts: List<String>,
    val date: String
)

sealed class AppEvent {
    data class DrowsinessDetected(val timestamp: Long, val earValue: Double) : AppEvent()
}

object AppStateManager {
    private val _cameraState = MutableStateFlow(CameraState())
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private val _faceStatus = MutableStateFlow(FaceStatus())
    val faceStatus: StateFlow<FaceStatus> = _faceStatus.asStateFlow()

    private val _fatigueConfig = MutableStateFlow(FatigueConfig())
    val fatigueConfig: StateFlow<FatigueConfig> = _fatigueConfig.asStateFlow()

    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    internal fun updateCameraStatus(status: CameraStatus) {
        _cameraState.value = _cameraState.value.copy(status = status)
    }

    internal fun updateGForce(gForce: Double) {
        _cameraState.value = _cameraState.value.copy(gForce = gForce)
    }

    internal fun updateSpeed(speedKph: Double) {
        _cameraState.value = _cameraState.value.copy(speedKph = speedKph)
    }

    internal fun updateGForceThreshold(threshold: Double) {
        _cameraState.value = _cameraState.value.copy(gForceThreshold = threshold)
    }

    internal fun updateBubbleActive(active: Boolean) {
        _cameraState.value = _cameraState.value.copy(bubbleActive = active)
    }

    internal fun updateTemperature(tempCelsius: Float) {
        val status = when {
            tempCelsius >= 50f -> TemperatureStatus.CRITICAL
            tempCelsius >= 45f -> TemperatureStatus.DANGER
            tempCelsius >= 40f -> TemperatureStatus.WARNING
            else -> TemperatureStatus.NORMAL
        }
        _cameraState.value = _cameraState.value.copy(temperature = tempCelsius, temperatureStatus = status)
    }

    internal fun updateFaceStatus(status: FaceStatus) {
        _faceStatus.value = status
    }

    internal fun updateFatigueConfig(config: FatigueConfig) {
        _fatigueConfig.value = config
    }

    internal suspend fun emitEvent(event: AppEvent) {
        _events.emit(event)
    }
}
