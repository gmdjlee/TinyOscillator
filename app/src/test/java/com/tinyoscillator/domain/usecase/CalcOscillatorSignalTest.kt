package com.tinyoscillator.domain.usecase

import com.tinyoscillator.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * CalcOscillatorUseCase - analyzeSignals 상세 테스트
 *
 * Trend 판정 로직과 CrossSignal 감지의 경계 조건을 검증합니다.
 */
class CalcOscillatorSignalTest {

    private lateinit var useCase: CalcOscillatorUseCase

    @Before
    fun setup() {
        useCase = CalcOscillatorUseCase(OscillatorConfig())
    }

    // ========== Trend 판정 테스트 ==========

    @Test
    fun `oscillator 양수 + MACD 양수면 BULLISH`() {
        val row = createRow(oscillator = 0.001, macd = 0.002)
        val signals = useCase.analyzeSignals(listOf(row))
        assertEquals(Trend.BULLISH, signals[0].trend)
    }

    @Test
    fun `oscillator 음수 + MACD 음수면 BEARISH`() {
        val row = createRow(oscillator = -0.001, macd = -0.002)
        val signals = useCase.analyzeSignals(listOf(row))
        assertEquals(Trend.BEARISH, signals[0].trend)
    }

    @Test
    fun `oscillator 양수 + MACD 음수면 NEUTRAL`() {
        val row = createRow(oscillator = 0.001, macd = -0.002)
        val signals = useCase.analyzeSignals(listOf(row))
        assertEquals(Trend.NEUTRAL, signals[0].trend)
    }

    @Test
    fun `oscillator 음수 + MACD 양수면 NEUTRAL`() {
        val row = createRow(oscillator = -0.001, macd = 0.002)
        val signals = useCase.analyzeSignals(listOf(row))
        assertEquals(Trend.NEUTRAL, signals[0].trend)
    }

    @Test
    fun `oscillator 0 + MACD 0이면 NEUTRAL`() {
        val row = createRow(oscillator = 0.0, macd = 0.0)
        val signals = useCase.analyzeSignals(listOf(row))
        assertEquals(Trend.NEUTRAL, signals[0].trend)
    }

    @Test
    fun `oscillator 0 + MACD 양수면 NEUTRAL`() {
        val row = createRow(oscillator = 0.0, macd = 0.001)
        val signals = useCase.analyzeSignals(listOf(row))
        assertEquals(Trend.NEUTRAL, signals[0].trend)
    }

    // ========== CrossSignal 감지 테스트 ==========

    @Test
    fun `음에서 양으로 전환은 GOLDEN_CROSS`() {
        val rows = listOf(
            createRow(oscillator = -0.001),
            createRow(oscillator = 0.001)
        )
        val signals = useCase.analyzeSignals(rows)
        assertEquals(CrossSignal.GOLDEN_CROSS, signals[1].crossSignal)
    }

    @Test
    fun `양에서 음으로 전환은 DEAD_CROSS`() {
        val rows = listOf(
            createRow(oscillator = 0.001),
            createRow(oscillator = -0.001)
        )
        val signals = useCase.analyzeSignals(rows)
        assertEquals(CrossSignal.DEAD_CROSS, signals[1].crossSignal)
    }

    @Test
    fun `0에서 양으로 전환은 GOLDEN_CROSS (prevOsc less or equal 0)`() {
        val rows = listOf(
            createRow(oscillator = 0.0),
            createRow(oscillator = 0.001)
        )
        val signals = useCase.analyzeSignals(rows)
        assertEquals(CrossSignal.GOLDEN_CROSS, signals[1].crossSignal)
    }

    @Test
    fun `0에서 음으로 전환은 DEAD_CROSS (prevOsc greater or equal 0)`() {
        val rows = listOf(
            createRow(oscillator = 0.0),
            createRow(oscillator = -0.001)
        )
        val signals = useCase.analyzeSignals(rows)
        assertEquals(CrossSignal.DEAD_CROSS, signals[1].crossSignal)
    }

    @Test
    fun `양에서 양으로 유지하면 crossSignal은 null`() {
        val rows = listOf(
            createRow(oscillator = 0.001),
            createRow(oscillator = 0.002)
        )
        val signals = useCase.analyzeSignals(rows)
        assertNull(signals[1].crossSignal)
    }

    @Test
    fun `음에서 음으로 유지하면 crossSignal은 null`() {
        val rows = listOf(
            createRow(oscillator = -0.001),
            createRow(oscillator = -0.002)
        )
        val signals = useCase.analyzeSignals(rows)
        assertNull(signals[1].crossSignal)
    }

    @Test
    fun `0에서 0으로 유지하면 crossSignal은 null`() {
        val rows = listOf(
            createRow(oscillator = 0.0),
            createRow(oscillator = 0.0)
        )
        val signals = useCase.analyzeSignals(rows)
        assertNull(signals[1].crossSignal)
    }

    @Test
    fun `연속된 교차 감지 (골든→데드)`() {
        val rows = listOf(
            createRow(oscillator = -0.001),
            createRow(oscillator = 0.001),
            createRow(oscillator = -0.001)
        )
        val signals = useCase.analyzeSignals(rows)
        assertEquals(CrossSignal.GOLDEN_CROSS, signals[1].crossSignal)
        assertEquals(CrossSignal.DEAD_CROSS, signals[2].crossSignal)
    }

    // ========== SignalAnalysis 필드 매핑 검증 ==========

    @Test
    fun `SignalAnalysis에 올바른 필드가 매핑된다`() {
        val row = createRow(
            date = "20240315",
            marketCapTril = 300.5,
            oscillator = 0.005,
            macd = 0.008,
            signal = 0.003
        )
        val signals = useCase.analyzeSignals(listOf(row))
        val s = signals[0]
        assertEquals("20240315", s.date)
        assertEquals(300.5, s.marketCapTril, 1e-10)
        assertEquals(0.005, s.oscillator, 1e-10)
        assertEquals(0.008, s.macd, 1e-10)
        assertEquals(0.003, s.signal, 1e-10)
    }

    // ========== Helper ==========

    private fun createRow(
        date: String = "20240101",
        marketCapTril: Double = 100.0,
        oscillator: Double = 0.0,
        macd: Double = 0.0,
        signal: Double = 0.0
    ) = OscillatorRow(
        date = date,
        marketCap = (marketCapTril * 1_000_000_000_000).toLong(),
        marketCapTril = marketCapTril,
        foreign5d = 0L,
        inst5d = 0L,
        supplyRatio = 0.0,
        ema12 = 0.0,
        ema26 = 0.0,
        macd = macd,
        signal = signal,
        oscillator = oscillator
    )
}
