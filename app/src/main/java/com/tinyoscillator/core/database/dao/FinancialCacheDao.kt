package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tinyoscillator.core.database.entity.FinancialCacheEntity

@Dao
interface FinancialCacheDao {
    @Query("SELECT * FROM financial_cache WHERE ticker = :ticker")
    suspend fun get(ticker: String): FinancialCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: FinancialCacheEntity)

    @Query("DELETE FROM financial_cache WHERE ticker = :ticker")
    suspend fun delete(ticker: String)

    @Query("DELETE FROM financial_cache WHERE cachedAt < :threshold")
    suspend fun deleteExpired(threshold: Long)
}
