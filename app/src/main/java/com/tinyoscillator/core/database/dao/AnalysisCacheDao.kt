package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<AnalysisCacheEntity>)

    @Query("DELETE FROM analysis_cache WHERE ticker = :ticker AND date < :cutoffDate")
    suspend fun deleteOlderThan(ticker: String, cutoffDate: String)

    @Query("DELETE FROM analysis_cache WHERE ticker = :ticker")
    suspend fun deleteByTicker(ticker: String)
}
