package com.tinyoscillator.data.engine

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import java.util.Random

class VectorizedIndicatorsTest {

    // ─── EMA 테스트 ───

    @Test
    fun `emaArray matches pandas ewm adjust=false for random series`() {
        val rng = Random(42L)
        val n = 252
        val prices = DoubleArray(n) { 50000.0 + rng.nextGaussian() * 1000 }
        val period = 12

        // VectorizedIndicators 결과
        val result = VectorizedIndicators.emaArray(prices, period)

        // 수동 계산 (pandas ewm(span=period, adjust=False) 와 동일)
        val alpha = 2.0 / (period + 1)
        val expected = DoubleArray(n)
        expected[0] = prices[0]
        for (i in 1 until n) {
            expected[i] = alpha * prices[i] + (1 - alpha) * expected[i - 1]
        }

        // 수치 정밀도 검증 (< 1e-6)
        for (i in 0 until n) {
            assertEquals("EMA[$i] 일치", expected[i], result[i], 1e-6)
        }
    }

    @Test
    fun `emaArray with period 1 equals original prices`() {
        val prices = doubleArrayOf(100.0, 200.0, 150.0, 300.0)
        val result = VectorizedIndicators.emaArray(prices, 1)
        assertArrayEquals(prices, result, 1e-10)
    }

    @Test
    fun `emaArray single element`() {
        val result = VectorizedIndicators.emaArray(doubleArrayOf(42.0), 10)
        assertEquals(1, result.size)
        assertEquals(42.0, result[0], 1e-10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `emaArray empty input throws`() {
        VectorizedIndicators.emaArray(doubleArrayOf(), 10)
    }

    @Test
    fun `emaList matches emaArray`() {
        val rng = Random(99L)
        val values = List(100) { rng.nextDouble() * 100 }
        val period = 26

        val fromArray = VectorizedIndicators.emaArray(values.toDoubleArray(), period)
        val fromList = VectorizedIndicators.emaList(values, period)

        assertEquals(fromArray.size, fromList.size)
        for (i in fromArray.indices) {
            assertEquals("인덱스 $i", fromArray[i], fromList[i], 1e-10)
        }
    }

    // ─── MACD 테스트 ───

    @Test
    fun `macdArray produces correct MACD line`() {
        val rng = Random(42L)
        val prices = DoubleArray(252) { 10000.0 + rng.nextGaussian() * 200 }

        val (macdLine, signalLine, histogram) = VectorizedIndicators.macdArray(prices)

        // MACD = EMA(12) - EMA(26)
        val ema12 = VectorizedIndicators.emaArray(prices, 12)
        val ema26 = VectorizedIndicators.emaArray(prices, 26)
        for (i in prices.indices) {
            assertEquals("MACD[$i]", ema12[i] - ema26[i], macdLine[i], 1e-10)
        }

        // Histogram = MACD - Signal
        for (i in prices.indices) {
            assertEquals("Histogram[$i]", macdLine[i] - signalLine[i], histogram[i], 1e-10)
        }

        // 길이 일치
        assertEquals(prices.size, macdLine.size)
        assertEquals(prices.size, signalLine.size)
        assertEquals(prices.size, histogram.size)
    }

    // ─── RSI 테스트 ───

    @Test
    fun `rsiArray output always in 0 to 100`() {
        val rng = Random(42L)
        val prices = DoubleArray(300) { 10000.0 + rng.nextGaussian() * 500 }

        val rsi = VectorizedIndicators.rsiArray(prices, 14)

        for (i in rsi.indices) {
            if (!rsi[i].isNaN()) {
                assertTrue("RSI[$i]=${rsi[i]} >= 0", rsi[i] >= 0.0)
                assertTrue("RSI[$i]=${rsi[i]} <= 100", rsi[i] <= 100.0)
            }
        }
    }

    @Test
    fun `rsiArray first period values are NaN`() {
        val prices = DoubleArray(30) { 100.0 + it * 2.0 }
        val rsi = VectorizedIndicators.rsiArray(prices, 14)

        // 첫 period개는 NaN (인덱스 0~13)
        for (i in 0 until 14) {
            assertTrue("RSI[$i] should be NaN", rsi[i].isNaN())
        }
        // period 이후는 유효한 값
        assertFalse("RSI[14] should not be NaN", rsi[14].isNaN())
    }

    @Test
    fun `rsiArray monotonically increasing prices gives RSI near 100`() {
        val prices = DoubleArray(50) { 1000.0 + it * 10.0 }
        val rsi = VectorizedIndicators.rsiArray(prices, 14)

        // 단조 증가 시 RSI ≈ 100
        val lastRsi = rsi.last()
        assertTrue("단조 증가 RSI=$lastRsi should be close to 100", lastRsi > 95.0)
    }

    @Test
    fun `rsiArray monotonically decreasing prices gives RSI near 0`() {
        val prices = DoubleArray(50) { 10000.0 - it * 10.0 }
        val rsi = VectorizedIndicators.rsiArray(prices, 14)

        val lastRsi = rsi.last()
        assertTrue("단조 감소 RSI=$lastRsi should be close to 0", lastRsi < 5.0)
    }

    // ─── 배치 계산 테스트 ───

    @Test
    fun `batchCompute output shape matches n_tickers`() {
        val rng = Random(42L)
        val nTickers = 100
        val nDays = 252

        val priceMatrix = Array(nTickers) { DoubleArray(nDays) { 50000.0 + rng.nextGaussian() * 5000 } }

        val result = VectorizedIndicators.batchCompute(priceMatrix)

        assertEquals(5, result.size)
        for ((name, arr) in result) {
            assertEquals("$name 크기 = $nTickers", nTickers, arr.size)
        }
    }

    @Test
    fun `batchCompute runs 100 tickers x 252 days within time limit`() {
        val rng = Random(42L)
        val nTickers = 100
        val nDays = 252

        val priceMatrix = Array(nTickers) { DoubleArray(nDays) { 50000.0 + rng.nextGaussian() * 5000 } }

        val start = System.currentTimeMillis()
        val result = VectorizedIndicators.batchCompute(priceMatrix)
        val elapsed = System.currentTimeMillis() - start

        println("batchCompute ${nTickers}x${nDays}: ${elapsed}ms")
        assertTrue("배치 계산 시간 < 500ms (실측: ${elapsed}ms)", elapsed < 500)

        // 결과 유효성 확인
        for ((name, arr) in result) {
            for (i in arr.indices) {
                assertFalse("$name[$i] should not be NaN when prices are valid",
                    name != "rsi" && arr[i].isNaN())
            }
        }
    }

    @Test
    fun `batchCompute ema values match individual emaArray`() {
        val rng = Random(77L)
        val prices = DoubleArray(100) { 5000.0 + rng.nextGaussian() * 200 }

        val batchResult = VectorizedIndicators.batchCompute(arrayOf(prices))
        val directEma12 = VectorizedIndicators.emaArray(prices, 12)

        assertEquals("ema_short 일치", directEma12.last(), batchResult["ema_short"]!![0], 1e-10)
    }

    @Test
    fun `batchCompute empty ticker array`() {
        val result = VectorizedIndicators.batchCompute(emptyArray())
        for ((_, arr) in result) {
            assertEquals(0, arr.size)
        }
    }

    // ─── CalcOscillatorUseCase 호환 테스트 ───

    @Test
    fun `emaList produces same result as original calcEma loop`() {
        val rng = Random(42L)
        val values = List(200) { rng.nextDouble() * 0.001 - 0.0005 }
        val period = 26

        // 원본 루프 기반 계산
        val alpha = 2.0 / (period + 1)
        val expected = ArrayList<Double>(values.size)
        expected.add(values[0])
        for (i in 1 until values.size) {
            expected.add(alpha * values[i] + (1.0 - alpha) * expected[i - 1])
        }

        // VectorizedIndicators 결과
        val result = VectorizedIndicators.emaList(values, period)

        for (i in values.indices) {
            assertEquals("인덱스 $i", expected[i], result[i], 1e-10)
        }
    }
}
