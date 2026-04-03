package com.tinyoscillator.data.engine.regime

import kotlin.math.abs

/**
 * 레짐별 알고리즘 가중치 테이블
 *
 * 각 레짐에 맞는 알고리즘 가중치를 정의:
 * - BULL_LOW_VOL: 모멘텀/추세 전략 우위 (PatternScan, SignalScoring, Logistic)
 * - BEAR_HIGH_VOL: 변동성/센티먼트 전략 우위 (HMM, BayesianUpdate, Correlation)
 * - SIDEWAYS: 평균회귀/통계 전략 우위 (NaiveBayes, Logistic, Correlation)
 * - CRISIS: 위기 감지/HMM 전략 우위 (HMM, BayesianUpdate, NaiveBayes)
 *
 * 알고리즘 이름은 StatisticalAnalysisEngine의 엔진 이름과 일치.
 */
object RegimeWeightTable {

    const val ALGO_NAIVE_BAYES = "NaiveBayes"
    const val ALGO_LOGISTIC = "Logistic"
    const val ALGO_HMM = "HMM"
    const val ALGO_PATTERN_SCAN = "PatternScan"
    const val ALGO_SIGNAL_SCORING = "SignalScoring"
    const val ALGO_CORRELATION = "Correlation"
    const val ALGO_BAYESIAN_UPDATE = "BayesianUpdate"
    const val ALGO_ORDER_FLOW = "OrderFlow"
    const val ALGO_DART_EVENT = "DartEvent"
    const val ALGO_KOREA_5FACTOR = "Korea5Factor"

    val ALL_ALGOS = listOf(
        ALGO_NAIVE_BAYES, ALGO_LOGISTIC, ALGO_HMM,
        ALGO_PATTERN_SCAN, ALGO_SIGNAL_SCORING,
        ALGO_CORRELATION, ALGO_BAYESIAN_UPDATE,
        ALGO_ORDER_FLOW, ALGO_DART_EVENT,
        ALGO_KOREA_5FACTOR
    )

    /**
     * 레짐별 가중치 테이블
     *
     * 가중치 설계 원리:
     * - BULL_LOW_VOL: 추세 추종 전략이 효과적 → PatternScan, SignalScoring 가중
     * - BEAR_HIGH_VOL: 레짐 탐지와 이벤트 반응이 중요 → HMM, DartEvent, OrderFlow 가중
     * - SIDEWAYS: 통계적 평균회귀가 작동 → NaiveBayes, Logistic, Correlation 가중
     * - CRISIS: 위기 감지와 이벤트 반응이 핵심 → HMM, BayesianUpdate, DartEvent 가중
     */
    val WEIGHT_TABLE: Map<String, Map<String, Double>> = mapOf(
        MarketRegimeClassifier.REGIME_BULL_LOW_VOL to mapOf(
            ALGO_NAIVE_BAYES to 0.06,
            ALGO_LOGISTIC to 0.10,
            ALGO_HMM to 0.05,
            ALGO_PATTERN_SCAN to 0.18,
            ALGO_SIGNAL_SCORING to 0.15,
            ALGO_CORRELATION to 0.05,
            ALGO_BAYESIAN_UPDATE to 0.08,
            ALGO_ORDER_FLOW to 0.11,
            ALGO_DART_EVENT to 0.09,
            ALGO_KOREA_5FACTOR to 0.13   // 팩터 지속성 높은 안정기에 가중
        ),
        MarketRegimeClassifier.REGIME_BEAR_HIGH_VOL to mapOf(
            ALGO_NAIVE_BAYES to 0.07,
            ALGO_LOGISTIC to 0.06,
            ALGO_HMM to 0.16,
            ALGO_PATTERN_SCAN to 0.05,
            ALGO_SIGNAL_SCORING to 0.06,
            ALGO_CORRELATION to 0.11,
            ALGO_BAYESIAN_UPDATE to 0.12,
            ALGO_ORDER_FLOW to 0.16,
            ALGO_DART_EVENT to 0.12,
            ALGO_KOREA_5FACTOR to 0.09
        ),
        MarketRegimeClassifier.REGIME_SIDEWAYS to mapOf(
            ALGO_NAIVE_BAYES to 0.12,
            ALGO_LOGISTIC to 0.13,
            ALGO_HMM to 0.05,
            ALGO_PATTERN_SCAN to 0.08,
            ALGO_SIGNAL_SCORING to 0.08,
            ALGO_CORRELATION to 0.12,
            ALGO_BAYESIAN_UPDATE to 0.08,
            ALGO_ORDER_FLOW to 0.12,
            ALGO_DART_EVENT to 0.10,
            ALGO_KOREA_5FACTOR to 0.12   // 횡보장에서 팩터 알파 유효
        ),
        MarketRegimeClassifier.REGIME_CRISIS to mapOf(
            ALGO_NAIVE_BAYES to 0.09,
            ALGO_LOGISTIC to 0.05,
            ALGO_HMM to 0.19,
            ALGO_PATTERN_SCAN to 0.04,
            ALGO_SIGNAL_SCORING to 0.04,
            ALGO_CORRELATION to 0.05,
            ALGO_BAYESIAN_UPDATE to 0.18,
            ALGO_ORDER_FLOW to 0.16,
            ALGO_DART_EVENT to 0.13,
            ALGO_KOREA_5FACTOR to 0.07   // 위기 시 팩터 모델 신뢰도 낮음
        )
    )

    /**
     * 레짐 이름으로 가중치 조회
     *
     * @param regimeName MarketRegimeClassifier.REGIME_* 상수
     * @return 알고리즘 이름 → 가중치 맵 (합계 1.0)
     */
    fun getWeights(regimeName: String): Map<String, Double> {
        return WEIGHT_TABLE[regimeName] ?: equalWeights()
    }

    /**
     * 균등 가중치 (레짐 미확인 시 기본값)
     */
    fun equalWeights(): Map<String, Double> {
        val w = 1.0 / ALL_ALGOS.size
        return ALL_ALGOS.associateWith { w }
    }

    /**
     * 가중치 테이블 유효성 검증
     *
     * @throws IllegalStateException 가중치 합이 1.0 ± 1e-6이 아닌 경우
     */
    fun validateWeights() {
        for ((regime, weights) in WEIGHT_TABLE) {
            // Check all algorithms present
            val missing = ALL_ALGOS.filter { it !in weights }
            check(missing.isEmpty()) {
                "레짐 '$regime'에 알고리즘 누락: $missing"
            }

            // Check sum = 1.0
            val sum = weights.values.sum()
            check(abs(sum - 1.0) < 1e-6) {
                "레짐 '$regime' 가중치 합이 1.0이 아닙니다: $sum"
            }

            // Check all weights positive
            val negative = weights.filter { it.value < 0 }
            check(negative.isEmpty()) {
                "레짐 '$regime'에 음수 가중치: $negative"
            }
        }

        // Check all regimes present
        val missingRegimes = MarketRegimeClassifier.REGIME_NAMES.filter { it !in WEIGHT_TABLE }
        check(missingRegimes.isEmpty()) {
            "가중치 테이블에 레짐 누락: $missingRegimes"
        }
    }
}
