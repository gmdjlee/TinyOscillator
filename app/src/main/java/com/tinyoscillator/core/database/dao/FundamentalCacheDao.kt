package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tinyoscillator.core.database.entity.FundamentalCacheEntity

@Dao
interface FundamentalCacheDao {

    @Query(
        """
        SELECT * FROM fundamental_cache
        WHERE ticker = :ticker AND date >= :startDate AND date <= :endDate
        ORDER BY date ASC
        """
    )
    suspend fun getByTickerDateRange(
        ticker: String,
        startDate: String,
        endDate: String
    ): List<FundamentalCacheEntity>

    @Query("SELECT MAX(date) FROM fundamental_cache WHERE ticker = :ticker")
    suspend fun getLatestDate(ticker: String): String?

    @Query("SELECT * FROM fundamental_cache ORDER BY ticker, date")
    suspend fun getAll(): List<FundamentalCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<FundamentalCacheEntity>)

    @Query("DELETE FROM fundamental_cache WHERE ticker = :ticker AND date < :cutoffDate")
    suspend fun deleteOlderThan(ticker: String, cutoffDate: String)

    /** 최근 N건의 펀더멘털 데이터 조회 (날짜 오름차순) — 통계 엔진용 */
    @Query(
        """
        SELECT * FROM fundamental_cache
        WHERE ticker = :ticker
        ORDER BY date DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentByTicker(ticker: String, limit: Int): List<FundamentalCacheEntity>

    /** 가장 최근 펀더멘털 데이터 1건 */
    @Query(
        """
        SELECT * FROM fundamental_cache
        WHERE ticker = :ticker
        ORDER BY date DESC
        LIMIT 1
        """
    )
    suspend fun getLatestByTicker(ticker: String): FundamentalCacheEntity?

    @Transaction
    suspend fun insertAndCleanup(entries: List<FundamentalCacheEntity>, ticker: String, cutoffDate: String) {
        insertAll(entries)
        deleteOlderThan(ticker, cutoffDate)
    }
}
