package com.duovial.engine

import org.junit.Test

class SaveModeLogicTest {

    /**
     * Mirror of BackgroundCameraService.saveEvent() / stopAndSaveBuffer() save mode selection.
     * Production code reference:
     *   BackgroundCameraService.kt lines 661-705
     *
     * If production logic changes, this test mirror MUST be updated accordingly.
     */
    private fun determineSaveMode(
        durationMs: Long,
        hasCompletedPrevSegment: Boolean
    ): SaveMode {
        if (!hasCompletedPrevSegment) {
            return SaveMode.SCENARIO_1
        }
        return when {
            durationMs < 3000 -> SaveMode.SAVE_ONLY_PREV
            durationMs < 14000 -> SaveMode.SAVE_PREV_AND_CURR
            else -> SaveMode.SAVE_ONLY_CURR
        }
    }

    /**
     * Mirror of BackgroundCameraService.handleEventSaveTransition() post-event part index.
     */
    private fun postEventPartIndex(saveMode: SaveMode): Int {
        return when (saveMode) {
            SaveMode.SCENARIO_1 -> 1
            SaveMode.SAVE_ONLY_PREV -> 1
            SaveMode.SAVE_ONLY_CURR -> 1
            SaveMode.SAVE_PREV_AND_CURR -> 2
        }
    }

    /**
     * Mirror of BackgroundCameraService.savePreEventSegments() — returns which segments
     * get saved and with what part indices.
     */
    private data class PreEventSavePlan(
        val segments: List<Pair<String, Int>>
    )

    private fun preEventSavePlan(
        saveMode: SaveMode,
        currentIndex: Int,
        prevIndex: Int
    ): PreEventSavePlan {
        return when (saveMode) {
            SaveMode.SCENARIO_1 -> PreEventSavePlan(
                listOf("segment_$currentIndex" to 0)
            )
            SaveMode.SAVE_PREV_AND_CURR -> PreEventSavePlan(
                listOf("segment_$prevIndex" to 0, "segment_$currentIndex" to 1)
            )
            SaveMode.SAVE_ONLY_PREV -> PreEventSavePlan(
                listOf("segment_$prevIndex" to 0)
            )
            SaveMode.SAVE_ONLY_CURR -> PreEventSavePlan(
                listOf("segment_$currentIndex" to 0)
            )
        }
    }

    enum class SaveMode {
        SCENARIO_1,
        SAVE_ONLY_PREV,
        SAVE_PREV_AND_CURR,
        SAVE_ONLY_CURR
    }

    // ── determineSaveMode ──

    @Test
    fun `no prev segment completed always returns SCENARIO_1`() {
        assertEquals(SaveMode.SCENARIO_1, determineSaveMode(1000, hasCompletedPrevSegment = false))
        assertEquals(SaveMode.SCENARIO_1, determineSaveMode(5000, hasCompletedPrevSegment = false))
        assertEquals(SaveMode.SCENARIO_1, determineSaveMode(20000, hasCompletedPrevSegment = false))
    }

    @Test
    fun `short duration with prev returns SAVE_ONLY_PREV`() {
        assertEquals(SaveMode.SAVE_ONLY_PREV, determineSaveMode(0, hasCompletedPrevSegment = true))
        assertEquals(SaveMode.SAVE_ONLY_PREV, determineSaveMode(1000, hasCompletedPrevSegment = true))
        assertEquals(SaveMode.SAVE_ONLY_PREV, determineSaveMode(2999, hasCompletedPrevSegment = true))
    }

    @Test
    fun `medium duration with prev returns SAVE_PREV_AND_CURR`() {
        assertEquals(SaveMode.SAVE_PREV_AND_CURR, determineSaveMode(3000, hasCompletedPrevSegment = true))
        assertEquals(SaveMode.SAVE_PREV_AND_CURR, determineSaveMode(8000, hasCompletedPrevSegment = true))
        assertEquals(SaveMode.SAVE_PREV_AND_CURR, determineSaveMode(13999, hasCompletedPrevSegment = true))
    }

    @Test
    fun `long duration with prev returns SAVE_ONLY_CURR`() {
        assertEquals(SaveMode.SAVE_ONLY_CURR, determineSaveMode(14000, hasCompletedPrevSegment = true))
        assertEquals(SaveMode.SAVE_ONLY_CURR, determineSaveMode(15000, hasCompletedPrevSegment = true))
        assertEquals(SaveMode.SAVE_ONLY_CURR, determineSaveMode(60000, hasCompletedPrevSegment = true))
    }

    // ── postEventPartIndex ──

    @Test
    fun `post event part index is 1 for SCENARIO_1`() {
        assertEquals(1, postEventPartIndex(SaveMode.SCENARIO_1))
    }

    @Test
    fun `post event part index is 1 for SAVE_ONLY_PREV`() {
        assertEquals(1, postEventPartIndex(SaveMode.SAVE_ONLY_PREV))
    }

    @Test
    fun `post event part index is 1 for SAVE_ONLY_CURR`() {
        assertEquals(1, postEventPartIndex(SaveMode.SAVE_ONLY_CURR))
    }

    @Test
    fun `post event part index is 2 for SAVE_PREV_AND_CURR`() {
        assertEquals(2, postEventPartIndex(SaveMode.SAVE_PREV_AND_CURR))
    }

    // ── preEventSavePlan ──

    @Test
    fun `SCENARIO_1 saves only current segment as part 0`() {
        val plan = preEventSavePlan(SaveMode.SCENARIO_1, currentIndex = 0, prevIndex = 1)
        assertEquals(1, plan.segments.size)
        assertEquals("segment_0" to 0, plan.segments[0])
    }

    @Test
    fun `SCENARIO_1 saves only current segment as part 0 with currentIndex 1`() {
        val plan = preEventSavePlan(SaveMode.SCENARIO_1, currentIndex = 1, prevIndex = 0)
        assertEquals(1, plan.segments.size)
        assertEquals("segment_1" to 0, plan.segments[0])
    }

    @Test
    fun `SAVE_ONLY_PREV saves prev segment as part 0`() {
        val plan = preEventSavePlan(SaveMode.SAVE_ONLY_PREV, currentIndex = 1, prevIndex = 0)
        assertEquals(1, plan.segments.size)
        assertEquals("segment_0" to 0, plan.segments[0])
    }

    @Test
    fun `SAVE_ONLY_CURR saves current segment as part 0`() {
        val plan = preEventSavePlan(SaveMode.SAVE_ONLY_CURR, currentIndex = 0, prevIndex = 1)
        assertEquals(1, plan.segments.size)
        assertEquals("segment_0" to 0, plan.segments[0])
    }

    @Test
    fun `SAVE_PREV_AND_CURR saves both segments`() {
        val plan = preEventSavePlan(SaveMode.SAVE_PREV_AND_CURR, currentIndex = 1, prevIndex = 0)
        assertEquals(2, plan.segments.size)
        assertEquals("segment_0" to 0, plan.segments[0])
        assertEquals("segment_1" to 1, plan.segments[1])
    }

    @Test
    fun `SAVE_PREV_AND_CURR save order is prev first then current`() {
        val plan = preEventSavePlan(SaveMode.SAVE_PREV_AND_CURR, currentIndex = 0, prevIndex = 1)
        assertEquals("segment_1", plan.segments[0].first)
        assertEquals(0, plan.segments[0].second)
        assertEquals("segment_0", plan.segments[1].first)
        assertEquals(1, plan.segments[1].second)
    }

    @Test
    fun `full table test all combos`() {
        val testCases = listOf(
            Triple(0L, false, SaveMode.SCENARIO_1),
            Triple(1000L, false, SaveMode.SCENARIO_1),
            Triple(5000L, false, SaveMode.SCENARIO_1),
            Triple(15000L, false, SaveMode.SCENARIO_1),
            Triple(0L, true, SaveMode.SAVE_ONLY_PREV),
            Triple(1000L, true, SaveMode.SAVE_ONLY_PREV),
            Triple(2999L, true, SaveMode.SAVE_ONLY_PREV),
            Triple(3000L, true, SaveMode.SAVE_PREV_AND_CURR),
            Triple(8000L, true, SaveMode.SAVE_PREV_AND_CURR),
            Triple(13999L, true, SaveMode.SAVE_PREV_AND_CURR),
            Triple(14000L, true, SaveMode.SAVE_ONLY_CURR),
            Triple(15000L, true, SaveMode.SAVE_ONLY_CURR),
            Triple(60000L, true, SaveMode.SAVE_ONLY_CURR)
        )
        for ((duration, hasPrev, expected) in testCases) {
            assertEquals(expected, determineSaveMode(duration, hasPrev),
                "Failed for duration=$duration hasPrev=$hasPrev")
        }
    }

    private fun assertEquals(expected: Any?, actual: Any?, message: String? = null) {
        if (expected != actual) {
            throw AssertionError(message ?: "Expected <$expected> but was <$actual>")
        }
    }
}
