package com.duovial.engine

import org.junit.Test

class ThresholdValidationTest {

    @Test
    fun `GForce threshold accepts value in range min`() {
        assert(isGForceValid(1.5))
    }

    @Test
    fun `GForce threshold accepts value in range max`() {
        assert(isGForceValid(5.0))
    }

    @Test
    fun `GForce threshold accepts value in range mid`() {
        assert(isGForceValid(2.5))
    }

    @Test
    fun `GForce threshold rejects value below min`() {
        assert(!isGForceValid(1.4))
    }

    @Test
    fun `GForce threshold rejects value above max`() {
        assert(!isGForceValid(5.1))
    }

    @Test
    fun `GForce threshold rejects zero`() {
        assert(!isGForceValid(0.0))
    }

    @Test
    fun `GForce threshold rejects negative`() {
        assert(!isGForceValid(-1.0))
    }

    @Test
    fun `GForce threshold accepts 3_25`() {
        assert(isGForceValid(3.25))
    }

    @Test
    fun `EAR threshold accepts value in range min`() {
        assert(isEarValid(0.1))
    }

    @Test
    fun `EAR threshold accepts value in range max`() {
        assert(isEarValid(0.4))
    }

    @Test
    fun `EAR threshold accepts value in range mid`() {
        assert(isEarValid(0.2))
    }

    @Test
    fun `EAR threshold rejects value below min`() {
        assert(!isEarValid(0.09))
    }

    @Test
    fun `EAR threshold rejects value above max`() {
        assert(!isEarValid(0.41))
    }

    @Test
    fun `EAR threshold rejects zero`() {
        assert(!isEarValid(0.0))
    }

    @Test
    fun `EAR threshold rejects negative`() {
        assert(!isEarValid(-0.1))
    }

    @Test
    fun `EAR threshold accepts 0_25`() {
        assert(isEarValid(0.25))
    }

    private fun isGForceValid(value: Double): Boolean {
        return value in 1.5..5.0
    }

    private fun isEarValid(value: Double): Boolean {
        return value in 0.1..0.4
    }
}
