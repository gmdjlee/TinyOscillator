package com.tinyoscillator.domain.indicator

import com.tinyoscillator.core.testing.annotations.FastTest
import com.tinyoscillator.core.testing.fixture.SyntheticData
import org.junit.experimental.categories.Category
import com.tinyoscillator.domain.model.Indicator
import org.junit.Assert.*
import org.junit.Test

@Category(FastTest::class)
class IndicatorCalculatorTest {

    // ── EMA ──────────────────────────────────────────────────────────────
    @Test
    fun `ema output length equals input length`() {
        val closes = SyntheticData.candles(50).map { it.close }.toFloatArray()
        assertEquals(50, IndicatorCalculator.ema(closes, 20).size)
    }

    @Test
    fun `ema warmup period is NaN`() {
        val closes = FloatArray(30) { 100f }
        val result = IndicatorCalculator.ema(closes, 20)
        repeat(19) { assertTrue("index $it should be NaN", result[it].isNaN()) }
        assertFalse(result[19].isNaN())
    }

    @Test
    fun `ema of constant series equals the constant`() {
        val closes = FloatArray(50) { 100f }
        val result = IndicatorCalculator.ema(closes, 10)
        result.filterNot { it.isNaN() }.forEach {
            assertEquals(100f, it, 0.001f)
        }
    }

    @Test
    fun `ema responds to price increase`() {
        val flat = FloatArray(20) { 100f }
        val spike = FloatArray(10) { 110f }
        val closes = flat + spike
        val result = IndicatorCalculator.ema(closes, 5)
        assertTrue("EMA should increase after price spike", result.last() > 100f)
    }

    // ── Bollinger ─────────────────────────────────────────────────────────
    @Test
    fun `bollinger upper always gte mid gte lower`() {
        val closes = SyntheticData.candles(60).map { it.close }.toFloatArray()
        val boll = IndicatorCalculator.bollinger(closes, 20, 2f)
        (0 until closes.size).filter { !boll.upper[it].isNaN() }.forEach { i ->
            assertTrue("upper >= mid at $i", boll.upper[i] >= boll.mid[i])
            assertTrue("mid >= lower at $i", boll.mid[i] >= boll.lower[i])
        }
    }

    @Test
    fun `bollinger mid equals simple moving average`() {
        val closes = FloatArray(30) { (it + 1).toFloat() }
        val boll = IndicatorCalculator.bollinger(closes, 10, 2f)
        // index 9 (첫 유효): SMA(1..10) = 5.5
        assertEquals(5.5f, boll.mid[9], 0.01f)
    }

    @Test
    fun `bollinger output length equals input`() {
        val closes = SyntheticData.candles(50).map { it.close }.toFloatArray()
        val boll = IndicatorCalculator.bollinger(closes)
        assertEquals(50, boll.upper.size)
        assertEquals(50, boll.mid.size)
        assertEquals(50, boll.lower.size)
    }

    // ── MACD ─────────────────────────────────────────────────────────────
    @Test
    fun `macd histogram equals macd minus signal`() {
        val closes = SyntheticData.candles(60).map { it.close }.toFloatArray()
        val macd = IndicatorCalculator.macd(closes)
        macd.histogram.forEachIndexed { i, h ->
            if (!h.isNaN()) {
                assertEquals(
                    "histogram mismatch at index $i",
                    macd.macdLine[i] - macd.signalLine[i], h, 1e-4f
                )
            }
        }
    }

    @Test
    fun `macd output length equals input`() {
        val closes = SyntheticData.candles(60).map { it.close }.toFloatArray()
        val macd = IndicatorCalculator.macd(closes)
        assertEquals(60, macd.macdLine.size)
        assertEquals(60, macd.signalLine.size)
        assertEquals(60, macd.histogram.size)
    }

    @Test
    fun `macd warmup is NaN`() {
        val closes = SyntheticData.candles(60).map { it.close }.toFloatArray()
        val macd = IndicatorCalculator.macd(closes, fast = 12, slow = 26, signal = 9)
        // slow EMA starts at index 25, signal EMA needs 9 more on top of that
        // slow=26 → first valid MACD line at index 25
        // signal=9 → first valid signal at index 25 + 8 = 33
        assertTrue(macd.signalLine[32].isNaN())
        assertFalse(macd.signalLine[33].isNaN())
    }

    // ── RSI ──────────────────────────────────────────────────────────────
    @Test
    fun `rsi output always in 0 to 100`() {
        val closes = SyntheticData.candles(100).map { it.close }.toFloatArray()
        IndicatorCalculator.rsi(closes, 14).filterNot { it.isNaN() }.forEach { v ->
            assertTrue("RSI $v out of range", v in 0f..100f)
        }
    }

    @Test
    fun `rsi is 100 when all gains`() {
        val closes = FloatArray(20) { (100f + it) }
        val result = IndicatorCalculator.rsi(closes, 5)
        assertEquals(100f, result[5], 0.01f)
    }

    @Test
    fun `rsi is 0 when all losses`() {
        val closes = FloatArray(20) { (100f - it) }
        val result = IndicatorCalculator.rsi(closes, 5)
        assertEquals(0f, result[5], 0.01f)
    }

    @Test
    fun `rsi output length equals input`() {
        val closes = SyntheticData.candles(50).map { it.close }.toFloatArray()
        assertEquals(50, IndicatorCalculator.rsi(closes, 14).size)
    }

    // ── Stochastic ────────────────────────────────────────────────────────
    @Test
    fun `stochastic k in 0 to 100`() {
        val candles = SyntheticData.candles(60)
        val result = IndicatorCalculator.stochastic(candles, 14)
        result.k.filterNot { it.isNaN() }.forEach { v ->
            assertTrue("Stoch K $v out of range", v in 0f..100f)
        }
    }

    // ── build() 통합 ─────────────────────────────────────────────────────
    @Test
    fun `build returns ema when ema selected`() {
        val candles = SyntheticData.candles(50)
        val data = IndicatorCalculator.build(
            candles,
            setOf(Indicator.EMA_SHORT),
            emptyMap(),
        )
        assertNotNull(data.emaSeries[Indicator.EMA_SHORT])
        assertNull(data.macd)
        assertNull(data.rsi)
    }

    @Test
    fun `build returns null macd when not selected`() {
        val candles = SyntheticData.candles(50)
        val data = IndicatorCalculator.build(candles, emptySet(), emptyMap())
        assertNull(data.macd)
        assertNull(data.bollinger)
    }
}
