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

    /**
     * 박병창 신호 테스트용 — 상승 추세 30봉 생성 (SMA5 > SMA20 구간 포함).
     * 앞 20봉은 SMA 워밍업용, 이후 10봉에서 신호 발생 가능.
     */
    fun uptrend(count: Int = 30, basePrice: Float = 10_000f): List<OhlcvPoint> {
        var price = basePrice
        return (0 until count).map { i ->
            val gain = price * 0.01f  // 매일 +1% 상승
            val open = price
            val close = price + gain
            val high = close * 1.005f
            val low = open * 0.995f
            price = close
            OhlcvPoint(i, open, high, low, close, 100_000L, "2025.01.${(i + 1).toString().padStart(2, '0')}")
        }
    }

    /**
     * 하락 추세 30봉 — 매도 신호 테스트용.
     */
    fun downtrend(count: Int = 30, basePrice: Float = 10_000f): List<OhlcvPoint> {
        var price = basePrice
        return (0 until count).map { i ->
            val loss = price * 0.01f
            val open = price
            val close = price - loss
            val high = open * 1.005f
            val low = close * 0.995f
            price = close
            OhlcvPoint(i, open, high, low, close, 100_000L, "2025.01.${(i + 1).toString().padStart(2, '0')}")
        }
    }

    /**
     * 특정 신호 유형 테스트용 합성 데이터.
     */
    @Suppress("LongMethod")
    fun signalCandles(type: PatternType): List<OhlcvPoint> = when (type) {
        PatternType.BUY_TREND -> {
            // 20봉 상승 + 양봉 + 거래량 급증(마지막 봉)
            uptrend(25).mapIndexed { i, p ->
                if (i == 24) p.copy(volume = 500_000L) else p
            }
        }

        PatternType.BUY_PULLBACK -> {
            // 20봉 상승 후 5봉 조정 (5일선 아래, 20일선 위, 양봉 반등)
            val rising = uptrend(20)
            val lastPrice = rising.last().close
            val pullback = (0 until 5).map { j ->
                val i = 20 + j
                val dip = lastPrice * (1f - 0.005f * (j + 1))
                if (j == 4) {
                    // 반등 양봉
                    OhlcvPoint(i, dip * 0.99f, dip * 1.01f, dip * 0.985f, dip, 100_000L, "")
                } else {
                    // 조정 음봉
                    OhlcvPoint(i, dip, dip * 1.005f, dip * 0.995f, dip * 0.99f, 80_000L, "")
                }
            }
            rising + pullback
        }

        PatternType.BUY_REVERSAL -> {
            // 하락 추세 20봉 + 거래량 폭증 + 아래꼬리 긴 봉
            val falling = downtrend(24)
            val lastPrice = falling.last().close
            val reversal = OhlcvPoint(
                24, lastPrice, lastPrice * 1.01f, lastPrice * 0.95f, lastPrice * 0.99f,
                1_500_000L, "",  // 거래량 15배
            )
            falling + reversal
        }

        PatternType.SELL_TOP -> {
            // 20봉 상승 후 음봉 + 거래량 급증
            val rising = uptrend(24)
            val peak = rising.last().close
            val topCandle = OhlcvPoint(
                24, peak * 1.01f, peak * 1.03f, peak * 0.98f, peak * 0.99f,
                500_000L, "",
            )
            rising + topCandle
        }

        PatternType.SELL_BREAKDOWN -> {
            // 상승 후 조정, 연속 음봉 2개 (5~20일선 사이)
            val rising = uptrend(20)
            val lastPrice = rising.last().close
            val breakdown = (0 until 5).map { j ->
                val i = 20 + j
                val dip = lastPrice * (1f - 0.005f * (j + 1))
                OhlcvPoint(i, dip, dip * 1.005f, dip * 0.99f, dip * 0.99f, 100_000L, "")
            }
            rising + breakdown
        }

        PatternType.BULL_FIFTY -> {
            // 전일 양봉 + 당일 양봉으로 중간값 위 유지
            val base = uptrend(24)
            val prev = base.last()
            val mid = (prev.open + prev.close) / 2f
            val bullFifty = OhlcvPoint(
                24, mid + 10f, mid + 50f, mid + 5f, mid + 40f, 100_000L, "",
            )
            base + bullFifty
        }

        PatternType.BEAR_FIFTY -> {
            // 전일 음봉 + 당일 음봉으로 중간값 아래
            val base = downtrend(24)
            val prev = base.last()
            val mid = (prev.open + prev.close) / 2f
            val bearFifty = OhlcvPoint(
                24, mid - 10f, mid - 5f, mid - 50f, mid - 40f, 100_000L, "",
            )
            base + bearFifty
        }
    }
}
