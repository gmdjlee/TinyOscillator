package com.tinyoscillator.data.engine

import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.DemarkTDRow
import com.tinyoscillator.domain.model.OscillatorRow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PatternScanEngineTest {

    private lateinit var engine: PatternScanEngine

    @Before
    fun setup() {
        engine = PatternScanEngine()
    }

    @Test
    fun `8개 패턴이 모두 분석된다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)
        val demarks = generateDemarks(100)

        val result = engine.analyze(prices, oscillators, demarks, null)

        assertEquals("8개 패턴", 8, result.allPatterns.size)
    }

    @Test
    fun `각 패턴의 승률이 0에서 1 범위이다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)
        val demarks = generateDemarks(100)

        val result = engine.analyze(prices, oscillators, demarks, null)

        for (pattern in result.allPatterns) {
            assertTrue("${pattern.patternName} winRate5d >= 0", pattern.winRate5d >= 0.0)
            assertTrue("${pattern.patternName} winRate5d <= 1", pattern.winRate5d <= 1.0)
            assertTrue("${pattern.patternName} winRate10d >= 0", pattern.winRate10d >= 0.0)
            assertTrue("${pattern.patternName} winRate10d <= 1", pattern.winRate10d <= 1.0)
            assertTrue("${pattern.patternName} winRate20d >= 0", pattern.winRate20d >= 0.0)
            assertTrue("${pattern.patternName} winRate20d <= 1", pattern.winRate20d <= 1.0)
        }
    }

    @Test
    fun `activePatterns는 allPatterns의 부분집합이다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)
        val demarks = generateDemarks(100)

        val result = engine.analyze(prices, oscillators, demarks, null)

        val allNames = result.allPatterns.map { it.patternName }.toSet()
        for (active in result.activePatterns) {
            assertTrue("${active.patternName}이 allPatterns에 포함", allNames.contains(active.patternName))
            assertTrue("active 패턴의 isActive가 true", active.isActive)
        }
    }

    @Test
    fun `최근 3회 결과만 반환된다`() = runTest {
        val prices = generatePrices(200)
        val oscillators = generateOscillators(200)
        val demarks = generateDemarks(200)

        val result = engine.analyze(prices, oscillators, demarks, null)

        for (pattern in result.allPatterns) {
            assertTrue("occurrences <= 3", pattern.occurrences.size <= 3)
        }
    }

    @Test
    fun `totalOccurrences가 occurrences size 이상이다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)
        val demarks = generateDemarks(100)

        val result = engine.analyze(prices, oscillators, demarks, null)

        for (pattern in result.allPatterns) {
            assertTrue("totalOccurrences >= occurrences.size",
                pattern.totalOccurrences >= pattern.occurrences.size)
        }
    }

    @Test
    fun `totalHistoricalDays가 정확하다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)
        val demarks = generateDemarks(100)

        val result = engine.analyze(prices, oscillators, demarks, null)

        assertEquals("totalHistoricalDays", 100, result.totalHistoricalDays)
    }

    @Test
    fun `데이터 부족 시 빈 결과 반환`() = runTest {
        val prices = generatePrices(10)
        val oscillators = generateOscillators(10)
        val demarks = generateDemarks(10)

        val result = engine.analyze(prices, oscillators, demarks, null)

        assertTrue("패턴 목록이 비어있어야 함", result.allPatterns.isEmpty())
    }

    @Test
    fun `MDD가 0 이상이다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)
        val demarks = generateDemarks(100)

        val result = engine.analyze(prices, oscillators, demarks, null)

        for (pattern in result.allPatterns) {
            assertTrue("avgMdd20d >= 0", pattern.avgMdd20d >= 0.0)
            for (occ in pattern.occurrences) {
                assertTrue("mdd20d >= 0", occ.mdd20d >= 0.0)
            }
        }
    }

    @Test
    fun `단일 캔들 데이터셋에서 빈 결과 반환`() = runTest {
        val prices = listOf(
            DailyTrading(
                date = "20250101",
                marketCap = 50000000000L,
                foreignNetBuy = 1000000L,
                instNetBuy = -500000L,
                closePrice = 50000
            )
        )
        val oscillators = listOf(
            OscillatorRow(
                date = "20250101",
                marketCap = 50000000000L, marketCapTril = 50.0,
                foreign5d = 1000000L, inst5d = -500000L, supplyRatio = 0.001,
                ema12 = 0.001, ema26 = 0.001, macd = 0.0,
                signal = 0.0, oscillator = 0.0
            )
        )
        val demarks = listOf(
            DemarkTDRow(date = "20250101", closePrice = 50000, marketCapTril = 50.0, tdSellCount = 0, tdBuyCount = 0)
        )

        val result = engine.analyze(prices, oscillators, demarks, null)

        // 1일치 데이터로는 어떤 패턴도 감지할 수 없음
        assertTrue("단일 캔들에서 패턴 없음", result.allPatterns.isEmpty())
    }

    @Test
    fun `2개 캔들 데이터셋에서도 안전하게 처리`() = runTest {
        val prices = (0 until 2).map { i ->
            DailyTrading(
                date = String.format("202501%02d", i + 1),
                marketCap = 50000000000L,
                foreignNetBuy = 1000000L,
                instNetBuy = -500000L,
                closePrice = 50000 + i * 100
            )
        }
        val oscillators = (0 until 2).map { i ->
            OscillatorRow(
                date = String.format("202501%02d", i + 1),
                marketCap = 50000000000L, marketCapTril = 50.0,
                foreign5d = 1000000L, inst5d = -500000L, supplyRatio = 0.001,
                ema12 = 0.001, ema26 = 0.001, macd = 0.0,
                signal = 0.0, oscillator = 0.0
            )
        }
        val demarks = (0 until 2).map { i ->
            DemarkTDRow(
                date = String.format("202501%02d", i + 1),
                closePrice = 50000 + i * 100, marketCapTril = 50.0,
                tdSellCount = 0, tdBuyCount = 0
            )
        }

        val result = engine.analyze(prices, oscillators, demarks, null)

        // 예외 없이 실행되어야 함
        assertTrue("2개 캔들도 안전하게 처리", result.allPatterns.isEmpty())
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

    private fun generateDemarks(days: Int): List<DemarkTDRow> {
        return (0 until days).map { i ->
            DemarkTDRow(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                closePrice = 50000 + (i % 7 - 3) * 500,
                marketCapTril = 50.0,
                tdSellCount = i % 14,
                tdBuyCount = (13 - i % 14).coerceAtLeast(0)
            )
        }
    }
}
