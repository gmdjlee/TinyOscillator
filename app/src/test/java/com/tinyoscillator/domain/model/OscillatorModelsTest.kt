package com.tinyoscillator.domain.model

import org.junit.Assert.*
import org.junit.Test

/**
 * 오실레이터 도메인 모델 단위 테스트
 *
 * DailyTrading, OscillatorRow, ChartData, SignalAnalysis, Trend, CrossSignal 검증
 */
class OscillatorModelsTest {

    // ========== DailyTrading 테스트 ==========

    @Test
    fun `DailyTrading 생성 및 프로퍼티 접근`() {
        val daily = DailyTrading("20240101", 1_000_000_000_000L, 5_000_000_000L, 3_000_000_000L)
        assertEquals("20240101", daily.date)
        assertEquals(1_000_000_000_000L, daily.marketCap)
        assertEquals(5_000_000_000L, daily.foreignNetBuy)
        assertEquals(3_000_000_000L, daily.instNetBuy)
    }

    @Test
    fun `DailyTrading 음수 순매수`() {
        val daily = DailyTrading("20240101", 100L, -500L, -300L)
        assertEquals(-500L, daily.foreignNetBuy)
        assertEquals(-300L, daily.instNetBuy)
    }

    @Test
    fun `DailyTrading 0 시가총액`() {
        val daily = DailyTrading("20240101", 0L, 100L, 50L)
        assertEquals(0L, daily.marketCap)
    }

    @Test
    fun `DailyTrading equals 및 hashCode`() {
        val d1 = DailyTrading("20240101", 100L, 50L, 30L)
        val d2 = DailyTrading("20240101", 100L, 50L, 30L)
        assertEquals(d1, d2)
        assertEquals(d1.hashCode(), d2.hashCode())
    }

    @Test
    fun `DailyTrading copy`() {
        val original = DailyTrading("20240101", 100L, 50L, 30L)
        val copied = original.copy(foreignNetBuy = 999L)
        assertEquals(999L, copied.foreignNetBuy)
        assertEquals(original.date, copied.date)
    }

    // ========== OscillatorRow 테스트 ==========

    @Test
    fun `OscillatorRow 생성 및 프로퍼티 접근`() {
        val row = OscillatorRow(
            date = "20240101",
            marketCap = 300_000_000_000_000L,
            marketCapTril = 300.0,
            foreign5d = 1000L,
            inst5d = 500L,
            supplyRatio = 0.001,
            ema12 = 0.0005,
            ema26 = 0.0003,
            macd = 0.0002,
            signal = 0.00015,
            oscillator = 0.00005
        )
        assertEquals("20240101", row.date)
        assertEquals(300.0, row.marketCapTril, 1e-10)
        assertEquals(0.0002, row.macd, 1e-10)
        assertEquals(0.00005, row.oscillator, 1e-10)
    }

    @Test
    fun `OscillatorRow MACD는 EMA12 - EMA26이어야 한다`() {
        val row = OscillatorRow(
            date = "20240101", marketCap = 100L, marketCapTril = 0.0001,
            foreign5d = 0L, inst5d = 0L, supplyRatio = 0.0,
            ema12 = 0.005, ema26 = 0.003,
            macd = 0.002, signal = 0.001, oscillator = 0.001
        )
        assertEquals(row.ema12 - row.ema26, row.macd, 1e-15)
    }

    @Test
    fun `OscillatorRow 오실레이터는 MACD - 시그널이어야 한다`() {
        val row = OscillatorRow(
            date = "20240101", marketCap = 100L, marketCapTril = 0.0001,
            foreign5d = 0L, inst5d = 0L, supplyRatio = 0.0,
            ema12 = 0.005, ema26 = 0.003,
            macd = 0.002, signal = 0.0015, oscillator = 0.0005
        )
        assertEquals(row.macd - row.signal, row.oscillator, 1e-15)
    }

    @Test
    fun `OscillatorRow equals`() {
        val r1 = OscillatorRow("20240101", 100L, 0.1, 10L, 5L, 0.01, 0.5, 0.3, 0.2, 0.15, 0.05)
        val r2 = OscillatorRow("20240101", 100L, 0.1, 10L, 5L, 0.01, 0.5, 0.3, 0.2, 0.15, 0.05)
        assertEquals(r1, r2)
    }

    // ========== ChartData 테스트 ==========

    @Test
    fun `ChartData 생성 및 프로퍼티 접근`() {
        val rows = listOf(
            OscillatorRow("20240101", 100L, 0.1, 10L, 5L, 0.01, 0.5, 0.3, 0.2, 0.15, 0.05)
        )
        val chart = ChartData("삼성전자", "005930", rows)
        assertEquals("삼성전자", chart.stockName)
        assertEquals("005930", chart.ticker)
        assertEquals(1, chart.rows.size)
    }

    @Test
    fun `ChartData 빈 rows`() {
        val chart = ChartData("테스트", "000000", emptyList())
        assertTrue(chart.rows.isEmpty())
    }

    // ========== SignalAnalysis 테스트 ==========

    @Test
    fun `SignalAnalysis 골든크로스 신호`() {
        val signal = SignalAnalysis(
            date = "20240101",
            marketCapTril = 300.0,
            oscillator = 0.001,
            macd = 0.002,
            signal = 0.001,
            trend = Trend.BULLISH,
            crossSignal = CrossSignal.GOLDEN_CROSS
        )
        assertEquals(CrossSignal.GOLDEN_CROSS, signal.crossSignal)
        assertEquals(Trend.BULLISH, signal.trend)
    }

    @Test
    fun `SignalAnalysis 데드크로스 신호`() {
        val signal = SignalAnalysis(
            date = "20240101",
            marketCapTril = 300.0,
            oscillator = -0.001,
            macd = -0.002,
            signal = -0.001,
            trend = Trend.BEARISH,
            crossSignal = CrossSignal.DEAD_CROSS
        )
        assertEquals(CrossSignal.DEAD_CROSS, signal.crossSignal)
        assertEquals(Trend.BEARISH, signal.trend)
    }

    @Test
    fun `SignalAnalysis 교차 없음`() {
        val signal = SignalAnalysis(
            date = "20240101",
            marketCapTril = 300.0,
            oscillator = 0.0,
            macd = 0.0,
            signal = 0.0,
            trend = Trend.NEUTRAL,
            crossSignal = null
        )
        assertNull(signal.crossSignal)
        assertEquals(Trend.NEUTRAL, signal.trend)
    }

    // ========== Trend enum 테스트 ==========

    @Test
    fun `Trend enum 값이 3개이다`() {
        assertEquals(3, Trend.entries.size)
    }

    @Test
    fun `Trend enum 값 확인`() {
        assertEquals(Trend.BULLISH, Trend.valueOf("BULLISH"))
        assertEquals(Trend.BEARISH, Trend.valueOf("BEARISH"))
        assertEquals(Trend.NEUTRAL, Trend.valueOf("NEUTRAL"))
    }

    // ========== CrossSignal enum 테스트 ==========

    @Test
    fun `CrossSignal enum 값이 2개이다`() {
        assertEquals(2, CrossSignal.entries.size)
    }

    @Test
    fun `CrossSignal enum 값 확인`() {
        assertEquals(CrossSignal.GOLDEN_CROSS, CrossSignal.valueOf("GOLDEN_CROSS"))
        assertEquals(CrossSignal.DEAD_CROSS, CrossSignal.valueOf("DEAD_CROSS"))
    }
}
