package com.duovial.state

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppStateManagerTest {

    @Test
    fun `initial camera state is INACTIVO`() = runTest {
        AppStateManager.cameraState.test {
            assertEquals(CameraStatus.INACTIVO, awaitItem().status)
        }
    }

    @Test
    fun `updateCameraStatus changes status to ACTIVO`() = runTest {
        AppStateManager.updateCameraStatus(CameraStatus.ACTIVO)
        AppStateManager.cameraState.test {
            assertEquals(CameraStatus.ACTIVO, awaitItem().status)
        }
    }

    @Test
    fun `updateCameraStatus changes to ERROR`() = runTest {
        AppStateManager.updateCameraStatus(CameraStatus.ERROR)
        AppStateManager.cameraState.test {
            assertEquals(CameraStatus.ERROR, awaitItem().status)
        }
    }

    @Test
    fun `updateGForce emits correct value`() = runTest {
        AppStateManager.updateGForce(3.2)
        AppStateManager.cameraState.test {
            val state = awaitItem()
            assertEquals(3.2, state.gForce)
        }
    }

    @Test
    fun `updateGForce preserves other state fields`() = runTest {
        AppStateManager.updateCameraStatus(CameraStatus.ACTIVO)
        AppStateManager.updateGForce(4.0)
        AppStateManager.cameraState.test {
            val state = awaitItem()
            assertEquals(CameraStatus.ACTIVO, state.status)
            assertEquals(4.0, state.gForce)
        }
    }

    @Test
    fun `updateSpeed emits correct value`() = runTest {
        AppStateManager.updateSpeed(80.5)
        AppStateManager.cameraState.test {
            assertEquals(80.5, awaitItem().speedKph)
        }
    }

    @Test
    fun `updateGForceThreshold emits correct value`() = runTest {
        AppStateManager.updateGForceThreshold(3.0)
        AppStateManager.cameraState.test {
            assertEquals(3.0, awaitItem().gForceThreshold)
        }
    }

    @Test
    fun `updateFaceStatus with face detected`() = runTest {
        val status = FaceStatus(enabled = true, faceDetected = true, earValue = 0.35, closedEyeDurationMs = 500L)
        AppStateManager.updateFaceStatus(status)
        AppStateManager.faceStatus.test {
            val emitted = awaitItem()
            assertTrue(emitted.enabled)
            assertTrue(emitted.faceDetected)
            assertEquals(0.35, emitted.earValue)
            assertEquals(500L, emitted.closedEyeDurationMs)
        }
    }

    @Test
    fun `updateFaceStatus with no face`() = runTest {
        val status = FaceStatus(enabled = true, faceDetected = false, earValue = 0.0, closedEyeDurationMs = 0L)
        AppStateManager.updateFaceStatus(status)
        AppStateManager.faceStatus.test {
            val emitted = awaitItem()
            assertTrue(emitted.enabled)
            assertFalse(emitted.faceDetected)
            assertEquals(0.0, emitted.earValue)
        }
    }

    @Test
    fun `updateFatigueConfig changes threshold`() = runTest {
        val config = FatigueConfig(earThreshold = 0.15, durationThresholdMs = 3000L, maxAlertsPerHour = 2)
        AppStateManager.updateFatigueConfig(config)
        AppStateManager.fatigueConfig.test {
            val emitted = awaitItem()
            assertEquals(0.15, emitted.earThreshold)
            assertEquals(3000L, emitted.durationThresholdMs)
            assertEquals(2, emitted.maxAlertsPerHour)
        }
    }

    @Test
    fun `updateFatigueConfig with snooze`() = runTest {
        val config = FatigueConfig(isSnoozed = true, alertCount = 2)
        AppStateManager.updateFatigueConfig(config)
        AppStateManager.fatigueConfig.test {
            val emitted = awaitItem()
            assertTrue(emitted.isSnoozed)
            assertEquals(2, emitted.alertCount)
        }
    }

    @Test
    fun `emitEvent delivers drowsiness event`() = runTest {
        val event = AppEvent.DrowsinessDetected(timestamp = 123456789L, earValue = 0.1)
        AppStateManager.events.test {
            AppStateManager.emitEvent(event)
            assertEquals(event, awaitItem())
        }
    }

    @Test
    fun `initial state can be read via direct value access`() = runTest {
        // Note: AppStateManager is a singleton; state may be affected by other tests.
        // Direct value read confirms the flow is accessible.
        val state = AppStateManager.cameraState.value
        // Just verify we can read it without exception
        assertTrue(state.status is CameraStatus)
        assertTrue(state.gForce >= 0.0)
    }

    @Test
    fun `faceStatus accessible via direct value`() = runTest {
        val status = AppStateManager.faceStatus.value
        assertTrue(status is FaceStatus)
    }

    @Test
    fun `fatigueConfig accessible via direct value`() = runTest {
        val config = AppStateManager.fatigueConfig.value
        assertTrue(config is FatigueConfig)
    }
}
