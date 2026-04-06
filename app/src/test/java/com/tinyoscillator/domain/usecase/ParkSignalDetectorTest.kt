package com.tinyoscillator.domain.usecase

import com.tinyoscillator.core.testing.annotations.FastTest
import com.tinyoscillator.core.testing.fixture.SyntheticData
import com.tinyoscillator.domain.model.OhlcvPoint
import com.tinyoscillator.domain.model.PatternType
import org.junit.Assert.*
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(FastTest::class)
class ParkSignalDetectorTest {

    // ── 매수 제1원칙: 추세 추종 ──

    @Test
    fun `BUY_TREND detected on uptrend with volume surge`() {
        val candles = SyntheticData.signalCandles(PatternType.BUY_TREND)
        assertTrue(
            ParkSignalDetector.detect(candles).any { it.type == PatternType.BUY_TREND }
        )
    }

    @Test
    fun `BUY_TREND NOT detected on downtrend`() {
        val candles = SyntheticData.downtrend(30)
        assertFalse(
            ParkSignalDetector.detect(candles).any { it.type == PatternType.BUY_TREND }
        )
    }

    // ── 매수 제2원칙: 눌림목 ──

    @Test
    fun `BUY_PULLBACK detected after pullback in uptrend`() {
        val candles = SyntheticData.signalCandles(PatternType.BUY_PULLBACK)
        assertTrue(
            ParkSignalDetector.detect(candles).any { it.type == PatternType.BUY_PULLBACK }
        )
    }

    // ── 매수 제3원칙: 역발상 ──

    @Test
    fun `BUY_REVERSAL detected on volume explosion below MA20`() {
        val candles = SyntheticData.signalCandles(PatternType.BUY_REVERSAL)
        assertTrue(
            ParkSignalDetector.detect(candles).any { it.type == PatternType.BUY_REVERSAL }
        )
    }

    @Test
    fun `BUY_REVERSAL NOT detected without volume explosion`() {
        // 하락 추세지만 거래량 평범
        val candles = SyntheticData.downtrend(30)
        assertFalse(
            ParkSignalDetector.detect(candles).any { it.type == PatternType.BUY_REVERSAL }
        )
    }

    // ── 매도 제1원칙: 수익 실현 ──

    @Test
    fun `SELL_TOP detected on bearish candle above MA5 with volume`() {
        val candles = SyntheticData.signalCandles(PatternType.SELL_TOP)
        assertTrue(
            ParkSignalDetector.detect(candles).any { it.type == PatternType.SELL_TOP }
        )
    }

    // ── 매도 제2원칙: 손절 ──

    @Test
    fun `SELL_BREAKDOWN detected on failed bounce between MA5 and MA20`() {
        val candles = SyntheticData.signalCandles(PatternType.SELL_BREAKDOWN)
        assertTrue(
            ParkSignalDetector.detect(candles).any { it.type == PatternType.SELL_BREAKDOWN }
        )
    }

    // ── 50% 룰 ──

    @Test
    fun `BULL_FIFTY detected when price holds above prev yang midpoint`() {
        val candles = SyntheticData.signalCandles(PatternType.BULL_FIFTY)
        assertTrue(
            ParkSignalDetector.detect(candles).any { it.type == PatternType.BULL_FIFTY }
        )
    }

    @Test
    fun `BEAR_FIFTY detected when price fails below prev eum midpoint`() {
        val candles = SyntheticData.signalCandles(PatternType.BEAR_FIFTY)
        assertTrue(
            ParkSignalDetector.detect(candles).any { it.type == PatternType.BEAR_FIFTY }
        )
    }

    // ── 불변 조건 ──

    @Test
    fun `all strength values in 0 to 1`() {
        ParkSignalDetector.detect(SyntheticData.candles(100))
            .forEach { r ->
                assertTrue(
                    "strength ${r.strength} out of range for ${r.type}",
                    r.strength in 0f..1f,
                )
            }
    }

    @Test
    fun `all indices within candle list bounds`() {
        val candles = SyntheticData.candles(50)
        ParkSignalDetector.detect(candles).forEach { r ->
            assertTrue(
                "index ${r.index} out of bounds [0, ${candles.lastIndex}]",
                r.index in candles.indices,
            )
        }
    }

    @Test
    fun `empty input returns empty result`() {
        assertTrue(ParkSignalDetector.detect(emptyList()).isEmpty())
    }

    @Test
    fun `fewer than 20 candles returns empty`() {
        assertTrue(ParkSignalDetector.detect(SyntheticData.candles(15)).isEmpty())
    }

    @Test
    fun `exactly 20 candles does not crash`() {
        ParkSignalDetector.detect(SyntheticData.candles(20))
    }

    // ── 성능 ──

    @Test
    fun `250 candles detected under 500ms`() {
        val candles = SyntheticData.candles(250)
        repeat(3) { ParkSignalDetector.detect(candles) }
        val start = System.nanoTime()
        ParkSignalDetector.detect(candles)
        val ms = (System.nanoTime() - start) / 1_000_000L
        assertTrue("Expected <500ms, got ${ms}ms", ms < 500L)
    }

    @Test
    fun `1000 candles detected under 500ms`() {
        val candles = SyntheticData.candles(1_000)
        repeat(3) { ParkSignalDetector.detect(candles) }
        val start = System.nanoTime()
        ParkSignalDetector.detect(candles)
        val ms = (System.nanoTime() - start) / 1_000_000L
        assertTrue("Expected <500ms, got ${ms}ms", ms < 500L)
    }
}
