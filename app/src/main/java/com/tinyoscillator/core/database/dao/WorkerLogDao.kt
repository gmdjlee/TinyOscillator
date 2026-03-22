package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.tinyoscillator.core.database.entity.WorkerLogEntity

@Dao
interface WorkerLogDao {

    @Query("SELECT * FROM worker_logs WHERE worker_name = :name ORDER BY executed_at DESC LIMIT 1")
    suspend fun getLatestLog(name: String): WorkerLogEntity?

    @Query("SELECT * FROM worker_logs WHERE worker_name = :name ORDER BY executed_at DESC LIMIT :limit")
    suspend fun getRecentLogs(name: String, limit: Int = 20): List<WorkerLogEntity>

    @Query("SELECT * FROM worker_logs WHERE status = 'error' ORDER BY executed_at DESC LIMIT :limit")
    suspend fun getRecentErrors(limit: Int = 50): List<WorkerLogEntity>

    @Query("SELECT * FROM worker_logs ORDER BY executed_at DESC LIMIT :limit")
    suspend fun getAllRecentLogs(limit: Int = 50): List<WorkerLogEntity>

    @Insert
    suspend fun insert(log: WorkerLogEntity)

    @Query("DELETE FROM worker_logs WHERE executed_at < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long)

    @Transaction
    suspend fun insertAndCleanup(log: WorkerLogEntity, keepDays: Int = 30) {
        insert(log)
        deleteOlderThan(System.currentTimeMillis() - keepDays * 86_400_000L)
    }
}
