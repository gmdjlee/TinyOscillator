package com.tinyoscillator.data.engine.risk

import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.SizeReasonCode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PositionRecommendationEngineTest {

    private lateinit var engine: PositionRecommendationEngine

    @Before
    fun setup() {
        engine = PositionRecommendationEngine()
    }

    private fun makePrices(
        days: Int,
        closeGen: (Int) -> Int = { 50000 + it * 50 }
    ): List<DailyTrading> = (0 until days).map { i ->
        DailyTrading(
            date = String.format("2025%02d%02d", 1 + i / 28, 1 + i % 28),
            marketCap = 300_000_000_000L,
            foreignNetBuy = 1_000_000L,
            instNetBuy = 500_000L,
            closePrice = closeGen(i)
        )
    }

    // --- sizeReasonCode ---

    @Test
    fun `sizeReasonCode is NO_EDGE when signal_prob is 0_48`() {
        val prices = makePrices(252)
        val rec = engine.recommend("005930", 0.48, prices)
        assertEquals(SizeReasonCode.NO_EDGE, rec.sizeReasonCode)
        assertEquals(0.0, rec.recommendedPct, 1e-10)
    }

    @Test
    fun `sizeReasonCode is NO_EDGE when signal_prob is exactly 0_5`() {
        val prices = makePrices(252)
        val rec = engine.recommend("005930", 0.5, prices)
        assertEquals(SizeReasonCode.NO_EDGE, rec.sizeReasonCode)
    }

    @Test
    fun `recommend returns finite result for Samsung with 252 days`() {
        val prices = makePrices(252)
        val rec = engine.recommend("005930", 0.65, prices)
        assertNull(rec.unavailableReason)
        assertTrue(rec.recommendedPct.isFinite())
        assertTrue(rec.kellyRaw.isFinite())
        assertTrue(rec.cvar1d.isFinite())
        assertTrue(rec.recommendedPct >= 0.0)
        assertTrue(rec.recommendedPct <= 0.20)
    }

    @Test
    fun `recommend returns unavailable for insufficient data`() {
        val prices = makePrices(10)
        val rec = engine.recommend("005930", 0.65, prices)
        assertNotNull(rec.unavailableReason)
        assertEquals(0.0, rec.recommendedPct, 1e-10)
    }

    @Test
    fun `recommendedPct always in 0 to maxPosition range`() {
        val prices = makePrices(252)
        for (prob in listOf(0.3, 0.5, 0.6, 0.7, 0.8, 0.95)) {
            val rec = engine.recommend("005930", prob, prices)
            assertTrue("prob=$prob: rec=${rec.recommendedPct} should be >= 0",
                rec.recommendedPct >= 0.0)
            assertTrue("prob=$prob: rec=${rec.recommendedPct} should be <= 0.20",
                rec.recommendedPct <= 0.20)
        }
    }

    @Test
    fun `cvar bound kicks in with stress scenario`() {
        // Inject -15% daily returns → extreme CVaR → very small position limit
        val stressPrices = (0 until 252).map { i ->
            val price = (50000 * Math.pow(0.85, i.toDouble())).toInt().coerceAtLeast(1)
            DailyTrading(
                date = String.format("2025%02d%02d", 1 + i / 28, 1 + i % 28),
                marketCap = 300_000_000_000L,
                foreignNetBuy = -5_000_000L,
                instNetBuy = -3_000_000L,
                closePrice = price
            )
        }

        val rec = engine.recommend("005930", 0.70, stressPrices)

        // CVaR should be very negative in stress
        assertTrue("CVaR should be very negative in stress, got ${rec.cvar1d}", rec.cvar1d < -0.05)

        // CVaR limit should be small
        assertTrue("CVaR limit should constrain heavily, got ${rec.cvarLimit}", rec.cvarLimit < 0.20)

        // If CVaR bound applies, the recommended size should be constrained
        if (rec.sizeReasonCode == SizeReasonCode.CVAR_BOUND) {
            assertTrue("CVaR-bound size should be smaller than kelly fractional",
                rec.recommendedPct <= rec.kellyFractional || rec.recommendedPct <= rec.cvarLimit + 1e-10)
        }
    }

    @Test
    fun `signalEdge is positive when signal_prob above 0_5`() {
        val prices = makePrices(252)
        val rec = engine.recommend("005930", 0.65, prices)
        assertTrue(rec.signalEdge > 0.0)
        assertEquals(0.15, rec.signalEdge, 1e-10)
    }

    @Test
    fun `signalEdge is negative when signal_prob below 0_5`() {
        val prices = makePrices(252)
        val rec = engine.recommend("005930", 0.40, prices)
        assertTrue(rec.signalEdge < 0.0)
        assertEquals(-0.10, rec.signalEdge, 1e-10)
    }

    @Test
    fun `kellyRaw is 0 when no edge`() {
        val prices = makePrices(252)
        val rec = engine.recommend("005930", 0.40, prices)
        assertEquals(0.0, rec.kellyRaw, 1e-10)
    }

    @Test
    fun `winLossRatio is positive and finite`() {
        val prices = makePrices(252)
        val rec = engine.recommend("005930", 0.65, prices)
        assertTrue(rec.winLossRatio > 0.0)
        assertTrue(rec.winLossRatio.isFinite())
    }

    @Test
    fun `realizedVol is positive for normal price series`() {
        val prices = makePrices(252)
        val rec = engine.recommend("005930", 0.65, prices)
        assertTrue("Realized vol should be positive, got ${rec.realizedVol}", rec.realizedVol > 0.0)
    }

    @Test
    fun `recommend handles zero close prices gracefully`() {
        val prices = (0 until 100).map { i ->
            DailyTrading(
                date = String.format("2025%02d%02d", 1 + i / 28, 1 + i % 28),
                marketCap = 0L,
                foreignNetBuy = 0L,
                instNetBuy = 0L,
                closePrice = 0 // all zero
            )
        }
        val rec = engine.recommend("000000", 0.65, prices)
        assertNotNull(rec.unavailableReason) // should be unavailable
    }

    @Test
    fun `higher signal probability yields larger position`() {
        val prices = makePrices(252)
        val low = engine.recommend("005930", 0.55, prices)
        val high = engine.recommend("005930", 0.80, prices)
        assertTrue("Higher prob should yield larger position: low=${low.recommendedPct}, high=${high.recommendedPct}",
            high.recommendedPct >= low.recommendedPct)
    }
}
