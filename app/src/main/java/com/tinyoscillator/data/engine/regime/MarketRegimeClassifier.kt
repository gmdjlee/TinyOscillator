package com.tinyoscillator.data.engine.regime

import com.tinyoscillator.domain.model.MarketRegimeResult
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 시장 레짐 분류기 — KOSPI 종합지수 기반 4-상태 Gaussian HMM
 *
 * 4개 레짐:
 * - BULL_LOW_VOL: 안정적 상승장 (낮은 변동성, 양의 수익률)
 * - BEAR_HIGH_VOL: 변동성 높은 하락장
 * - SIDEWAYS: 박스권 (낮은 수익률, 중간 변동성)
 * - CRISIS: 위기 구간 (극단적 하락, 매우 높은 변동성)
 *
 * Features:
 * 1. 1-day log return
 * 2. 20-day realized volatility (annualized)
 * 3. 60-day price momentum (close/close[60] - 1)
 * 4. 20-day return skewness
 */
@Singleton
class MarketRegimeClassifier @Inject constructor() {

    companion object {
        const val N_REGIMES = 4
        const val N_FEATURES = 4
        const val LOOKBACK = 60
        const val VOL_WINDOW = 20
        const val SKEW_WINDOW = 20
        const val ANNUALIZATION_FACTOR = 252.0 // trading days per year

        // Regime names
        const val REGIME_BULL_LOW_VOL = "BULL_LOW_VOL"
        const val REGIME_BEAR_HIGH_VOL = "BEAR_HIGH_VOL"
        const val REGIME_SIDEWAYS = "SIDEWAYS"
        const val REGIME_CRISIS = "CRISIS"

        val REGIME_NAMES = listOf(REGIME_BULL_LOW_VOL, REGIME_BEAR_HIGH_VOL, REGIME_SIDEWAYS, REGIME_CRISIS)
        val REGIME_DESCRIPTIONS = mapOf(
            REGIME_BULL_LOW_VOL to "안정적 상승장",
            REGIME_BEAR_HIGH_VOL to "변동성 하락장",
            REGIME_SIDEWAYS to "박스권 횡보",
            REGIME_CRISIS to "위기 구간"
        )
    }

    private var hmm = GaussianHmm(
        nStates = N_REGIMES,
        nFeatures = N_FEATURES,
        nIter = 300,
        randomState = 42
    )

    private var stateToRegimeMapping: IntArray = intArrayOf(0, 1, 2, 3)
    private var isTrained = false

    /**
     * 종가 배열에서 4차원 특성 벡터 생성
     *
     * @param closes KOSPI 일별 종가 (날짜 오름차순)
     * @return N×4 특성 행렬 (NaN 행 제거 후)
     */
    fun buildFeatures(closes: DoubleArray): Array<DoubleArray> {
        require(closes.size > LOOKBACK + 1) {
            "최소 ${LOOKBACK + 1}개의 종가가 필요합니다 (현재: ${closes.size})"
        }

        // 1-day log returns
        val logReturns = DoubleArray(closes.size - 1) { i ->
            if (closes[i] > 0 && closes[i + 1] > 0) ln(closes[i + 1] / closes[i]) else 0.0
        }

        val features = mutableListOf<DoubleArray>()

        // Start from index where all features are available (after warmup)
        val startIdx = LOOKBACK // need 60 days for momentum
        for (i in startIdx until logReturns.size) {
            // Feature 1: 1-day log return
            val logReturn = logReturns[i]

            // Feature 2: 20-day realized volatility (annualized)
            val volStart = max(0, i - VOL_WINDOW + 1)
            val volWindow = logReturns.sliceArray(volStart..i)
            val realizedVol = stddev(volWindow) * sqrt(ANNUALIZATION_FACTOR)

            // Feature 3: 60-day price momentum
            // closes index = logReturns index + 1 for the current close
            val currentCloseIdx = i + 1
            val pastCloseIdx = currentCloseIdx - LOOKBACK
            val momentum = if (pastCloseIdx >= 0 && closes[pastCloseIdx] > 0) {
                closes[currentCloseIdx] / closes[pastCloseIdx] - 1.0
            } else 0.0

            // Feature 4: 20-day return skewness
            val skewness = skewness(volWindow)

            // Check for NaN/Inf
            if (logReturn.isFinite() && realizedVol.isFinite() &&
                momentum.isFinite() && skewness.isFinite()
            ) {
                features.add(doubleArrayOf(logReturn, realizedVol, momentum, skewness))
            }
        }

        return features.toTypedArray()
    }

    /**
     * HMM 학습
     *
     * @param closes KOSPI 일별 종가 (최소 121일, 권장 504일)
     */
    fun fit(closes: DoubleArray) {
        val features = buildFeatures(closes)
        require(features.size >= N_REGIMES * 2) {
            "학습에 충분한 특성 데이터가 없습니다 (현재: ${features.size})"
        }

        Timber.d("MarketRegimeClassifier 학습 시작: ${features.size}개 관측값")
        hmm.fit(features)

        // Label states by sorting: assign regime names based on learned means
        stateToRegimeMapping = labelStates()
        isTrained = true
        Timber.d("MarketRegimeClassifier 학습 완료 — 상태 매핑: ${stateToRegimeMapping.toList()}")
    }

    /**
     * 현재 레짐 예측
     *
     * @param closes KOSPI 일별 종가
     * @return MarketRegimeResult (레짐 ID, 이름, 신뢰도, 확률 벡터, 지속 기간)
     */
    fun predictRegime(closes: DoubleArray): MarketRegimeResult {
        val features = buildFeatures(closes)
        if (features.isEmpty()) return defaultResult()

        // Use the HMM that may be heuristic or trained
        val proba = if (isTrained) {
            hmm.predictProba(features)
        } else {
            // Heuristic fallback using feature characteristics
            return heuristicRegime(features)
        }

        val lastProba = proba.last()

        // Map HMM state probabilities to regime probabilities
        val regimeProba = DoubleArray(N_REGIMES)
        for (hmmState in 0 until N_REGIMES) {
            val regimeIdx = stateToRegimeMapping[hmmState]
            regimeProba[regimeIdx] += lastProba[hmmState]
        }

        val regimeId = regimeProba.indices.maxByOrNull { regimeProba[it] } ?: 0
        val confidence = regimeProba[regimeId]

        // Compute regime duration (consecutive days in current regime)
        val path = hmm.predict(features)
        val currentHmmState = path.last()
        val currentRegimeId = stateToRegimeMapping[currentHmmState]
        var duration = 1
        for (i in path.size - 2 downTo 0) {
            if (stateToRegimeMapping[path[i]] == currentRegimeId) duration++ else break
        }

        return MarketRegimeResult(
            regimeId = regimeId,
            regimeName = REGIME_NAMES[regimeId],
            regimeDescription = REGIME_DESCRIPTIONS[REGIME_NAMES[regimeId]] ?: "",
            confidence = confidence,
            probaVec = regimeProba.toList(),
            regimeDurationDays = duration
        )
    }

    /**
     * 모델 상태 직렬화 (JSON-safe)
     */
    fun saveModel(): Map<String, Any> = mapOf(
        "hmm" to hmm.toStateMap(),
        "stateToRegimeMapping" to stateToRegimeMapping.toList(),
        "isTrained" to isTrained
    )

    /**
     * 모델 상태 복원
     */
    @Suppress("UNCHECKED_CAST")
    fun loadModel(state: Map<String, Any>) {
        val hmmState = state["hmm"] as? Map<String, Any> ?: return
        hmm = GaussianHmm.fromStateMap(hmmState)
        stateToRegimeMapping = (state["stateToRegimeMapping"] as? List<Number>)
            ?.map { it.toInt() }?.toIntArray()
            ?: intArrayOf(0, 1, 2, 3)
        isTrained = state["isTrained"] as? Boolean ?: false
    }

    val trained: Boolean get() = isTrained

    // ─── State labeling ───

    /**
     * HMM의 학습된 상태를 레짐 이름에 매핑.
     *
     * 기준:
     * - CRISIS: 가장 높은 변동성 (mean[1]) + 가장 낮은 수익률 (mean[0])
     * - BULL_LOW_VOL: 가장 높은 수익률 + 낮은 변동성
     * - BEAR_HIGH_VOL: 음의 수익률 + 높은 변동성
     * - SIDEWAYS: 나머지
     */
    private fun labelStates(): IntArray {
        data class StateScore(val hmmState: Int, val returnMean: Double, val volMean: Double)

        val scores = (0 until N_REGIMES).map { s ->
            StateScore(s, hmm.means[s][0], hmm.means[s][1])
        }

        val mapping = IntArray(N_REGIMES) { -1 }
        val assigned = mutableSetOf<Int>()

        // 1. CRISIS: highest volatility AND lowest return
        val crisisCandidate = scores
            .filter { it.hmmState !in assigned }
            .maxByOrNull { it.volMean - it.returnMean * 10 }!!
        mapping[crisisCandidate.hmmState] = REGIME_NAMES.indexOf(REGIME_CRISIS)
        assigned.add(crisisCandidate.hmmState)

        // 2. BULL_LOW_VOL: highest return AND lowest volatility
        val bullCandidate = scores
            .filter { it.hmmState !in assigned }
            .maxByOrNull { it.returnMean * 10 - it.volMean }!!
        mapping[bullCandidate.hmmState] = REGIME_NAMES.indexOf(REGIME_BULL_LOW_VOL)
        assigned.add(bullCandidate.hmmState)

        // 3. BEAR_HIGH_VOL: negative return + high volatility (remaining)
        val remaining = scores.filter { it.hmmState !in assigned }
        val bearCandidate = remaining.minByOrNull { it.returnMean - it.volMean }!!
        mapping[bearCandidate.hmmState] = REGIME_NAMES.indexOf(REGIME_BEAR_HIGH_VOL)
        assigned.add(bearCandidate.hmmState)

        // 4. SIDEWAYS: last remaining
        val sidewaysCandidate = scores.first { it.hmmState !in assigned }
        mapping[sidewaysCandidate.hmmState] = REGIME_NAMES.indexOf(REGIME_SIDEWAYS)

        return mapping
    }

    // ─── Heuristic fallback ───

    private fun heuristicRegime(features: Array<DoubleArray>): MarketRegimeResult {
        val last = features.last()
        val logReturn = last[0]
        val realizedVol = last[1]
        val momentum = last[2]

        val regimeId = when {
            realizedVol > 0.30 && momentum < -0.10 -> 3 // CRISIS
            realizedVol > 0.20 && logReturn < 0 -> 1     // BEAR_HIGH_VOL
            momentum > 0.05 && realizedVol < 0.20 -> 0   // BULL_LOW_VOL
            else -> 2                                      // SIDEWAYS
        }

        val probaVec = DoubleArray(N_REGIMES) { 0.1 }
        probaVec[regimeId] = 0.7

        return MarketRegimeResult(
            regimeId = regimeId,
            regimeName = REGIME_NAMES[regimeId],
            regimeDescription = REGIME_DESCRIPTIONS[REGIME_NAMES[regimeId]] ?: "",
            confidence = 0.7,
            probaVec = probaVec.toList(),
            regimeDurationDays = 1
        )
    }

    private fun defaultResult() = MarketRegimeResult(
        regimeId = 2,
        regimeName = REGIME_SIDEWAYS,
        regimeDescription = REGIME_DESCRIPTIONS[REGIME_SIDEWAYS] ?: "",
        confidence = 0.25,
        probaVec = listOf(0.25, 0.25, 0.25, 0.25),
        regimeDurationDays = 0
    )

    // ─── Math helpers ───

    private fun stddev(arr: DoubleArray): Double {
        if (arr.size < 2) return 0.0
        val mean = arr.average()
        return sqrt(arr.map { (it - mean) * (it - mean) }.average())
    }

    private fun skewness(arr: DoubleArray): Double {
        if (arr.size < 3) return 0.0
        val n = arr.size.toDouble()
        val mean = arr.average()
        val m2 = arr.sumOf { (it - mean) * (it - mean) } / n
        val m3 = arr.sumOf { (it - mean) * (it - mean) * (it - mean) } / n
        val s = sqrt(m2)
        return if (s > 1e-10) m3 / (s * s * s) else 0.0
    }
}
