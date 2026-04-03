package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tinyoscillator.core.database.entity.MacroIndicatorEntity

@Dao
interface MacroDao {

    @Query("SELECT * FROM macro_indicator WHERE indicator_key = :key ORDER BY year_month DESC LIMIT :limit")
    suspend fun getByIndicator(key: String, limit: Int = 30): List<MacroIndicatorEntity>

    @Query("SELECT * FROM macro_indicator WHERE year_month = :yearMonth")
    suspend fun getByMonth(yearMonth: String): List<MacroIndicatorEntity>

    @Query("SELECT * FROM macro_indicator ORDER BY year_month DESC")
    suspend fun getAll(): List<MacroIndicatorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MacroIndicatorEntity>)

    @Query("SELECT MAX(updated_at) FROM macro_indicator")
    suspend fun lastUpdatedAt(): Long?

    @Query("SELECT COUNT(*) FROM macro_indicator")
    suspend fun count(): Int

    @Query("DELETE FROM macro_indicator WHERE year_month < :cutoffYm")
    suspend fun deleteOlderThan(cutoffYm: String)
}
