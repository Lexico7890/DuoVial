package com.duovial.engine

import org.junit.Test

class FatigueAlertLogicTest {

    /**
     * Mirror of BackgroundCameraService.handleFaceProcessingResult() + triggerFatigueAlert().
     * Production code reference:
     *   BackgroundCameraService.kt lines 1001-1056
     *
     * If production logic changes, this test mirror MUST be updated accordingly.
     */

    data class AlertState(
        val closedEyeStartTime: Long = 0L,
        val currentClosedEyeDurationMs: Long = 0L,
        val alertCountThisHour: Int = 0,
        val hourResetTime: Long = 0L,
        val isSnoozed: Boolean = false,
        val snoozeEndTime: Long = 0L,
        val lastAlertTime: Long = 0L,
        var alertTriggered: Boolean = false
    )

    /**
     * Mirror of BackgroundCameraService.handleFaceProcessingResult().
     * Production code reference: BackgroundCameraService.kt lines 1001-1046
     */
    private fun processFaceResult(
        state: AlertState,
        now: Long,
        faceDetected: Boolean,
        earValue: Double,
        earThreshold: Double,
        closedEyeDurationMs: Long,
        maxAlertsPerHour: Int
    ): AlertState {
        val result = state.copy(alertTriggered = false)

        var next = if (!faceDetected || earValue >= earThreshold) {
            result.copy(
                closedEyeStartTime = 0L,
                currentClosedEyeDurationMs = 0L
            )
        } else {
            val startTime = if (result.closedEyeStartTime == 0L) now else result.closedEyeStartTime
            val duration = now - startTime
            val updated = result.copy(
                closedEyeStartTime = startTime,
                currentClosedEyeDurationMs = duration
            )
            if (duration < closedEyeDurationMs) return checkSnoozeExpiry(updated, now)
            var alert = updated
            if (now - alert.hourResetTime > 3600000L) {
                alert = alert.copy(alertCountThisHour = 0, hourResetTime = now)
            }
            alert = checkSnoozeExpiry(alert, now)
            val minAlertInterval = 5000L
            if (alert.alertCountThisHour < maxAlertsPerHour && !alert.isSnoozed) {
                if (now - alert.lastAlertTime > minAlertInterval) {
                    alert = alert.copy(
                        alertTriggered = true,
                        lastAlertTime = now,
                        alertCountThisHour = alert.alertCountThisHour + 1
                    )
                }
            }
            alert
        }

        // Snooze expiry check runs regardless of eyes open/closed (matches production line 1036-1038)
        return checkSnoozeExpiry(next, now)
    }

    private fun checkSnoozeExpiry(state: AlertState, now: Long): AlertState {
        return if (state.isSnoozed && now >= state.snoozeEndTime) {
            state.copy(isSnoozed = false)
        } else {
            state
        }
    }

    // ── Eyes open resets counter ──

    @Test
    fun `eyes open resets closed eye counter`() {
        val state = AlertState(
            closedEyeStartTime = 1000L,
            currentClosedEyeDurationMs = 500L
        )
        val result = processFaceResult(
            state, now = 1500L,
            faceDetected = true, earValue = 0.35,
            earThreshold = 0.2, closedEyeDurationMs = 2000L,
            maxAlertsPerHour = 3
        )
        assert(result.closedEyeStartTime == 0L)
        assert(result.currentClosedEyeDurationMs == 0L)
        assert(!result.alertTriggered)
    }

    @Test
    fun `no face detected resets counter`() {
        val state = AlertState(
            closedEyeStartTime = 1000L,
            currentClosedEyeDurationMs = 800L
        )
        val result = processFaceResult(
            state, now = 1800L,
            faceDetected = false, earValue = 0.0,
            earThreshold = 0.2, closedEyeDurationMs = 2000L,
            maxAlertsPerHour = 3
        )
        assert(result.closedEyeStartTime == 0L)
        assert(result.currentClosedEyeDurationMs == 0L)
    }

    @Test
    fun `EAR above threshold does not trigger`() {
        val state = AlertState(hourResetTime = 0L)
        val result = processFaceResult(
            state, now = 1000L,
            faceDetected = true, earValue = 0.25,
            earThreshold = 0.2, closedEyeDurationMs = 2000L,
            maxAlertsPerHour = 3
        )
        assert(result.closedEyeStartTime == 0L)
        assert(!result.alertTriggered)
    }

    // ── Eyes closed starts counter ──

    @Test
    fun `eyes closed below threshold starts counter`() {
        val state = AlertState(hourResetTime = 0L)
        val result = processFaceResult(
            state, now = 5000L,
            faceDetected = true, earValue = 0.15,
            earThreshold = 0.2, closedEyeDurationMs = 2000L,
            maxAlertsPerHour = 3
        )
        assert(result.closedEyeStartTime == 5000L)
        assert(result.currentClosedEyeDurationMs == 0L)
        assert(!result.alertTriggered)
    }

    @Test
    fun `eyes closed duration accumulates`() {
        val state = AlertState(
            closedEyeStartTime = 5000L,
            hourResetTime = 0L
        )
        val result = processFaceResult(
            state, now = 6000L,
            faceDetected = true, earValue = 0.15,
            earThreshold = 0.2, closedEyeDurationMs = 2000L,
            maxAlertsPerHour = 3
        )
        assert(result.currentClosedEyeDurationMs == 1000L)
        assert(!result.alertTriggered)
    }

    // ── Alert triggers ──

    @Test
    fun `triggers alert when duration threshold exceeded`() {
        val state = AlertState(
            closedEyeStartTime = 5000L,
            hourResetTime = 0L
        )
        val result = processFaceResult(
            state, now = 7000L, // 2000ms closed
            faceDetected = true, earValue = 0.15,
            earThreshold = 0.2, closedEyeDurationMs = 2000L,
            maxAlertsPerHour = 3
        )
        assert(result.alertTriggered)
        assert(result.alertCountThisHour == 1)
        assert(result.lastAlertTime == 7000L)
    }

    @Test
    fun `does not trigger alert twice within 5 seconds`() {
        val state = AlertState(
            closedEyeStartTime = 5000L,
            hourResetTime = 0L,
            lastAlertTime = 7000L,
            alertCountThisHour = 1
        )
        // Second trigger attempt at 8000ms — only 1s after last alert
        val result = processFaceResult(
            state, now = 8000L,
            faceDetected = true, earValue = 0.15,
            earThreshold = 0.2, closedEyeDurationMs = 2000L,
            maxAlertsPerHour = 3
        )
        assert(!result.alertTriggered)
        assert(result.alertCountThisHour == 1)
    }

    @Test
    fun `triggers alert after 5 second cooldown`() {
        val state = AlertState(
            closedEyeStartTime = 12000L,
            hourResetTime = 0L,
            lastAlertTime = 7000L,
            alertCountThisHour = 1
        )
        val result = processFaceResult(
            state, now = 14000L, // 2000ms closed, 7s after last alert
            faceDetected = true, earValue = 0.15,
            earThreshold = 0.2, closedEyeDurationMs = 2000L,
            maxAlertsPerHour = 3
        )
        assert(result.alertTriggered)
        assert(result.alertCountThisHour == 2)
    }

    // ── Max alerts per hour ──

    @Test
    fun `does not trigger if max alerts per hour reached`() {
        val state = AlertState(
            closedEyeStartTime = 5000L,
            hourResetTime = 0L,
            alertCountThisHour = 3,
            lastAlertTime = 0L
        )
        val result = processFaceResult(
            state, now = 7000L,
            faceDetected = true, earValue = 0.15,
            earThreshold = 0.2, closedEyeDurationMs = 2000L,
            maxAlertsPerHour = 3
        )
        assert(!result.alertTriggered)
        assert(result.alertCountThisHour == 3)
    }

    @Test
    fun `hour reset allows alerts again`() {
        val state = AlertState(
            closedEyeStartTime = 4000000L,
            hourResetTime = 0L,
            alertCountThisHour = 3,
            lastAlertTime = 3000000L
        )
        // now is 4002000 — hour elapsed, alert count should reset
        val result = processFaceResult(
            state, now = 4002000L, // 2000ms after closed start, > 1h since hourResetTime
            faceDetected = true, earValue = 0.15,
            earThreshold = 0.2, closedEyeDurationMs = 2000L,
            maxAlertsPerHour = 3
        )
        assert(result.alertCountThisHour == 1) // reset to 0 then +1
        assert(result.alertTriggered)
    }

    // ── Snooze ──

    @Test
    fun `does not trigger while snoozed`() {
        val state = AlertState(
            closedEyeStartTime = 5000L,
            hourResetTime = 0L,
            isSnoozed = true,
            snoozeEndTime = 10000L
        )
        val result = processFaceResult(
            state, now = 7000L, // still within snooze window
            faceDetected = true, earValue = 0.15,
            earThreshold = 0.2, closedEyeDurationMs = 2000L,
            maxAlertsPerHour = 3
        )
        assert(!result.alertTriggered)
    }

    @Test
    fun `triggers alert after snooze expires`() {
        val state = AlertState(
            closedEyeStartTime = 15000L,
            hourResetTime = 0L,
            isSnoozed = true,
            snoozeEndTime = 10000L
        )
        val result = processFaceResult(
            state, now = 17000L, // past snoozeEndTime, 2000ms duration
            faceDetected = true, earValue = 0.15,
            earThreshold = 0.2, closedEyeDurationMs = 2000L,
            maxAlertsPerHour = 3
        )
        assert(result.alertTriggered)
        assert(!result.isSnoozed)
    }

    @Test
    fun `snooze auto-expires even without alert`() {
        val state = AlertState(
            isSnoozed = true,
            snoozeEndTime = 10000L
        )
        val result = processFaceResult(
            state, now = 12000L,
            faceDetected = true, earValue = 0.35, // eyes open, no alert
            earThreshold = 0.2, closedEyeDurationMs = 2000L,
            maxAlertsPerHour = 3
        )
        assert(!result.isSnoozed)
    }

    // ── Snooze expiry (dedicated method) ──

    @Test
    fun `checkSnoozeExpiry unsnoozes after end time`() {
        val state = AlertState(isSnoozed = true, snoozeEndTime = 10000L)
        val result = checkSnoozeExpiry(state, now = 10000L)
        assert(!result.isSnoozed)
    }

    @Test
    fun `checkSnoozeExpiry keeps snooze if not expired`() {
        val state = AlertState(isSnoozed = true, snoozeEndTime = 10000L)
        val result = checkSnoozeExpiry(state, now = 9999L)
        assert(result.isSnoozed)
    }
}
