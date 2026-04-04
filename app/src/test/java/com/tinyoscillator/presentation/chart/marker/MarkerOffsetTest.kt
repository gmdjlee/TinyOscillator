package com.tinyoscillator.presentation.chart.marker

import com.tinyoscillator.core.testing.annotations.FastTest
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category
import org.junit.Test

@Category(FastTest::class)
class MarkerOffsetTest {

    /**
     * MarkerView.getOffset() 경계 감지 로직을 순수 Float 연산으로 검증.
     * OhlcvMarkerView 내부: canvasX > chartWidth / 2 → 왼쪽 팝업
     */
    private fun computeOffsetX(canvasX: Float, chartWidth: Float, markerWidth: Float): Float =
        if (canvasX > chartWidth / 2f) -markerWidth else 0f

    @Test
    fun `marker in left half stays right`() {
        assertEquals(0f, computeOffsetX(100f, 600f, 120f))
    }

    @Test
    fun `marker in right half flips left`() {
        assertEquals(-120f, computeOffsetX(400f, 600f, 120f))
    }

    @Test
    fun `marker exactly at center stays right`() {
        // 300 is NOT > 300, so stays right
        assertEquals(0f, computeOffsetX(300f, 600f, 120f))
    }

    @Test
    fun `marker just past center flips left`() {
        assertEquals(-120f, computeOffsetX(301f, 600f, 120f))
    }

    @Test
    fun `marker at far right flips left`() {
        assertEquals(-200f, computeOffsetX(599f, 600f, 200f))
    }
}
