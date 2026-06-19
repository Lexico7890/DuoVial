package com.duovial.platform

import android.content.Context
import com.duovial.services.BackgroundCameraService
import com.duovial.state.AppStateManager
import com.duovial.state.CameraStatus
import com.duovial.state.FaceStatus
import com.duovial.state.FatigueConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CameraServiceManagerAndroidTest {

    private lateinit var manager: CameraServiceManagerAndroid

    @Before
    fun setup() {
        val context = mockk<Context>(relaxed = true)
        every { context.packageName } returns "com.duovial"
        mockkObject(BackgroundCameraService.Companion)
        manager = CameraServiceManagerAndroid(context)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `startStandby sends intent without crashing`() {
        // Smoke test: verify it doesn't throw
        manager.startStandby()
    }

    @Test
    fun `startRecording sends intent without crashing`() {
        manager.startRecording()
    }

    @Test
    fun `triggerPanic sends intent without crashing`() {
        manager.triggerPanic()
    }

    @Test
    fun `getGForceThreshold returns default when service is null`() {
        every { BackgroundCameraService.instance } returns null
        every { BackgroundCameraService.pendingGForceThreshold } returns null

        val threshold = manager.getGForceThreshold()
        assert(threshold == 2.5)
    }

    @Test
    fun `getGForceThreshold returns instance value`() {
        val mockService = mockk<BackgroundCameraService>(relaxed = true)
        every { mockService.gForceThreshold } returns 4.0
        every { BackgroundCameraService.instance } returns mockService

        val threshold = manager.getGForceThreshold()
        assert(threshold == 4.0)
    }

    @Test
    fun `getGForceThreshold returns pending value when service is null`() {
        every { BackgroundCameraService.instance } returns null
        BackgroundCameraService.pendingGForceThreshold = 3.5

        val threshold = manager.getGForceThreshold()
        assert(threshold == 3.5)
        BackgroundCameraService.pendingGForceThreshold = null
    }

    @Test
    fun `setGForceThreshold updates instance when service exists`() {
        val mockService = mockk<BackgroundCameraService>(relaxed = true)
        every { BackgroundCameraService.instance } returns mockService

        manager.setGForceThreshold(3.0)

        verify { mockService.setGForceThreshold(3.0) }
    }

    @Test
    fun `setGForceThreshold stores pending when service is null`() {
        every { BackgroundCameraService.instance } returns null

        manager.setGForceThreshold(1.8)

        assert(BackgroundCameraService.pendingGForceThreshold == 1.8)
        BackgroundCameraService.pendingGForceThreshold = null
    }

    @Test
    fun `setEarThreshold updates instance when service exists`() {
        val mockService = mockk<BackgroundCameraService>(relaxed = true)
        every { BackgroundCameraService.instance } returns mockService

        manager.setEarThreshold(0.3)

        verify { mockService.setEarThreshold(0.3) }
    }

    @Test
    fun `setEarThreshold stores pending when service is null`() {
        every { BackgroundCameraService.instance } returns null

        manager.setEarThreshold(0.25)

        assert(BackgroundCameraService.pendingEarThreshold == 0.25)
        BackgroundCameraService.pendingEarThreshold = null
    }

    @Test
    fun `watchCameraState returns current state`() {
        AppStateManager.updateCameraStatus(CameraStatus.INACTIVO)
        val state = manager.watchCameraState()
        assert(state.status == CameraStatus.INACTIVO)
    }

    @Test
    fun `watchFaceStatus returns current status`() {
        val status = FaceStatus(enabled = true, faceDetected = true, earValue = 0.3, closedEyeDurationMs = 100L)
        AppStateManager.updateFaceStatus(status)
        val result = manager.watchFaceStatus()
        assert(result.enabled)
        assert(result.faceDetected)
    }

    @Test
    fun `getFatigueStatus returns default when service is null`() {
        every { BackgroundCameraService.instance } returns null

        val status: FatigueConfig = manager.getFatigueStatus()
        assert(status.earThreshold == 0.2)
        assert(status.durationThresholdMs == 2000L)
        assert(status.maxAlertsPerHour == 3)
        assert(!status.isSnoozed)
        assert(status.alertCount == 0)
    }
}
