package com.tinyoscillator.domain.model

import kotlinx.serialization.Serializable

// ─── Stacking Ensemble 도메인 모델 ───

/** 메타 학습기 상태 정보 (UI 표시용) */
@Serializable
data class MetaLearnerStatus(
    val isFitted: Boolean = false,
    val nTrainingSamples: Int = 0,
    val lastFitDate: String = "",
    val topAlgo: String = "",
    val topAlgoWeight: Float = 0f,
    val featureImportance: Map<String, Float> = emptyMap()
)

/** 메타 학습기 직렬화 상태 (저장/복원용) */
@Serializable
data class MetaLearnerState(
    val coefficients: Map<String, Double> = emptyMap(),
    val intercept: Double = 0.0,
    val algoNames: List<String> = emptyList(),
    val fittedAt: Long = 0,
    val nSamples: Int = 0
)

/** 앙상블 이력 항목 (OOF 학습용) */
data class EnsembleHistoryEntry(
    val date: String,
    val ticker: String,
    val signals: Map<String, Double>,
    val actualOutcome: Int // 1=up, 0=down
)
