package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tinyoscillator.core.database.entity.FearGreedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FearGreedDao {

    @Query("SELECT * FROM fear_greed_index WHERE market = :market ORDER BY date DESC LIMIT 730")
    fun getAllByMarket(market: String): Flow<List<FearGreedEntity>>

    @Query("SELECT * FROM fear_greed_index WHERE market = :market AND date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun getByMarketAndDateRange(market: String, startDate: String, endDate: String): Flow<List<FearGreedEntity>>

    @Query("SELECT * FROM fear_greed_index WHERE market = :market ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentData(market: String, limit: Int): List<FearGreedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(indices: List<FearGreedEntity>)

    @Query("DELETE FROM fear_greed_index")
    suspend fun deleteAll()

    @Query("DELETE FROM fear_greed_index WHERE market = :market")
    suspend fun deleteByMarket(market: String)

    @Query("SELECT COUNT(*) FROM fear_greed_index WHERE market = :market")
    suspend fun getCountByMarket(market: String): Int

    @Query("SELECT MAX(date) FROM fear_greed_index WHERE market = :market")
    suspend fun getLatestDate(market: String): String?

    @Query("SELECT MAX(lastUpdated) FROM fear_greed_index WHERE market = :market")
    suspend fun getLastUpdateTime(market: String): Long?

    @Query("SELECT * FROM fear_greed_index ORDER BY date ASC")
    suspend fun getAllList(): List<FearGreedEntity>
}
