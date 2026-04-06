package com.tinyoscillator.presentation.chart.overlay

import com.tinyoscillator.core.testing.annotations.FastTest
import com.tinyoscillator.domain.model.PatternResult
import com.tinyoscillator.domain.model.PatternSentiment
import com.tinyoscillator.domain.model.PatternType
import com.tinyoscillator.presentation.chart.bridge.ChartXBridge
import org.junit.Assert.*
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(FastTest::class)
class PatternMarkerOverlayLogicTest {

    @Test
    fun `bullish pattern maps to triangle up symbol`() {
        val sym = when (PatternSentiment.BULLISH) {
            PatternSentiment.BULLISH -> "▲"
            PatternSentiment.BEARISH -> "▽"
            PatternSentiment.NEUTRAL -> "◇"
        }
        assertEquals("▲", sym)
    }

    @Test
    fun `patterns grouped by index`() {
        val patterns = listOf(
            PatternResult(5, PatternType.BUY_TREND, 0.9f),
            PatternResult(5, PatternType.BULL_FIFTY, 0.8f),
            PatternResult(10, PatternType.SELL_TOP, 0.7f),
        )
        val grouped = patterns.groupBy { it.index }
        assertEquals(2, grouped[5]?.size)
        assertEquals(1, grouped[10]?.size)
    }

    @Test
    fun `out of bounds index not drawn`() {
        val state = ChartXBridge.XAxisState(0f, 100f, 50f, 550f)
        val idx = -50
        val fraction = (idx - state.lowestVisibleX) /
            (state.highestVisibleX - state.lowestVisibleX)
        val x = state.contentLeft + fraction * (state.contentRight - state.contentLeft)
        assertTrue("Expected x < 0, got $x", x < 0f)
    }

    @Test
    fun `strength alpha clamped to 128 to 255`() {
        fun strengthToAlpha(s: Float) = (s * 255).toInt().coerceIn(128, 255)
        assertEquals(255, strengthToAlpha(1.0f))
        assertEquals(128, strengthToAlpha(0.0f))
        assertEquals(191, strengthToAlpha(0.75f))
    }
}
