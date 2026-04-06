package com.tinyoscillator.domain.usecase

import com.tinyoscillator.domain.model.OhlcvPoint
import com.tinyoscillator.domain.model.PatternResult
import com.tinyoscillator.domain.model.PatternType

/**
 * 박병창 매매의 기술 신호 감지기.
 *
 * 일봉 OHLCV 데이터에서 다음 7가지 신호를 판별:
 * - 매수 3원칙 (5일선 위 / 눌림목 / 역발상)
 * - 매도 2원칙 (수익실현 / 손절)
 * - 50% 룰 2종 (황소 / 곰)
 *
 * 순간체결량·호가잔량은 일봉에서 사용 불가하므로
 * 거래량 비율(당일/20일 평균)로 대체한다.
 */
object ParkSignalDetector {

    /** SMA 계산에 필요한 최소 봉 수 */
    private const val MIN_CANDLES = 20

    fun detect(candles: List<OhlcvPoint>): List<PatternResult> {
        if (candles.size < MIN_CANDLES) return emptyList()

        val sma5 = sma(candles, 5)
        val sma20 = sma(candles, 20)
        val volRatio = volumeRatio(candles, 20)

        val results = mutableListOf<PatternResult>()

        for (i in MIN_CANDLES until candles.size) {
            val cur = candles[i]
            val prev = candles[i - 1]
            val ma5 = sma5[i]
            val ma20 = sma20[i]
            val prevMa5 = sma5[i - 1]
            val prevMa20 = sma20[i - 1]
            val vr = volRatio[i]

            // ── 매수 제1원칙: 5일선 위 추세 추종 ──
            // 종가 > 5일선, 양봉, 거래량 증가(1.2배 이상)
            if (cur.close > ma5 && cur.close > cur.open && vr >= 1.2f) {
                val strength = calcStrength(
                    priceAboveMa = (cur.close - ma5) / ma5,
                    volumeRatio = vr,
                    bodyRatio = bodyRatio(cur),
                )
                results += PatternResult(i, PatternType.BUY_TREND, strength)
            }

            // ── 매수 제2원칙: 눌림목 (5~20일선 사이) 반등 ──
            // 5일선 아래, 20일선 위, 20일선 상승 중, 양봉
            if (cur.close < ma5 && cur.close > ma20
                && ma20 > prevMa20 && cur.close > cur.open
            ) {
                val depth = (ma5 - cur.close) / (ma5 - ma20).coerceAtLeast(0.01f)
                val strength = (0.5f + depth * 0.3f + minOf(vr / 3f, 0.2f))
                    .coerceIn(0f, 1f)
                results += PatternResult(i, PatternType.BUY_PULLBACK, strength)
            }

            // ── 매수 제3원칙: 20일선 아래 역발상 (고위험) ──
            // 종가 < 20일선, 거래량 3배 이상 폭증, 아래꼬리 길이 > 몸통
            if (cur.close < ma20 && vr >= 3.0f) {
                val lowerShadow = minOf(cur.open, cur.close) - cur.low
                val body = kotlin.math.abs(cur.close - cur.open).coerceAtLeast(0.01f)
                if (lowerShadow > body) {
                    val strength = (minOf(vr / 5f, 0.5f) + minOf(lowerShadow / body / 4f, 0.5f))
                        .coerceIn(0f, 1f)
                    results += PatternResult(i, PatternType.BUY_REVERSAL, strength)
                }
            }

            // ── 매도 제1원칙: 5일선 위 수익실현 ──
            // 종가 > 5일선이었으나 음봉 + 거래량 급증(1.5배) + 상승 둔화(고가-종가 큼)
            if (cur.close > ma5 && cur.close < cur.open && vr >= 1.5f) {
                val topShadow = cur.high - cur.open  // 음봉이므로 open > close
                val range = (cur.high - cur.low).coerceAtLeast(0.01f)
                if (topShadow / range > 0.3f || prev.close > prevMa5) {
                    val strength = calcStrength(
                        priceAboveMa = (cur.close - ma5) / ma5,
                        volumeRatio = vr,
                        bodyRatio = bodyRatio(cur),
                    )
                    results += PatternResult(i, PatternType.SELL_TOP, strength)
                }
            }

            // ── 매도 제2원칙: 5~20일선 사이 반등 실패 ──
            // 5일선 아래, 20일선 위 또는 근접, 음봉 지속
            if (cur.close < ma5 && cur.close >= ma20 * 0.98f
                && cur.close < cur.open && prev.close < prev.open
            ) {
                val proximity = 1f - ((cur.close - ma20) / (ma5 - ma20).coerceAtLeast(0.01f))
                    .coerceIn(0f, 1f)
                val strength = (0.4f + proximity * 0.4f + minOf(vr / 3f, 0.2f))
                    .coerceIn(0f, 1f)
                results += PatternResult(i, PatternType.SELL_BREAKDOWN, strength)
            }

            // ── 황소 50% 룰: 전일 양봉의 중간값 지지 ──
            if (prev.close > prev.open) { // 전일 양봉
                val mid50 = (prev.open + prev.close) / 2f
                if (cur.close > mid50 && cur.close > cur.open) {
                    val margin = (cur.close - mid50) / (prev.close - prev.open).coerceAtLeast(0.01f)
                    results += PatternResult(
                        i, PatternType.BULL_FIFTY,
                        (0.5f + minOf(margin, 1f) * 0.5f).coerceIn(0f, 1f),
                    )
                }
            }

            // ── 곰 50% 룰: 전일 음봉의 중간값 이탈 ──
            if (prev.close < prev.open) { // 전일 음봉
                val mid50 = (prev.open + prev.close) / 2f
                if (cur.close < mid50 && cur.close < cur.open) {
                    val margin = (mid50 - cur.close) / (prev.open - prev.close).coerceAtLeast(0.01f)
                    results += PatternResult(
                        i, PatternType.BEAR_FIFTY,
                        (0.5f + minOf(margin, 1f) * 0.5f).coerceIn(0f, 1f),
                    )
                }
            }
        }

        return results
    }

    // ── 내부 유틸 ──

    /** 단순 이동평균 계산. 인덱스 < period-1은 NaN. */
    private fun sma(candles: List<OhlcvPoint>, period: Int): FloatArray {
        val result = FloatArray(candles.size) { Float.NaN }
        if (candles.size < period) return result
        var sum = 0f
        for (i in candles.indices) {
            sum += candles[i].close
            if (i >= period) sum -= candles[i - period].close
            if (i >= period - 1) result[i] = sum / period
        }
        return result
    }

    /** 당일 거래량 / 20일 평균 거래량 비율. */
    private fun volumeRatio(candles: List<OhlcvPoint>, period: Int): FloatArray {
        val result = FloatArray(candles.size) { 1f }
        if (candles.size < period) return result
        var sum = 0L
        for (i in candles.indices) {
            sum += candles[i].volume
            if (i >= period) sum -= candles[i - period].volume
            if (i >= period - 1) {
                val avg = (sum.toFloat() / period).coerceAtLeast(1f)
                result[i] = candles[i].volume / avg
            }
        }
        return result
    }

    private fun bodyRatio(c: OhlcvPoint): Float {
        val range = (c.high - c.low).coerceAtLeast(0.01f)
        return kotlin.math.abs(c.close - c.open) / range
    }

    /** 공통 강도 계산: MA 이격도 + 거래량 비율 + 몸통 비율 가중 합산 */
    private fun calcStrength(
        priceAboveMa: Float,
        volumeRatio: Float,
        bodyRatio: Float,
    ): Float {
        val priceFactor = minOf(kotlin.math.abs(priceAboveMa) * 10f, 0.4f)
        val volFactor = minOf(volumeRatio / 5f, 0.4f)
        val bodyFactor = minOf(bodyRatio, 0.2f)
        return (priceFactor + volFactor + bodyFactor).coerceIn(0f, 1f)
    }
}
