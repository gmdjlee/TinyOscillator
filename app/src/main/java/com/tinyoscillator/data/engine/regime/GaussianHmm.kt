package com.tinyoscillator.data.engine.regime

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pure Kotlin 4-state Gaussian HMM (diagonal covariance)
 *
 * Baum-Welch (EM) 학습, Forward-Backward, Viterbi 알고리즘 구현.
 * hmmlearn GaussianHMM의 핵심 기능을 Kotlin으로 이식.
 *
 * @param nStates 숨은 상태 수 (default: 4)
 * @param nFeatures 관측 벡터 차원 (default: 4)
 * @param nIter Baum-Welch 최대 반복 수 (default: 300)
 * @param convergenceTol 로그 우도 수렴 임계값
 * @param randomState 재현성을 위한 시드
 */
class GaussianHmm(
    val nStates: Int = 4,
    val nFeatures: Int = 4,
    val nIter: Int = 300,
    val convergenceTol: Double = 1e-4,
    val randomState: Int = 42
) {
    // HMM 파라미터
    var startProb = DoubleArray(nStates) { 1.0 / nStates }
    var transmat = Array(nStates) { DoubleArray(nStates) { 1.0 / nStates } }
    var means = Array(nStates) { DoubleArray(nFeatures) }
    var covars = Array(nStates) { DoubleArray(nFeatures) { 1.0 } } // diagonal variances

    private var isFitted = false

    /**
     * Baum-Welch (EM) 알고리즘으로 HMM 파라미터 학습
     *
     * @param observations T×D 관측 행렬 (T: 시점 수, D: 특성 수)
     */
    fun fit(observations: Array<DoubleArray>) {
        require(observations.size >= nStates * 2) {
            "최소 ${nStates * 2}개의 관측값이 필요합니다 (현재: ${observations.size})"
        }
        require(observations[0].size == nFeatures) {
            "특성 수 불일치: 기대=$nFeatures, 실제=${observations[0].size}"
        }

        initializeParams(observations)

        var prevLogLikelihood = Double.NEGATIVE_INFINITY

        for (iter in 0 until nIter) {
            // E-step: Forward-Backward
            val (alpha, scalingFactors) = forwardScaled(observations)
            val beta = backwardScaled(observations, scalingFactors)
            val gamma = computeGamma(alpha, beta)
            val xi = computeXi(observations, alpha, beta, scalingFactors)

            // Log-likelihood from scaling factors
            val logLikelihood = scalingFactors.sumOf { ln(max(it, 1e-300)) }

            // M-step: update parameters
            updateParams(observations, gamma, xi)

            // Convergence check
            if (iter > 0 && abs(logLikelihood - prevLogLikelihood) < convergenceTol) {
                break
            }
            prevLogLikelihood = logLikelihood
        }

        isFitted = true
    }

    /**
     * Viterbi 알고리즘 — 최적 상태 경로 추정
     */
    fun predict(observations: Array<DoubleArray>): IntArray {
        val T = observations.size
        val delta = Array(T) { DoubleArray(nStates) }
        val psi = Array(T) { IntArray(nStates) }

        // 초기화
        for (j in 0 until nStates) {
            delta[0][j] = ln(max(startProb[j], 1e-300)) + lnEmission(j, observations[0])
        }

        // 재귀
        for (t in 1 until T) {
            for (j in 0 until nStates) {
                var maxVal = Double.NEGATIVE_INFINITY
                var maxIdx = 0
                for (i in 0 until nStates) {
                    val v = delta[t - 1][i] + ln(max(transmat[i][j], 1e-300))
                    if (v > maxVal) {
                        maxVal = v
                        maxIdx = i
                    }
                }
                delta[t][j] = maxVal + lnEmission(j, observations[t])
                psi[t][j] = maxIdx
            }
        }

        // 역추적
        val path = IntArray(T)
        path[T - 1] = delta[T - 1].indices.maxByOrNull { delta[T - 1][it] } ?: 0
        for (t in T - 2 downTo 0) {
            path[t] = psi[t + 1][path[t + 1]]
        }

        return path
    }

    /**
     * Forward 알고리즘 — 각 시점의 상태 사후 확률 반환
     */
    fun predictProba(observations: Array<DoubleArray>): Array<DoubleArray> {
        val (alpha, scalingFactors) = forwardScaled(observations)
        val beta = backwardScaled(observations, scalingFactors)
        return computeGamma(alpha, beta)
    }

    /**
     * 로그 우도 계산
     */
    fun score(observations: Array<DoubleArray>): Double {
        val (_, scalingFactors) = forwardScaled(observations)
        return scalingFactors.sumOf { ln(max(it, 1e-300)) }
    }

    // ─── 초기화 ───

    private fun initializeParams(observations: Array<DoubleArray>) {
        val rng = Random(randomState)
        val T = observations.size

        // K-means style initialization: partition data into nStates clusters
        val indices = (0 until T).shuffled(rng)
        val clusterSize = T / nStates

        for (s in 0 until nStates) {
            val start = s * clusterSize
            val end = if (s == nStates - 1) T else (s + 1) * clusterSize
            val clusterObs = indices.subList(start, end).map { observations[it] }

            // Mean of cluster
            for (d in 0 until nFeatures) {
                means[s][d] = clusterObs.map { it[d] }.average()
            }

            // Variance of cluster (diagonal)
            for (d in 0 until nFeatures) {
                val mean = means[s][d]
                val variance = clusterObs.map { (it[d] - mean) * (it[d] - mean) }.average()
                covars[s][d] = max(variance, 1e-6) // floor to prevent zero variance
            }
        }

        // Uniform start and transition
        startProb = DoubleArray(nStates) { 1.0 / nStates }
        transmat = Array(nStates) { DoubleArray(nStates) { 1.0 / nStates } }
    }

    // ─── Forward (scaled) ───

    internal fun forwardScaled(observations: Array<DoubleArray>): Pair<Array<DoubleArray>, DoubleArray> {
        val T = observations.size
        val alpha = Array(T) { DoubleArray(nStates) }
        val c = DoubleArray(T) // scaling factors

        // t=0
        for (j in 0 until nStates) {
            alpha[0][j] = startProb[j] * emission(j, observations[0])
        }
        c[0] = alpha[0].sum()
        if (c[0] > 0) for (j in 0 until nStates) alpha[0][j] /= c[0]

        // t=1..T-1
        for (t in 1 until T) {
            for (j in 0 until nStates) {
                var sum = 0.0
                for (i in 0 until nStates) {
                    sum += alpha[t - 1][i] * transmat[i][j]
                }
                alpha[t][j] = sum * emission(j, observations[t])
            }
            c[t] = alpha[t].sum()
            if (c[t] > 0) for (j in 0 until nStates) alpha[t][j] /= c[t]
        }

        return Pair(alpha, c)
    }

    // ─── Backward (scaled) ───

    internal fun backwardScaled(observations: Array<DoubleArray>, c: DoubleArray): Array<DoubleArray> {
        val T = observations.size
        val beta = Array(T) { DoubleArray(nStates) }

        // t=T-1
        for (j in 0 until nStates) beta[T - 1][j] = 1.0

        // t=T-2..0
        for (t in T - 2 downTo 0) {
            for (i in 0 until nStates) {
                var sum = 0.0
                for (j in 0 until nStates) {
                    sum += transmat[i][j] * emission(j, observations[t + 1]) * beta[t + 1][j]
                }
                beta[t][i] = sum
            }
            if (c[t + 1] > 0) for (i in 0 until nStates) beta[t][i] /= c[t + 1]
        }

        return beta
    }

    // ─── Gamma & Xi ───

    private fun computeGamma(alpha: Array<DoubleArray>, beta: Array<DoubleArray>): Array<DoubleArray> {
        val T = alpha.size
        val gamma = Array(T) { t ->
            val row = DoubleArray(nStates) { j -> alpha[t][j] * beta[t][j] }
            val sum = row.sum()
            if (sum > 0) for (j in row.indices) row[j] /= sum
            row
        }
        return gamma
    }

    private fun computeXi(
        observations: Array<DoubleArray>,
        alpha: Array<DoubleArray>,
        beta: Array<DoubleArray>,
        c: DoubleArray
    ): Array<Array<DoubleArray>> {
        val T = observations.size
        val xi = Array(T - 1) { Array(nStates) { DoubleArray(nStates) } }

        for (t in 0 until T - 1) {
            var denom = 0.0
            for (i in 0 until nStates) {
                for (j in 0 until nStates) {
                    xi[t][i][j] = alpha[t][i] * transmat[i][j] *
                            emission(j, observations[t + 1]) * beta[t + 1][j]
                    denom += xi[t][i][j]
                }
            }
            if (denom > 0) {
                for (i in 0 until nStates) {
                    for (j in 0 until nStates) {
                        xi[t][i][j] /= denom
                    }
                }
            }
        }

        return xi
    }

    // ─── M-step ───

    private fun updateParams(
        observations: Array<DoubleArray>,
        gamma: Array<DoubleArray>,
        xi: Array<Array<DoubleArray>>
    ) {
        val T = observations.size

        // Start probabilities
        for (j in 0 until nStates) {
            startProb[j] = max(gamma[0][j], 1e-10)
        }
        normalizeInPlace(startProb)

        // Transition matrix
        for (i in 0 until nStates) {
            var gammaSum = 0.0
            for (t in 0 until T - 1) gammaSum += gamma[t][i]
            for (j in 0 until nStates) {
                var xiSum = 0.0
                for (t in 0 until T - 1) xiSum += xi[t][i][j]
                transmat[i][j] = if (gammaSum > 1e-10) xiSum / gammaSum else 1.0 / nStates
            }
            normalizeInPlace(transmat[i])
        }

        // Means and covariances
        for (j in 0 until nStates) {
            var gammaSum = 0.0
            for (t in 0 until T) gammaSum += gamma[t][j]

            for (d in 0 until nFeatures) {
                // Mean
                var weightedSum = 0.0
                for (t in 0 until T) weightedSum += gamma[t][j] * observations[t][d]
                means[j][d] = if (gammaSum > 1e-10) weightedSum / gammaSum else 0.0

                // Variance (diagonal)
                var weightedVarSum = 0.0
                for (t in 0 until T) {
                    val diff = observations[t][d] - means[j][d]
                    weightedVarSum += gamma[t][j] * diff * diff
                }
                covars[j][d] = if (gammaSum > 1e-10) max(weightedVarSum / gammaSum, 1e-6) else 1.0
            }
        }
    }

    // ─── Emission probabilities ───

    internal fun emission(state: Int, obs: DoubleArray): Double {
        var logProb = 0.0
        for (d in 0 until nFeatures) {
            val diff = obs[d] - means[state][d]
            val variance = covars[state][d]
            logProb += -0.5 * (diff * diff / variance + ln(2.0 * Math.PI * variance))
        }
        return max(exp(logProb), 1e-300)
    }

    private fun lnEmission(state: Int, obs: DoubleArray): Double {
        var logProb = 0.0
        for (d in 0 until nFeatures) {
            val diff = obs[d] - means[state][d]
            val variance = covars[state][d]
            logProb += -0.5 * (diff * diff / variance + ln(2.0 * Math.PI * variance))
        }
        return logProb
    }

    // ─── Serialization ───

    fun toStateMap(): Map<String, Any> = mapOf(
        "nStates" to nStates,
        "nFeatures" to nFeatures,
        "startProb" to startProb.toList(),
        "transmat" to transmat.map { it.toList() },
        "means" to means.map { it.toList() },
        "covars" to covars.map { it.toList() },
        "isFitted" to isFitted
    )

    companion object {
        fun fromStateMap(state: Map<String, Any>): GaussianHmm {
            @Suppress("UNCHECKED_CAST")
            val nStates = (state["nStates"] as Number).toInt()
            @Suppress("UNCHECKED_CAST")
            val nFeatures = (state["nFeatures"] as Number).toInt()
            val hmm = GaussianHmm(nStates = nStates, nFeatures = nFeatures)

            @Suppress("UNCHECKED_CAST")
            hmm.startProb = (state["startProb"] as List<Number>).map { it.toDouble() }.toDoubleArray()

            @Suppress("UNCHECKED_CAST")
            hmm.transmat = (state["transmat"] as List<List<Number>>).map { row ->
                row.map { it.toDouble() }.toDoubleArray()
            }.toTypedArray()

            @Suppress("UNCHECKED_CAST")
            hmm.means = (state["means"] as List<List<Number>>).map { row ->
                row.map { it.toDouble() }.toDoubleArray()
            }.toTypedArray()

            @Suppress("UNCHECKED_CAST")
            hmm.covars = (state["covars"] as List<List<Number>>).map { row ->
                row.map { it.toDouble() }.toDoubleArray()
            }.toTypedArray()

            hmm.isFitted = state["isFitted"] as? Boolean ?: true
            return hmm
        }
    }

    private fun normalizeInPlace(arr: DoubleArray) {
        val sum = arr.sum()
        if (sum > 0) {
            for (i in arr.indices) arr[i] /= sum
        } else {
            for (i in arr.indices) arr[i] = 1.0 / arr.size
        }
    }
}
