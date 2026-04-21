package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tinyoscillator.core.database.entity.AnalysisCacheEntity

@Dao
interface AnalysisCacheDao {

    @Query(
        """
        SELECT * FROM analysis_cache
        WHERE ticker = :ticker AND date >= :startDate AND date <= :endDate
        ORDER BY date ASC
        """
    )
    suspend fun getByTickerDateRange(
        ticker: String,
        startDate: String,
        endDate: String
    ): List<AnalysisCacheEntity>

    @Query("SELECT MAX(date) FROM analysis_cache WHERE ticker = :ticker")
    suspend fun getLatestDate(ticker: String): String?

    @Query("SELECT MIN(date) FROM analysis_cache WHERE ticker = :ticker")
    suspend fun getEarliestDate(ticker: String): String?

    @Query("SELECT DISTINCT ticker FROM analysis_cache WHERE date = :date")
    suspend fun getTickersForDate(date: String): List<String>

    @Query("DELETE FROM analysis_cache WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("SELECT * FROM analysis_cache ORDER BY ticker, date")
    suspend fun getAll(): List<AnalysisCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<AnalysisCacheEntity>)

    @Query("DELETE FROM analysis_cache WHERE ticker = :ticker AND date < :cutoffDate")
    suspend fun deleteOlderThan(ticker: String, cutoffDate: String)

    /** 전체 종목에 대해 cutoffDate 미만 row를 일괄 삭제 (주기적 TTL 청소용) */
    @Query("DELETE FROM analysis_cache WHERE date < :cutoffDate")
    suspend fun deleteAllOlderThan(cutoffDate: String): Int

    @Query("DELETE FROM analysis_cache WHERE ticker = :ticker")
    suspend fun deleteByTicker(ticker: String)

    /** 최근 N건의 일별 데이터 조회 (날짜 오름차순) — 통계 엔진용 */
    @Query(
        """
        SELECT * FROM analysis_cache
        WHERE ticker = :ticker
        ORDER BY date DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentByTicker(ticker: String, limit: Int): List<AnalysisCacheEntity>

    @Query("SELECT COUNT(*) FROM analysis_cache WHERE ticker = :ticker AND volume = 0 AND open_price = 0 AND close_price > 0")
    suspend fun countMissingOhlcv(ticker: String): Int

    /** Atomically insert new entries and clean up old ones. */
    @Transaction
    suspend fun insertAndCleanup(entries: List<AnalysisCacheEntity>, ticker: String, cutoffDate: String) {
        insertAll(entries)
        deleteOlderThan(ticker, cutoffDate)
    }
}
