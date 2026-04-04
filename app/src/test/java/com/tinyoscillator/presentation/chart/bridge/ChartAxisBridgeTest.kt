package com.tinyoscillator.presentation.chart.bridge

import com.tinyoscillator.core.testing.annotations.FastTest
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category
import org.junit.Test

@Category(FastTest::class)
class ChartAxisBridgeTest {

    @Test
    fun `default axis range is 0 to 1`() {
        val bridge = ChartAxisBridge()
        assertEquals(0f, bridge.axisRange.value.yMin)
        assertEquals(1f, bridge.axisRange.value.yMax)
    }
}
