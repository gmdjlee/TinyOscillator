package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tinyoscillator.core.database.entity.MarketPerEntity

@Dao
interface MarketPerDao {

    @Query(
        """
        SELECT * FROM market_per
        WHERE market = :market AND date >= :startDate AND date <= :endDate
        ORDER BY date ASC
        """
    )
    suspend fun getByMarketDateRange(
        market: String,
        startDate: String,
        endDate: String
    ): List<MarketPerEntity>

    @Query("SELECT MAX(date) FROM market_per WHERE market = :market")
    suspend fun getLatestDate(market: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<MarketPerEntity>)

    @Query("DELETE FROM market_per WHERE market = :market AND date < :cutoffDate")
    suspend fun deleteOlderThan(market: String, cutoffDate: String)

    @Transaction
    suspend fun insertAndCleanup(entries: List<MarketPerEntity>, market: String, cutoffDate: String) {
        insertAll(entries)
        deleteOlderThan(market, cutoffDate)
    }
}
