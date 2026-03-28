package com.tinyoscillator.data.engine

import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.HmmResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Hidden Markov Model 레짐 탐지 엔진
 *
 * 시장 레짐(숨은 상태)을 탐지: 저변동상승/저변동횡보/고변동상승/고변동하락.
 * Heuristic HMM — 사전 정의된 파라미터 사용 (Baum-Welch 미사용).
 * Forward Algorithm으로 상태 확률 분포, Viterbi로 최적 경로 추정.
 */
@Singleton
class HmmRegimeEngine @Inject constructor() {

    companion object {
        const val NUM_STATES = 4
        private const val ROLLING_WINDOW = 60
        private const val RECENT_PATH_LENGTH = 20

        // 방출 확률 — 각 상태별 2D 가우시안 파라미터 [return_z, volatility_z]
        val EMISSION_MEANS = arrayOf(
            doubleArrayOf(+0.5, -0.5),   // REGIME_0: 저변동 상승
            doubleArrayOf(0.0, -0.3),    // REGIME_1: 저변동 횡보
            doubleArrayOf(+0.3, +1.0),   // REGIME_2: 고변동 상승
            doubleArrayOf(-0.5, +1.2)    // REGIME_3: 고변동 하락
        )

        val EMISSION_STDS = arrayOf(
            doubleArrayOf(0.8, 0.6),
            doubleArrayOf(0.7, 0.5),
            doubleArrayOf(1.2, 1.0),
            doubleArrayOf(1.0, 0.8)
        )

        // 전환 행렬 (대각선 0.90~0.95 — 레짐 지속성 높음)
        val TRANSITION_MATRIX = arrayOf(
            doubleArrayOf(0.93, 0.04, 0.02, 0.01),
            doubleArrayOf(0.05, 0.90, 0.03, 0.02),
            doubleArrayOf(0.02, 0.03, 0.92, 0.03),
            doubleArrayOf(0.01, 0.02, 0.04, 0.93)
        )

        // 초기 상태 확률 (균등)
        val INITIAL_PROBS = doubleArrayOf(0.25, 0.25, 0.25, 0.25)
    }

    /**
     * HMM 레짐 분석 실행
     *
     * @param prices 일별 거래 데이터 (날짜 오름차순, 최소 ROLLING_WINDOW+1일)
     */
    suspend fun analyze(prices: List<DailyTrading>): HmmResult {
        require(prices.size >= ROLLING_WINDOW + 1) {
            "최소 ${ROLLING_WINDOW + 1}일의 가격 데이터가 필요합니다 (현재: ${prices.size})"
        }

        // Step 1: 관측값 생성
        val observations = generateObservations(prices)
        if (observations.isEmpty()) {
            return defaultResult()
        }

        // Step 2: Forward Algorithm
        val alpha = forwardAlgorithm(observations)

        // Step 3: Viterbi Algorithm
        val viterbiPath = viterbiAlgorithm(observations)

        // 현재 상태 확률 분포 (Forward의 마지막 시점)
        val currentProbs = alpha.last()
        val currentRegime = currentProbs.indices.maxByOrNull { currentProbs[it] } ?: 0

        // 현재 레짐에서 다른 상태로의 전환 확률
        val transitionProbs = mutableMapOf<String, Double>()
        for (j in 0 until NUM_STATES) {
            val fromName = HmmResult.regimeToDescription(currentRegime).substringBefore(" (")
            val toName = HmmResult.regimeToDescription(j).substringBefore(" (")
            transitionProbs["$fromName→$toName"] = TRANSITION_MATRIX[currentRegime][j]
        }

        // 최근 N일 경로
        val recentPath = viterbiPath.takeLast(RECENT_PATH_LENGTH)

        return HmmResult(
            currentRegime = currentRegime,
            regimeProbabilities = currentProbs,
            transitionProbabilities = transitionProbs,
            recentRegimePath = recentPath,
            regimeDescription = HmmResult.regimeToDescription(currentRegime)
        )
    }

    /**
     * 관측값 생성: 일별 수익률과 변동성을 z-score 정규화
     */
    internal fun generateObservations(prices: List<DailyTrading>): List<DoubleArray> {
        if (prices.size < 2) return emptyList()

        // 일별 수익률
        val returns = DoubleArray(prices.size - 1) { i ->
            val prev = prices[i].closePrice.toDouble()
            val curr = prices[i + 1].closePrice.toDouble()
            if (prev > 0) (curr - prev) / prev else 0.0
        }

        // 일별 변동성 (|high-low|/close 대신 |return| 사용 — close만 보유)
        val volatilities = returns.map { kotlin.math.abs(it) }.toDoubleArray()

        // z-score 정규화 (60일 롤링)
        val observations = mutableListOf<DoubleArray>()
        for (i in returns.indices) {
            val windowStart = max(0, i - ROLLING_WINDOW + 1)
            val windowReturns = returns.sliceArray(windowStart..i)
            val windowVols = volatilities.sliceArray(windowStart..i)

            val returnZ = zScore(returns[i], windowReturns)
            val volZ = zScore(volatilities[i], windowVols)
            observations.add(doubleArrayOf(returnZ, volZ))
        }

        return observations
    }

    /**
     * Forward Algorithm — α(t, j) = [Σᵢ α(t-1, i) × aᵢⱼ] × bⱼ(oₜ)
     * 각 시점의 상태 확률 분포 계산 (정규화 적용)
     */
    internal fun forwardAlgorithm(observations: List<DoubleArray>): List<DoubleArray> {
        val T = observations.size
        val alpha = ArrayList<DoubleArray>(T)

        // 초기화: α(0, j) = π(j) × b(j, o₀)
        val alpha0 = DoubleArray(NUM_STATES) { j ->
            INITIAL_PROBS[j] * emissionProbability(j, observations[0])
        }
        normalizeInPlace(alpha0)
        alpha.add(alpha0)

        // 재귀: α(t, j) = [Σᵢ α(t-1, i) × a(i,j)] × b(j, oₜ)
        for (t in 1 until T) {
            val alphaT = DoubleArray(NUM_STATES) { j ->
                var sum = 0.0
                for (i in 0 until NUM_STATES) {
                    sum += alpha[t - 1][i] * TRANSITION_MATRIX[i][j]
                }
                sum * emissionProbability(j, observations[t])
            }
            normalizeInPlace(alphaT)
            alpha.add(alphaT)
        }

        return alpha
    }

    /**
     * Viterbi Algorithm — 최적 상태 경로 추정
     */
    internal fun viterbiAlgorithm(observations: List<DoubleArray>): List<Int> {
        val T = observations.size
        // 로그 공간에서 계산 (언더플로우 방지)
        val delta = Array(T) { DoubleArray(NUM_STATES) }
        val psi = Array(T) { IntArray(NUM_STATES) }

        // 초기화
        for (j in 0 until NUM_STATES) {
            delta[0][j] = ln(INITIAL_PROBS[j]) + lnEmissionProbability(j, observations[0])
            psi[0][j] = 0
        }

        // 재귀
        for (t in 1 until T) {
            for (j in 0 until NUM_STATES) {
                var maxVal = Double.NEGATIVE_INFINITY
                var maxIdx = 0
                for (i in 0 until NUM_STATES) {
                    val v = delta[t - 1][i] + ln(TRANSITION_MATRIX[i][j])
                    if (v > maxVal) {
                        maxVal = v
                        maxIdx = i
                    }
                }
                delta[t][j] = maxVal + lnEmissionProbability(j, observations[t])
                psi[t][j] = maxIdx
            }
        }

        // 역추적
        val path = IntArray(T)
        path[T - 1] = delta[T - 1].indices.maxByOrNull { delta[T - 1][it] } ?: 0
        for (t in T - 2 downTo 0) {
            path[t] = psi[t + 1][path[t + 1]]
        }

        return path.toList()
    }

    /**
     * 방출 확률: 2D 독립 가우시안
     * b(j, o) = Π_d N(o_d | μ_jd, σ_jd)
     */
    internal fun emissionProbability(state: Int, observation: DoubleArray): Double {
        var prob = 1.0
        for (d in observation.indices) {
            prob *= gaussianPdf(observation[d], EMISSION_MEANS[state][d], EMISSION_STDS[state][d])
        }
        return max(prob, 1e-300) // 수치 안정성
    }

    private fun lnEmissionProbability(state: Int, observation: DoubleArray): Double {
        var logProb = 0.0
        for (d in observation.indices) {
            logProb += lnGaussianPdf(observation[d], EMISSION_MEANS[state][d], EMISSION_STDS[state][d])
        }
        return logProb
    }

    internal fun gaussianPdf(x: Double, mean: Double, std: Double): Double {
        val z = (x - mean) / std
        return exp(-0.5 * z * z) / (std * sqrt(2.0 * Math.PI))
    }

    private fun lnGaussianPdf(x: Double, mean: Double, std: Double): Double {
        val z = (x - mean) / std
        return -0.5 * z * z - ln(std) - 0.5 * ln(2.0 * Math.PI)
    }

    private fun zScore(value: Double, window: DoubleArray): Double {
        if (window.size < 2) return 0.0
        val mean = window.average()
        val std = sqrt(window.map { (it - mean) * (it - mean) }.average())
        return if (std > 1e-10) (value - mean) / std else 0.0
    }

    private fun normalizeInPlace(arr: DoubleArray) {
        val sum = arr.sum()
        if (sum > 0) {
            for (i in arr.indices) arr[i] /= sum
        } else {
            for (i in arr.indices) arr[i] = 1.0 / arr.size
        }
    }

    private fun defaultResult() = HmmResult(
        currentRegime = HmmResult.REGIME_LOW_VOL_SIDEWAYS,
        regimeProbabilities = doubleArrayOf(0.25, 0.25, 0.25, 0.25),
        transitionProbabilities = emptyMap(),
        recentRegimePath = emptyList(),
        regimeDescription = HmmResult.regimeToDescription(HmmResult.REGIME_LOW_VOL_SIDEWAYS)
    )
}
