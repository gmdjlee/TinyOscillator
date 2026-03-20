package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tinyoscillator.core.database.entity.AnalysisHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisHistoryDao {

    @Query("SELECT * FROM analysis_history ORDER BY last_analyzed_at DESC LIMIT :limit")
    fun getRecent(limit: Int = 30): Flow<List<AnalysisHistoryEntity>>

    @Query("DELETE FROM analysis_history WHERE ticker = :ticker")
    suspend fun deleteByTicker(ticker: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AnalysisHistoryEntity)

    @Query("SELECT * FROM analysis_history ORDER BY last_analyzed_at DESC")
    suspend fun getAll(): List<AnalysisHistoryEntity>

    @Query("SELECT COUNT(*) FROM analysis_history")
    suspend fun getCount(): Int

    @Query(
        """
        DELETE FROM analysis_history WHERE id IN (
            SELECT id FROM analysis_history
            ORDER BY last_analyzed_at ASC
            LIMIT :excess
        )
        """
    )
    suspend fun deleteOldest(excess: Int)

    @Transaction
    suspend fun saveWithFifo(ticker: String, name: String, maxHistory: Int) {
        deleteByTicker(ticker)
        insert(AnalysisHistoryEntity(ticker = ticker, name = name, lastAnalyzedAt = System.currentTimeMillis()))
        val count = getCount()
        if (count > maxHistory) {
            deleteOldest(count - maxHistory)
        }
    }
}
