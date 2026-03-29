package com.tinyoscillator.domain.usecase

import org.junit.Assert.*
import org.junit.Test

/**
 * FearGreedCalculator 순수 계산 로직 검증 테스트
 */
class FearGreedCalculatorTest {

    // -- 헬퍼 --

    private fun dayData(
        date: String,
        index: Double,
        call: Double,
        put: Double,
        vix: Double,
        b5: Double,
        b10: Double
    ) = FearGreedCalculator.FearGreedDayData(date, index, call, put, vix, b5, b10)

    /**
     * 200+ 합성 데이터 생성.
     * 시장 지수는 2000에서 시작하여 소폭 변동.
     */
    private fun generateSyntheticData(count: Int = 250): List<FearGreedCalculator.FearGreedDayData> {
        val data = mutableListOf<FearGreedCalculator.FearGreedDayData>()
        for (i in 0 until count) {
            val dayNum = i + 1
            val dateStr = String.format("2024-%02d-%02d", (dayNum / 28) % 12 + 1, dayNum % 28 + 1)
            val index = 2000.0 + (i % 50) * 5.0 - 125.0 // 변동 범위
            val call = 10000.0 + (i % 30) * 100.0
            val put = 8000.0 + (i % 25) * 120.0
            val vix = 15.0 + (i % 20) * 0.5
            val b5 = 3.0 + (i % 10) * 0.05
            val b10 = 3.5 + (i % 15) * 0.03
            data.add(dayData(dateStr, index, call, put, vix, b5, b10))
        }
        return data
    }

    // ==========================================================
    // rollingMean5 테스트
    // ==========================================================

    @Test
    fun `rollingMean5 — 빈 리스트`() {
        val result = FearGreedCalculator.rollingMean5(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `rollingMean5 — 5개 미만`() {
        val result = FearGreedCalculator.rollingMean5(listOf(10L, 20L, 30L))
        assertEquals(3, result.size)
        // 처음 4개(여기서는 3개 모두)는 NaN
        assertTrue(result[0].isNaN())
        assertTrue(result[1].isNaN())
        assertTrue(result[2].isNaN())
    }

    @Test
    fun `rollingMean5 — 정상 계산`() {
        val values = listOf(10L, 20L, 30L, 40L, 50L, 60L, 70L)
        val result = FearGreedCalculator.rollingMean5(values)

        assertEquals(7, result.size)
        // 처음 4개는 NaN
        for (i in 0..3) assertTrue("index $i should be NaN", result[i].isNaN())
        // index 4: mean(10,20,30,40,50) = 30.0
        assertEquals(30.0, result[4], 0.001)
        // index 5: mean(20,30,40,50,60) = 40.0
        assertEquals(40.0, result[5], 0.001)
        // index 6: mean(30,40,50,60,70) = 50.0
        assertEquals(50.0, result[6], 0.001)
    }

    // ==========================================================
    // calcRsi 테스트
    // ==========================================================

    @Test
    fun `calcRsi — 2개 미만`() {
        val result0 = FearGreedCalculator.calcRsi(emptyList())
        assertTrue(result0.isEmpty())

        val result1 = FearGreedCalculator.calcRsi(listOf(100.0))
        assertEquals(1, result1.size)
        assertTrue(result1[0].isNaN())
    }

    @Test
    fun `calcRsi — 정상 계산`() {
        // 20개 데이터 생성 (변동하는 종가)
        val prices = (0 until 20).map { 100.0 + it * 2.0 - (it % 3) * 3.0 }
        val result = FearGreedCalculator.calcRsi(prices, window = 10)

        assertEquals(20, result.size)
        // 처음 10개는 NaN
        for (i in 0..9) {
            assertTrue("index $i should be NaN", result[i].isNaN())
        }
        // 나머지는 0~100 범위 (NaN이 아닌 유한한 값)
        for (i in 10 until 20) {
            val v = result[i]
            if (v.isFinite()) {
                assertTrue("RSI[$i]=$v should be >= 0", v >= 0.0)
                assertTrue("RSI[$i]=$v should be <= 100", v <= 100.0)
            }
        }
    }

    @Test
    fun `calcRsi — 모든 값 상승`() {
        // 30개 연속 상승 데이터
        val prices = (0 until 30).map { 100.0 + it * 5.0 }
        val result = FearGreedCalculator.calcRsi(prices, window = 10)

        // 모든 상승이므로 avgLoss=0 → NaN (함수 구현: avgLoss==0이면 NaN)
        for (i in 10 until 30) {
            // 순수 상승시 loss=0이므로 RSI는 NaN 반환 (구현 확인)
            assertTrue("All-rising RSI[$i] should be NaN (avgLoss=0)", result[i].isNaN())
        }
    }

    // ==========================================================
    // calcMacd 테스트
    // ==========================================================

    @Test
    fun `calcMacd — 빈 리스트`() {
        val result = FearGreedCalculator.calcMacd(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `calcMacd — 일정한 값`() {
        // 50개 동일한 값
        val constant = List(50) { 100.0 }
        val result = FearGreedCalculator.calcMacd(constant)

        assertEquals(50, result.size)
        // EMA(short) == EMA(long) == 100 → MACD line ≈ 0 → signal ≈ 0 → histogram ≈ 0
        // 마지막 값은 0에 매우 가까워야 함
        assertEquals(0.0, result.last(), 0.01)
    }

    // ==========================================================
    // calcFearGreed 테스트
    // ==========================================================

    @Test
    fun `calcFearGreed — 빈 리스트`() {
        val result = FearGreedCalculator.calcFearGreed(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `calcFearGreed — 최소 데이터`() {
        // 5개만 입력 — 결과는 나오지만 NaN이 많을 수 있음
        val data = (1..5).map { i ->
            dayData("2024-01-0$i", 2000.0 + i, 10000.0, 8000.0, 15.0, 3.0, 3.5)
        }
        val result = FearGreedCalculator.calcFearGreed(data)
        assertEquals(5, result.size)
        // 최소 데이터이므로 대부분 NaN일 수 있음 — 크래시 없이 결과 반환 확인
    }

    @Test
    fun `calcFearGreed — 충분한 데이터`() {
        val data = generateSyntheticData(250)
        val result = FearGreedCalculator.calcFearGreed(data)

        assertEquals(250, result.size)

        // 후반부에 유효한 (finite) 결과가 존재하는지 확인
        val validResults = result.filter { it.fearGreedValue.isFinite() }
        assertTrue("충분한 데이터에서 유효 결과가 있어야 함", validResults.isNotEmpty())

        // 유효한 fearGreedValue는 [0, 1] 범위
        for (r in validResults) {
            assertTrue("fearGreedValue=${r.fearGreedValue} should be >= 0", r.fearGreedValue >= -0.001)
            assertTrue("fearGreedValue=${r.fearGreedValue} should be <= 1", r.fearGreedValue <= 1.001)
        }

        // 유효한 결과 중 oscillator가 finite인 것이 있어야 함
        val finiteOsc = validResults.filter { it.oscillator.isFinite() }
        assertTrue("finite oscillator 값이 있어야 함", finiteOsc.isNotEmpty())
    }

    // ==========================================================
    // calculateEma 테스트
    // ==========================================================

    @Test
    fun `calculateEma — 기본 동작`() {
        val values = listOf(10.0, 20.0, 30.0, 40.0, 50.0)
        val period = 3
        val alpha = 2.0 / (period + 1) // 0.5

        val result = FearGreedCalculator.calculateEma(values, period)
        assertEquals(5, result.size)

        // 첫 값 = values[0]
        assertEquals(10.0, result[0], 0.001)
        // 두 번째: alpha * 20 + (1-alpha) * 10 = 0.5*20 + 0.5*10 = 15
        assertEquals(15.0, result[1], 0.001)
        // 세 번째: 0.5 * 30 + 0.5 * 15 = 22.5
        assertEquals(22.5, result[2], 0.001)
        // 네 번째: 0.5 * 40 + 0.5 * 22.5 = 31.25
        assertEquals(31.25, result[3], 0.001)
        // 다섯 번째: 0.5 * 50 + 0.5 * 31.25 = 40.625
        assertEquals(40.625, result[4], 0.001)
    }

    // ==========================================================
    // minMaxNormalize (via calcFearGreed) 테스트
    // ==========================================================

    @Test
    fun `minMaxNormalize — via calcFearGreed 정규화 값이 0~1 범위`() {
        // 다양한 범위의 데이터 생성
        val data = generateSyntheticData(200)
        val result = FearGreedCalculator.calcFearGreed(data)

        // 유효한 결과의 모든 정규화 구성요소가 [0, 1] 범위인지 확인
        val validResults = result.filter {
            it.momentum.isFinite() && it.putCallRatio.isFinite() &&
                    it.volatility.isFinite() && it.spread.isFinite() && it.rsi.isFinite()
        }

        assertTrue("유효한 정규화 결과가 있어야 함", validResults.isNotEmpty())

        for (r in validResults) {
            assertTrue("momentum=${r.momentum} in [0,1]", r.momentum in 0.0..1.0)
            assertTrue("putCallRatio=${r.putCallRatio} in [0,1]", r.putCallRatio in 0.0..1.0)
            assertTrue("volatility=${r.volatility} in [0,1]", r.volatility in 0.0..1.0)
            assertTrue("spread=${r.spread} in [0,1]", r.spread in 0.0..1.0)
            assertTrue("rsi=${r.rsi} in [0,1]", r.rsi in 0.0..1.0)
        }
    }
}
