package com.tinyoscillator.domain.model

import kotlinx.serialization.Serializable

/**
 * 점진적 학습(Incremental Learning) 관련 도메인 모델
 */

/** 점진적 나이브 베이즈 직렬화 상태 */
@Serializable
data class IncrementalNaiveBayesState(
    val classCounts: Map<Int, Int>,
    val featureBinCounts: Map<String, Map<String, Map<Int, Int>>>,
    val featureBins: Map<String, Set<String>>,
    val totalSamples: Int,
    val fittedAt: Long = 0
)

/** 점진적 로지스틱 회귀 직렬화 상태 */
@Serializable
data class IncrementalLogisticRegressionState(
    val weights: List<Double>,
    val bias: Double,
    val featureNames: List<String>,
    val totalSamples: Int,
    val learningRateStep: Int,
    val fittedAt: Long = 0
)

/** 모델 매니저 통합 상태 */
@Serializable
data class IncrementalModelManagerState(
    val naiveBayesState: IncrementalNaiveBayesState? = null,
    val logisticState: IncrementalLogisticRegressionState? = null,
    val brierHistory: List<BrierEntry> = emptyList(),
    val baselineBrier: Double? = null,
    val savedAt: Long = 0
)

/** 일별 Brier 점수 기록 */
@Serializable
data class BrierEntry(
    val date: String,
    val brierScore: Double,
    val modelName: String
)

/** 모델 드리프트 알림 */
data class ModelDriftAlert(
    val modelName: String,
    val currentBrier: Double,
    val baselineBrier: Double,
    val degradation: Double,
    val detectedAt: Long = System.currentTimeMillis()
)

/** 점진적 모델 업데이트 요약 */
data class IncrementalUpdateSummary(
    val updatedModels: List<String>,
    val samplesSeen: Int,
    val trainingMs: Long,
    val driftAlerts: List<ModelDriftAlert>
)
