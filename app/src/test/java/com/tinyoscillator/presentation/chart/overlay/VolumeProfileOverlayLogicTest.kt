package com.tinyoscillator.presentation.chart.overlay

import com.tinyoscillator.core.testing.annotations.FastTest
import org.junit.Assert.assertEquals
import org.junit.Test

@FastTest
class VolumeProfileOverlayLogicTest {

    private fun priceToY(price: Float, yMin: Float, yMax: Float, chartH: Float): Float =
        chartH * (1f - (price - yMin) / (yMax - yMin))

    @Test
    fun `price at yMax maps to y=0`() =
        assertEquals(0f, priceToY(100f, 50f, 100f, 400f), 0.01f)

    @Test
    fun `price at yMin maps to chartHeight`() =
        assertEquals(400f, priceToY(50f, 50f, 100f, 400f), 0.01f)

    @Test
    fun `price at midpoint maps to half chartHeight`() =
        assertEquals(200f, priceToY(75f, 50f, 100f, 400f), 0.01f)

    @Test
    fun `bar width proportional to volume fraction`() {
        val maxVol = 1_000L
        val bucketVol = 500L
        val barMaxW = 100f
        val barW = (bucketVol.toFloat() / maxVol) * barMaxW
        assertEquals(50f, barW, 0.01f)
    }

    @Test
    fun `bull fraction of bar width`() {
        val bullVol = 300L; val totalVol = 500L; val barW = 50f
        val bullW = (bullVol.toFloat() / totalVol) * barW
        assertEquals(30f, bullW, 0.01f)
    }
}
