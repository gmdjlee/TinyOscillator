package com.tinyoscillator.data.engine.macro

import com.tinyoscillator.domain.model.MacroEnvironment
import com.tinyoscillator.domain.model.MacroSignalResult
import com.tinyoscillator.data.engine.regime.RegimeWeightTable
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 매크로 환경 분류 및 앙상블 가중치 오버레이
 *
 * BOK ECOS 매크로 YoY 변화율을 기반으로 4가지 매크로 환경을 분류하고,
 * 기존 레짐 기반 앙상블 가중치를 조정합니다.
 *
 * 분류 규칙 (상수 기반, 매직 넘버 없음):
 * - TIGHTENING: 기준금리 YoY > +0.5pp
 * - EASING: 기준금리 YoY < -0.5pp
 * - STAGFLATION: 산업생산 YoY < -5% AND CPI YoY > 3%
 * - NEUTRAL: 그 외
 */
@Singleton
class MacroRegimeOverlay @Inject constructor() {

    companion object {
        // ─── 분류 임계값 (상수) ───

        /** 긴축 판단 기준: 기준금리 YoY 변화 > +0.5pp */
        const val TIGHTENING_RATE_THRESHOLD = 0.5

        /** 완화 판단 기준: 기준금리 YoY 변화 < -0.5pp */
        const val EASING_RATE_THRESHOLD = -0.5

        /** 스태그플레이션 판단: 산업생산 YoY 하락 임계값 */
        const val STAGFLATION_IIP_THRESHOLD = -5.0

        /** 스태그플레이션 판단: CPI YoY 상승 임계값 */
        const val STAGFLATION_CPI_THRESHOLD = 3.0

        // ─── 가중치 조정 비율 (상수) ───

        /** 긴축 시 모멘텀 가중치 감소 비율 */
        const val TIGHTENING_MOMENTUM_REDUCTION = 0.20

        /** 긴축 시 HMM 가중치 증가 비율 */
        const val TIGHTENING_HMM_BOOST = 0.20

        /** 스태그플레이션 시 모멘텀 가중치 감소 비율 */
        const val STAGFLATION_MOMENTUM_REDUCTION = 0.25

        /** 스태그플레이션 시 DartEvent 가중치 증가 비율 */
        const val STAGFLATION_DART_BOOST = 0.15

        /** 스태그플레이션 시 OrderFlow 가중치 증가 비율 */
        const val STAGFLATION_ORDERFLOW_BOOST = 0.10

        /** 완화 시 모멘텀 가중치 증가 비율 */
        const val EASING_MOMENTUM_BOOST = 0.15

        /** 완화 시 HMM 가중치 감소 비율 */
        const val EASING_HMM_REDUCTION = 0.10
    }

    /**
     * 매크로 환경 분류
     *
     * STAGFLATION이 가장 우선 판단 (경기침체+인플레이션 동시),
     * 그 다음 금리 방향으로 TIGHTENING/EASING,
     * 해당 없으면 NEUTRAL.
     */
    fun classifyMacroEnvironment(macroVec: MacroSignalResult): MacroEnvironment {
        // 스태그플레이션 최우선 판단
        if (macroVec.iipYoy < STAGFLATION_IIP_THRESHOLD &&
            macroVec.cpiYoy > STAGFLATION_CPI_THRESHOLD) {
            return MacroEnvironment.STAGFLATION
        }

        // 금리 방향 판단
        return when {
            macroVec.baseRateYoy > TIGHTENING_RATE_THRESHOLD -> MacroEnvironment.TIGHTENING
            macroVec.baseRateYoy < EASING_RATE_THRESHOLD -> MacroEnvironment.EASING
            else -> MacroEnvironment.NEUTRAL
        }
    }

    /**
     * 매크로 환경에 따라 앙상블 가중치 조정
     *
     * - TIGHTENING: 모멘텀(PatternScan, SignalScoring) ↓20%, HMM ↑20%
     * - EASING: 모멘텀 ↑15%, HMM ↓10%
     * - STAGFLATION: 모멘텀 ↓25%, DartEvent ↑15%, OrderFlow ↑10%
     * - NEUTRAL: 조정 없음 (원본 가중치 그대로 반환)
     *
     * 모든 조정 후 가중치는 합 = 1.0으로 정규화됩니다.
     *
     * @param currentWeights 레짐 기반 현재 가중치 (algo → weight)
     * @param macroEnv 매크로 환경 분류
     * @return 매크로 조정된 가중치 (합 = 1.0)
     */
    fun adjustRegimeWeights(
        currentWeights: Map<String, Double>,
        macroEnv: MacroEnvironment
    ): Map<String, Double> {
        if (macroEnv == MacroEnvironment.NEUTRAL || currentWeights.isEmpty()) {
            return currentWeights
        }

        val adjusted = currentWeights.toMutableMap()

        when (macroEnv) {
            MacroEnvironment.TIGHTENING -> {
                // 모멘텀 전략 약화, HMM(레짐 탐지) 강화
                adjustWeight(adjusted, RegimeWeightTable.ALGO_PATTERN_SCAN, -TIGHTENING_MOMENTUM_REDUCTION)
                adjustWeight(adjusted, RegimeWeightTable.ALGO_SIGNAL_SCORING, -TIGHTENING_MOMENTUM_REDUCTION)
                adjustWeight(adjusted, RegimeWeightTable.ALGO_HMM, +TIGHTENING_HMM_BOOST)
                adjustWeight(adjusted, RegimeWeightTable.ALGO_BAYESIAN_UPDATE, +TIGHTENING_HMM_BOOST)
            }
            MacroEnvironment.EASING -> {
                // 모멘텀 전략 강화, HMM 약화
                adjustWeight(adjusted, RegimeWeightTable.ALGO_PATTERN_SCAN, +EASING_MOMENTUM_BOOST)
                adjustWeight(adjusted, RegimeWeightTable.ALGO_SIGNAL_SCORING, +EASING_MOMENTUM_BOOST)
                adjustWeight(adjusted, RegimeWeightTable.ALGO_HMM, -EASING_HMM_REDUCTION)
            }
            MacroEnvironment.STAGFLATION -> {
                // 모멘텀 크게 약화, 이벤트+자금흐름 강화
                adjustWeight(adjusted, RegimeWeightTable.ALGO_PATTERN_SCAN, -STAGFLATION_MOMENTUM_REDUCTION)
                adjustWeight(adjusted, RegimeWeightTable.ALGO_SIGNAL_SCORING, -STAGFLATION_MOMENTUM_REDUCTION)
                adjustWeight(adjusted, RegimeWeightTable.ALGO_DART_EVENT, +STAGFLATION_DART_BOOST)
                adjustWeight(adjusted, RegimeWeightTable.ALGO_ORDER_FLOW, +STAGFLATION_ORDERFLOW_BOOST)
            }
            MacroEnvironment.NEUTRAL -> { /* no-op */ }
        }

        // 음수 가중치 방지
        for (key in adjusted.keys) {
            if (adjusted[key]!! < 0.01) {
                adjusted[key] = 0.01
            }
        }

        // 정규화: 합 = 1.0
        return normalize(adjusted)
    }

    /**
     * 가중치를 비율적으로 조정
     *
     * @param weights 가중치 맵 (mutable)
     * @param algo 알고리즘 이름
     * @param ratio 조정 비율 (양수=증가, 음수=감소). 예: 0.20 = +20%
     */
    private fun adjustWeight(weights: MutableMap<String, Double>, algo: String, ratio: Double) {
        val current = weights[algo] ?: return
        weights[algo] = current * (1.0 + ratio)
    }

    /**
     * 가중치 정규화 (합 = 1.0)
     */
    internal fun normalize(weights: Map<String, Double>): Map<String, Double> {
        val sum = weights.values.sum()
        if (sum <= 0.0) return weights
        return weights.mapValues { (_, v) -> v / sum }
    }

    /**
     * 매크로 신호 결과에 환경 분류를 적용하고 로깅
     */
    fun applyClassification(macroSignal: MacroSignalResult): MacroSignalResult {
        val env = classifyMacroEnvironment(macroSignal)
        Timber.d("매크로 환경 분류: %s (금리YoY=%.2fpp, IIP=%.1f%%, CPI=%.1f%%)",
            env.name, macroSignal.baseRateYoy, macroSignal.iipYoy, macroSignal.cpiYoy)
        return macroSignal.copy(macroEnv = env.name)
    }
}
