package com.tinyoscillator.presentation.chart.bridge

import com.tinyoscillator.core.testing.annotations.FastTest
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category
import org.junit.Test

@Category(FastTest::class)
class ChartXBridgeTest {
    private val bridge = ChartXBridge()

    @Test
    fun `default state has expected visible range`() {
        val state = bridge.state.value
        assertEquals(0f, state.lowestVisibleX, 0.01f)
        assertEquals(100f, state.highestVisibleX, 0.01f)
    }

    @Test
    fun `indexToX returns contentLeft for lowestVisibleX`() {
        // default: lowestVisibleX=0, highestVisibleX=100, contentLeft=0, contentRight=1
        // index=0 → fraction=0 → contentLeft + 0 = 0
        val x = bridge.indexToX(0)
        assertEquals(0f, x, 0.01f)
    }

    @Test
    fun `indexToX returns contentRight for highestVisibleX`() {
        // index=100 → fraction=1 → contentLeft + 1*(contentRight-contentLeft) = 1
        val x = bridge.indexToX(100)
        assertEquals(1f, x, 0.01f)
    }
}
