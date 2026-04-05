package com.tinyoscillator.domain.model

/**
 * 분석 파이프라인의 각 단계.
 * 완료된 단계부터 순서대로 UI에 표시된다.
 */
sealed interface AnalysisStep {
    val stepId: String
    val isLoading: Boolean
    val isComplete: Boolean

    /** 1단계: 가격 데이터 로딩 */
    data class PriceData(
        override val stepId: String = "price",
        val ticker: String = "",
        val currentPrice: Long = 0L,
        val priceChange: Float = 0f,
        val volume: Long = 0L,
        override val isLoading: Boolean = true,
        override val isComplete: Boolean = false,
    ) : AnalysisStep

    /** 2단계: 기술 지표 (EMA, MACD, RSI, 볼린저) */
    data class TechnicalIndicators(
        override val stepId: String = "technical",
        val ema5: Float = 0f,
        val ema20: Float = 0f,
        val ema60: Float = 0f,
        val macdHistogram: Float = 0f,
        val rsi: Float = 0f,
        val bollingerPct: Float = 0f,
        override val isLoading: Boolean = true,
        override val isComplete: Boolean = false,
    ) : AnalysisStep

    /** 3단계: 앙상블 신호 (모든 알고리즘 완료 후) */
    data class EnsembleSignal(
        override val stepId: String = "ensemble",
        val algoResults: Map<String, Float> = emptyMap(),
        val ensembleScore: Float = 0f,
        val rationale: Map<String, String> = emptyMap(),
        override val isLoading: Boolean = true,
        override val isComplete: Boolean = false,
    ) : AnalysisStep

    /** 4단계: 외부 보조 데이터 (DART 공시, 기관 순매수 등 — 선택적) */
    data class ExternalData(
        override val stepId: String = "external",
        val dartEvents: List<String> = emptyList(),
        val institutionalFlow: Float = 0f,
        val consensusTarget: Long? = null,
        override val isLoading: Boolean = true,
        override val isComplete: Boolean = false,
    ) : AnalysisStep
}

/**
 * 진행 중인 모든 단계를 담는 상태.
 * steps에 완료된 단계가 순서대로 쌓임.
 */
data class ProgressiveAnalysisState(
    val ticker: String = "",
    val steps: List<AnalysisStep> = emptyList(),
    val isFullyComplete: Boolean = false,
) {
    val priceData: AnalysisStep.PriceData?
        get() = steps.filterIsInstance<AnalysisStep.PriceData>().firstOrNull()
    val technicalData: AnalysisStep.TechnicalIndicators?
        get() = steps.filterIsInstance<AnalysisStep.TechnicalIndicators>().firstOrNull()
    val ensembleData: AnalysisStep.EnsembleSignal?
        get() = steps.filterIsInstance<AnalysisStep.EnsembleSignal>().firstOrNull()
    val externalData: AnalysisStep.ExternalData?
        get() = steps.filterIsInstance<AnalysisStep.ExternalData>().firstOrNull()
}
