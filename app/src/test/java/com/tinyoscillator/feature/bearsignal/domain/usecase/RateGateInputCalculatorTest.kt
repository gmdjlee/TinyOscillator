package com.tinyoscillator.feature.bearsignal.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class RateGateInputCalculatorTest {

    @Test
    fun `computeDirection 상승이면 hike`() {
        assertEquals("hike", RateGateInputCalculator.computeDirection(3.50, 3.25))
    }

    @Test
    fun `computeDirection 하락이면 ease`() {
        assertEquals("ease", RateGateInputCalculator.computeDirection(3.00, 3.25))
    }

    @Test
    fun `computeDirection 동일하면 hold`() {
        assertEquals("hold", RateGateInputCalculator.computeDirection(3.25, 3.25))
    }

    @Test
    fun `computeDirection 상수는 scoreGate 문자열과 동일`() {
        assertEquals("hike", RateGateInputCalculator.DIR_HIKE)
        assertEquals("hold", RateGateInputCalculator.DIR_HOLD)
        assertEquals("ease", RateGateInputCalculator.DIR_EASE)
    }
}
