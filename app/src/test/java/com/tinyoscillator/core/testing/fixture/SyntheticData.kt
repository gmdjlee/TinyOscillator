package com.tinyoscillator.core.testing.fixture

import com.tinyoscillator.domain.model.OhlcvPoint
import com.tinyoscillator.domain.model.PatternType

object SyntheticData {

    fun candles(
        count: Int = 50,
        basePrice: Float = 70_000f,
        volatility: Float = 0.02f,
        seed: Long = 42L,
    ): List<OhlcvPoint> {
        val rng = java.util.Random(seed)
        var price = basePrice
        return (0 until count).map { i ->
            val change = price * volatility * (rng.nextFloat() - 0.5f)
            val open = price
            val close = (price + change).coerceAtLeast(1f)
            val high = maxOf(open, close) * (1f + rng.nextFloat() * 0.005f)
            val low = minOf(open, close) * (1f - rng.nextFloat() * 0.005f)
            price = close
            OhlcvPoint(
                i, open, high, low, close,
                (kotlin.math.abs(rng.nextLong()) % 1_000_000L + 10_000L),
                "2025.${(i / 20 + 1).toString().padStart(2, '0')}.${(i % 20 + 1).toString().padStart(2, '0')}"
            )
        }
    }

    fun patternCandles(type: PatternType): List<OhlcvPoint> = when (type) {
        PatternType.DOJI ->
            listOf(OhlcvPoint(0, 100f, 110f, 90f, 100.5f, 1000, ""))

        PatternType.HAMMER ->
            listOf(OhlcvPoint(0, 100f, 102f, 78f, 101f, 1000, ""))

        PatternType.BULLISH_ENGULFING -> listOf(
            OhlcvPoint(0, 106f, 107f, 97f, 97f, 1000, ""),
            OhlcvPoint(1, 94f, 110f, 93f, 109f, 2000, ""),
        )

        PatternType.MORNING_STAR -> listOf(
            OhlcvPoint(0, 110f, 112f, 100f, 100f, 1000, ""),
            OhlcvPoint(1, 99f, 101f, 97f, 99f, 500, ""),
            OhlcvPoint(2, 100f, 113f, 99f, 110f, 2000, ""),
        )

        else -> candles(10)
    }
}
