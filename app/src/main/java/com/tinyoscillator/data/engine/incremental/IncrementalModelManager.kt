package com.tinyoscillator.data.engine.incremental

import com.tinyoscillator.data.engine.ensemble.SignalHistoryStore
import com.tinyoscillator.data.engine.regime.RegimeWeightTable
import com.tinyoscillator.domain.model.BrierEntry
import com.tinyoscillator.domain.model.IncrementalModelManagerState
import com.tinyoscillator.domain.model.IncrementalUpdateSummary
import com.tinyoscillator.domain.model.ModelDriftAlert
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 점진적 모델 매니저
 *
 * IncrementalNaiveBayes와 IncrementalLogisticRegression을 통합 관리.
 * - coldStartIfNeeded(): 미학습 시 이력 기반 초기 학습
 * - dailyUpdate(): 1-샘플 점진적 갱신 + 드리프트 검사
 * - saveAll() / loadAll(): 상태 직렬화
 */
class IncrementalModelManager(
    private val algoNames: List<String> = RegimeWeightTable.ALL_ALGOS
) {

    companion object {
        const val COLD_START_SAMPLES = 252
        const val MIN_SAMPLES_FOR_UPDATE = 30
        const val BRIER_WINDOW = 30
        const val BASELINE_WINDOW = 90
        const val DRIFT_THRESHOLD = 0.05

        const val MODEL_NB = "IncrementalNaiveBayes"
        const val MODEL_LR = "IncrementalLogisticRegression"
    }

    val naiveBayes = IncrementalNaiveBayes(featureNames = algoNames)
    val logisticRegression = IncrementalLogisticRegression(featureNames = algoNames)

    // 일별 Brier score 이력 (드리프트 검출용)
    private val brierHistory = mutableListOf<BrierEntry>()
    private var baselineBrier: Double? = null

    /**
     * 미학습 시 이력 기반 초기 학습 (cold start).
     *
     * 이미 학습된 경우 건너뜀.
     * SignalHistoryStore에서 마지막 252행을 가져와 warmStart 실행.
     *
     * @return true if warm start was performed
     */
    suspend fun coldStartIfNeeded(signalHistoryStore: SignalHistoryStore): Boolean {
        if (naiveBayes.isFitted && logisticRegression.isFitted) {
            Timber.d("점진적 모델 이미 학습됨 — cold start 건너뜀")
            return false
        }

        val history = signalHistoryStore.getHistory(minSamples = MIN_SAMPLES_FOR_UPDATE) ?: run {
            Timber.d("점진적 모델 cold start 건너뜀: 이력 부족 (최소 %d 필요)", MIN_SAMPLES_FOR_UPDATE)
            return false
        }

        val samples = history.takeLast(COLD_START_SAMPLES)
        Timber.i("점진적 모델 cold start: %d 샘플", samples.size)

        // NaiveBayes
        val nbSignals = samples.map { it.signals }
        val labels = samples.map { it.actualOutcome }
        naiveBayes.warmStart(nbSignals, labels)

        // LogisticRegression
        val lrSignals = samples.map { entry ->
            algoNames.map { entry.signals[it] ?: 0.5 }.toDoubleArray()
        }
        logisticRegression.warmStart(lrSignals, labels)

        // 초기 Brier 베이스라인 설정
        computeAndSetBaseline(samples.map { it.signals }, labels)

        Timber.i("점진적 모델 cold start 완료: NB=%d, LR=%d 샘플",
            samples.size, samples.size)
        return true
    }

    /**
     * 1-샘플 점진적 갱신.
     *
     * @param newSignals 오늘의 알고리즘별 보정 확률
     * @param newLabel 어제의 실제 결과 (1=up, 0=down)
     * @return 업데이트 요약
     */
    fun dailyUpdate(
        newSignals: Map<String, Double>,
        newLabel: Int
    ): IncrementalUpdateSummary {
        val start = System.currentTimeMillis()
        val updatedModels = mutableListOf<String>()
        val driftAlerts = mutableListOf<ModelDriftAlert>()

        // NaiveBayes 갱신
        naiveBayes.update(newSignals, newLabel)
        updatedModels.add(MODEL_NB)

        // LogisticRegression 갱신
        val lrSignal = algoNames.map { newSignals[it] ?: 0.5 }.toDoubleArray()
        logisticRegression.update(lrSignal, newLabel)
        updatedModels.add(MODEL_LR)

        // Brier score 계산 + 드리프트 검사
        val nbPred = naiveBayes.predictProba(newSignals)
        val lrPred = logisticRegression.predictProba(lrSignal)
        val today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)

        val nbBrier = (nbPred - newLabel) * (nbPred - newLabel)
        val lrBrier = (lrPred - newLabel) * (lrPred - newLabel)

        brierHistory.add(BrierEntry(today, nbBrier, MODEL_NB))
        brierHistory.add(BrierEntry(today, lrBrier, MODEL_LR))

        // 드리프트 검사
        checkDrift(MODEL_NB)?.let { driftAlerts.add(it) }
        checkDrift(MODEL_LR)?.let { driftAlerts.add(it) }

        val elapsed = System.currentTimeMillis() - start

        return IncrementalUpdateSummary(
            updatedModels = updatedModels,
            samplesSeen = naiveBayes.saveState().totalSamples,
            trainingMs = elapsed,
            driftAlerts = driftAlerts
        )
    }

    /**
     * 앙상블 예측: 두 점진적 모델의 평균 확률.
     */
    fun predictProba(signals: Map<String, Double>): Double {
        if (!naiveBayes.isFitted || !logisticRegression.isFitted) return 0.5
        val nbProb = naiveBayes.predictProba(signals)
        val lrProb = logisticRegression.predictProba(signals)
        return ((nbProb + lrProb) / 2.0).coerceIn(0.001, 0.999)
    }

    /**
     * 드리프트 검사: 최근 30일 Brier vs 90일 베이스라인.
     *
     * 열화 > 0.05 시 ModelDriftAlert 반환.
     */
    fun checkDrift(modelName: String): ModelDriftAlert? {
        val modelEntries = brierHistory.filter { it.modelName == modelName }
        if (modelEntries.size < BRIER_WINDOW) return null

        val recent = modelEntries.takeLast(BRIER_WINDOW)
        val currentBrier = recent.map { it.brierScore }.average()

        val baseline = baselineBrier ?: run {
            if (modelEntries.size >= BASELINE_WINDOW) {
                modelEntries.takeLast(BASELINE_WINDOW).map { it.brierScore }.average()
            } else {
                modelEntries.map { it.brierScore }.average()
            }
        }

        val degradation = currentBrier - baseline
        if (degradation > DRIFT_THRESHOLD) {
            Timber.w("모델 드리프트 감지: %s (현재=%.4f, 기준=%.4f, 열화=%.4f)",
                modelName, currentBrier, baseline, degradation)
            return ModelDriftAlert(
                modelName = modelName,
                currentBrier = currentBrier,
                baselineBrier = baseline,
                degradation = degradation
            )
        }
        return null
    }

    /**
     * 모든 드리프트 알림 조회.
     */
    fun getDriftAlerts(): List<ModelDriftAlert> {
        return listOfNotNull(checkDrift(MODEL_NB), checkDrift(MODEL_LR))
    }

    fun saveAll(): IncrementalModelManagerState {
        return IncrementalModelManagerState(
            naiveBayesState = if (naiveBayes.isFitted) naiveBayes.saveState() else null,
            logisticState = if (logisticRegression.isFitted) logisticRegression.saveState() else null,
            brierHistory = brierHistory.toList(),
            baselineBrier = baselineBrier,
            savedAt = System.currentTimeMillis()
        )
    }

    fun loadAll(state: IncrementalModelManagerState) {
        state.naiveBayesState?.let { naiveBayes.loadState(it) }
        state.logisticState?.let { logisticRegression.loadState(it) }
        brierHistory.clear()
        brierHistory.addAll(state.brierHistory)
        baselineBrier = state.baselineBrier
    }

    private fun computeAndSetBaseline(signals: List<Map<String, Double>>, labels: List<Int>) {
        if (signals.size < 10) return
        var totalBrier = 0.0
        for (i in signals.indices) {
            val nbPred = naiveBayes.predictProba(signals[i])
            val lrPred = logisticRegression.predictProba(signals[i])
            val avgPred = (nbPred + lrPred) / 2.0
            totalBrier += (avgPred - labels[i]) * (avgPred - labels[i])
        }
        baselineBrier = totalBrier / signals.size
        Timber.d("점진적 모델 Brier 베이스라인: %.4f", baselineBrier)
    }
}
