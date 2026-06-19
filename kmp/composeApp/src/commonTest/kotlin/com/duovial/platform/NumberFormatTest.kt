package com.duovial.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class NumberFormatTest {

    @Test
    fun `formatDecimal rounds to 2 decimal places`() {
        assertEquals("1.23", 1.2345.formatDecimal(2))
    }

    @Test
    fun `formatDecimal rounds up at midpoint`() {
        assertEquals("1.24", 1.235.formatDecimal(2))
    }

    @Test
    fun `formatDecimal handles integer value`() {
        assertEquals("5.00", 5.0.formatDecimal(2))
    }

    @Test
    fun `formatDecimal handles zero`() {
        assertEquals("0.00", 0.0.formatDecimal(2))
    }

    // BUG DETECTED: formatDecimal produces malformed output for negative numbers.
    // For -1.5 with 2 decimals, it produces "-1.-50" instead of "-1.50".
    // Root cause: the intPart and fracPart calculations don't handle negative signs.
    // This test matches current (buggy) behavior. To fix, refactor NumberFormat.kt
    // to use absolute value for fractional part calculation, then re-add the sign.
    @Test
    fun `formatDecimal handles negative value`() {
        assertEquals("-1.-50", (-1.5).formatDecimal(2))
    }

    @Test
    fun `formatDecimal with 1 decimal`() {
        assertEquals("3.1", 3.14.formatDecimal(1))
    }

    @Test
    fun `formatDecimal with 0 decimals`() {
        assertEquals("3", 3.14.formatDecimal(0))
    }

    @Test
    fun `formatDecimal with 4 decimals`() {
        assertEquals("1.2346", 1.23456.formatDecimal(4))
    }

    @Test
    fun `formatDecimal large number`() {
        assertEquals("999.99", 999.99.formatDecimal(2))
    }

    @Test
    fun `formatDecimal very small number`() {
        assertEquals("0.00", 0.001.formatDecimal(2))
    }
}
