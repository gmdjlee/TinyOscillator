package com.tinyoscillator.data.engine.incremental

import com.tinyoscillator.domain.model.IncrementalLogisticRegressionState
import kotlin.math.exp

/**
 * 점진적 로지스틱 회귀 (SGD 기반)
 *
 * sklearn SGDClassifier(loss='log_loss', learning_rate='adaptive', eta0=0.01) 의 Kotlin 구현.
 * warmStart()로 초기 배치 학습, update()로 단일 샘플 SGD 갱신.
 *
 * L2 정규화, 클래스 균형 가중치, 적응형 학습률 적용.
 */
class IncrementalLogisticRegression(
    private val featureNames: List<String>,
    private val eta0: Double = 0.01,
    private val lambda: Double = 0.001,
    private val batchEpochs: Int = 100
) {

    private var weights: DoubleArray = DoubleArray(featureNames.size)
    private var bias: Double = 0.0
    private var totalSamples: Int = 0
    private var learningRateStep: Int = 0
    private var fittedAt: Long = 0

    val isFitted: Boolean get() = totalSamples > 0

    /**
     * 초기 배치 학습 (경사하강법).
     *
     * @param signals 각 샘플의 알고리즘별 보정 확률 리스트 (featureNames 순서)
     * @param labels 각 샘플의 실제 결과 (1=up, 0=down)
     */
    fun warmStart(signals: List<DoubleArray>, labels: List<Int>) {
        require(signals.size == labels.size) { "signals/labels 크기 불일치" }
        if (signals.isEmpty()) return

        val n = signals.size
        val d = featureNames.size

        // 클래스 균형 가중치
        val posCount = labels.count { it == 1 }.coerceAtLeast(1)
        val negCount = labels.count { it == 0 }.coerceAtLeast(1)
        val posWeight = n.toDouble() / (2 * posCount)
        val negWeight = n.toDouble() / (2 * negCount)

        weights = DoubleArray(d)
        bias = 0.0

        val lr = eta0

        for (epoch in 0 until batchEpochs) {
            val gradW = DoubleArray(d)
            var gradB = 0.0

            for (i in 0 until n) {
                val z = dotProduct(weights, signals[i]) + bias
                val pred = sigmoid(z)
                val sampleWeight = if (labels[i] == 1) posWeight else negWeight
                val error = (pred - labels[i]) * sampleWeight

                for (j in 0 until d) {
                    gradW[j] += error * signals[i][j]
                }
                gradB += error
            }

            // Average + L2 regularization
            for (j in 0 until d) {
                gradW[j] = gradW[j] / n + lambda * weights[j]
                weights[j] -= lr * gradW[j]
            }
            bias -= lr * gradB / n
        }

        totalSamples = n
        learningRateStep = n
        fittedAt = System.currentTimeMillis()
    }

    /**
     * 단일 샘플 SGD 갱신.
     *
     * 적응형 학습률: eta = eta0 / (1 + eta0 * lambda * t)
     *
     * @param signal 알고리즘별 보정 확률 배열 (featureNames 순서)
     * @param label 실제 결과 (1=up, 0=down)
     */
    fun update(signal: DoubleArray, label: Int) {
        require(label == 0 || label == 1) { "label은 0 또는 1이어야 함" }
        require(signal.size == featureNames.size) {
            "signal 크기(${signal.size})가 featureNames(${featureNames.size})와 불일치"
        }

        learningRateStep++
        val eta = eta0 / (1.0 + eta0 * lambda * learningRateStep)

        val z = dotProduct(weights, signal) + bias
        val pred = sigmoid(z)
        val error = pred - label

        for (j in weights.indices) {
            weights[j] -= eta * (error * signal[j] + lambda * weights[j])
        }
        bias -= eta * error

        totalSamples++
    }

    /**
     * 상승 확률 예측.
     *
     * @param signal 알고리즘별 보정 확률 배열 (featureNames 순서)
     * @return P(UP | signals) ∈ [0, 1]
     */
    fun predictProba(signal: DoubleArray): Double {
        if (totalSamples == 0) return 0.5
        val z = dotProduct(weights, signal) + bias
        return sigmoid(z).coerceIn(0.001, 0.999)
    }

    /**
     * Map 형태의 signal → DoubleArray 변환 후 예측.
     */
    fun predictProba(signal: Map<String, Double>): Double {
        val arr = featureNames.map { signal[it] ?: 0.5 }.toDoubleArray()
        return predictProba(arr)
    }

    fun saveState(): IncrementalLogisticRegressionState {
        return IncrementalLogisticRegressionState(
            weights = weights.toList(),
            bias = bias,
            featureNames = featureNames.toList(),
            totalSamples = totalSamples,
            learningRateStep = learningRateStep,
            fittedAt = fittedAt
        )
    }

    fun loadState(state: IncrementalLogisticRegressionState) {
        weights = state.weights.toDoubleArray()
        bias = state.bias
        totalSamples = state.totalSamples
        learningRateStep = state.learningRateStep
        fittedAt = state.fittedAt
    }

    private fun sigmoid(z: Double): Double {
        return 1.0 / (1.0 + exp(-z.coerceIn(-500.0, 500.0)))
    }

    private fun dotProduct(a: DoubleArray, b: DoubleArray): Double {
        var sum = 0.0
        for (i in a.indices) {
            if (i < b.size) sum += a[i] * b[i]
        }
        return sum
    }
}
