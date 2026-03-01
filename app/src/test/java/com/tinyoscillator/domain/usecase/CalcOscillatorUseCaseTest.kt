package com.tinyoscillator.domain.usecase

import com.tinyoscillator.domain.model.CrossSignal
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.OscillatorConfig
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 수급 오실레이터 검증 테스트
 *
 * 엑셀 로직과의 1:1 동일성을 검증합니다.
 */
class CalcOscillatorUseCaseTest {

    private lateinit var useCase: CalcOscillatorUseCase
    private val TOLERANCE = 1e-12

    @Before
    fun setup() {
        useCase = CalcOscillatorUseCase(OscillatorConfig())
    }

    @Test
    fun `EMA 12일 계산이 엑셀 수식과 동일한지 검증`() {
        val values = listOf(10.0, 12.0, 11.0, 13.0, 14.0, 12.0, 15.0, 16.0, 14.0, 13.0)
        val period = 12
        val alpha = 2.0 / (period + 1)

        val result = useCase.calcEma(values, period)

        assertEquals("EMA 첫 값", values[0], result[0], TOLERANCE)

        var expectedEma = values[0]
        for (i in 1 until values.size) {
            expectedEma = alpha * values[i] + (1 - alpha) * expectedEma
            assertEquals("EMA[$i]", expectedEma, result[i], TOLERANCE)
        }
    }

    @Test
    fun `EMA 26일 계산 검증`() {
        val values = List(30) { (it + 1).toDouble() + (it % 3) * 0.5 }
        val period = 26
        val alpha = 2.0 / (period + 1)

        val result = useCase.calcEma(values, period)

        var expected = values[0]
        for (i in 1 until values.size) {
            expected = alpha * values[i] + (1 - alpha) * expected
            assertEquals("EMA26[$i]", expected, result[i], TOLERANCE)
        }
    }

    @Test
    fun `EMA 9일 시그널 계산 검증`() {
        val macdValues = listOf(0.001, -0.002, 0.003, 0.001, -0.001, 0.002, 0.004, -0.003, 0.001, 0.002)
        val period = 9
        val alpha = 2.0 / (period + 1)

        assertEquals("시그널 α 값", 0.2, alpha, TOLERANCE)

        val result = useCase.calcEma(macdValues, period)

        var expected = macdValues[0]
        for (i in 1 until macdValues.size) {
            expected = 0.2 * macdValues[i] + 0.8 * expected
            assertEquals("Signal[$i]", expected, result[i], TOLERANCE)
        }
    }

    @Test
    fun `5일 누적 순매수가 엑셀 rolling sum과 동일한지 검증`() {
        val data = listOf(
            DailyTrading("20240101", 1000000000000, 100, 50),
            DailyTrading("20240102", 1000000000000, 200, -30),
            DailyTrading("20240103", 1000000000000, -50, 100),
            DailyTrading("20240104", 1000000000000, 300, 200),
            DailyTrading("20240105", 1000000000000, 150, -100),
            DailyTrading("20240108", 1000000000000, -200, 50),
            DailyTrading("20240109", 1000000000000, 100, 300),
        )

        val result = useCase.execute(data)

        assertEquals("외국인 5일합 Day1", 100L, result[0].foreign5d)
        assertEquals("기관 5일합 Day1", 50L, result[0].inst5d)
        assertEquals("외국인 5일합 Day2", 300L, result[1].foreign5d)
        assertEquals("외국인 5일합 Day3", 250L, result[2].foreign5d)
        assertEquals("외국인 5일합 Day4", 550L, result[3].foreign5d)
        assertEquals("외국인 5일합 Day5 (full window)", 700L, result[4].foreign5d)
        assertEquals("기관 5일합 Day5", 220L, result[4].inst5d)
        assertEquals("외국인 5일합 Day6 (sliding)", 400L, result[5].foreign5d)
        assertEquals("외국인 5일합 Day7", 300L, result[6].foreign5d)
    }

    @Test
    fun `수급비율이 엑셀 시기외 시트와 동일한지 검증`() {
        val mcap = 1_000_000_000_000L
        val data = listOf(
            DailyTrading("20240101", mcap, 1_000_000_000, 500_000_000),
            DailyTrading("20240102", mcap, -500_000_000, 2_000_000_000),
            DailyTrading("20240103", mcap, 3_000_000_000, -1_000_000_000),
            DailyTrading("20240104", mcap, 0, 0),
            DailyTrading("20240105", mcap, 500_000_000, 500_000_000),
        )

        val result = useCase.execute(data)

        val expectedRatio1 = (1_000_000_000.0 + 500_000_000.0) / mcap.toDouble()
        assertEquals("수급비율 Day1", expectedRatio1, result[0].supplyRatio, TOLERANCE)

        val expectedRatio5 = (4_000_000_000.0 + 2_000_000_000.0) / mcap.toDouble()
        assertEquals("수급비율 Day5", expectedRatio5, result[4].supplyRatio, TOLERANCE)
    }

    @Test
    fun `시가총액이 0일 때 수급비율이 0인지 검증`() {
        val data = listOf(DailyTrading("20240101", 0L, 100, 50))
        val result = useCase.execute(data)
        assertEquals("시가총액 0 → 수급비율 0", 0.0, result[0].supplyRatio, TOLERANCE)
    }

    @Test
    fun `MACD가 EMA12 - EMA26과 동일한지 검증`() {
        val data = generateSampleData(30)
        val result = useCase.execute(data)

        for (row in result) {
            assertEquals("MACD = EMA12 - EMA26", row.ema12 - row.ema26, row.macd, TOLERANCE)
        }
    }

    @Test
    fun `시그널이 MACD의 EMA 9일과 동일한지 검증`() {
        val data = generateSampleData(30)
        val result = useCase.execute(data)
        val macdValues = result.map { it.macd }

        val expectedSignal = useCase.calcEma(macdValues, OscillatorConfig.EMA_SIGNAL)

        for (i in result.indices) {
            assertEquals("시그널[$i]", expectedSignal[i], result[i].signal, TOLERANCE)
        }
    }

    @Test
    fun `오실레이터가 MACD - 시그널과 동일한지 검증`() {
        val data = generateSampleData(50)
        val result = useCase.execute(data)

        for (row in result) {
            assertEquals("오실레이터 = MACD - 시그널", row.macd - row.signal, row.oscillator, TOLERANCE)
        }
    }

    @Test
    fun `시가총액 조단위 변환이 엑셀과 동일한지 검증`() {
        val data = listOf(
            DailyTrading("20240101", 4_500_000_000_000_000L, 100, 50),
            DailyTrading("20240102", 12_345_678_900_000L, 200, 100),
        )
        val result = useCase.execute(data)

        assertEquals("450조", 450.0, result[0].marketCapTril, 0.001)
        assertEquals("~1.23조", 1.23456789, result[1].marketCapTril, 0.00001)
    }

    @Test
    fun `전체 파이프라인이 Python pandas 결과와 동일한지 검증`() {
        val mcap = 100_000_000_000_000L
        val foreignBuys = longArrayOf(
            5_000_000_000, -3_000_000_000, 8_000_000_000, -1_000_000_000, 4_000_000_000,
            -6_000_000_000, 2_000_000_000, 7_000_000_000, -2_000_000_000, 3_000_000_000
        )
        val instBuys = longArrayOf(
            2_000_000_000, 4_000_000_000, -5_000_000_000, 3_000_000_000, 1_000_000_000,
            6_000_000_000, -3_000_000_000, -1_000_000_000, 5_000_000_000, 2_000_000_000
        )

        val data = List(10) { i ->
            DailyTrading("2024010${i + 1}", mcap, foreignBuys[i], instBuys[i])
        }

        val result = useCase.execute(data)

        val f5d = LongArray(10)
        val i5d = LongArray(10)
        for (idx in 0 until 10) {
            val start = maxOf(0, idx - 4)
            f5d[idx] = (start..idx).sumOf { foreignBuys[it] }
            i5d[idx] = (start..idx).sumOf { instBuys[it] }
        }

        val ratios = DoubleArray(10) { (f5d[it] + i5d[it]).toDouble() / mcap.toDouble() }

        val a12 = 2.0 / 13
        val a26 = 2.0 / 27
        val ema12 = DoubleArray(10)
        val ema26 = DoubleArray(10)
        ema12[0] = ratios[0]
        ema26[0] = ratios[0]
        for (idx in 1 until 10) {
            ema12[idx] = a12 * ratios[idx] + (1 - a12) * ema12[idx - 1]
            ema26[idx] = a26 * ratios[idx] + (1 - a26) * ema26[idx - 1]
        }

        val macd = DoubleArray(10) { ema12[it] - ema26[it] }

        val aSignal = 2.0 / 10
        val signal = DoubleArray(10)
        signal[0] = macd[0]
        for (idx in 1 until 10) {
            signal[idx] = aSignal * macd[idx] + (1 - aSignal) * signal[idx - 1]
        }

        val osc = DoubleArray(10) { macd[it] - signal[it] }

        for (idx in 0 until 10) {
            assertEquals("5일합(외국인)[$idx]", f5d[idx], result[idx].foreign5d)
            assertEquals("5일합(기관)[$idx]", i5d[idx], result[idx].inst5d)
            assertEquals("수급비율[$idx]", ratios[idx], result[idx].supplyRatio, TOLERANCE)
            assertEquals("EMA12[$idx]", ema12[idx], result[idx].ema12, TOLERANCE)
            assertEquals("EMA26[$idx]", ema26[idx], result[idx].ema26, TOLERANCE)
            assertEquals("MACD[$idx]", macd[idx], result[idx].macd, TOLERANCE)
            assertEquals("시그널[$idx]", signal[idx], result[idx].signal, TOLERANCE)
            assertEquals("오실레이터[$idx]", osc[idx], result[idx].oscillator, TOLERANCE)
        }
    }

    @Test
    fun `EMA alpha 값이 엑셀 파라미터와 정확히 일치하는지 검증`() {
        val config = OscillatorConfig()
        assertEquals("EMA12 α = 2/13", 2.0 / 13, config.alphaFast, TOLERANCE)
        assertEquals("EMA26 α = 2/27", 2.0 / 27, config.alphaSlow, TOLERANCE)
        assertEquals("Signal α = 2/10", 0.2, config.alphaSignal, TOLERANCE)
    }

    @Test
    fun `골든크로스와 데드크로스 감지 검증`() {
        val data = generateSampleData(50)
        val result = useCase.execute(data)
        val signals = useCase.analyzeSignals(result)

        for (i in 1 until result.size) {
            val prevOsc = result[i - 1].oscillator
            val currOsc = result[i].oscillator

            if (prevOsc <= 0 && currOsc > 0) {
                assertEquals("골든크로스 감지", CrossSignal.GOLDEN_CROSS, signals[i].crossSignal)
            } else if (prevOsc >= 0 && currOsc < 0) {
                assertEquals("데드크로스 감지", CrossSignal.DEAD_CROSS, signals[i].crossSignal)
            } else {
                assertNull("교차 없음", signals[i].crossSignal)
            }
        }
    }

    @Test
    fun `단일 데이터 포인트 처리`() {
        val data = listOf(DailyTrading("20240101", 1000000000000, 100, 50))
        val result = useCase.execute(data)

        assertEquals(1, result.size)
        assertEquals(100L, result[0].foreign5d)
        assertEquals(50L, result[0].inst5d)
        assertEquals("단일포인트 MACD", 0.0, result[0].macd, TOLERANCE)
        assertEquals("단일포인트 오실레이터", 0.0, result[0].oscillator, TOLERANCE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `빈 데이터 입력 시 예외`() {
        useCase.execute(emptyList())
    }

    @Test
    fun `음수 시가총액 처리`() {
        val data = listOf(DailyTrading("20240101", -1000000000000, 100, 50))
        val result = useCase.execute(data)
        assertEquals(1, result.size)
    }

    private fun generateSampleData(days: Int): List<DailyTrading> {
        val mcap = 100_000_000_000_000L
        return List(days) { i ->
            val date = String.format("2024%02d%02d", (i / 28) + 1, (i % 28) + 1)
            val foreignBuy = (Math.sin(i * 0.3) * 5_000_000_000).toLong()
            val instBuy = (Math.cos(i * 0.2) * 3_000_000_000).toLong()
            DailyTrading(date, mcap, foreignBuy, instBuy)
        }
    }
}
