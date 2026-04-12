package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tinyoscillator.core.database.entity.AnalysisSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: AnalysisSnapshotEntity): Long

    @Query("SELECT * FROM analysis_snapshots WHERE ticker = :ticker ORDER BY analyzed_at DESC")
    fun getByTicker(ticker: String): Flow<List<AnalysisSnapshotEntity>>

    @Query("SELECT * FROM analysis_snapshots WHERE ticker = :ticker ORDER BY analyzed_at DESC LIMIT :limit")
    suspend fun getRecentByTicker(ticker: String, limit: Int = 10): List<AnalysisSnapshotEntity>

    @Query("SELECT * FROM analysis_snapshots WHERE id = :id")
    suspend fun getById(id: Long): AnalysisSnapshotEntity?

    @Query("DELETE FROM analysis_snapshots WHERE ticker = :ticker AND id NOT IN (SELECT id FROM analysis_snapshots WHERE ticker = :ticker ORDER BY analyzed_at DESC LIMIT :keepCount)")
    suspend fun deleteOldSnapshots(ticker: String, keepCount: Int = 20)

    @Query("SELECT COUNT(*) FROM analysis_snapshots WHERE ticker = :ticker")
    suspend fun getCountByTicker(ticker: String): Int

    @Query("SELECT DISTINCT ticker, name FROM analysis_snapshots ORDER BY analyzed_at DESC")
    suspend fun getDistinctTickers(): List<SnapshotTickerInfo>

    data class SnapshotTickerInfo(val ticker: String, val name: String)
}
