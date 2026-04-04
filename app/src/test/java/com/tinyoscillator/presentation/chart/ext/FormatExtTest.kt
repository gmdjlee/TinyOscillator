package com.tinyoscillator.presentation.chart.ext

import com.tinyoscillator.core.testing.annotations.FastTest
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category
import org.junit.Test

@Category(FastTest::class)
class FormatExtTest {

    @Test
    fun `formatKRW formats 73400 with commas and won sign`() {
        assertEquals("73,400원", 73_400L.formatKRW())
    }

    @Test
    fun `formatKRW formats 1234567 correctly`() {
        assertEquals("1,234,567원", 1_234_567L.formatKRW())
    }

    @Test
    fun `formatKRW handles zero`() {
        assertEquals("0원", 0L.formatKRW())
    }

    @Test
    fun `formatKRW handles 1 billion`() {
        assertEquals("1,000,000,000원", 1_000_000_000L.formatKRW())
    }

    @Test
    fun `formatKRW Float delegates to Long`() {
        assertEquals("73,400원", 73_400f.formatKRW())
    }
}
