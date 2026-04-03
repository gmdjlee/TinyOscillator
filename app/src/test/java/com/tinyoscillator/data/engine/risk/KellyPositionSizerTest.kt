package com.tinyoscillator.data.engine.risk

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class KellyPositionSizerTest {

    private lateinit var sizer: KellyPositionSizer

    @Before
    fun setup() {
        sizer = KellyPositionSizer(fraction = 0.25, maxPosition = 0.20)
    }

    // --- kelly_fraction ---

    @Test
    fun `kellyFraction returns 0 when signal_prob less than 0_5`() {
        // p=0.4, WLR=1.0 → f* = (0.4*1 - 0.6)/1 = -0.2 → max(0, -0.2) = 0
        assertEquals(0.0, sizer.kellyFraction(0.4, 1.0), 1e-10)
    }

    @Test
    fun `kellyFraction returns 0 when signal_prob is exactly 0_5 with WLR 1`() {
        // p=0.5, WLR=1.0 → f* = (0.5 - 0.5)/1 = 0
        assertEquals(0.0, sizer.kellyFraction(0.5, 1.0), 1e-10)
    }

    @Test
    fun `kellyFraction returns positive when signal_prob greater than 0_5`() {
        // p=0.6, WLR=1.5 → f* = (0.6*1.5 - 0.4)/1.5 = (0.9-0.4)/1.5 = 0.333
        val f = sizer.kellyFraction(0.6, 1.5)
        assertTrue("Expected positive Kelly, got $f", f > 0.0)
    }

    @Test
    fun `kellyFraction with high WLR yields larger fraction`() {
        val lowWlr = sizer.kellyFraction(0.6, 1.0)
        val highWlr = sizer.kellyFraction(0.6, 3.0)
        assertTrue("Higher WLR should give larger Kelly", highWlr > lowWlr)
    }

    @Test
    fun `kellyFraction clamps win_prob to 0-1 range`() {
        val f1 = sizer.kellyFraction(-0.1, 1.0) // treated as 0.0
        assertEquals(0.0, f1, 1e-10)

        val f2 = sizer.kellyFraction(1.1, 1.0) // treated as 1.0
        assertTrue("Kelly with prob=1.0 should be positive", f2 > 0)
    }

    // --- size ---

    @Test
    fun `size output always in 0 to maxPosition range`() {
        val result = sizer.size(signalProb = 0.9, wlr = 5.0, realizedVol = 0.10)
        assertTrue(result.recommendedPct >= 0.0)
        assertTrue(result.recommendedPct <= sizer.maxPosition)
    }

    @Test
    fun `size returns 0 when no edge`() {
        val result = sizer.size(signalProb = 0.3, wlr = 1.0)
        assertEquals(0.0, result.rawKelly, 1e-10)
        assertEquals(0.0, result.recommendedPct, 1e-10)
    }

    @Test
    fun `size applies fractional Kelly`() {
        val result = sizer.size(signalProb = 0.7, wlr = 2.0, realizedVol = 0.15, portfolioVolTarget = 0.15)
        // fracKelly = rawKelly * 0.25
        assertEquals(result.rawKelly * 0.25, result.fracKelly, 1e-10)
    }

    @Test
    fun `size vol adjustment reduces size when realized vol is high`() {
        val lowVol = sizer.size(signalProb = 0.65, wlr = 1.5, realizedVol = 0.10, portfolioVolTarget = 0.15)
        val highVol = sizer.size(signalProb = 0.65, wlr = 1.5, realizedVol = 0.50, portfolioVolTarget = 0.15)
        assertTrue("Higher vol should reduce size", highVol.volAdjSize <= lowVol.volAdjSize)
    }

    @Test
    fun `size signal edge is signalProb minus 0_5`() {
        val result = sizer.size(signalProb = 0.65, wlr = 1.5)
        assertEquals(0.15, result.signalEdge, 1e-10)
    }

    @Test
    fun `size caps at maxPosition`() {
        // Very strong signal should still be capped
        val result = sizer.size(signalProb = 0.99, wlr = 10.0, realizedVol = 0.05, portfolioVolTarget = 0.30)
        assertTrue(result.recommendedPct <= sizer.maxPosition)
    }

    // --- estimateWinLossRatio ---

    @Test
    fun `estimateWinLossRatio returns 1_0 for empty returns`() {
        assertEquals(1.0, sizer.estimateWinLossRatio(doubleArrayOf()), 1e-10)
    }

    @Test
    fun `estimateWinLossRatio clipped to 0_5 to 10_0`() {
        // All positive returns → huge WLR → clipped to 10.0
        val allPositive = DoubleArray(100) { 0.01 }
        assertEquals(1.0, sizer.estimateWinLossRatio(allPositive), 1e-10) // no losses → default 1.0

        // Mix with tiny losses
        val mixReturns = DoubleArray(100) { if (it < 90) 0.05 else -0.001 }
        val wlr = sizer.estimateWinLossRatio(mixReturns)
        assertTrue(wlr in 0.5..10.0)
    }

    @Test
    fun `estimateWinLossRatio is reasonable for typical market returns`() {
        // Simulate returns: slight positive bias
        val returns = DoubleArray(252) { i ->
            if (i % 3 == 0) -0.01 else 0.015
        }
        val wlr = sizer.estimateWinLossRatio(returns)
        assertTrue("WLR should be > 1 for positive bias", wlr > 1.0)
        assertTrue("WLR should be reasonable", wlr < 5.0)
    }

    // --- computeReturns ---

    @Test
    fun `computeReturns produces correct daily returns`() {
        val prices = listOf(100, 110, 105)
        val returns = KellyPositionSizer.computeReturns(prices)
        assertEquals(2, returns.size)
        assertEquals(0.10, returns[0], 1e-10) // (110-100)/100
        assertEquals(-0.04545, returns[1], 1e-4)  // (105-110)/110
    }

    @Test
    fun `computeReturns handles single price`() {
        assertTrue(KellyPositionSizer.computeReturns(listOf(100)).isEmpty())
    }

    // --- realizedVolatility ---

    @Test
    fun `realizedVolatility returns default for insufficient data`() {
        assertEquals(0.30, KellyPositionSizer.realizedVolatility(DoubleArray(5)), 1e-10)
    }

    @Test
    fun `realizedVolatility is positive for typical returns`() {
        val returns = DoubleArray(60) { i -> if (i % 2 == 0) 0.01 else -0.005 }
        val vol = KellyPositionSizer.realizedVolatility(returns)
        assertTrue("Volatility should be positive", vol > 0)
        assertTrue("Volatility should be reasonable (< 200%)", vol < 2.0)
    }
}
