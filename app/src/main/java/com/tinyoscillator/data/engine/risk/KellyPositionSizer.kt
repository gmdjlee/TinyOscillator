package com.tinyoscillator.data.engine.risk

import com.tinyoscillator.domain.model.KellyResult
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Fractional Kelly Criterion 포지션 사이저.
 *
 * Kelly 공식: f* = (p·b − q) / b
 * - p: 승률 (signal_prob)
 * - q: 1 − p
 * - b: Win/Loss Ratio
 *
 * Fractional Kelly: f* × fraction (기본 0.25 = quarter-Kelly)
 * 변동성 조정: vol_adj = min(target_vol / realized_vol, 1.0)
 *
 * 분석 참고용 — 매매 실행 없음.
 *
 * @param fraction Kelly 분율 (0.25 = quarter-Kelly)
 * @param maxPosition 최대 포지션 비중 (0.20 = 20%)
 */
class KellyPositionSizer(
    val fraction: Double = 0.25,
    val maxPosition: Double = 0.20
) {

    /**
     * 수익률 배열에서 Win/Loss Ratio 추정.
     *
     * 승: return > threshold, 패: return ≤ threshold
     * WLR = mean(wins) / |mean(losses)|, [0.5, 10.0]으로 클립.
     *
     * @param returns 일별 수익률 배열
     * @param signalThreshold 승/패 기준선 (기본 0.0)
     * @return Win/Loss Ratio
     */
    fun estimateWinLossRatio(returns: DoubleArray, signalThreshold: Double = 0.0): Double {
        if (returns.isEmpty()) return 1.0

        val wins = returns.filter { it > signalThreshold }
        val losses = returns.filter { it <= signalThreshold }

        if (wins.isEmpty() || losses.isEmpty()) return 1.0

        val meanWin = wins.average()
        val meanLoss = abs(losses.average())

        if (meanLoss < 1e-10) return 10.0

        return (meanWin / meanLoss).coerceIn(0.5, 10.0)
    }

    /**
     * Kelly 비율 계산.
     *
     * f* = (p·b − q) / b, max(0, f*)
     *
     * @param winProb 승률 (signal_prob from ensemble)
     * @param wlr Win/Loss Ratio
     * @return 원시 Kelly 비율 (0.0 이상)
     */
    fun kellyFraction(winProb: Double, wlr: Double): Double {
        val p = winProb.coerceIn(0.0, 1.0)
        val q = 1.0 - p
        val b = wlr.coerceAtLeast(0.01)

        val f = (p * b - q) / b
        return max(0.0, f)
    }

    /**
     * 최종 포지션 사이즈 계산.
     *
     * @param signalProb 앙상블 상승 확률 [0, 1]
     * @param wlr Win/Loss Ratio
     * @param portfolioVolTarget 포트폴리오 변동성 목표 (연율, 기본 0.15)
     * @param realizedVol 종목 실현 변동성 (연율)
     * @return KellyResult
     */
    fun size(
        signalProb: Double,
        wlr: Double,
        portfolioVolTarget: Double = 0.15,
        realizedVol: Double = 0.25
    ): KellyResult {
        val rawKelly = kellyFraction(signalProb, wlr)
        val fracKelly = rawKelly * fraction
        val volAdj = min(portfolioVolTarget / (realizedVol + 1e-8), 1.0)
        val volAdjSize = fracKelly * volAdj
        val finalSize = min(volAdjSize, maxPosition)
        val signalEdge = signalProb - 0.5

        return KellyResult(
            rawKelly = rawKelly,
            fracKelly = fracKelly,
            volAdjSize = volAdjSize,
            signalEdge = signalEdge,
            recommendedPct = finalSize
        )
    }

    companion object {
        /**
         * 일별 수익률에서 20일 실현 변동성 계산 (연율화).
         *
         * @param returns 일별 수익률 배열 (최소 20개)
         * @param window 관측 윈도우 (기본 20)
         * @return 연율화 변동성
         */
        fun realizedVolatility(returns: DoubleArray, window: Int = 20): Double {
            if (returns.size < window) return 0.30 // 기본 30% 가정

            val recent = returns.takeLast(window).toDoubleArray()
            val mean = recent.average()
            val variance = recent.map { (it - mean) * (it - mean) }.average()
            val dailyVol = kotlin.math.sqrt(variance)

            // 연율화 (√252 ≈ 15.87)
            return dailyVol * kotlin.math.sqrt(252.0)
        }

        /**
         * 종가 배열에서 일별 수익률 계산.
         *
         * @param closePrices 종가 배열 (시간순)
         * @return 일별 수익률 배열 (크기 = closePrices.size - 1)
         */
        fun computeReturns(closePrices: List<Int>): DoubleArray {
            if (closePrices.size < 2) return doubleArrayOf()
            return DoubleArray(closePrices.size - 1) { i ->
                val prev = closePrices[i].toDouble()
                if (prev == 0.0) 0.0
                else (closePrices[i + 1].toDouble() - prev) / prev
            }
        }
    }
}
