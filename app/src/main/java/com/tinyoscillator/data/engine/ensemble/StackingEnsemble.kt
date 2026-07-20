package com.tinyoscillator.data.engine.ensemble

import com.tinyoscillator.domain.model.MetaLearnerState
import com.tinyoscillator.domain.model.MetaLearnerStatus
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * 2-레벨 스태킹 앙상블
 *
 * Level-0: 기존 9개 알고리즘 (calibrated probabilities)
 * Level-1: 로지스틱 회귀 메타 학습기 (OOF 예측에 대해 학습)
 *
 * - TimeSeriesSplit (시계열 순서 보존, 미래 누출 방지)
 * - L2 정규화 (C=0.5 기본값)
 * - 최소 60개 라벨 샘플 필요
 */
open class StackingEnsemble(
    private val baseModelNames: List<String>,
    private val metaC: Double = 0.5
) {

    companion object {
        const val MIN_SAMPLES = 60
        private const val MAX_ITER = 200
        private const val TOL = 1e-6
    }

    /**
     * 학습된 메타 학습기 상태 — 계수·절편·학습 메타를 하나의 불변 홀더로 묶어 @Volatile 참조로
     * 원자적 스왑한다. 워커(fit/loadState 쓰기)와 분석 코루틴(predictProba 읽기)이 다른 스레드에서
     * 접근할 때 계수만 갱신되고 절편은 이전 값인 "찢어진 읽기"를 방지한다.
     */
    private class FittedModel(
        val coefficients: DoubleArray,
        val intercept: Double,
        val fittedAt: Long,
        val nSamples: Int
    )

    @Volatile
    private var model: FittedModel? = null

    val isFitted: Boolean get() = model != null

    // ─── OOF (Out-of-Fold) 예측 수집 ───

    /**
     * TimeSeriesSplit으로 OOF 예측 행렬 생성.
     *
     * @param signals 2D: [n_samples][n_algos] — 각 샘플의 알고리즘별 보정 확률
     * @param labels [n_samples] — 실제 결과 (1=up, 0=down)
     * @param cv 폴드 수
     * @return OOF 예측 행렬 [n_samples][n_algos] (각 샘플은 해당 샘플이 test에 있던 폴드의 예측)
     */
    fun collectOofPredictions(
        signals: Array<DoubleArray>,
        labels: IntArray,
        cv: Int = 5
    ): DoubleArray {
        val n = signals.size
        require(n >= MIN_SAMPLES) { "최소 $MIN_SAMPLES 샘플 필요, 현재: $n" }
        require(n == labels.size) { "signals/labels 크기 불일치" }

        // OOF prediction: 각 샘플에 대해 해당 샘플이 test fold에 있을 때의 예측값
        val oofPredictions = DoubleArray(n) { 0.5 } // default 0.5

        val splits = timeSeriesSplit(n, cv)
        for ((trainIndices, testIndices) in splits) {
            // 해당 fold의 train 데이터로 간단한 로지스틱 회귀 학습
            val trainX = trainIndices.map { signals[it] }.toTypedArray()
            val trainY = trainIndices.map { labels[it] }.toIntArray()

            val (coef, b) = fitLogistic(trainX, trainY)

            // test 데이터에 대해 예측
            for (idx in testIndices) {
                oofPredictions[idx] = sigmoid(dotProduct(coef, signals[idx]) + b)
            }
        }

        return oofPredictions
    }

    /**
     * 메타 학습기 학습.
     *
     * @param signals 2D: [n_samples][n_algos]
     * @param labels [n_samples] — 1=up, 0=down
     */
    fun fit(signals: Array<DoubleArray>, labels: IntArray) {
        val n = signals.size
        require(n >= MIN_SAMPLES) { "최소 $MIN_SAMPLES 샘플 필요, 현재: $n" }
        require(n == labels.size) { "signals/labels 크기 불일치" }
        require(signals.isNotEmpty() && signals[0].size == baseModelNames.size) {
            "signals 열 수(${signals.firstOrNull()?.size})가 baseModelNames(${baseModelNames.size})와 불일치"
        }

        // 전체 데이터로 최종 메타 학습기 학습
        val (coef, b) = fitLogistic(signals, labels)
        model = FittedModel(
            coefficients = coef,
            intercept = b,
            fittedAt = System.currentTimeMillis(),
            nSamples = n
        )

        Timber.i("━━━ StackingEnsemble 학습 완료: n=%d, algos=%d ━━━", n, baseModelNames.size)
        logFeatureImportance()
    }

    /**
     * 현재 신호로 상승 확률 예측.
     *
     * @param currentSignals {algo_name: calibrated_prob}
     * @return 확률 [0, 1]
     */
    fun predictProba(currentSignals: Map<String, Double>): Double {
        val m = model ?: throw IllegalStateException("메타 학습기가 학습되지 않음")
        val x = baseModelNames.map { currentSignals[it] ?: 0.5 }.toDoubleArray()
        return sigmoid(dotProduct(m.coefficients, x) + m.intercept)
    }

    /**
     * 정규화된 |coefficient| 기반 특성 중요도.
     */
    fun featureImportance(): Map<String, Float> {
        val coef = model?.coefficients ?: return emptyMap()
        val absSum = coef.sumOf { abs(it) }.let { if (it == 0.0) 1.0 else it }
        return baseModelNames.mapIndexed { i, name ->
            name to (abs(coef[i]) / absSum).toFloat()
        }.toMap()
    }

    /** 상태 저장 (JSON 직렬화용) */
    fun saveState(): MetaLearnerState {
        val m = model
        val coef = m?.coefficients
        return MetaLearnerState(
            coefficients = if (coef != null) {
                baseModelNames.mapIndexed { i, name -> name to coef[i] }.toMap()
            } else emptyMap(),
            intercept = m?.intercept ?: 0.0,
            algoNames = baseModelNames.toList(),
            fittedAt = m?.fittedAt ?: 0L,
            nSamples = m?.nSamples ?: 0
        )
    }

    /** 상태 복원 */
    fun loadState(state: MetaLearnerState) {
        if (state.coefficients.isEmpty()) {
            model = null
            return
        }
        model = FittedModel(
            coefficients = baseModelNames.map { state.coefficients[it] ?: 0.0 }.toDoubleArray(),
            intercept = state.intercept,
            fittedAt = state.fittedAt,
            nSamples = state.nSamples
        )
        Timber.d("StackingEnsemble 상태 복원: n=%d, fitted=%d", state.nSamples, state.fittedAt)
    }

    /** UI용 상태 요약 */
    fun getStatus(): MetaLearnerStatus {
        val m = model
        val importance = featureImportance()
        val top = importance.maxByOrNull { it.value }
        val fittedAt = m?.fittedAt ?: 0L
        return MetaLearnerStatus(
            isFitted = m != null,
            nTrainingSamples = m?.nSamples ?: 0,
            lastFitDate = if (fittedAt > 0) {
                java.time.Instant.ofEpochMilli(fittedAt)
                    .atZone(java.time.ZoneId.of("Asia/Seoul"))
                    .toLocalDate().toString()
            } else "",
            topAlgo = top?.key ?: "",
            topAlgoWeight = top?.value ?: 0f,
            featureImportance = importance
        )
    }

    // ─── 내부: TimeSeriesSplit ───

    /**
     * TimeSeriesSplit: 시간 순서 보존 CV 분할.
     * 각 fold에서 train = [:split], test = [split:split+testSize]
     */
    internal fun timeSeriesSplit(n: Int, cv: Int): List<Pair<IntArray, IntArray>> {
        require(cv >= 2) { "cv >= 2 필요" }
        val minTrainSize = n / (cv + 1)
        val testSize = n / (cv + 1)
        require(testSize >= 1) { "샘플($n)이 너무 적어 $cv-fold 분할 불가" }

        val splits = mutableListOf<Pair<IntArray, IntArray>>()
        for (i in 0 until cv) {
            val trainEnd = minTrainSize + i * testSize
            val testEnd = (trainEnd + testSize).coerceAtMost(n)
            if (trainEnd >= testEnd) continue
            splits.add(
                (0 until trainEnd).toList().toIntArray() to
                        (trainEnd until testEnd).toList().toIntArray()
            )
        }
        return splits
    }

    // ─── 내부: 로지스틱 회귀 (L2 정규화, gradient descent) ───

    /**
     * L2 정규화 로지스틱 회귀 학습 (경량 구현).
     * λ = 1/(C * n) per scikit-learn convention.
     */
    private fun fitLogistic(
        x: Array<DoubleArray>,
        y: IntArray
    ): Pair<DoubleArray, Double> {
        val n = x.size
        val d = x[0].size
        val lambda = 1.0 / (metaC * n)
        val lr = 0.1 // learning rate
        val coef = DoubleArray(d) { 0.0 }
        var bias = 0.0

        for (iter in 0 until MAX_ITER) {
            val gradCoef = DoubleArray(d) { 0.0 }
            var gradBias = 0.0
            var totalLoss = 0.0

            for (i in 0 until n) {
                val z = dotProduct(coef, x[i]) + bias
                val p = sigmoid(z)
                val err = p - y[i]
                for (j in 0 until d) {
                    gradCoef[j] += err * x[i][j]
                }
                gradBias += err

                // log loss for convergence check
                val yy = y[i].toDouble()
                totalLoss += -(yy * ln(p.coerceIn(1e-15, 1.0 - 1e-15)) +
                        (1 - yy) * ln((1 - p).coerceIn(1e-15, 1.0 - 1e-15)))
            }

            // Average + L2 regularization
            var maxGrad = 0.0
            for (j in 0 until d) {
                gradCoef[j] = gradCoef[j] / n + lambda * coef[j]
                maxGrad = maxOf(maxGrad, abs(gradCoef[j]))
            }
            gradBias /= n

            // Update
            for (j in 0 until d) {
                coef[j] -= lr * gradCoef[j]
            }
            bias -= lr * gradBias

            if (maxGrad < TOL) break
        }

        return coef to bias
    }

    private fun sigmoid(z: Double): Double {
        return 1.0 / (1.0 + exp(-z.coerceIn(-500.0, 500.0)))
    }

    private fun dotProduct(a: DoubleArray, b: DoubleArray): Double {
        var sum = 0.0
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }

    private fun logFeatureImportance() {
        val importance = featureImportance()
        val sorted = importance.entries.sortedByDescending { it.value }
        Timber.d("메타 학습기 특성 중요도:")
        sorted.forEach { (name, weight) ->
            Timber.d("  %s: %.3f", name, weight)
        }
    }
}
