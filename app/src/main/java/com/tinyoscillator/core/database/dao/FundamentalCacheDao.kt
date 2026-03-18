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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<FundamentalCacheEntity>)

    @Query("DELETE FROM fundamental_cache WHERE ticker = :ticker AND date < :cutoffDate")
    suspend fun deleteOlderThan(ticker: String, cutoffDate: String)

    @Transaction
    suspend fun insertAndCleanup(entries: List<FundamentalCacheEntity>, ticker: String, cutoffDate: String) {
        insertAll(entries)
        deleteOlderThan(ticker, cutoffDate)
    }
}
