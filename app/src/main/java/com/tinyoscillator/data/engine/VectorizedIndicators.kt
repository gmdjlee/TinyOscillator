package com.tinyoscillator.data.engine

import kotlin.math.abs
import kotlin.math.max

/**
 * 벡터화된 기술적 지표 계산 유틸리티
 *
 * DoubleArray 기반으로 최적화된 EMA/MACD/RSI 계산.
 * List<Double> 대신 DoubleArray를 사용하여 autoboxing 제거,
 * 사전 할당된 배열로 GC 압력 최소화.
 *
 * pandas ewm(adjust=False) 및 Wilder smoothing과 수치적으로 동일.
 */
object VectorizedIndicators {

    /**
     * EMA (지수이동평균) — DoubleArray 기반
     *
     * pandas.ewm(span=period, adjust=False).mean()과 동일.
     *
     * @param prices 가격 배열 (길이 ≥ 1)
     * @param period EMA 기간
     * @return EMA 배열 (입력과 동일 길이)
     */
    fun emaArray(prices: DoubleArray, period: Int): DoubleArray {
        require(prices.isNotEmpty()) { "prices must not be empty" }
        require(period >= 1) { "period must be >= 1" }

        val n = prices.size
        val result = DoubleArray(n)
        val alpha = 2.0 / (period + 1)
        val oneMinusAlpha = 1.0 - alpha

        result[0] = prices[0]
        for (i in 1 until n) {
            result[i] = alpha * prices[i] + oneMinusAlpha * result[i - 1]
        }
        return result
    }

    /**
     * MACD — DoubleArray 기반
     *
     * @param prices 가격 배열
     * @param fast 단기 EMA 기간 (기본 12)
     * @param slow 장기 EMA 기간 (기본 26)
     * @param signal 시그널 EMA 기간 (기본 9)
     * @return Triple(macdLine, signalLine, histogram)
     */
    fun macdArray(
        prices: DoubleArray,
        fast: Int = 12,
        slow: Int = 26,
        signal: Int = 9
    ): Triple<DoubleArray, DoubleArray, DoubleArray> {
        val emaFast = emaArray(prices, fast)
        val emaSlow = emaArray(prices, slow)

        val n = prices.size
        val macdLine = DoubleArray(n) { emaFast[it] - emaSlow[it] }
        val signalLine = emaArray(macdLine, signal)
        val histogram = DoubleArray(n) { macdLine[it] - signalLine[it] }

        return Triple(macdLine, signalLine, histogram)
    }

    /**
     * RSI (상대강도지수) — Wilder smoothing 기반
     *
     * Wilder의 원래 평활법 사용 (SMA가 아닌 지수 평활).
     * alpha = 1/period (pandas ewm과 달리 Wilder 방식).
     *
     * @param prices 가격 배열 (길이 ≥ period + 1)
     * @param period RSI 기간 (기본 14)
     * @return RSI 배열 (0~100, 첫 period개는 NaN으로 불완전)
     */
    fun rsiArray(prices: DoubleArray, period: Int = 14): DoubleArray {
        require(prices.size >= 2) { "prices must have at least 2 elements" }
        require(period >= 1) { "period must be >= 1" }

        val n = prices.size
        val result = DoubleArray(n) { Double.NaN }

        // 가격 변동 계산
        val changes = DoubleArray(n - 1) { prices[it + 1] - prices[it] }

        // 첫 period개 변동의 평균으로 초기값 설정
        if (changes.size < period) return result

        var avgGain = 0.0
        var avgLoss = 0.0
        for (i in 0 until period) {
            if (changes[i] > 0) avgGain += changes[i]
            else avgLoss += abs(changes[i])
        }
        avgGain /= period
        avgLoss /= period

        // RSI 계산 (첫 유효 값)
        result[period] = if (avgLoss == 0.0) 100.0
        else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)

        // Wilder smoothing으로 후속 값 계산
        val smoothFactor = (period - 1.0) / period
        val invPeriod = 1.0 / period
        for (i in period until changes.size) {
            val change = changes[i]
            avgGain = avgGain * smoothFactor + max(change, 0.0) * invPeriod
            avgLoss = avgLoss * smoothFactor + max(-change, 0.0) * invPeriod

            result[i + 1] = if (avgLoss == 0.0) 100.0
            else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
        }

        return result
    }

    /**
     * 다중 종목 배치 계산
     *
     * @param priceMatrix (n_tickers x n_days) 가격 행렬
     * @param emaShort 단기 EMA 기간 (기본 12)
     * @param emaLong 장기 EMA 기간 (기본 26)
     * @param rsiPeriod RSI 기간 (기본 14)
     * @return {indicator_name: DoubleArray(n_tickers)} 최신 값만 반환
     */
    fun batchCompute(
        priceMatrix: Array<DoubleArray>,
        emaShort: Int = 12,
        emaLong: Int = 26,
        rsiPeriod: Int = 14
    ): Map<String, DoubleArray> {
        val nTickers = priceMatrix.size
        val emaShortLast = DoubleArray(nTickers)
        val emaLongLast = DoubleArray(nTickers)
        val macdLast = DoubleArray(nTickers)
        val signalLast = DoubleArray(nTickers)
        val rsiLast = DoubleArray(nTickers)

        for (t in 0 until nTickers) {
            val prices = priceMatrix[t]
            if (prices.isEmpty()) continue

            val emaS = emaArray(prices, emaShort)
            val emaL = emaArray(prices, emaLong)
            emaShortLast[t] = emaS.last()
            emaLongLast[t] = emaL.last()

            val (macd, sig, _) = macdArray(prices, emaShort, emaLong)
            macdLast[t] = macd.last()
            signalLast[t] = sig.last()

            val rsi = rsiArray(prices, rsiPeriod)
            rsiLast[t] = rsi.last()
        }

        return mapOf(
            "ema_short" to emaShortLast,
            "ema_long" to emaLongLast,
            "macd" to macdLast,
            "signal" to signalLast,
            "rsi" to rsiLast
        )
    }

    /**
     * List<Double> → DoubleArray EMA (CalcOscillatorUseCase 호환용)
     *
     * 기존 calcEma(List<Double>, Int): List<Double> 대체.
     */
    fun emaList(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val arr = emaArray(values.toDoubleArray(), period)
        return arr.toList()
    }
}
