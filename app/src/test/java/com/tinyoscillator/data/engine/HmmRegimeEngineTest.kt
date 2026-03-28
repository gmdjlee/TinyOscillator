package com.tinyoscillator.data.engine

import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.HmmResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class HmmRegimeEngineTest {

    private lateinit var engine: HmmRegimeEngine

    @Before
    fun setup() {
        engine = HmmRegimeEngine()
    }

    @Test
    fun `Forward algorithm 정규화 확인 - 확률합 1점0`() = runTest {
        val prices = generatePrices(100)
        val observations = engine.generateObservations(prices)
        val alpha = engine.forwardAlgorithm(observations)

        for ((t, probs) in alpha.withIndex()) {
            val sum = probs.sum()
            assertEquals("시점 $t 에서 확률 합이 1.0이어야 함", 1.0, sum, 0.001)
        }
    }

    @Test
    fun `regimeProbabilities 합이 1점0이다`() = runTest {
        val prices = generatePrices(100)
        val result = engine.analyze(prices)

        val sum = result.regimeProbabilities.sum()
        assertEquals("레짐 확률 합이 1.0이어야 함", 1.0, sum, 0.001)
    }

    @Test
    fun `각 레짐 확률이 0에서 1 범위이다`() = runTest {
        val prices = generatePrices(100)
        val result = engine.analyze(prices)

        for (i in result.regimeProbabilities.indices) {
            assertTrue("레짐 $i 확률 >= 0", result.regimeProbabilities[i] >= 0.0)
            assertTrue("레짐 $i 확률 <= 1", result.regimeProbabilities[i] <= 1.0)
        }
    }

    @Test
    fun `currentRegime이 0에서 3 범위이다`() = runTest {
        val prices = generatePrices(100)
        val result = engine.analyze(prices)

        assertTrue("currentRegime >= 0", result.currentRegime >= 0)
        assertTrue("currentRegime <= 3", result.currentRegime <= 3)
    }

    @Test
    fun `Viterbi 경로가 유효한 상태만 포함한다`() = runTest {
        val prices = generatePrices(100)
        val result = engine.analyze(prices)

        for (state in result.recentRegimePath) {
            assertTrue("상태 $state 는 0~3 범위", state in 0..3)
        }
    }

    @Test
    fun `regimeDescription이 비어있지 않다`() = runTest {
        val prices = generatePrices(100)
        val result = engine.analyze(prices)

        assertTrue("regimeDescription이 비어있지 않아야 함", result.regimeDescription.isNotBlank())
    }

    @Test
    fun `안정적 상승 데이터에서 상승 레짐 확률이 높다`() = runTest {
        // 꾸준히 상승하는 저변동 데이터
        val prices = generateStableTrendingPrices(100, dailyReturn = 0.005)
        val result = engine.analyze(prices)

        // REGIME_0 (저변동 상승) 또는 REGIME_2 (고변동 상승) 확률이 높아야 함
        val upRegimeProb = result.regimeProbabilities[HmmResult.REGIME_LOW_VOL_UP] +
                result.regimeProbabilities[HmmResult.REGIME_HIGH_VOL_UP]
        assertTrue("상승 레짐 확률이 0.3 이상이어야 함", upRegimeProb >= 0.3)
    }

    @Test
    fun `gaussianPdf가 양수를 반환한다`() {
        val pdf = engine.gaussianPdf(0.0, 0.0, 1.0)
        assertTrue("가우시안 PDF > 0", pdf > 0.0)
    }

    @Test
    fun `gaussianPdf 평균에서 최대값이다`() {
        val atMean = engine.gaussianPdf(0.0, 0.0, 1.0)
        val awayFromMean = engine.gaussianPdf(2.0, 0.0, 1.0)
        assertTrue("평균에서 더 높은 확률", atMean > awayFromMean)
    }

    @Test
    fun `transitionProbabilities가 비어있지 않다`() = runTest {
        val prices = generatePrices(100)
        val result = engine.analyze(prices)

        assertTrue("전환 확률이 있어야 함", result.transitionProbabilities.isNotEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `데이터 부족 시 예외 발생`() = runTest {
        val prices = generatePrices(30) // 최소 61일 필요
        engine.analyze(prices)
    }

    // ─── 헬퍼 ───

    private fun generatePrices(days: Int): List<DailyTrading> {
        return (0 until days).map { i ->
            val variation = ((i % 7) - 3) * 500
            DailyTrading(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = (50000 + variation) * 1000000L,
                foreignNetBuy = (variation * 1000).toLong(),
                instNetBuy = (-variation * 500).toLong(),
                closePrice = 50000 + variation
            )
        }
    }

    private fun generateStableTrendingPrices(days: Int, dailyReturn: Double): List<DailyTrading> {
        var price = 50000.0
        return (0 until days).map { i ->
            price *= (1.0 + dailyReturn)
            DailyTrading(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = (price * 1000000).toLong(),
                foreignNetBuy = 1000000L,
                instNetBuy = 500000L,
                closePrice = price.toInt()
            )
        }
    }
}
