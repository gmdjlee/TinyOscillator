package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tinyoscillator.core.database.entity.MarketOscillatorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MarketOscillatorDao {

    @Query("SELECT * FROM market_oscillator WHERE market = :market ORDER BY date DESC LIMIT 730")
    fun getMarketData(market: String): Flow<List<MarketOscillatorEntity>>

    @Query("SELECT * FROM market_oscillator WHERE market = :market AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getDataByDateRange(market: String, startDate: String, endDate: String): Flow<List<MarketOscillatorEntity>>

    @Query("SELECT * FROM market_oscillator WHERE market = :market ORDER BY date DESC LIMIT 1")
    suspend fun getLatestData(market: String): MarketOscillatorEntity?

    @Query("SELECT COUNT(*) FROM market_oscillator WHERE market = :market")
    suspend fun getDataCount(market: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(data: List<MarketOscillatorEntity>)

    @Query("DELETE FROM market_oscillator WHERE market = :market AND date < date('now', '-' || :keepDays || ' days')")
    suspend fun deleteOldData(market: String, keepDays: Int)

    @Query("DELETE FROM market_oscillator")
    suspend fun deleteAll()

    @Transaction
    suspend fun insertAndCleanup(data: List<MarketOscillatorEntity>, market: String, keepDays: Int) {
        insertAll(data)
        deleteOldData(market, keepDays)
    }
}
