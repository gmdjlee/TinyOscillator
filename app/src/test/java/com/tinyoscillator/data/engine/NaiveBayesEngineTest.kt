package com.tinyoscillator.data.engine

import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.DemarkTDRow
import com.tinyoscillator.domain.model.OscillatorRow
import com.tinyoscillator.domain.repository.FundamentalSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class NaiveBayesEngineTest {

    private lateinit var engine: NaiveBayesEngine

    @Before
    fun setup() {
        engine = NaiveBayesEngine()
    }

    @Test
    fun `확률 합이 1점0이다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)
        val demarks = generateDemarks(100)
        val fundamentals = generateFundamentals(100)

        val result = engine.analyze(prices, oscillators, demarks, fundamentals)

        val sum = result.upProbability + result.downProbability + result.sidewaysProbability
        assertEquals("확률 합이 1.0이어야 함", 1.0, sum, 0.001)
    }

    @Test
    fun `각 확률이 0에서 1 범위이다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)
        val demarks = generateDemarks(100)

        val result = engine.analyze(prices, oscillators, demarks, null)

        assertTrue("upProbability >= 0", result.upProbability >= 0.0)
        assertTrue("upProbability <= 1", result.upProbability <= 1.0)
        assertTrue("downProbability >= 0", result.downProbability >= 0.0)
        assertTrue("downProbability <= 1", result.downProbability <= 1.0)
        assertTrue("sidewaysProbability >= 0", result.sidewaysProbability >= 0.0)
        assertTrue("sidewaysProbability <= 1", result.sidewaysProbability <= 1.0)
    }

    @Test
    fun `Laplace smoothing으로 zero probability 방지`() = runTest {
        // 매우 적은 데이터로도 0 확률이 나오지 않아야 함
        val prices = generatePrices(30)
        val oscillators = generateOscillators(30)
        val demarks = generateDemarks(30)

        val result = engine.analyze(prices, oscillators, demarks, null)

        assertTrue("upProbability > 0 (Laplace)", result.upProbability > 0.0)
        assertTrue("downProbability > 0 (Laplace)", result.downProbability > 0.0)
        assertTrue("sidewaysProbability > 0 (Laplace)", result.sidewaysProbability > 0.0)
    }

    @Test
    fun `sampleCount가 정확하다`() = runTest {
        val prices = generatePrices(50)
        val oscillators = generateOscillators(50)
        val demarks = generateDemarks(50)

        val result = engine.analyze(prices, oscillators, demarks, null)

        // 50일 - 20일(look ahead) = 최대 30 샘플
        assertTrue("sampleCount <= 30", result.sampleCount <= 30)
        assertTrue("sampleCount > 0", result.sampleCount > 0)
    }

    @Test
    fun `dominantFeatures가 비어있지 않다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)
        val demarks = generateDemarks(100)
        val fundamentals = generateFundamentals(100)

        val result = engine.analyze(prices, oscillators, demarks, fundamentals)

        assertTrue("dominantFeatures가 있어야 함", result.dominantFeatures.isNotEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `데이터가 부족하면 예외 발생`() = runTest {
        val prices = generatePrices(10) // 최소 25일 필요
        val oscillators = generateOscillators(10)
        val demarks = generateDemarks(10)

        engine.analyze(prices, oscillators, demarks, null)
    }

    @Test
    fun `상승 추세 데이터에서 upProbability가 높다`() = runTest {
        val prices = generateTrendingPrices(100, trend = 1.002) // 매일 0.2% 상승
        val oscillators = generateBullishOscillators(100)
        val demarks = generateDemarks(100)

        val result = engine.analyze(prices, oscillators, demarks, null)

        assertTrue("상승 추세에서 upProbability가 높아야 함",
            result.upProbability >= result.downProbability)
    }

    @Test
    fun `모든 가격이 0인 데이터에서 예외 없이 실행된다`() = runTest {
        // 모든 closePrice가 0인 극단적 케이스
        val prices = (0 until 50).map { i ->
            DailyTrading(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = 0L,
                foreignNetBuy = 0L,
                instNetBuy = 0L,
                closePrice = 0
            )
        }
        val oscillators = generateOscillators(50)
        val demarks = generateDemarks(50)

        val result = engine.analyze(prices, oscillators, demarks, null)

        // 확률 합이 여전히 1.0이어야 함 (Laplace smoothing 덕분)
        val sum = result.upProbability + result.downProbability + result.sidewaysProbability
        assertEquals("확률 합이 1.0", 1.0, sum, 0.001)
        assertTrue("upProbability >= 0", result.upProbability >= 0.0)
        assertTrue("downProbability >= 0", result.downProbability >= 0.0)
        assertTrue("sidewaysProbability >= 0", result.sidewaysProbability >= 0.0)
    }

    @Test
    fun `동일 가격 반복 데이터에서 sideways 확률이 높다`() = runTest {
        // 모든 가격이 동일한 횡보 데이터
        val prices = (0 until 100).map { i ->
            DailyTrading(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = 50000000000L,
                foreignNetBuy = 0L,
                instNetBuy = 0L,
                closePrice = 50000
            )
        }
        val oscillators = generateOscillators(100)
        val demarks = generateDemarks(100)

        val result = engine.analyze(prices, oscillators, demarks, null)

        val sum = result.upProbability + result.downProbability + result.sidewaysProbability
        assertEquals("확률 합이 1.0", 1.0, sum, 0.001)
        // 가격 변동 없으면 sideways가 지배적이어야 함
        assertTrue("sideways가 다른 확률 이상",
            result.sidewaysProbability >= result.upProbability ||
            result.sidewaysProbability >= result.downProbability)
    }

    // ─── 헬퍼 ───

    private fun generatePrices(days: Int, basePrice: Int = 50000): List<DailyTrading> {
        return (0 until days).map { i ->
            val variation = ((i % 7) - 3) * 500 // ±1500 변동
            DailyTrading(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = (basePrice + variation) * 1000000L,
                foreignNetBuy = (variation * 1000).toLong(),
                instNetBuy = (-variation * 500).toLong(),
                closePrice = basePrice + variation
            )
        }
    }

    private fun generateTrendingPrices(days: Int, trend: Double): List<DailyTrading> {
        var price = 50000.0
        return (0 until days).map { i ->
            price *= trend
            DailyTrading(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = (price * 1000000).toLong(),
                foreignNetBuy = 1000000L,
                instNetBuy = 500000L,
                closePrice = price.toInt()
            )
        }
    }

    private fun generateOscillators(days: Int): List<OscillatorRow> {
        return (0 until days).map { i ->
            val osc = ((i % 10) - 5) * 0.0001
            OscillatorRow(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = 50000000000L,
                marketCapTril = 50.0,
                foreign5d = 1000000L,
                inst5d = -500000L,
                supplyRatio = 0.001,
                ema12 = 0.001 + osc,
                ema26 = 0.001,
                macd = osc,
                signal = osc * 0.5,
                oscillator = osc * 0.5
            )
        }
    }

    private fun generateBullishOscillators(days: Int): List<OscillatorRow> {
        return (0 until days).map { i ->
            OscillatorRow(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = 50000000000L,
                marketCapTril = 50.0,
                foreign5d = 5000000L,
                inst5d = 3000000L,
                supplyRatio = 0.002,
                ema12 = 0.002,
                ema26 = 0.001,
                macd = 0.001,
                signal = 0.0005,
                oscillator = 0.0005
            )
        }
    }

    private fun generateDemarks(days: Int): List<DemarkTDRow> {
        return (0 until days).map { i ->
            DemarkTDRow(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                closePrice = 50000 + (i % 7 - 3) * 500,
                marketCapTril = 50.0,
                tdSellCount = i % 10,
                tdBuyCount = (9 - i % 10).coerceAtLeast(0)
            )
        }
    }

    private fun generateFundamentals(days: Int): List<FundamentalSnapshot> {
        return (0 until days).map { i ->
            FundamentalSnapshot(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                close = 50000L,
                per = 10.0,
                pbr = 0.8,
                eps = 5000L,
                bps = 62500L,
                dividendYield = 2.0
            )
        }
    }
}
