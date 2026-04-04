package com.tinyoscillator.data.engine.calibration

import com.tinyoscillator.domain.model.CalibratorState
import com.tinyoscillator.domain.model.ReliabilityBin
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * 알고리즘별 확률 보정기 — Isotonic Regression 또는 Platt (Sigmoid) Scaling 지원.
 *
 * 각 알고리즘의 원시 점수를 잘 보정된 확률로 변환한다.
 * 상태는 JSON 직렬화 가능한 dict로 저장/로드 (pickle 미사용).
 */
@Singleton
class SignalCalibrator @Inject constructor() {

    private val calibrators = mutableMapOf<String, FittedCalibrator>()

    /** 지원 알고리즘 목록 */
    companion object {
        val ALGO_NAMES = listOf(
            "NaiveBayes", "Logistic", "HMM",
            "PatternScan", "SignalScoring", "BayesianUpdate",
            "Korea5Factor", "SectorCorrelation"
        )

        private const val EPS = 1e-15
    }

    // ─── Public API ───

    /**
     * 하나의 알고리즘에 대해 보정기를 학습한다.
     * @param method "isotonic" 또는 "sigmoid"
     */
    fun fit(
        algoName: String,
        rawScores: DoubleArray,
        labels: DoubleArray,
        method: String = "isotonic"
    ) {
        require(rawScores.size == labels.size) { "rawScores and labels must have same length" }
        require(rawScores.size >= 2) { "Need at least 2 samples to fit" }

        val calibrator = when (method) {
            "isotonic" -> fitIsotonic(rawScores, labels)
            "sigmoid" -> fitSigmoid(rawScores, labels)
            else -> throw IllegalArgumentException("Unknown method: $method (use 'isotonic' or 'sigmoid')")
        }
        calibrators[algoName] = calibrator
        Timber.d("보정기 학습 완료: %s (method=%s, n=%d)", algoName, method, rawScores.size)
    }

    /**
     * 원시 점수를 보정된 확률로 변환한다.
     * 보정기가 없으면 원시 점수를 [0,1]로 클램핑하여 반환.
     */
    fun transform(algoName: String, rawScore: Double): Double {
        val calibrator = calibrators[algoName]
            ?: return rawScore.coerceIn(0.0, 1.0)
        return calibrator.transform(rawScore)
    }

    /** 보정기가 학습되어 있는지 확인 */
    fun isFitted(algoName: String): Boolean = calibrators.containsKey(algoName)

    /**
     * 모든 보정기 상태를 JSON 직렬화 가능한 리스트로 반환.
     */
    fun saveState(): List<CalibratorState> = calibrators.map { (name, cal) ->
        cal.toState(name)
    }

    /**
     * 저장된 상태에서 보정기를 복원한다.
     */
    fun loadState(states: List<CalibratorState>) {
        calibrators.clear()
        for (state in states) {
            val calibrator = when (state.method) {
                "isotonic" -> {
                    val xs = state.isotonicXs ?: continue
                    val ys = state.isotonicYs ?: continue
                    IsotonicCalibrator(xs.toDoubleArray(), ys.toDoubleArray())
                }
                "sigmoid" -> {
                    val a = state.params["a"] ?: continue
                    val b = state.params["b"] ?: continue
                    SigmoidCalibrator(a, b)
                }
                else -> continue
            }
            calibrators[state.algoName] = calibrator
        }
        Timber.d("보정기 상태 복원: %d개 알고리즘", calibrators.size)
    }

    /**
     * 신뢰도 통계 계산: fraction_of_positives, mean_predicted_value, brier_score, log_loss
     */
    fun reliabilityStats(
        algoName: String,
        rawScores: DoubleArray,
        labels: DoubleArray,
        nBins: Int = 10
    ): Map<String, Any> {
        require(rawScores.size == labels.size)
        val n = rawScores.size
        if (n == 0) return emptyMap()

        val calibrated = DoubleArray(n) { transform(algoName, rawScores[it]) }

        // Brier score
        val brierScore = calibrated.indices.sumOf { i ->
            val diff = calibrated[i] - labels[i]
            diff * diff
        } / n

        // Log loss
        val logLoss = -calibrated.indices.sumOf { i ->
            val p = calibrated[i].coerceIn(EPS, 1.0 - EPS)
            labels[i] * ln(p) + (1.0 - labels[i]) * ln(1.0 - p)
        } / n

        // Reliability bins
        val bins = buildReliabilityBins(calibrated, labels, nBins)

        return mapOf(
            "brier_score" to brierScore,
            "log_loss" to logLoss,
            "reliability_bins" to bins,
            "sample_count" to n
        )
    }

    // ─── Internal: Isotonic Regression (Pool Adjacent Violators) ───

    private fun fitIsotonic(rawScores: DoubleArray, labels: DoubleArray): IsotonicCalibrator {
        // Sort by raw score
        val indices = rawScores.indices.sortedBy { rawScores[it] }
        val sortedX = DoubleArray(indices.size) { rawScores[indices[it]] }
        val sortedY = DoubleArray(indices.size) { labels[indices[it]] }

        // Pool Adjacent Violators Algorithm (PAVA)
        val n = sortedY.size
        val result = sortedY.copyOf()
        val weight = DoubleArray(n) { 1.0 }
        val blockStart = IntArray(n) { it }

        var i = 0
        while (i < n - 1) {
            if (result[i] > result[i + 1]) {
                // Pool: merge blocks
                val newVal = (result[i] * weight[i] + result[i + 1] * weight[i + 1]) /
                        (weight[i] + weight[i + 1])
                val newWeight = weight[i] + weight[i + 1]
                result[i] = newVal
                weight[i] = newWeight

                // Shift remaining elements
                for (j in i + 1 until n - 1) {
                    result[j] = result[j + 1]
                    weight[j] = weight[j + 1]
                    sortedX[j] = sortedX[j + 1]
                    blockStart[j] = blockStart[j + 1]
                }

                // Check backward
                if (i > 0) i--
            } else {
                i++
            }
        }

        // Build deduplicated isotonic function
        // Group consecutive equal x values, keep unique (x, y) pairs
        val uniqueXs = mutableListOf<Double>()
        val uniqueYs = mutableListOf<Double>()
        var prevX = Double.NaN
        for (j in sortedX.indices) {
            if (sortedX[j] != prevX) {
                uniqueXs.add(sortedX[j])
                uniqueYs.add(result[j].coerceIn(0.0, 1.0))
                prevX = sortedX[j]
            }
        }

        return IsotonicCalibrator(uniqueXs.toDoubleArray(), uniqueYs.toDoubleArray())
    }

    // ─── Internal: Platt / Sigmoid Scaling ───

    private fun fitSigmoid(rawScores: DoubleArray, labels: DoubleArray): SigmoidCalibrator {
        // Platt scaling: P(y=1|f) = 1 / (1 + exp(a*f + b))
        // Fit a, b via gradient descent on log-likelihood
        var a = 0.0
        var b = 0.0
        val lr = 0.01
        val epochs = 200
        val n = rawScores.size

        for (epoch in 0 until epochs) {
            var gradA = 0.0
            var gradB = 0.0
            for (i in 0 until n) {
                val p = sigmoid(a * rawScores[i] + b)
                val error = p - labels[i]
                gradA += error * rawScores[i]
                gradB += error
            }
            a -= lr * gradA / n
            b -= lr * gradB / n
        }

        return SigmoidCalibrator(a, b)
    }

    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))

    // ─── Reliability bins ───

    internal fun buildReliabilityBins(
        calibrated: DoubleArray,
        labels: DoubleArray,
        nBins: Int
    ): List<ReliabilityBin> {
        val bins = mutableListOf<ReliabilityBin>()
        val binWidth = 1.0 / nBins
        for (b in 0 until nBins) {
            val lo = b * binWidth
            val hi = (b + 1) * binWidth
            val mid = (lo + hi) / 2.0
            val inBin = calibrated.indices.filter { calibrated[it] >= lo && calibrated[it] < hi }
            if (inBin.isEmpty()) {
                bins.add(ReliabilityBin(mid, 0.0, 0))
            } else {
                val fracPos = inBin.sumOf { labels[it] } / inBin.size
                bins.add(ReliabilityBin(mid, fracPos, inBin.size))
            }
        }
        return bins
    }

    // ─── Calibrator implementations ───

    private sealed interface FittedCalibrator {
        fun transform(rawScore: Double): Double
        fun toState(algoName: String): CalibratorState
    }

    /** Isotonic calibrator — piecewise linear interpolation */
    private class IsotonicCalibrator(
        val xs: DoubleArray,
        val ys: DoubleArray
    ) : FittedCalibrator {
        override fun transform(rawScore: Double): Double {
            if (xs.isEmpty()) return rawScore.coerceIn(0.0, 1.0)
            if (rawScore <= xs.first()) return ys.first()
            if (rawScore >= xs.last()) return ys.last()

            // Binary search for interpolation
            var lo = 0
            var hi = xs.size - 1
            while (lo < hi - 1) {
                val mid = (lo + hi) / 2
                if (xs[mid] <= rawScore) lo = mid else hi = mid
            }
            // Linear interpolation
            val t = if (xs[hi] == xs[lo]) 0.5
            else (rawScore - xs[lo]) / (xs[hi] - xs[lo])
            return (ys[lo] + t * (ys[hi] - ys[lo])).coerceIn(0.0, 1.0)
        }

        override fun toState(algoName: String) = CalibratorState(
            algoName = algoName,
            method = "isotonic",
            params = emptyMap(),
            isotonicXs = xs.toList(),
            isotonicYs = ys.toList()
        )
    }

    /** Sigmoid (Platt) calibrator: P = sigmoid(a*x + b) */
    private class SigmoidCalibrator(val a: Double, val b: Double) : FittedCalibrator {
        override fun transform(rawScore: Double): Double {
            return (1.0 / (1.0 + exp(-(a * rawScore + b)))).coerceIn(0.0, 1.0)
        }

        override fun toState(algoName: String) = CalibratorState(
            algoName = algoName,
            method = "sigmoid",
            params = mapOf("a" to a, "b" to b)
        )
    }
}
