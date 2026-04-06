package com.tinyoscillator.domain.model

/**
 * 신호 보정(Calibration) 관련 도메인 모델
 */

/** 알고리즘별 보정 메트릭 */
data class CalibrationMetrics(
    val algoName: String,
    val brierScore: Double,
    val logLoss: Double,
    val ece: Double,
    val reliabilityBins: List<ReliabilityBin>,
    val sampleCount: Int,
    val needsRecalibration: Boolean
)

/** 신뢰도 히스토그램 빈 */
data class ReliabilityBin(
    val binMid: Double,
    val fractionPositive: Double,
    val count: Int
)

/** Walk-forward 검증 결과 */
data class WalkForwardResult(
    val meanBrierScore: Double,
    val stdBrierScore: Double,
    val meanLogLoss: Double,
    val stdLogLoss: Double,
    val nFolds: Int,
    val perFoldBrier: List<Double>,
    val perFoldLogLoss: List<Double>
)

/** 보정기 직렬화 상태 (JSON 호환) */
data class CalibratorState(
    val algoName: String,
    val method: String,
    val params: Map<String, Double>,
    val isotonicXs: List<Double>? = null,
    val isotonicYs: List<Double>? = null,
    val fittedAt: Long = System.currentTimeMillis()
)

/** StatisticalResult에서 추출한 알고리즘별 원시 점수 */
data class RawSignalScore(
    val algoName: String,
    val rawScore: Double
)

/** 보정 전후 점수 쌍 */
data class CalibratedScore(
    val algoName: String,
    val rawScore: Double,
    val calibratedScore: Double
)

/** StatisticalResult에 보정 점수를 추가한 래퍼 */
data class CalibratedStatisticalResult(
    val statisticalResult: StatisticalResult,
    val calibratedScores: List<CalibratedScore>
)
