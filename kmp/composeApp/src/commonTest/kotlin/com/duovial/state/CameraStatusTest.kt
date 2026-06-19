package com.duovial.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CameraStatusTest {

    @Test
    fun `all CameraStatus values exist`() {
        val values = CameraStatus.entries
        assertEquals(5, values.size)
        assertNotNull(CameraStatus.INACTIVO)
        assertNotNull(CameraStatus.INICIANDO)
        assertNotNull(CameraStatus.ACTIVO)
        assertNotNull(CameraStatus.GUARDANDO)
        assertNotNull(CameraStatus.ERROR)
    }

    @Test
    fun `CameraState default values`() {
        val state = CameraState()
        assertEquals(CameraStatus.INACTIVO, state.status)
        assertEquals(1.0, state.gForce)
        assertEquals(0.0, state.speedKph)
        assertEquals(2.5, state.gForceThreshold)
    }

    @Test
    fun `CameraState copy preserves values`() {
        val state = CameraState(
            status = CameraStatus.ACTIVO,
            gForce = 2.3,
            speedKph = 60.0,
            gForceThreshold = 3.0
        )
        val copied = state.copy(status = CameraStatus.ERROR)
        assertEquals(CameraStatus.ERROR, copied.status)
        assertEquals(2.3, copied.gForce)
        assertEquals(60.0, copied.speedKph)
        assertEquals(3.0, copied.gForceThreshold)
    }

    @Test
    fun `FaceStatus default values`() {
        val status = FaceStatus()
        assertEquals(false, status.enabled)
        assertEquals(false, status.faceDetected)
        assertEquals(0.0, status.earValue)
        assertEquals(0L, status.closedEyeDurationMs)
    }

    @Test
    fun `FatigueConfig default values`() {
        val config = FatigueConfig()
        assertEquals(0.2, config.earThreshold)
        assertEquals(2000L, config.durationThresholdMs)
        assertEquals(3, config.maxAlertsPerHour)
        assertEquals(false, config.isSnoozed)
        assertEquals(0, config.alertCount)
    }

    @Test
    fun `FatigueConfig copy with snooze`() {
        val config = FatigueConfig()
        val snoozed = config.copy(isSnoozed = true, alertCount = 1)
        assertEquals(true, snoozed.isSnoozed)
        assertEquals(1, snoozed.alertCount)
    }
}
