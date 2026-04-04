package com.tinyoscillator.domain.usecase

import com.tinyoscillator.core.testing.annotations.FastTest
import com.tinyoscillator.core.testing.fixture.SyntheticData
import org.junit.experimental.categories.Category
import com.tinyoscillator.domain.model.OhlcvPoint
import com.tinyoscillator.domain.model.PatternType
import org.junit.Assert.*
import org.junit.Test

@Category(FastTest::class)
class CandlePatternDetectorTest {

    // -- single candle --

    @Test
    fun `doji detected - body under 8pct of range`() {
        val c = OhlcvPoint(0, 100f, 110f, 90f, 100.5f, 1_000, "")
        assertTrue(CandlePatternDetector.detect(listOf(c))
            .any { it.type == PatternType.DOJI })
    }

    @Test
    fun `doji NOT detected - body 10pct of range`() {
        val c = OhlcvPoint(0, 100f, 110f, 90f, 102f, 1_000, "")
        assertFalse(CandlePatternDetector.detect(listOf(c))
            .any { it.type == PatternType.DOJI })
    }

    @Test
    fun `hammer detected - lower shadow over 2x body`() {
        // body=2, upper=0.5, lower=22 → lower>body*2 ✓, upper<body*0.5 ✓
        val c = OhlcvPoint(0, 100f, 102.5f, 78f, 102f, 1_000, "")
        assertTrue(CandlePatternDetector.detect(listOf(c))
            .any { it.type == PatternType.HAMMER })
    }

    @Test
    fun `hammer NOT detected - no lower shadow`() {
        val c = OhlcvPoint(0, 100f, 120f, 98f, 118f, 1_000, "")
        assertFalse(CandlePatternDetector.detect(listOf(c))
            .any { it.type == PatternType.HAMMER })
    }

    @Test
    fun `shooting star requires bearish candle`() {
        val bull = OhlcvPoint(0, 100f, 122f, 98f, 118f, 1_000, "")
        assertFalse(CandlePatternDetector.detect(listOf(bull))
            .any { it.type == PatternType.SHOOTING_STAR })
    }

    @Test
    fun `shooting star detected on bearish candle with large upper shadow`() {
        // body=6, upper=15, lower=1 → upper>body*2 ✓, lower<body*0.3=1.8 ✓, !isBull ✓
        val bear = OhlcvPoint(0, 115f, 130f, 108f, 109f, 1_000, "")
        assertTrue(CandlePatternDetector.detect(listOf(bear))
            .any { it.type == PatternType.SHOOTING_STAR })
    }

    // -- 2 candle --

    @Test
    fun `bullish engulfing detected on valid sequence`() {
        val candles = SyntheticData.patternCandles(PatternType.BULLISH_ENGULFING)
        assertTrue(CandlePatternDetector.detect(candles)
            .any { it.type == PatternType.BULLISH_ENGULFING })
    }

    @Test
    fun `bullish engulfing NOT detected same direction`() {
        val candles = listOf(
            OhlcvPoint(0, 100f, 106f, 98f, 105f, 1_000, ""),
            OhlcvPoint(1, 96f, 112f, 95f, 110f, 2_000, ""),
        )
        assertFalse(CandlePatternDetector.detect(candles)
            .any { it.type == PatternType.BULLISH_ENGULFING })
    }

    @Test
    fun `bearish engulfing detected`() {
        val candles = listOf(
            OhlcvPoint(0, 98f, 108f, 97f, 106f, 1_000, ""),
            OhlcvPoint(1, 108f, 110f, 95f, 96f, 2_000, ""),
        )
        assertTrue(CandlePatternDetector.detect(candles)
            .any { it.type == PatternType.BEARISH_ENGULFING })
    }

    @Test
    fun `piercing line requires close above prev midpoint`() {
        val belowMid = listOf(
            OhlcvPoint(0, 110f, 111f, 100f, 100f, 1_000, ""),
            OhlcvPoint(1, 98f, 104f, 97f, 103f, 1_000, ""),
        )
        assertFalse(CandlePatternDetector.detect(belowMid)
            .any { it.type == PatternType.PIERCING_LINE })
    }

    // -- 3 candle --

    @Test
    fun `morning star needs 3 candles`() {
        val two = SyntheticData.patternCandles(PatternType.MORNING_STAR).take(2)
        assertFalse(CandlePatternDetector.detect(two)
            .any { it.type == PatternType.MORNING_STAR })
    }

    @Test
    fun `morning star detected on valid 3-candle sequence`() {
        val candles = SyntheticData.patternCandles(PatternType.MORNING_STAR)
        assertTrue(CandlePatternDetector.detect(candles)
            .any { it.type == PatternType.MORNING_STAR })
    }

    @Test
    fun `three white soldiers all must be bullish`() {
        val notAll = listOf(
            OhlcvPoint(0, 100f, 106f, 98f, 105f, 1_000, ""),
            OhlcvPoint(1, 104f, 110f, 103f, 109f, 1_000, ""),
            OhlcvPoint(2, 108f, 113f, 100f, 101f, 1_000, ""),
        )
        assertFalse(CandlePatternDetector.detect(notAll)
            .any { it.type == PatternType.THREE_WHITE_SOLDIERS })
    }

    @Test
    fun `three black crows detected`() {
        // p2: bear(110→103), p1: bear(104→95) open in [103,110], c: bear(96→84) open in [95,104]
        val candles = listOf(
            OhlcvPoint(0, 110f, 112f, 103f, 103f, 1_000, ""),
            OhlcvPoint(1, 104f, 106f, 95f, 95f, 1_000, ""),
            OhlcvPoint(2, 96f, 98f, 84f, 84f, 1_000, ""),
        )
        assertTrue(CandlePatternDetector.detect(candles)
            .any { it.type == PatternType.THREE_BLACK_CROWS })
    }

    // -- invariants --

    @Test
    fun `all strength values in 0 to 1`() {
        CandlePatternDetector.detect(SyntheticData.candles(100))
            .forEach { r ->
                assertTrue("strength ${r.strength} out of range for ${r.type}",
                    r.strength in 0f..1f)
            }
    }

    @Test
    fun `all indices within candle list bounds`() {
        val candles = SyntheticData.candles(50)
        CandlePatternDetector.detect(candles).forEach { r ->
            assertTrue("index ${r.index} out of bounds [0, ${candles.lastIndex}]",
                r.index in candles.indices)
        }
    }

    @Test
    fun `empty input returns empty result`() =
        assertTrue(CandlePatternDetector.detect(emptyList()).isEmpty())

    @Test
    fun `single candle does not crash`() {
        CandlePatternDetector.detect(SyntheticData.candles(1))
    }

    @Test
    fun `zero-range candle is skipped safely`() {
        val flat = OhlcvPoint(0, 100f, 100f, 100f, 100f, 1_000, "")
        CandlePatternDetector.detect(listOf(flat))
    }

    // -- performance --

    @Test
    fun `250 candles detected under 500ms`() {
        val candles = SyntheticData.candles(250)
        // warmup JIT
        repeat(3) { CandlePatternDetector.detect(candles) }
        val start = System.nanoTime()
        CandlePatternDetector.detect(candles)
        val ms = (System.nanoTime() - start) / 1_000_000L
        assertTrue("Expected <500ms, got ${ms}ms", ms < 500L)
    }

    @Test
    fun `1000 candles detected under 500ms`() {
        val candles = SyntheticData.candles(1_000)
        // warmup JIT
        repeat(3) { CandlePatternDetector.detect(candles) }
        val start = System.nanoTime()
        CandlePatternDetector.detect(candles)
        val ms = (System.nanoTime() - start) / 1_000_000L
        assertTrue("Expected <500ms, got ${ms}ms", ms < 500L)
    }
}
