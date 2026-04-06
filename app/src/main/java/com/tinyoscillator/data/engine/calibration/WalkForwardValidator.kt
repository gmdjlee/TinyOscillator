package com.tinyoscillator.data.engine.calibration

import com.tinyoscillator.domain.model.WalkForwardResult
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Walk-Forward 검증기 — 시계열 교차검증으로 미래 정보 누출을 방지.
 *
 * 학습 윈도우를 고정 크기로 이동하면서 다음 기간을 예측하는 방식.
 * train: [t, t+n_train), test: [t+n_train, t+n_train+n_test)
 *
 * @param nTrain 학습 윈도우 크기 (기본 252 = 약 1년 거래일)
 * @param nTest 테스트 윈도우 크기 (기본 21 = 약 1개월)
 * @param step 윈도우 이동 간격 (기본 21)
 */
class WalkForwardValidator(
    val nTrain: Int = 252,
    val nTest: Int = 21,
    val step: Int = 21
) {
    init {
        require(nTrain > 0) { "nTrain must be positive" }
        require(nTest > 0) { "nTest must be positive" }
        require(step > 0) { "step must be positive" }
    }

    /**
     * 인덱스 기반 train/test 분할 생성.
     * @return List of (trainIndices, testIndices) pairs
     */
    fun split(nTotal: Int): List<Pair<List<Int>, List<Int>>> {
        require(nTotal >= nTrain + nTest) {
            "nTotal ($nTotal) must be >= nTrain ($nTrain) + nTest ($nTest)"
        }

        val splits = mutableListOf<Pair<List<Int>, List<Int>>>()
        var start = 0
        while (start + nTrain + nTest <= nTotal) {
            val trainIndices = (start until start + nTrain).toList()
            val testIndices = (start + nTrain until start + nTrain + nTest).toList()
            splits.add(trainIndices to testIndices)
            start += step
        }
        return splits
    }

    /**
     * Walk-forward 평가 실행.
     *
     * @param predictFn (X_train, y_train, X_test) → predicted probabilities for test set
     * @param scores 전체 원시 점수 배열
     * @param labels 전체 실제 결과 배열 (0.0 or 1.0)
     * @return 폴드별 Brier score와 log-loss의 평균/표준편차
     */
    fun evaluate(
        predictFn: (DoubleArray, DoubleArray, DoubleArray) -> DoubleArray,
        scores: DoubleArray,
        labels: DoubleArray
    ): WalkForwardResult {
        require(scores.size == labels.size) { "scores and labels must have same length" }

        val splits = split(scores.size)
        if (splits.isEmpty()) {
            return WalkForwardResult(
                meanBrierScore = Double.NaN,
                stdBrierScore = Double.NaN,
                meanLogLoss = Double.NaN,
                stdLogLoss = Double.NaN,
                nFolds = 0,
                perFoldBrier = emptyList(),
                perFoldLogLoss = emptyList()
            )
        }

        val brierScores = mutableListOf<Double>()
        val logLosses = mutableListOf<Double>()

        for ((trainIdx, testIdx) in splits) {
            val xTrain = DoubleArray(trainIdx.size) { scores[trainIdx[it]] }
            val yTrain = DoubleArray(trainIdx.size) { labels[trainIdx[it]] }
            val xTest = DoubleArray(testIdx.size) { scores[testIdx[it]] }
            val yTest = DoubleArray(testIdx.size) { labels[testIdx[it]] }

            val predictions = predictFn(xTrain, yTrain, xTest)
            require(predictions.size == yTest.size) { "predictFn must return array of same size as X_test" }

            brierScores.add(brierScore(predictions, yTest))
            logLosses.add(logLoss(predictions, yTest))
        }

        return WalkForwardResult(
            meanBrierScore = brierScores.average(),
            stdBrierScore = std(brierScores),
            meanLogLoss = logLosses.average(),
            stdLogLoss = std(logLosses),
            nFolds = splits.size,
            perFoldBrier = brierScores.toList(),
            perFoldLogLoss = logLosses.toList()
        )
    }

    companion object {
        private const val EPS = 1e-15

        fun brierScore(predicted: DoubleArray, actual: DoubleArray): Double {
            require(predicted.size == actual.size)
            if (predicted.isEmpty()) return 0.0
            return predicted.indices.sumOf { i ->
                val diff = predicted[i] - actual[i]
                diff * diff
            } / predicted.size
        }

        fun logLoss(predicted: DoubleArray, actual: DoubleArray): Double {
            require(predicted.size == actual.size)
            if (predicted.isEmpty()) return 0.0
            return -predicted.indices.sumOf { i ->
                val p = predicted[i].coerceIn(EPS, 1.0 - EPS)
                actual[i] * ln(p) + (1.0 - actual[i]) * ln(1.0 - p)
            } / predicted.size
        }

        fun std(values: List<Double>): Double {
            if (values.size < 2) return 0.0
            val mean = values.average()
            val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
            return sqrt(variance)
        }
    }
}
