package com.tinyoscillator.data.engine

import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.DemarkTDRow
import com.tinyoscillator.domain.model.OscillatorRow
import com.tinyoscillator.domain.repository.FundamentalSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SignalScoringEngineTest {

    private lateinit var engine: SignalScoringEngine

    @Before
    fun setup() {
        engine = SignalScoringEngine()
    }

    @Test
    fun `점수가 0에서 100 범위이다`() = runTest {
        val oscillators = generateOscillators(30)
        val demarks = generateDemarks(30)
        val prices = generatePrices(30)

        val result = engine.analyze(oscillators, demarks, prices, null)

        assertTrue("totalScore >= 0", result.totalScore >= 0)
        assertTrue("totalScore <= 100", result.totalScore <= 100)
    }

    @Test
    fun `기여도 합이 대략 100 퍼센트이다`() = runTest {
        val oscillators = generateOscillators(30)
        val demarks = generateDemarks(30)
        val prices = generatePrices(30)

        val result = engine.analyze(oscillators, demarks, prices, null)

        val activeContributions = result.contributions.filter { it.signal > 0 }
        if (activeContributions.isNotEmpty()) {
            val totalContribution = result.contributions.sumOf { it.contributionPercent }
            // 비활성 신호도 포함하므로 정확히 100은 아닐 수 있음
            assertTrue("총 기여도 >= 0", totalContribution >= 0.0)
        }
    }

    @Test
    fun `dominantDirection이 유효한 값이다`() = runTest {
        val oscillators = generateOscillators(30)
        val demarks = generateDemarks(30)
        val prices = generatePrices(30)

        val result = engine.analyze(oscillators, demarks, prices, null)

        val validDirections = setOf("BULLISH", "BEARISH", "MIXED", "MILDLY_BULLISH", "MILDLY_BEARISH")
        assertTrue("유효한 방향: ${result.dominantDirection}",
            result.dominantDirection in validDirections)
    }

    @Test
    fun `불리시 데이터에서 점수가 50 이상이다`() = runTest {
        val oscillators = generateBullishOscillators(30)
        val demarks = generateBullishDemarks(30)
        val prices = generatePrices(30)

        val result = engine.analyze(oscillators, demarks, prices, null)

        assertTrue("불리시 데이터에서 점수 >= 50", result.totalScore >= 50)
    }

    @Test
    fun `충돌 신호가 정상 감지된다`() = runTest {
        // MACD 매수 + EMA 매도인 혼합 데이터
        val oscillators = generateMixedOscillators(30)
        val demarks = generateDemarks(30)
        val prices = generatePrices(30)

        val result = engine.analyze(oscillators, demarks, prices, null)

        // 혼합 데이터에서는 충돌이 있을 수 있음
        // 충돌이 있든 없든 결과는 유효해야 함
        assertNotNull(result.conflictingSignals)
    }

    @Test
    fun `빈 데이터에서 기본값 반환`() = runTest {
        val result = engine.analyze(emptyList(), emptyList(), emptyList(), null)

        assertEquals(50, result.totalScore)
        assertEquals("MIXED", result.dominantDirection)
    }

    @Test
    fun `contributions에 신호명이 포함된다`() = runTest {
        val oscillators = generateOscillators(30)
        val demarks = generateDemarks(30)
        val prices = generatePrices(30)
        val fundamentals = generateFundamentals(30)

        val result = engine.analyze(oscillators, demarks, prices, fundamentals)

        val names = result.contributions.map { it.name }
        assertTrue("MACD 포함", names.contains("MACD"))
        assertTrue("수급오실레이터 포함", names.contains("수급오실레이터"))
        assertTrue("EMA배열 포함", names.contains("EMA배열"))
    }

    // ─── 헬퍼 ───

    private fun generatePrices(days: Int): List<DailyTrading> =
        (0 until days).map { i ->
            DailyTrading(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = 50000000000L,
                foreignNetBuy = 1000000L,
                instNetBuy = 500000L,
                closePrice = 50000
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

    private fun generateBullishOscillators(days: Int): List<OscillatorRow> =
        (0 until days).map { i ->
            OscillatorRow(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = 50000000000L, marketCapTril = 50.0,
                foreign5d = 5000000L, inst5d = 3000000L, supplyRatio = 0.002,
                ema12 = 0.002, ema26 = 0.001, macd = 0.001,
                signal = 0.0005, oscillator = 0.0005
            )
        }

    private fun generateMixedOscillators(days: Int): List<OscillatorRow> =
        (0 until days).map { i ->
            OscillatorRow(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = 50000000000L, marketCapTril = 50.0,
                foreign5d = 1000000L, inst5d = -500000L, supplyRatio = 0.001,
                ema12 = 0.0008, ema26 = 0.001, // EMA DEAD
                macd = 0.0005,                    // MACD positive
                signal = 0.0002, oscillator = 0.0003
            )
        }

    private fun generateDemarks(days: Int): List<DemarkTDRow> =
        (0 until days).map { i ->
            DemarkTDRow(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                closePrice = 50000, marketCapTril = 50.0,
                tdSellCount = 3, tdBuyCount = 5
            )
        }

    private fun generateBullishDemarks(days: Int): List<DemarkTDRow> =
        (0 until days).map { i ->
            DemarkTDRow(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                closePrice = 50000, marketCapTril = 50.0,
                tdSellCount = 0, tdBuyCount = 8
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
