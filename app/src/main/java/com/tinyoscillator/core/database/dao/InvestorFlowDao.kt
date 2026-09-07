package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tinyoscillator.core.database.entity.InvestorFlowEntity

/**
 * [InvestorFlowEntity] DAO. 명세: docs/TASK_investor_volume_profile.md §6.4.
 */
@Dao
interface InvestorFlowDao {

    @Upsert
    suspend fun upsertAll(rows: List<InvestorFlowEntity>)

    @Query("SELECT * FROM investor_flow WHERE ticker = :ticker AND date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getRange(ticker: String, from: String, to: String): List<InvestorFlowEntity>

    @Query("SELECT MAX(date) FROM investor_flow WHERE ticker = :ticker")
    suspend fun latestDate(ticker: String): String?

    @Query("SELECT MIN(date) FROM investor_flow WHERE ticker = :ticker")
    suspend fun earliestDate(ticker: String): String?

    @Query("SELECT MAX(fetchedAt) FROM investor_flow WHERE ticker = :ticker")
    suspend fun latestFetchedAt(ticker: String): Long?

    @Query("DELETE FROM investor_flow WHERE ticker = :ticker AND date < :cutoff")
    suspend fun deleteOlderThan(ticker: String, cutoff: String)
}
