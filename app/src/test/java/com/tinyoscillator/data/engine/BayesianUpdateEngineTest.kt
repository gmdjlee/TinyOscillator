package com.tinyoscillator.data.engine

import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.DemarkTDRow
import com.tinyoscillator.domain.model.OscillatorRow
import com.tinyoscillator.domain.repository.EtfAmountPoint
import com.tinyoscillator.domain.repository.FundamentalSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BayesianUpdateEngineTest {

    private lateinit var engine: BayesianUpdateEngine

    @Before
    fun setup() {
        engine = BayesianUpdateEngine()
    }

    @Test
    fun `posterior가 0에서 1 범위이다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)
        val demarks = generateDemarks(100)

        val result = engine.analyze(prices, oscillators, demarks, null)

        assertTrue("posterior >= 0", result.finalPosterior >= 0.0)
        assertTrue("posterior <= 1", result.finalPosterior <= 1.0)
    }

    @Test
    fun `prior가 0에서 1 범위이다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)
        val demarks = generateDemarks(100)

        val result = engine.analyze(prices, oscillators, demarks, null)

        assertTrue("prior >= 0", result.priorProbability >= 0.0)
        assertTrue("prior <= 1", result.priorProbability <= 1.0)
    }

    @Test
    fun `갱신 히스토리가 비어있지 않다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)
        val demarks = generateDemarks(100)

        val result = engine.analyze(prices, oscillators, demarks, null)

        assertTrue("갱신 히스토리 존재", result.updateHistory.isNotEmpty())
    }

    @Test
    fun `각 갱신의 before와 after가 0에서 1 범위이다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)
        val demarks = generateDemarks(100)

        val result = engine.analyze(prices, oscillators, demarks, null)

        for (update in result.updateHistory) {
            assertTrue("beforeProb >= 0: ${update.signalName}", update.beforeProb >= 0.0)
            assertTrue("beforeProb <= 1: ${update.signalName}", update.beforeProb <= 1.0)
            assertTrue("afterProb >= 0: ${update.signalName}", update.afterProb >= 0.0)
            assertTrue("afterProb <= 1: ${update.signalName}", update.afterProb <= 1.0)
        }
    }

    @Test
    fun `deltaProb가 afterProb 마이너스 beforeProb와 일치한다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)
        val demarks = generateDemarks(100)

        val result = engine.analyze(prices, oscillators, demarks, null)

        for (update in result.updateHistory) {
            val expected = update.afterProb - update.beforeProb
            assertEquals("delta = after - before: ${update.signalName}",
                expected, update.deltaProb, 0.001)
        }
    }

    @Test
    fun `마지막 갱신의 afterProb가 finalPosterior와 일치한다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)
        val demarks = generateDemarks(100)

        val result = engine.analyze(prices, oscillators, demarks, null)

        if (result.updateHistory.isNotEmpty()) {
            val lastAfter = result.updateHistory.last().afterProb
            assertEquals("마지막 after = finalPosterior",
                result.finalPosterior, lastAfter, 0.001)
        }
    }

    @Test
    fun `likelihoodRatio가 양수이다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)
        val demarks = generateDemarks(100)

        val result = engine.analyze(prices, oscillators, demarks, null)

        for (update in result.updateHistory) {
            assertTrue("likelihoodRatio > 0: ${update.signalName}",
                update.likelihoodRatio > 0.0)
        }
    }

    @Test
    fun `데이터 부족 시 기본값 반환`() = runTest {
        val prices = generatePrices(15) // 최소 30 필요
        val oscillators = generateOscillators(15)
        val demarks = generateDemarks(15)

        val result = engine.analyze(prices, oscillators, demarks, null)

        assertEquals("기본 posterior", 0.5, result.finalPosterior, 0.001)
        assertEquals("기본 prior", 0.5, result.priorProbability, 0.001)
    }

    @Test
    fun `펀더멘털 데이터 포함 시 PBR 신호가 추가된다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)
        val demarks = generateDemarks(100)
        val fundamentals = generateFundamentals(100)

        val result = engine.analyze(prices, oscillators, demarks, fundamentals)

        val pbrUpdates = result.updateHistory.filter { it.signalName.startsWith("PBR") }
        assertTrue("PBR 갱신이 있어야 함", pbrUpdates.isNotEmpty())
    }

    // ─── ETF_FLOW 신호 테스트 ───

    @Test
    fun `ETF자금흐름 신호 포함 시 갱신 히스토리에 ETF자금흐름이 있다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)
        val demarks = generateDemarks(100)
        val etfTrend = generateEtfAmountTrend(100)

        val result = engine.analyze(prices, oscillators, demarks, null, etfTrend)

        val etfUpdates = result.updateHistory.filter { it.signalName.startsWith("ETF자금흐름") }
        assertTrue("ETF자금흐름 갱신이 있어야 함", etfUpdates.isNotEmpty())
    }

    @Test
    fun `ETF 데이터 없이도 기존 결과와 동일하다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)
        val demarks = generateDemarks(100)

        val resultWithout = engine.analyze(prices, oscillators, demarks, null, null)
        val resultDefault = engine.analyze(prices, oscillators, demarks, null)

        assertEquals("동일 posterior", resultWithout.finalPosterior, resultDefault.finalPosterior, 0.001)
        assertEquals("동일 prior", resultWithout.priorProbability, resultDefault.priorProbability, 0.001)
    }

    @Test
    fun `ETF자금흐름 포함 시 posterior가 유효 범위이다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)
        val demarks = generateDemarks(100)
        val etfTrend = generateEtfAmountTrend(100)

        val result = engine.analyze(prices, oscillators, demarks, null, etfTrend)

        assertTrue("posterior >= 0", result.finalPosterior >= 0.0)
        assertTrue("posterior <= 1", result.finalPosterior <= 1.0)
        assertTrue("prior >= 0", result.priorProbability >= 0.0)
        assertTrue("prior <= 1", result.priorProbability <= 1.0)
    }

    // ─── 헬퍼 ───

    private fun generateEtfAmountTrend(days: Int): List<EtfAmountPoint> =
        (0 until days).map { i ->
            EtfAmountPoint(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                totalAmount = 1000000L + (i * 10000L) + ((i % 5 - 2) * 50000L),
                etfCount = 5 + (i % 3)
            )
        }

    private fun generatePrices(days: Int): List<DailyTrading> =
        (0 until days).map { i ->
            val variation = ((i % 7) - 3) * 500
            DailyTrading(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = (50000 + variation) * 1000000L,
                foreignNetBuy = (variation * 1000).toLong(),
                instNetBuy = (-variation * 500).toLong(),
                closePrice = 50000 + variation
            )
        }

    private fun generateOscillators(days: Int): List<OscillatorRow> =
        (0 until days).map { i ->
            val osc = ((i % 10) - 5) * 0.0001
            OscillatorRow(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = 50000000000L, marketCapTril = 50.0,
                foreign5d = 1000000L, inst5d = -500000L, supplyRatio = 0.001,
                ema12 = 0.001 + osc, ema26 = 0.001, macd = osc,
                signal = osc * 0.5, oscillator = osc * 0.5
            )
        }

    private fun generateDemarks(days: Int): List<DemarkTDRow> =
        (0 until days).map { i ->
            DemarkTDRow(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                closePrice = 50000, marketCapTril = 50.0,
                tdSellCount = i % 10, tdBuyCount = (9 - i % 10).coerceAtLeast(0)
            )
        }

    private fun generateFundamentals(days: Int): List<FundamentalSnapshot> =
        (0 until days).map { i ->
            FundamentalSnapshot(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                close = 50000L, per = 10.0, pbr = 0.8,
                eps = 5000L, bps = 62500L, dividendYield = 2.0
            )
        }
}
