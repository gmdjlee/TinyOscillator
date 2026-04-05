package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tinyoscillator.core.database.entity.CalibrationStateEntity
import com.tinyoscillator.core.database.entity.SignalHistoryEntity
import com.tinyoscillator.domain.model.AlgoAccuracyRow

data class TickerAvgScore(val ticker: String, @androidx.room.ColumnInfo(name = "avg_score") val avgScore: Double)

@Dao
interface CalibrationDao {

    // ─── Signal History ───

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignalHistory(entries: List<SignalHistoryEntity>)

    @Query("""
        SELECT * FROM signal_history
        WHERE algo_name = :algoName AND outcome_return IS NOT NULL
        ORDER BY date ASC
    """)
    suspend fun getCompletedHistory(algoName: String): List<SignalHistoryEntity>

    @Query("""
        SELECT * FROM signal_history
        WHERE outcome_return IS NULL AND date <= :cutoffDate
    """)
    suspend fun getPendingOutcomes(cutoffDate: String): List<SignalHistoryEntity>

    @Query("UPDATE signal_history SET outcome_return = :returnValue WHERE id = :id")
    suspend fun updateOutcome(id: Long, returnValue: Double)

    @Query("DELETE FROM signal_history WHERE date < :cutoffDate")
    suspend fun deleteOldHistory(cutoffDate: String)

    @Query("SELECT COUNT(*) FROM signal_history WHERE algo_name = :algoName AND outcome_return IS NOT NULL")
    suspend fun getCompletedCount(algoName: String): Int

    // ─── T+N Outcome Updates ───

    @Query("SELECT * FROM signal_history WHERE outcome_t1 IS NULL AND created_at < :cutoffMs ORDER BY created_at ASC LIMIT 50")
    suspend fun getPendingT1Updates(cutoffMs: Long): List<SignalHistoryEntity>

    @Query("SELECT * FROM signal_history WHERE outcome_t5 IS NULL AND created_at < :cutoffMs ORDER BY created_at ASC LIMIT 50")
    suspend fun getPendingT5Updates(cutoffMs: Long): List<SignalHistoryEntity>

    @Query("SELECT * FROM signal_history WHERE outcome_t20 IS NULL AND created_at < :cutoffMs ORDER BY created_at ASC LIMIT 50")
    suspend fun getPendingT20Updates(cutoffMs: Long): List<SignalHistoryEntity>

    @Query("UPDATE signal_history SET outcome_t1 = :outcome WHERE id = :id")
    suspend fun updateT1(id: Long, outcome: Float)

    @Query("UPDATE signal_history SET outcome_t5 = :outcome WHERE id = :id")
    suspend fun updateT5(id: Long, outcome: Float)

    @Query("UPDATE signal_history SET outcome_t20 = :outcome WHERE id = :id")
    suspend fun updateT20(id: Long, outcome: Float)

    // ─── Accuracy Aggregation ───

    @Query("""
        SELECT algo_name AS algoName,
               COUNT(*) as total,
               SUM(CASE WHEN (raw_score > 0.5 AND outcome_t1 > 0)
                          OR (raw_score < 0.5 AND outcome_t1 < 0)
                        THEN 1 ELSE 0 END) as hits
        FROM signal_history
        WHERE ticker = :ticker
          AND outcome_t1 IS NOT NULL
          AND created_at > :sinceMs
        GROUP BY algo_name
    """)
    suspend fun getAccuracyByAlgo(ticker: String, sinceMs: Long): List<AlgoAccuracyRow>

    @Query("""
        SELECT * FROM signal_history
        WHERE ticker = :ticker
        ORDER BY created_at DESC LIMIT :limit
    """)
    fun observeAllHistory(ticker: String, limit: Int = 120): kotlinx.coroutines.flow.Flow<List<SignalHistoryEntity>>

    @Query("SELECT DISTINCT ticker FROM signal_history WHERE outcome_t1 IS NULL AND created_at < :cutoffMs")
    suspend fun getPendingTickers(cutoffMs: Long): List<String>

    // ─── Heatmap ───

    @Query("""
        SELECT AVG(raw_score) FROM signal_history
        WHERE ticker = :ticker AND date = :date
    """)
    suspend fun getAverageScoreForDay(ticker: String, date: String): Double?

    @Query("SELECT DISTINCT date FROM signal_history WHERE ticker = :ticker AND date >= :sinceDate ORDER BY date ASC")
    suspend fun getDistinctDates(ticker: String, sinceDate: String): List<String>

    /** 종목별 최신 평균 신호 점수 (스크리너용) */
    @Query("""
        SELECT ticker, AVG(raw_score) as avg_score FROM signal_history
        WHERE date = (SELECT MAX(date) FROM signal_history sh2 WHERE sh2.ticker = signal_history.ticker)
        GROUP BY ticker
    """)
    suspend fun getLatestAvgScoresByTicker(): List<TickerAvgScore>

    // ─── Calibration State ───

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCalibrationState(state: CalibrationStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCalibrationStates(states: List<CalibrationStateEntity>)

    @Query("SELECT * FROM calibration_state")
    suspend fun getAllCalibrationStates(): List<CalibrationStateEntity>

    @Query("SELECT * FROM calibration_state WHERE algo_name = :algoName")
    suspend fun getCalibrationState(algoName: String): CalibrationStateEntity?

    @Query("DELETE FROM calibration_state")
    suspend fun clearAllCalibrationStates()
}
