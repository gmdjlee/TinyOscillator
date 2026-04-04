package com.tinyoscillator.domain.indicator

import com.tinyoscillator.domain.model.Indicator
import com.tinyoscillator.domain.model.IndicatorParams
import com.tinyoscillator.domain.model.OhlcvPoint
import com.tinyoscillator.domain.model.OverlayType

object IndicatorCalculator {

    // ── EMA ──────────────────────────────────────────────────────────────
    fun ema(closes: FloatArray, period: Int): FloatArray {
        val result = FloatArray(closes.size) { Float.NaN }
        if (closes.size < period) return result
        val k = 2f / (period + 1f)
        result[period - 1] = closes.take(period).average().toFloat()
        for (i in period until closes.size)
            result[i] = closes[i] * k + result[i - 1] * (1f - k)
        return result
    }

    // ── Bollinger Bands ───────────────────────────────────────────────────
    data class BollingerResult(val upper: FloatArray, val mid: FloatArray, val lower: FloatArray)

    fun bollinger(closes: FloatArray, period: Int = 20, multiplier: Float = 2f): BollingerResult {
        val upper = FloatArray(closes.size) { Float.NaN }
        val mid = FloatArray(closes.size) { Float.NaN }
        val lower = FloatArray(closes.size) { Float.NaN }
        for (i in period - 1 until closes.size) {
            val window = closes.slice(i - period + 1..i)
            val mean = window.average().toFloat()
            val variance = window.sumOf { ((it - mean) * (it - mean)).toDouble() }.toFloat() / period
            val std = kotlin.math.sqrt(variance)
            mid[i] = mean
            upper[i] = mean + multiplier * std
            lower[i] = mean - multiplier * std
        }
        return BollingerResult(upper, mid, lower)
    }

    // ── MACD ─────────────────────────────────────────────────────────────
    data class MacdResult(
        val macdLine: FloatArray,
        val signalLine: FloatArray,
        val histogram: FloatArray,
    )

    fun macd(closes: FloatArray, fast: Int = 12, slow: Int = 26, signal: Int = 9): MacdResult {
        val emaFast = ema(closes, fast)
        val emaSlow = ema(closes, slow)
        val macdLine = FloatArray(closes.size) { i ->
            if (emaFast[i].isNaN() || emaSlow[i].isNaN()) Float.NaN
            else emaFast[i] - emaSlow[i]
        }
        val validStart = macdLine.indexOfFirst { !it.isNaN() }
        val signalLine = FloatArray(closes.size) { Float.NaN }
        if (validStart >= 0) {
            val sigEma = ema(macdLine.drop(validStart).toFloatArray(), signal)
            sigEma.forEachIndexed { i, v -> signalLine[validStart + i] = v }
        }
        val histogram = FloatArray(closes.size) { i ->
            if (macdLine[i].isNaN() || signalLine[i].isNaN()) Float.NaN
            else macdLine[i] - signalLine[i]
        }
        return MacdResult(macdLine, signalLine, histogram)
    }

    // ── RSI ──────────────────────────────────────────────────────────────
    fun rsi(closes: FloatArray, period: Int = 14): FloatArray {
        val result = FloatArray(closes.size) { Float.NaN }
        if (closes.size <= period) return result
        val delta = FloatArray(closes.size - 1) { closes[it + 1] - closes[it] }
        var avgGain = delta.take(period).filter { it > 0f }.sum() / period
        var avgLoss = delta.take(period).filter { it < 0f }.map { -it }.sum() / period
        result[period] = if (avgLoss == 0f) 100f
        else 100f - 100f / (1f + avgGain / avgLoss)
        for (i in period until delta.size) {
            val gain = if (delta[i] > 0f) delta[i] else 0f
            val loss = if (delta[i] < 0f) -delta[i] else 0f
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
            result[i + 1] = if (avgLoss == 0f) 100f
            else 100f - 100f / (1f + avgGain / avgLoss)
        }
        return result
    }

    // ── Stochastic ───────────────────────────────────────────────────────
    data class StochResult(val k: FloatArray, val d: FloatArray)

    fun stochastic(candles: List<OhlcvPoint>, period: Int = 14, smoothK: Int = 3): StochResult {
        val rawK = FloatArray(candles.size) { Float.NaN }
        for (i in period - 1 until candles.size) {
            val window = candles.subList(i - period + 1, i + 1)
            val highest = window.maxOf { it.high }
            val lowest = window.minOf { it.low }
            rawK[i] = if (highest == lowest) 50f
            else (candles[i].close - lowest) / (highest - lowest) * 100f
        }
        // smoothK via SMA-style EMA on valid values only
        val k = smoothArray(rawK, smoothK)
        val d = smoothArray(k, smoothK)
        return StochResult(k, d)
    }

    /** NaN-aware smoothing: applies EMA only over non-NaN values */
    private fun smoothArray(input: FloatArray, period: Int): FloatArray {
        val result = FloatArray(input.size) { Float.NaN }
        val validStart = input.indexOfFirst { !it.isNaN() }
        if (validStart < 0) return result
        val valid = input.drop(validStart).toFloatArray()
        val smoothed = ema(valid, period)
        smoothed.forEachIndexed { i, v -> result[validStart + i] = v }
        return result
    }

    // ── IndicatorData 통합 빌드 ───────────────────────────────────────────
    data class IndicatorData(
        val emaSeries: Map<Indicator, FloatArray> = emptyMap(),
        val bollinger: BollingerResult? = null,
        val macd: MacdResult? = null,
        val rsi: FloatArray? = null,
        val stochastic: StochResult? = null,
    )

    fun build(
        candles: List<OhlcvPoint>,
        indicators: Set<Indicator>,
        params: Map<Indicator, IndicatorParams>,
    ): IndicatorData {
        val closes = candles.map { it.close }.toFloatArray()
        val emaSeries = mutableMapOf<Indicator, FloatArray>()
        indicators.filter { it.overlayType == OverlayType.PRICE }.forEach { ind ->
            val p = params[ind] ?: ind.defaultParams
            when (ind) {
                Indicator.EMA_SHORT, Indicator.EMA_MID, Indicator.EMA_LONG ->
                    emaSeries[ind] = ema(closes, p.period)
                else -> { /* 볼린저·볼륨프로파일은 별도 */ }
            }
        }
        return IndicatorData(
            emaSeries = emaSeries,
            bollinger = if (Indicator.BOLLINGER in indicators) {
                val p = params[Indicator.BOLLINGER] ?: Indicator.BOLLINGER.defaultParams
                bollinger(closes, p.period, p.multiplier)
            } else null,
            macd = if (Indicator.MACD in indicators) {
                val p = params[Indicator.MACD] ?: Indicator.MACD.defaultParams
                macd(closes, p.fast, p.slow, p.signal)
            } else null,
            rsi = if (Indicator.RSI in indicators)
                rsi(closes, params[Indicator.RSI]?.period ?: 14)
            else null,
            stochastic = if (Indicator.STOCHASTIC in indicators)
                stochastic(candles, params[Indicator.STOCHASTIC]?.period ?: 14)
            else null,
        )
    }
}
