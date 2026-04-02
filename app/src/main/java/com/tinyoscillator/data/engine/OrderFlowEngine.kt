package com.tinyoscillator.data.engine

import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.OrderFlowResult
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sign

/**
 * 투자자 자금흐름 분석 엔진 (Order Flow Engine)
 *
 * KRX 투자자별 순매수 데이터(외국인/기관/개인)를 기반으로
 * 자금흐름 불균형(OFI), 기관-외국인 괴리도, 외국인 매수 압력을 산출.
 *
 * 투자자 데이터는 T+1 기준 18:00 KST 이후 확정됨.
 * KOSPI/KOSDAQ 종목에만 유효 (ETF/ELW 제외).
 *
 * 핵심 지표:
 * - OFI (Order Flow Imbalance): (foreign - retail) / (|foreign| + |inst| + |retail| + ε)
 * - Institutional Divergence: sign(inst) ≠ sign(foreign) 비율
 * - Foreign Buy Pressure: 외국인 순매수 모멘텀 (정규화)
 */
@Singleton
class OrderFlowEngine @Inject constructor() {

    companion object {
        const val MIN_DAYS = 30
        private const val EPSILON = 1_000_000.0  // 1백만원 (0 나누기 방지)
        private const val OFI_WINDOW_SHORT = 5
        private const val OFI_WINDOW_LONG = 20
        private const val DIVERGENCE_WINDOW = 10
        private const val FBP_LOOKBACK = 20
        private const val Z_WINDOW = 60
    }

    /**
     * 투자자 자금흐름 분석 실행
     *
     * @param prices 일별 거래 데이터 (foreignNetBuy, instNetBuy 포함, 날짜 오름차순)
     * @return OrderFlowResult (모든 점수 0~1 정규화)
     */
    suspend fun analyze(prices: List<DailyTrading>): OrderFlowResult {
        require(prices.size >= MIN_DAYS) {
            "자금흐름 분석에 최소 ${MIN_DAYS}일 데이터 필요 (현재: ${prices.size}일)"
        }

        // 원시 투자자 흐름 추출
        val foreignFlows = prices.map { it.foreignNetBuy.toDouble() }
        val instFlows = prices.map { it.instNetBuy.toDouble() }
        val retailFlows = prices.mapIndexed { i, _ ->
            -(foreignFlows[i] + instFlows[i])  // 개인 = -(외국인 + 기관)
        }
        val closePrices = prices.map { it.closePrice.toDouble() }

        // 1. OFI (Order Flow Imbalance) — 5일, 20일
        val ofi5d = calcOfi(foreignFlows, instFlows, retailFlows, OFI_WINDOW_SHORT)
        val ofi20d = calcOfi(foreignFlows, instFlows, retailFlows, OFI_WINDOW_LONG)

        // 2. Institutional Divergence — 기관/외국인 방향 불일치율
        val instDiverge = calcInstitutionalDivergence(foreignFlows, instFlows, DIVERGENCE_WINDOW)

        // 3. Foreign Buy Pressure — 외국인 순매수 모멘텀
        val fbp = calcForeignBuyPressure(foreignFlows, FBP_LOOKBACK)

        // 4. 가격 추세 정렬도
        val trendAlignment = calcTrendAlignment(foreignFlows, closePrices)

        // 5. 평균회귀 신호 (극단적 흐름 탐지)
        val meanReversion = calcMeanReversionSignal(ofi20d, foreignFlows)

        // 6. OFI를 시그모이드로 0~1 신호 변환
        val signalScore = ofiToSignal(ofi20d, foreignFlows)

        // 7. 방향 및 강도 판정
        val flowDirection = detectFlowDirection(ofi5d, ofi20d, fbp)
        val flowStrength = detectFlowStrength(ofi5d, fbp, instDiverge)

        // 8. 종합 매수 우위 점수 (0~1, 0.5=중립)
        val buyerDominance = calcBuyerDominanceScore(signalScore, trendAlignment, instDiverge)

        val details = mapOf(
            "ofi_5d" to ofi5d,
            "ofi_20d" to ofi20d,
            "inst_diverge" to instDiverge,
            "fbp_20d" to fbp,
            "foreign_last_5d_avg" to foreignFlows.takeLast(5).average(),
            "inst_last_5d_avg" to instFlows.takeLast(5).average()
        )

        Timber.d("OrderFlow 분석 완료: signal=%.3f, direction=%s, strength=%s",
            signalScore, flowDirection, flowStrength)

        return OrderFlowResult(
            buyerDominanceScore = buyerDominance,
            ofi5d = ofi5d,
            ofi20d = ofi20d,
            institutionalDivergence = instDiverge,
            foreignBuyPressure = fbp,
            signalScore = signalScore,
            flowDirection = flowDirection,
            flowStrength = flowStrength,
            trendAlignment = trendAlignment,
            meanReversionSignal = meanReversion,
            analysisDetails = details,
            dataDate = prices.last().date
        )
    }

    /**
     * OFI (Order Flow Imbalance) 산출
     * = rolling mean of (foreign - retail) / (|foreign| + |inst| + |retail| + ε)
     */
    private fun calcOfi(
        foreign: List<Double>, inst: List<Double>, retail: List<Double>, window: Int
    ): Double {
        val n = foreign.size
        if (n < window) return 0.0

        val ofiDaily = (0 until n).map { i ->
            val denom = abs(foreign[i]) + abs(inst[i]) + abs(retail[i]) + EPSILON
            (foreign[i] - retail[i]) / denom
        }

        // rolling mean of last `window` days
        return ofiDaily.takeLast(window).average()
    }

    /**
     * 기관-외국인 괴리도
     * = rolling mean of [sign(inst) ≠ sign(foreign)] over window days
     */
    private fun calcInstitutionalDivergence(
        foreign: List<Double>, inst: List<Double>, window: Int
    ): Double {
        val n = foreign.size
        if (n < window) return 0.0

        val divergenceDaily = foreign.indices.map { i ->
            if (sign(foreign[i]) != sign(inst[i]) && abs(foreign[i]) > EPSILON && abs(inst[i]) > EPSILON) 1.0
            else 0.0
        }

        return divergenceDaily.takeLast(window).average()
    }

    /**
     * 외국인 매수 압력 (정규화된 모멘텀)
     * = sum(foreign last N) / max(rolling |foreign| sum, 1)
     */
    private fun calcForeignBuyPressure(foreign: List<Double>, lookback: Int): Double {
        val n = foreign.size
        if (n < lookback) return 0.0

        val recentSum = foreign.takeLast(lookback).sum()
        val absSum = foreign.takeLast(lookback).sumOf { abs(it) }
        val denom = max(absSum, EPSILON)
        return (recentSum / denom).coerceIn(-1.0, 1.0)
    }

    /**
     * 가격 추세 정렬도
     * 외국인 순매수 방향과 가격 모멘텀 방향이 일치하는 비율 (0~1)
     */
    private fun calcTrendAlignment(foreign: List<Double>, closes: List<Double>): Double {
        val n = foreign.size
        if (n < 10) return 0.5

        val window = minOf(20, n - 1)
        var aligned = 0
        var total = 0

        for (i in (n - window) until n) {
            if (i < 1) continue
            val priceChange = closes[i] - closes[i - 1]
            if (abs(foreign[i]) > EPSILON) {
                total++
                if (sign(foreign[i]) == sign(priceChange)) aligned++
            }
        }

        return if (total > 0) aligned.toDouble() / total else 0.5
    }

    /**
     * 평균회귀 신호 — OFI가 극단치일 때 반대 방향 조정 신호
     * Z-score 기반, 극단치(|z| > 2) → 높은 평균회귀 점수
     */
    private fun calcMeanReversionSignal(currentOfi: Double, foreign: List<Double>): Double {
        val n = foreign.size
        if (n < Z_WINDOW) return 0.0

        // OFI 시계열 생성
        val ofiSeries = (Z_WINDOW until n).map { i ->
            val slice = foreign.subList(i - OFI_WINDOW_LONG, i)
            val absSlice = slice.sumOf { abs(it) }
            if (absSlice > EPSILON) slice.sum() / absSlice else 0.0
        }

        if (ofiSeries.size < 10) return 0.0

        val mean = ofiSeries.average()
        val std = ofiSeries.standardDeviation()
        if (std < 1e-10) return 0.0

        val z = (currentOfi - mean) / std
        // 극단치일수록 평균회귀 점수 ↑ (양수 극단 → 매도 조정, 음수 극단 → 매수 조정)
        return (abs(z) / 3.0).coerceIn(0.0, 1.0)
    }

    /**
     * OFI → 시그널 점수 변환 (Z-score + sigmoid)
     */
    private fun ofiToSignal(currentOfi: Double, foreign: List<Double>): Double {
        val n = foreign.size
        if (n < Z_WINDOW) return 0.5

        // 간단한 Z-score 기반 시그모이드
        val ofiSeries = (OFI_WINDOW_LONG until n).map { i ->
            val slice = foreign.subList(i - OFI_WINDOW_LONG, i)
            val absSlice = slice.sumOf { abs(it) }
            if (absSlice > EPSILON) slice.sum() / absSlice else 0.0
        }

        if (ofiSeries.size < 10) return 0.5

        val mean = ofiSeries.average()
        val std = ofiSeries.standardDeviation()
        if (std < 1e-10) return 0.5

        val z = (currentOfi - mean) / std
        return sigmoid(z)
    }

    private fun detectFlowDirection(ofi5d: Double, ofi20d: Double, fbp: Double): String {
        val score = ofi5d * 0.4 + ofi20d * 0.3 + fbp * 0.3
        return when {
            score > 0.15 -> "BUY"
            score < -0.15 -> "SELL"
            else -> "NEUTRAL"
        }
    }

    private fun detectFlowStrength(ofi5d: Double, fbp: Double, diverge: Double): String {
        val magnitude = (abs(ofi5d) + abs(fbp)) / 2.0
        val uncertainty = diverge  // 높을수록 불확실
        val adjustedMag = magnitude * (1.0 - uncertainty * 0.5)

        return when {
            adjustedMag > 0.3 -> "STRONG"
            adjustedMag > 0.1 -> "MODERATE"
            else -> "WEAK"
        }
    }

    /**
     * 종합 매수 우위 점수 (0~1, 0.5=중립)
     */
    private fun calcBuyerDominanceScore(
        signalScore: Double, trendAlignment: Double, diverge: Double
    ): Double {
        // 기본 점수는 신호 점수
        var score = signalScore * 0.6 + trendAlignment * 0.3 + (1.0 - diverge) * 0.1
        // 기관-외국인 괴리가 높으면 중립쪽으로 축소
        if (diverge > 0.5) {
            score = score * 0.7 + 0.5 * 0.3  // 30% 중립 쪽으로
        }
        return score.coerceIn(0.0, 1.0)
    }

    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))

    private fun List<Double>.standardDeviation(): Double {
        if (size < 2) return 0.0
        val mean = average()
        val variance = sumOf { (it - mean) * (it - mean) } / (size - 1)
        return kotlin.math.sqrt(variance)
    }
}
