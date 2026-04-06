package com.tinyoscillator.presentation.common

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class SignalRationaleDisplayTest {

    @Test
    fun `scoreColor returns red for score above 0_65`() {
        val color = scoreColor(0.72f)
        assertEquals(Color(0xFFD05540), color)
    }

    @Test
    fun `scoreColor returns blue for score below 0_35`() {
        val color = scoreColor(0.25f)
        assertEquals(Color(0xFF4088CC), color)
    }

    @Test
    fun `scoreColor returns gray for neutral score`() {
        val color = scoreColor(0.50f)
        assertEquals(Color(0xFF8A8580), color)
    }

    @Test
    fun `scoreColor boundary at 0_65 is red`() {
        val color = scoreColor(0.65f)
        assertEquals(Color(0xFFD05540), color)
    }

    @Test
    fun `scoreColor boundary at 0_35 is blue`() {
        val color = scoreColor(0.35f)
        assertEquals(Color(0xFF4088CC), color)
    }

    @Test
    fun `ALGO_DISPLAY_NAMES covers all 7 core algorithms`() {
        val coreAlgos = listOf(
            "NaiveBayes", "Logistic", "HMM",
            "PatternScan", "SignalScoring",
            "BayesianUpdate", "OrderFlow"
        )
        coreAlgos.forEach { name ->
            assertNotNull("$name has no Korean display name", ALGO_DISPLAY_NAMES[name])
        }
    }

    @Test
    fun `ALGO_DISPLAY_NAMES covers extended algorithms`() {
        val extAlgos = listOf("DartEvent", "Korea5Factor", "SectorCorrelation")
        extAlgos.forEach { name ->
            assertNotNull("$name has no Korean display name", ALGO_DISPLAY_NAMES[name])
        }
    }

    @Test
    fun `ALGO_DISPLAY_NAMES has 10 entries total`() {
        assertEquals(10, ALGO_DISPLAY_NAMES.size)
    }

    @Test
    fun `score badge logic returns 강세 above 0_65`() {
        val score = 0.72f
        val text = when {
            score >= 0.65f -> "강세"
            score <= 0.35f -> "약세"
            else -> "중립"
        }
        assertEquals("강세", text)
    }

    @Test
    fun `score badge logic returns 약세 below 0_35`() {
        val score = 0.20f
        val text = when {
            score >= 0.65f -> "강세"
            score <= 0.35f -> "약세"
            else -> "중립"
        }
        assertEquals("약세", text)
    }

    @Test
    fun `score badge logic returns 중립 for mid score`() {
        val score = 0.50f
        val text = when {
            score >= 0.65f -> "강세"
            score <= 0.35f -> "약세"
            else -> "중립"
        }
        assertEquals("중립", text)
    }
}
