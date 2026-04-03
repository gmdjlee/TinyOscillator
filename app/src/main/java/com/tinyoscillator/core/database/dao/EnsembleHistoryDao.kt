package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tinyoscillator.core.database.entity.EnsembleHistoryEntity

@Dao
interface EnsembleHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: EnsembleHistoryEntity)

    /** 결과가 채워진 전체 이력 (시간순) */
    @Query("""
        SELECT * FROM ensemble_history
        WHERE actual_outcome IS NOT NULL
        ORDER BY date ASC
    """)
    suspend fun getCompletedHistory(): List<EnsembleHistoryEntity>

    /** 특정 종목의 결과가 채워진 이력 */
    @Query("""
        SELECT * FROM ensemble_history
        WHERE ticker = :ticker AND actual_outcome IS NOT NULL
        ORDER BY date ASC
    """)
    suspend fun getCompletedHistoryByTicker(ticker: String): List<EnsembleHistoryEntity>

    /** 특정 레짐의 결과가 채워진 이력 */
    @Query("""
        SELECT * FROM ensemble_history
        WHERE regime_id = :regimeId AND actual_outcome IS NOT NULL
        ORDER BY date ASC
    """)
    suspend fun getCompletedHistoryByRegime(regimeId: String): List<EnsembleHistoryEntity>

    /** 결과가 아직 미확인인 이력 (T+1 업데이트 대상) */
    @Query("""
        SELECT * FROM ensemble_history
        WHERE actual_outcome IS NULL AND date <= :cutoffDate
    """)
    suspend fun getPendingOutcomes(cutoffDate: String): List<EnsembleHistoryEntity>

    /** 결과 업데이트 */
    @Query("""
        UPDATE ensemble_history
        SET actual_outcome = :outcome, next_day_return = :nextDayReturn
        WHERE ticker = :ticker AND date = :date
    """)
    suspend fun updateOutcome(ticker: String, date: String, outcome: Int, nextDayReturn: Double)

    /** 결과가 채워진 전체 샘플 수 */
    @Query("SELECT COUNT(*) FROM ensemble_history WHERE actual_outcome IS NOT NULL")
    suspend fun getCompletedCount(): Int

    /** 최근 학습 데이터 증가분 (마지막 학습 이후 추가된 샘플 수) */
    @Query("SELECT COUNT(*) FROM ensemble_history WHERE actual_outcome IS NOT NULL AND created_at > :sinceTimestamp")
    suspend fun getNewSamplesSince(sinceTimestamp: Long): Int

    /** 오래된 이력 삭제 */
    @Query("DELETE FROM ensemble_history WHERE date < :cutoffDate")
    suspend fun deleteOldHistory(cutoffDate: String)

    /** 전체 삭제 */
    @Query("DELETE FROM ensemble_history")
    suspend fun deleteAll()
}
