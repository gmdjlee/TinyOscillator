package com.tinyoscillator.data.repository

import com.tinyoscillator.core.database.dao.CalibrationDao
import com.tinyoscillator.core.database.entity.SignalHistoryEntity
import com.tinyoscillator.domain.model.AlgoAccuracyRow
import com.tinyoscillator.domain.model.AlgoResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 신호 이력 저장·조회·적중률 집계 리포지토리.
 *
 * 분석 시점에 recordSignal()로 기록하고, WorkManager가 T+1/T+5/T+20 수익률을 채운다.
 */
@Singleton
class SignalHistoryRepository @Inject constructor(
    private val dao: CalibrationDao,
) {

    /** 신호 발생 시 즉시 기록 */
    suspend fun recordSignal(
        ticker: String,
        algoResults: Map<String, AlgoResult>,
        date: String,
    ) = withContext(Dispatchers.IO) {
        val entries = algoResults.map { (name, r) ->
            SignalHistoryEntity(
                ticker = ticker,
                algoName = name,
                rawScore = r.score.toDouble(),
                date = date,
            )
        }
        dao.insertSignalHistory(entries)
    }

    /** 알고리즘별 적중률 조회 (최근 N일) */
    suspend fun getAccuracy(
        ticker: String,
        windowDays: Int = 60,
    ): Map<String, AlgoAccuracyRow> = withContext(Dispatchers.IO) {
        val since = System.currentTimeMillis() - windowDays * 86_400_000L
        dao.getAccuracyByAlgo(ticker, since).associateBy { it.algoName }
    }

    /** 신호 이력 관찰 */
    fun observeHistory(ticker: String): Flow<List<SignalHistoryEntity>> =
        dao.observeAllHistory(ticker, limit = 120)

    /** T+1 결과가 없는 레코드의 고유 ticker 목록 */
    suspend fun getPendingTickers(): List<String> = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 86_400_000L
        dao.getPendingTickers(cutoff)
    }

    /** T+1 결과 업데이트 */
    suspend fun updateT1Outcomes(
        priceMap: Map<Long, Float>,
    ) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 86_400_000L
        val pending = dao.getPendingT1Updates(cutoff)
        pending.forEach { entry ->
            val outcome = priceMap[entry.id] ?: return@forEach
            dao.updateT1(entry.id, outcome)
        }
    }

    /** T+5 결과 업데이트 */
    suspend fun updateT5Outcomes(
        priceMap: Map<Long, Float>,
    ) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 5 * 86_400_000L
        val pending = dao.getPendingT5Updates(cutoff)
        pending.forEach { entry ->
            val outcome = priceMap[entry.id] ?: return@forEach
            dao.updateT5(entry.id, outcome)
        }
    }

    /** T+20 결과 업데이트 */
    suspend fun updateT20Outcomes(
        priceMap: Map<Long, Float>,
    ) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 20 * 86_400_000L
        val pending = dao.getPendingT20Updates(cutoff)
        pending.forEach { entry ->
            val outcome = priceMap[entry.id] ?: return@forEach
            dao.updateT20(entry.id, outcome)
        }
    }

    /** 오래된 데이터 정리 */
    suspend fun pruneOldData(keepDays: Int = 365) = withContext(Dispatchers.IO) {
        val cutoff = java.time.LocalDate.now().minusDays(keepDays.toLong())
            .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
        dao.deleteOldHistory(cutoff)
    }
}
