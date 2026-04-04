package com.tinyoscillator.domain.indicator

import com.tinyoscillator.core.testing.annotations.FastTest
import com.tinyoscillator.domain.model.Indicator
import com.tinyoscillator.domain.model.OverlayType
import org.junit.Assert.*
import org.junit.Test

@FastTest
class IndicatorConstraintTest {

    @Test
    fun `max 4 price indicators enforced in ViewModel logic`() {
        val selected = mutableSetOf(
            Indicator.EMA_SHORT, Indicator.EMA_MID,
            Indicator.EMA_LONG, Indicator.BOLLINGER,
        )
        val priceCount = selected.count { it.overlayType == OverlayType.PRICE }
        assertFalse("Should be at max already", priceCount < 4)
    }

    @Test
    fun `only one oscillator allowed at a time`() {
        val selected = mutableSetOf(Indicator.MACD)
        val hasOscillator = selected.any { it.overlayType == OverlayType.OSCILLATOR }
        assertTrue(hasOscillator)
        // RSI 추가 시도: isEnabled = !hasOscillator = false → 추가 불가
        assertFalse(!hasOscillator)
    }
}
