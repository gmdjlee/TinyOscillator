package com.tinyoscillator.domain

import com.tinyoscillator.domain.model.AlgoAccuracyRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalAccuracyCalculationTest {

    @Test
    fun `accuracy is hits divided by total`() {
        val row = AlgoAccuracyRow(algoName = "hmm", total = 20, hits = 14)
        assertEquals(0.70f, row.accuracy, 0.01f)
    }

    @Test
    fun `accuracy is 0 when total is 0`() {
        val row = AlgoAccuracyRow(algoName = "hmm", total = 0, hits = 0)
        assertEquals(0f, row.accuracy)
    }

    @Test
    fun `accuracy is 1_0 when all hits`() {
        val row = AlgoAccuracyRow(algoName = "hmm", total = 10, hits = 10)
        assertEquals(1.0f, row.accuracy, 0.001f)
    }

    @Test
    fun `hit condition - bullish signal AND positive outcome`() {
        val signalScore = 0.72f
        val outcome = 0.03f   // +3% 수익
        val isHit = (signalScore > 0.5f && outcome > 0f) ||
                (signalScore < 0.5f && outcome < 0f)
        assertTrue(isHit)
    }

    @Test
    fun `miss condition - bullish signal AND negative outcome`() {
        val signalScore = 0.72f
        val outcome = -0.02f
        val isHit = (signalScore > 0.5f && outcome > 0f) ||
                (signalScore < 0.5f && outcome < 0f)
        assertFalse(isHit)
    }

    @Test
    fun `neutral signal near 0_5 can match either direction`() {
        val signalScore = 0.51f
        val posOutcome = 0.01f
        val negOutcome = -0.01f
        assertTrue((signalScore > 0.5f && posOutcome > 0f))
        assertFalse((signalScore > 0.5f && negOutcome > 0f))
    }

    @Test
    fun `bearish signal AND negative outcome is a hit`() {
        val signalScore = 0.3f
        val outcome = -0.05f
        val isHit = (signalScore > 0.5f && outcome > 0f) ||
                (signalScore < 0.5f && outcome < 0f)
        assertTrue(isHit)
    }

    @Test
    fun `exact 0_5 signal is neither bullish nor bearish`() {
        val signalScore = 0.5f
        val outcome = 0.01f
        val isHit = (signalScore > 0.5f && outcome > 0f) ||
                (signalScore < 0.5f && outcome < 0f)
        assertFalse(isHit) // 0.5 is exactly on the boundary — neither condition met
    }

    @Test
    fun `accuracy preserves precision for large samples`() {
        val row = AlgoAccuracyRow(algoName = "bayes", total = 1000, hits = 573)
        assertEquals(0.573f, row.accuracy, 0.001f)
    }
}
