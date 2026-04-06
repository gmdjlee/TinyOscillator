package com.tinyoscillator.data.engine.calibration

import com.tinyoscillator.domain.model.CalibrationMetrics
import com.tinyoscillator.domain.model.ReliabilityBin
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * 보정 품질 모니터 — 알고리즘별 롤링 윈도우로 Brier score, ECE를 추적.
 *
 * 기준 Brier score 대비 0.05 이상 악화되면 재보정 플래그를 발생시킨다.
 *
 * @param windowSize 롤링 윈도우 크기 (기본 60일)
 * @param degradationThreshold 재보정 트리거 임계값 (기본 0.05)
 */
@Singleton
class CalibrationMonitor @Inject constructor() {

    var windowSize: Int = 60
        private set
    var degradationThreshold: Double = 0.05
        private set

    private val observations = mutableMapOf<String, ArrayDeque<Observation>>()
    private val baselineBrier = mutableMapOf<String, Double>()

    private data class Observation(
        val predictedProb: Double,
        val actualOutcome: Double // 0.0 or 1.0
    )

    // ─── Configuration ───

    fun configure(windowSize: Int = 60, degradationThreshold: Double = 0.05) {
        this.windowSize = windowSize
        this.degradationThreshold = degradationThreshold
    }

    // ─── Public API ───

    /**
     * 관측값 추가. 윈도우 크기 초과 시 가장 오래된 항목 제거.
     */
    fun addObservation(algoName: String, predProb: Double, outcome: Double) {
        val deque = observations.getOrPut(algoName) { ArrayDeque() }
        deque.addLast(Observation(predProb, outcome))
        while (deque.size > windowSize) {
            deque.removeFirst()
        }
    }

    /**
     * 알고리즘별 보정 메트릭 반환.
     */
    fun getMetrics(algoName: String): CalibrationMetrics {
        val obs = observations[algoName]
        if (obs == null || obs.isEmpty()) {
            return CalibrationMetrics(
                algoName = algoName,
                brierScore = Double.NaN,
                logLoss = Double.NaN,
                ece = Double.NaN,
                reliabilityBins = emptyList(),
                sampleCount = 0,
                needsRecalibration = false
            )
        }

        val preds = DoubleArray(obs.size) { obs.elementAt(it).predictedProb }
        val actuals = DoubleArray(obs.size) { obs.elementAt(it).actualOutcome }

        val brier = WalkForwardValidator.brierScore(preds, actuals)
        val ll = WalkForwardValidator.logLoss(preds, actuals)
        val bins = buildReliabilityBins(preds, actuals, 10)
        val ece = calcEce(bins, obs.size)

        val needsRecal = checkDegradation(algoName, brier)

        return CalibrationMetrics(
            algoName = algoName,
            brierScore = brier,
            logLoss = ll,
            ece = ece,
            reliabilityBins = bins,
            sampleCount = obs.size,
            needsRecalibration = needsRecal
        )
    }

    /**
     * 기준 Brier score 설정. 보정 직후 호출하여 기준점을 잡는다.
     */
    fun setBaseline(algoName: String, brierScore: Double) {
        baselineBrier[algoName] = brierScore
        Timber.d("보정 기준 설정: %s → Brier=%.4f", algoName, brierScore)
    }

    /** 관측값 초기화 */
    fun clear(algoName: String) {
        observations.remove(algoName)
        baselineBrier.remove(algoName)
    }

    /** 전체 초기화 */
    fun clearAll() {
        observations.clear()
        baselineBrier.clear()
    }

    /** 현재 관측값 수 */
    fun observationCount(algoName: String): Int = observations[algoName]?.size ?: 0

    // ─── Internal ───

    private fun checkDegradation(algoName: String, currentBrier: Double): Boolean {
        val baseline = baselineBrier[algoName] ?: return false
        val degraded = currentBrier - baseline > degradationThreshold
        if (degraded) {
            Timber.w("보정 품질 악화 감지: %s (기준=%.4f, 현재=%.4f, 차이=%.4f)",
                algoName, baseline, currentBrier, currentBrier - baseline)
        }
        return degraded
    }

    private fun buildReliabilityBins(
        preds: DoubleArray,
        actuals: DoubleArray,
        nBins: Int
    ): List<ReliabilityBin> {
        val bins = mutableListOf<ReliabilityBin>()
        val binWidth = 1.0 / nBins
        for (b in 0 until nBins) {
            val lo = b * binWidth
            val hi = (b + 1) * binWidth
            val mid = (lo + hi) / 2.0
            val inBin = preds.indices.filter { preds[it] >= lo && preds[it] < hi }
            if (inBin.isEmpty()) {
                bins.add(ReliabilityBin(mid, 0.0, 0))
            } else {
                val fracPos = inBin.sumOf { actuals[it] } / inBin.size
                bins.add(ReliabilityBin(mid, fracPos, inBin.size))
            }
        }
        return bins
    }

    /** Expected Calibration Error: weighted average |fraction_positive - bin_mid| */
    private fun calcEce(bins: List<ReliabilityBin>, totalCount: Int): Double {
        if (totalCount == 0) return 0.0
        return bins.sumOf { bin ->
            abs(bin.fractionPositive - bin.binMid) * bin.count
        } / totalCount
    }
}
