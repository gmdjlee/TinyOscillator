package com.tinyoscillator.data.engine.incremental

import com.tinyoscillator.domain.model.IncrementalNaiveBayesState
import kotlin.math.exp
import kotlin.math.ln

/**
 * 점진적 나이브 베이즈 분류기
 *
 * 9개 알고리즘의 보정된 신호 확률을 3-bin으로 이산화하여 분류.
 * partial_fit 패턴: warmStart()로 초기 배치 학습, update()로 단일 샘플 갱신.
 *
 * 클래스: 0=DOWN, 1=UP
 * 특성: 각 알고리즘의 calibrated probability → {LOW, MED, HIGH}
 */
class IncrementalNaiveBayes(
    private val featureNames: List<String>,
    private val alpha: Double = 0.5
) {

    companion object {
        private const val BIN_LOW = "LOW"
        private const val BIN_MED = "MED"
        private const val BIN_HIGH = "HIGH"
        private val ALL_BINS = listOf(BIN_LOW, BIN_MED, BIN_HIGH)
        private val ALL_CLASSES = listOf(0, 1)
    }

    // 클래스별 관측 수: {class -> count}
    private val classCounts = mutableMapOf(0 to 0, 1 to 0)

    // 특성-빈-클래스 카운트: {featureName -> {bin -> {class -> count}}}
    private val featureBinCounts = mutableMapOf<String, MutableMap<String, MutableMap<Int, Int>>>()

    // 각 특성에서 관측된 빈 목록
    private val featureBins = mutableMapOf<String, MutableSet<String>>()

    private var totalSamples = 0
    private var fittedAt = 0L

    val isFitted: Boolean get() = totalSamples > 0

    init {
        // 모든 특성에 대해 빈 구조 초기화
        for (name in featureNames) {
            featureBins[name] = ALL_BINS.toMutableSet()
            featureBinCounts[name] = mutableMapOf()
            for (bin in ALL_BINS) {
                featureBinCounts[name]!![bin] = mutableMapOf(0 to 0, 1 to 0)
            }
        }
    }

    /**
     * 연속값 → 3-bin 이산화
     */
    fun discretize(value: Double): String = when {
        value < 0.33 -> BIN_LOW
        value < 0.67 -> BIN_MED
        else -> BIN_HIGH
    }

    /**
     * 초기 배치 학습.
     *
     * @param signals 각 샘플의 알고리즘별 보정 확률 {algoName: prob}
     * @param labels 각 샘플의 실제 결과 (1=up, 0=down)
     */
    fun warmStart(signals: List<Map<String, Double>>, labels: List<Int>) {
        require(signals.size == labels.size) { "signals/labels 크기 불일치" }

        // 카운트 초기화
        classCounts[0] = 0
        classCounts[1] = 0
        for (name in featureNames) {
            for (bin in ALL_BINS) {
                featureBinCounts[name]!![bin] = mutableMapOf(0 to 0, 1 to 0)
            }
        }
        totalSamples = 0

        for (i in signals.indices) {
            incrementCounts(signals[i], labels[i])
        }

        fittedAt = System.currentTimeMillis()
    }

    /**
     * 단일 샘플 점진적 갱신.
     *
     * @param signal 알고리즘별 보정 확률 {algoName: prob}
     * @param label 실제 결과 (1=up, 0=down)
     */
    fun update(signal: Map<String, Double>, label: Int) {
        require(label in ALL_CLASSES) { "label은 0 또는 1이어야 함" }
        incrementCounts(signal, label)
    }

    /**
     * 상승 확률 예측.
     *
     * @param signal 알고리즘별 보정 확률 {algoName: prob}
     * @return P(UP | signals) ∈ [0, 1]
     */
    fun predictProba(signal: Map<String, Double>): Double {
        if (totalSamples == 0) return 0.5

        val numClasses = ALL_CLASSES.size

        val logPosteriors = mutableMapOf<Int, Double>()
        for (cls in ALL_CLASSES) {
            // 로그 사전 확률
            var logProb = ln(
                (classCounts[cls]!! + alpha) / (totalSamples + numClasses * alpha)
            )

            // 로그 우도
            for (name in featureNames) {
                val value = signal[name] ?: 0.5
                val bin = discretize(value)
                val numBins = ALL_BINS.size
                val count = featureBinCounts[name]?.get(bin)?.get(cls) ?: 0
                val classCount = classCounts[cls]!!

                val likelihood = (count + alpha) / (classCount + numBins * alpha)
                logProb += ln(likelihood)
            }

            logPosteriors[cls] = logProb
        }

        // 로그 → 확률 (오버플로우 방지)
        val maxLog = logPosteriors.values.max()
        val expValues = logPosteriors.mapValues { (_, v) -> exp(v - maxLog) }
        val sumExp = expValues.values.sum()

        return (expValues[1]!! / sumExp).coerceIn(0.001, 0.999)
    }

    fun saveState(): IncrementalNaiveBayesState {
        return IncrementalNaiveBayesState(
            classCounts = classCounts.toMap(),
            featureBinCounts = featureBinCounts.mapValues { (_, binMap) ->
                binMap.mapValues { (_, classMap) -> classMap.toMap() }
            },
            featureBins = featureBins.mapValues { (_, bins) -> bins.toSet() },
            totalSamples = totalSamples,
            fittedAt = fittedAt
        )
    }

    fun loadState(state: IncrementalNaiveBayesState) {
        classCounts.clear()
        classCounts.putAll(state.classCounts)

        featureBinCounts.clear()
        for ((name, binMap) in state.featureBinCounts) {
            featureBinCounts[name] = mutableMapOf()
            for ((bin, classMap) in binMap) {
                featureBinCounts[name]!![bin] = classMap.toMutableMap()
            }
        }

        featureBins.clear()
        for ((name, bins) in state.featureBins) {
            featureBins[name] = bins.toMutableSet()
        }

        totalSamples = state.totalSamples
        fittedAt = state.fittedAt
    }

    private fun incrementCounts(signal: Map<String, Double>, label: Int) {
        classCounts[label] = (classCounts[label] ?: 0) + 1
        totalSamples++

        for (name in featureNames) {
            val value = signal[name] ?: 0.5
            val bin = discretize(value)
            featureBinCounts.getOrPut(name) { mutableMapOf() }
                .getOrPut(bin) { mutableMapOf(0 to 0, 1 to 0) }
            featureBinCounts[name]!![bin]!![label] =
                (featureBinCounts[name]!![bin]!![label] ?: 0) + 1
        }
    }
}
