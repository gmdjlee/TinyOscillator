package com.tinyoscillator.data.engine

import com.tinyoscillator.domain.model.DailyTrading
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class OrderFlowEngineTest {

    private lateinit var engine: OrderFlowEngine

    @Before
    fun setup() {
        engine = OrderFlowEngine()
    }

    private fun makePrices(
        days: Int,
        foreignGen: (Int) -> Long = { 1_000_000L * (it % 5 - 2) },
        instGen: (Int) -> Long = { 500_000L * (it % 3 - 1) },
        closeGen: (Int) -> Int = { 50000 + it * 100 }
    ): List<DailyTrading> = (0 until days).map { i ->
        DailyTrading(
            date = String.format("2026%02d%02d", 1 + i / 28, 1 + i % 28),
            marketCap = 300_000_000_000L,
            foreignNetBuy = foreignGen(i),
            instNetBuy = instGen(i),
            closePrice = closeGen(i)
        )
    }

    @Test
    fun `analyze returns result with all fields populated`() = runTest {
        val prices = makePrices(60)
        val result = engine.analyze(prices)

        assertNotNull(result)
        assertTrue(result.flowDirection in listOf("BUY", "SELL", "NEUTRAL"))
        assertTrue(result.flowStrength in listOf("STRONG", "MODERATE", "WEAK"))
        assertTrue(result.dataDate.isNotEmpty())
    }

    @Test
    fun `buyerDominanceScore is bounded 0 to 1`() = runTest {
        val prices = makePrices(60)
        val result = engine.analyze(prices)

        assertTrue("Score should be >= 0: ${result.buyerDominanceScore}",
            result.buyerDominanceScore >= 0.0)
        assertTrue("Score should be <= 1: ${result.buyerDominanceScore}",
            result.buyerDominanceScore <= 1.0)
    }

    @Test
    fun `signalScore is bounded 0 to 1`() = runTest {
        val prices = makePrices(100)
        val result = engine.analyze(prices)

        assertTrue(result.signalScore >= 0.0)
        assertTrue(result.signalScore <= 1.0)
    }

    @Test
    fun `OFI is bounded -1 to 1`() = runTest {
        val prices = makePrices(60)
        val result = engine.analyze(prices)

        assertTrue("OFI 5d should be in [-1,1]: ${result.ofi5d}",
            result.ofi5d >= -1.0 && result.ofi5d <= 1.0)
        assertTrue("OFI 20d should be in [-1,1]: ${result.ofi20d}",
            result.ofi20d >= -1.0 && result.ofi20d <= 1.0)
    }

    @Test
    fun `institutional divergence is bounded 0 to 1`() = runTest {
        val prices = makePrices(60)
        val result = engine.analyze(prices)

        assertTrue(result.institutionalDivergence >= 0.0)
        assertTrue(result.institutionalDivergence <= 1.0)
    }

    @Test
    fun `foreign buy pressure is bounded -1 to 1`() = runTest {
        val prices = makePrices(60)
        val result = engine.analyze(prices)

        assertTrue(result.foreignBuyPressure >= -1.0)
        assertTrue(result.foreignBuyPressure <= 1.0)
    }

    @Test
    fun `strong foreign buying produces BUY direction`() = runTest {
        // Increasing foreign buying (with variance for Z-score to work)
        val prices = makePrices(60,
            foreignGen = { (5_000_000_000L + it * 100_000_000L) },  // 50~110억원 매수, 증가 추세
            instGen = { (2_000_000_000L + it * 50_000_000L) },       // 기관도 매수
            closeGen = { 50000 + it * 200 }     // 상승 추세
        )
        val result = engine.analyze(prices)

        assertEquals("BUY", result.flowDirection)
        // OFI should be positive since foreign > retail
        assertTrue("OFI 20d should be positive: ${result.ofi20d}", result.ofi20d > 0)
    }

    @Test
    fun `strong foreign selling produces SELL direction`() = runTest {
        val prices = makePrices(60,
            foreignGen = { -(5_000_000_000L + it * 100_000_000L) },  // 매도 증가
            instGen = { -(2_000_000_000L + it * 50_000_000L) },
            closeGen = { 60000 - it * 200 }     // 하락 추세
        )
        val result = engine.analyze(prices)

        assertEquals("SELL", result.flowDirection)
        // OFI should be negative since foreign < retail (retail = -(foreign+inst), so retail is positive → selling)
        assertTrue("OFI 20d should be negative: ${result.ofi20d}", result.ofi20d < 0)
    }

    @Test
    fun `divergence is high when inst and foreign go opposite`() = runTest {
        val prices = makePrices(60,
            foreignGen = { 5_000_000_000L },    // 외국인 항상 매수
            instGen = { -5_000_000_000L }        // 기관 항상 매도
        )
        val result = engine.analyze(prices)

        assertTrue("Divergence should be high: ${result.institutionalDivergence}",
            result.institutionalDivergence > 0.5)
    }

    @Test
    fun `divergence is low when inst and foreign go same direction`() = runTest {
        val prices = makePrices(60,
            foreignGen = { 5_000_000_000L },    // 둘 다 매수
            instGen = { 3_000_000_000L }
        )
        val result = engine.analyze(prices)

        assertTrue("Divergence should be low: ${result.institutionalDivergence}",
            result.institutionalDivergence < 0.2)
    }

    @Test
    fun `trend alignment is high when flow matches price direction`() = runTest {
        val prices = makePrices(60,
            foreignGen = { 5_000_000_000L },    // 외국인 매수
            instGen = { 1_000_000_000L },
            closeGen = { 50000 + it * 100 }     // 가격 상승 (매수와 일치)
        )
        val result = engine.analyze(prices)

        assertTrue("Trend alignment should be high: ${result.trendAlignment}",
            result.trendAlignment > 0.5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on insufficient data`() = runTest {
        val prices = makePrices(10)  // less than MIN_DAYS=30
        engine.analyze(prices)
    }

    @Test
    fun `zero flow produces neutral result`() = runTest {
        val prices = makePrices(60,
            foreignGen = { 0L },
            instGen = { 0L }
        )
        val result = engine.analyze(prices)

        assertEquals("NEUTRAL", result.flowDirection)
        assertTrue("Score should be near 0.5: ${result.buyerDominanceScore}",
            abs(result.buyerDominanceScore - 0.5) < 0.2)
    }

    @Test
    fun `analysisDetails contains expected keys`() = runTest {
        val prices = makePrices(60)
        val result = engine.analyze(prices)

        assertTrue(result.analysisDetails.containsKey("ofi_5d"))
        assertTrue(result.analysisDetails.containsKey("ofi_20d"))
        assertTrue(result.analysisDetails.containsKey("inst_diverge"))
        assertTrue(result.analysisDetails.containsKey("fbp_20d"))
        assertTrue(result.analysisDetails.containsKey("foreign_last_5d_avg"))
        assertTrue(result.analysisDetails.containsKey("inst_last_5d_avg"))
    }
}
