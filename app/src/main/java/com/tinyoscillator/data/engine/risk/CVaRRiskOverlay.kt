package com.tinyoscillator.data.engine.risk

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * CVaR (Conditional Value at Risk) 리스크 오버레이.
 *
 * 포지션 크기를 꼬리 위험(tail risk)으로 제한.
 * 주 방법: Cornish-Fisher 확장 CVaR (왜도+첨도 보정)
 * 폴백: Historical CVaR
 *
 * 분석 참고용 — 매매 실행 없음.
 *
 * @param confidence 신뢰수준 (기본 0.95)
 * @param lookback 관측 기간 (일, 기본 252)
 */
class CVaRRiskOverlay(
    val confidence: Double = 0.95,
    val lookback: Int = 252
) {

    /**
     * Historical CVaR: VaR 이하 수익률의 평균.
     *
     * @param returns 일별 수익률 배열
     * @return CVaR (음수, 예상 손실)
     */
    fun historicalCvar(returns: DoubleArray): Double {
        if (returns.isEmpty()) return -0.05 // 기본값

        val sorted = returns.copyOf().also { it.sort() }
        val cutoff = ((1.0 - confidence) * sorted.size).toInt().coerceAtLeast(1)
        val tail = sorted.take(cutoff)

        return if (tail.isNotEmpty()) tail.average() else sorted.first()
    }

    /**
     * Cornish-Fisher 확장 CVaR.
     *
     * 정규분포 가정에 왜도(skewness)와 초과 첨도(excess kurtosis)를 보정.
     * CF z-score: z_cf = z + (z²-1)·S/6 + (z³-3z)·K/24 - (2z³-5z)·S²/36
     *
     * @param returns 일별 수익률 배열
     * @return CVaR (음수, 예상 손실)
     */
    fun cornishFisherCvar(returns: DoubleArray): Double {
        if (returns.size < 30) return historicalCvar(returns)

        val n = returns.size
        val mean = returns.average()
        val variance = returns.map { (it - mean).pow(2) }.average()
        val std = sqrt(variance)
        if (std < 1e-10) return 0.0

        // 왜도
        val skewness = returns.map { ((it - mean) / std).pow(3) }.average()

        // 초과 첨도
        val excessKurtosis = returns.map { ((it - mean) / std).pow(4) }.average() - 3.0

        // 표준정규 z (95% → z = 1.6449)
        val z = normalQuantile(1.0 - confidence)  // 음수 (하위 꼬리)

        // Cornish-Fisher 보정
        val s = skewness
        val k = excessKurtosis
        val zCf = z + (z * z - 1.0) * s / 6.0 +
                  (z.pow(3) - 3.0 * z) * k / 24.0 -
                  (2.0 * z.pow(3) - 5.0 * z) * s * s / 36.0

        // CF VaR
        val varCf = mean + zCf * std

        // CVaR ≈ VaR에서 꼬리쪽 적분 근사
        // E[X | X < VaR] ≈ μ − σ · φ(z_cf) / Φ(z_cf)
        val phiZcf = normalPdf(zCf)
        val cdfZcf = normalCdf(zCf)
        val cvarCf = if (cdfZcf > 1e-10) {
            mean - std * phiZcf / cdfZcf
        } else {
            varCf
        }

        return cvarCf.coerceAtMost(0.0) // CVaR는 항상 음수 또는 0
    }

    /**
     * CVaR 기반 포지션 한도.
     *
     * max_position = budget_loss_pct / |cvar|
     *
     * @param cvar CVaR 값 (음수)
     * @param budgetLossPct 일일 허용 손실 비율 (기본 0.02 = 2%)
     * @return 포지션 한도 [0, 1]
     */
    fun positionLimit(cvar: Double, budgetLossPct: Double = 0.02): Double {
        if (cvar >= 0.0) return 0.0 // 잘못된 CVaR → 0 (안전장치)
        val limit = budgetLossPct / abs(cvar)
        return limit.coerceIn(0.0, 1.0)
    }

    /**
     * Kelly 크기와 CVaR 한도 중 작은 값 반환.
     *
     * @param kellySize Kelly 기반 포지션 크기
     * @param cvarLimit CVaR 기반 포지션 한도
     * @return 리스크 조정 포지션 크기
     */
    fun riskAdjustedSize(kellySize: Double, cvarLimit: Double): Double {
        return minOf(kellySize, cvarLimit).coerceAtLeast(0.0)
    }

    companion object {
        /** 표준정규 PDF: φ(z) = exp(-z²/2) / √(2π) */
        fun normalPdf(z: Double): Double {
            return exp(-z * z / 2.0) / sqrt(2.0 * Math.PI)
        }

        /**
         * 표준정규 CDF 근사 (Abramowitz & Stegun).
         *
         * 최대 오차 ≈ 1.5×10⁻⁷
         */
        fun normalCdf(z: Double): Double {
            if (z < -8.0) return 0.0
            if (z > 8.0) return 1.0

            val x = abs(z)
            val t = 1.0 / (1.0 + 0.2316419 * x)
            val d = 0.3989422804014327 // 1/√(2π)
            val p = d * exp(-x * x / 2.0) *
                    (t * (0.319381530 +
                     t * (-0.356563782 +
                     t * (1.781477937 +
                     t * (-1.821255978 +
                     t * 1.330274429)))))
            return if (z > 0) 1.0 - p else p
        }

        /**
         * 표준정규 분위수 (Beasley-Springer-Moro 근사).
         *
         * @param p 확률 (0 < p < 1)
         * @return z such that Φ(z) = p
         */
        fun normalQuantile(p: Double): Double {
            if (p <= 0.0) return -8.0
            if (p >= 1.0) return 8.0

            // Rational approximation for central region
            if (p > 0.5) return -normalQuantile(1.0 - p)

            val t = sqrt(-2.0 * kotlin.math.ln(p))
            val c0 = 2.515517
            val c1 = 0.802853
            val c2 = 0.010328
            val d1 = 1.432788
            val d2 = 0.189269
            val d3 = 0.001308

            return -(t - (c0 + c1 * t + c2 * t * t) /
                    (1.0 + d1 * t + d2 * t * t + d3 * t * t * t))
        }
    }
}
