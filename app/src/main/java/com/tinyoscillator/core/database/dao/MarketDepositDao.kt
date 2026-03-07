package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tinyoscillator.core.database.entity.MarketDepositEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MarketDepositDao {

    @Query("SELECT * FROM market_deposits ORDER BY date DESC LIMIT 730")
    fun getAllDeposits(): Flow<List<MarketDepositEntity>>

    @Query("SELECT * FROM market_deposits WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getByDateRange(startDate: String, endDate: String): Flow<List<MarketDepositEntity>>

    @Query("SELECT * FROM market_deposits ORDER BY date DESC LIMIT 1")
    suspend fun getLatestDeposit(): MarketDepositEntity?

    @Query("SELECT last_updated FROM market_deposits ORDER BY last_updated DESC LIMIT 1")
    suspend fun getLastUpdateTime(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(deposits: List<MarketDepositEntity>)

    @Query("DELETE FROM market_deposits")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM market_deposits")
    suspend fun getCount(): Int
}
