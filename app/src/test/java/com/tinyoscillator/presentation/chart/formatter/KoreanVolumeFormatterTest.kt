package com.tinyoscillator.presentation.chart.formatter

import com.tinyoscillator.core.testing.annotations.FastTest
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category
import org.junit.Test

@Category(FastTest::class)
class KoreanVolumeFormatterTest {
    private val fmt = KoreanVolumeFormatter()

    @Test
    fun `100억 formatted correctly`() {
        assertEquals("100억", fmt.getAxisLabel(10_000_000_000f, null))
    }

    @Test
    fun `50만 formatted correctly`() {
        assertEquals("50만", fmt.getAxisLabel(500_000f, null))
    }

    @Test
    fun `under 10000 shows integer`() {
        assertEquals("9999", fmt.getAxisLabel(9_999f, null))
    }

    @Test
    fun `1조 formatted correctly`() {
        assertEquals("1조", fmt.getAxisLabel(1_000_000_000_000f, null))
    }

    @Test
    fun `1억 formatted correctly`() {
        assertEquals("1억", fmt.getAxisLabel(100_000_000f, null))
    }

    @Test
    fun `zero shows 0`() {
        assertEquals("0", fmt.getAxisLabel(0f, null))
    }
}
