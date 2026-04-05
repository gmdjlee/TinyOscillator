package com.tinyoscillator.presentation.common

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HeatmapColorTest {

    @Test
    fun `score above 0_75 maps to strong bull red`() =
        assertEquals(Color(0xFFD85A30), heatmapColor(0.80f))

    @Test
    fun `score between 0_60 and 0_74 maps to mild bull`() =
        assertEquals(Color(0xFFE88A66), heatmapColor(0.65f))

    @Test
    fun `score between 0_40 and 0_59 maps to neutral gray`() =
        assertEquals(Color(0xFF888780), heatmapColor(0.50f))

    @Test
    fun `score between 0_25 and 0_39 maps to mild bear blue`() =
        assertEquals(Color(0xFF6699CC), heatmapColor(0.30f))

    @Test
    fun `score below 0_25 maps to strong bear blue`() =
        assertEquals(Color(0xFF378ADD), heatmapColor(0.20f))

    @Test
    fun `boundary 0_75 maps to strong bull`() =
        assertEquals(Color(0xFFD85A30), heatmapColor(0.75f))

    @Test
    fun `boundary 0_40 maps to neutral`() =
        assertEquals(Color(0xFF888780), heatmapColor(0.40f))

    @Test
    fun `boundary 0_60 maps to mild bull`() =
        assertEquals(Color(0xFFE88A66), heatmapColor(0.60f))

    @Test
    fun `all scores in 0_0 to 1_0 produce non-null color`() {
        (0..100).forEach { i ->
            val score = i / 100f
            assertNotNull(heatmapColor(score))
        }
    }
}
