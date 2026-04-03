package com.tinyoscillator.data.engine.ensemble

import com.tinyoscillator.core.database.dao.EnsembleHistoryDao
import com.tinyoscillator.core.database.entity.EnsembleHistoryEntity
import com.tinyoscillator.data.engine.ensemble.StackingEnsemble.Companion.MIN_SAMPLES
import com.tinyoscillator.domain.model.EnsembleHistoryEntry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앙상블 학습 이력 저장소 — Room DB 기반.
 *
 * 분석 시점에 9개 알고리즘의 보정 확률을 저장하고,
 * T+1 거래일에 실제 결과를 채워 OOF 학습 데이터를 축적.
 */
@Singleton
class SignalHistoryStore @Inject constructor(
    private val ensembleHistoryDao: EnsembleHistoryDao
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 분석 결과 기록 (T+0 시점).
     *
     * @param ticker 종목코드
     * @param date 분석일 (yyyyMMdd)
     * @param signals {algo_name: calibrated_prob}
     * @param regimeId 시장 레짐 (nullable)
     */
    suspend fun append(
        ticker: String,
        date: String,
        signals: Map<String, Double>,
        regimeId: String? = null
    ) {
        val signalsJson = json.encodeToString(signals)
        ensembleHistoryDao.upsert(
            EnsembleHistoryEntity(
                ticker = ticker,
                date = date,
                signalsJson = signalsJson,
                regimeId = regimeId
            )
        )
    }

    /**
     * T+1 결과 업데이트 — 다음 거래일 종가 확인 후 호출.
     */
    suspend fun updateOutcome(ticker: String, date: String, outcome: Int, nextDayReturn: Double) {
        ensembleHistoryDao.updateOutcome(ticker, date, outcome, nextDayReturn)
    }

    /**
     * 전체 학습 데이터 조회 (결과가 채워진 항목만).
     *
     * @param minSamples 최소 샘플 수 (미달 시 null 반환)
     * @return 학습 가능한 데이터 리스트 또는 null
     */
    suspend fun getHistory(minSamples: Int = MIN_SAMPLES): List<EnsembleHistoryEntry>? {
        val entities = ensembleHistoryDao.getCompletedHistory()
        if (entities.size < minSamples) {
            Timber.d("앙상블 이력 부족: %d < %d", entities.size, minSamples)
            return null
        }
        return entities.mapNotNull { toEntry(it) }
    }

    /**
     * 특정 레짐의 학습 데이터 조회.
     */
    suspend fun getHistoryByRegime(regimeId: String, minSamples: Int = MIN_SAMPLES): List<EnsembleHistoryEntry>? {
        val entities = ensembleHistoryDao.getCompletedHistoryByRegime(regimeId)
        if (entities.size < minSamples) return null
        return entities.mapNotNull { toEntry(it) }
    }

    /**
     * 결과 미확인 이력 조회 (T+1 업데이트 대상).
     */
    suspend fun getPendingOutcomes(cutoffDate: String): List<EnsembleHistoryEntity> {
        return ensembleHistoryDao.getPendingOutcomes(cutoffDate)
    }

    /**
     * 완료된 전체 샘플 수.
     */
    suspend fun getCompletedCount(): Int {
        return ensembleHistoryDao.getCompletedCount()
    }

    /**
     * 특정 시점 이후 추가된 새 샘플 수 (재학습 판단용).
     */
    suspend fun getNewSamplesSince(sinceTimestamp: Long): Int {
        return ensembleHistoryDao.getNewSamplesSince(sinceTimestamp)
    }

    /**
     * 학습 데이터를 StackingEnsemble.fit()에 전달할 형태로 변환.
     *
     * @param algoNames 알고리즘 이름 순서 (열 순서)
     * @return (signals[n][d], labels[n]) 또는 null (데이터 부족)
     */
    suspend fun toTrainingData(algoNames: List<String>): Pair<Array<DoubleArray>, IntArray>? {
        val entries = getHistory() ?: return null
        val signals = entries.map { entry ->
            algoNames.map { entry.signals[it] ?: 0.5 }.toDoubleArray()
        }.toTypedArray()
        val labels = entries.map { it.actualOutcome }.toIntArray()
        return signals to labels
    }

    private fun toEntry(entity: EnsembleHistoryEntity): EnsembleHistoryEntry? {
        return try {
            val signals: Map<String, Double> = json.decodeFromString(entity.signalsJson)
            EnsembleHistoryEntry(
                date = entity.date,
                ticker = entity.ticker,
                signals = signals,
                actualOutcome = entity.actualOutcome ?: return null
            )
        } catch (e: Exception) {
            Timber.w(e, "앙상블 이력 파싱 실패: ticker=%s, date=%s", entity.ticker, entity.date)
            null
        }
    }
}
