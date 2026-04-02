package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tinyoscillator.core.database.entity.KospiIndexEntity
import com.tinyoscillator.core.database.entity.RegimeStateEntity

@Dao
interface RegimeDao {

    // ─── KOSPI Index ───

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKospiIndex(entities: List<KospiIndexEntity>)

    @Query("SELECT * FROM kospi_index ORDER BY date ASC")
    suspend fun getAllKospiIndex(): List<KospiIndexEntity>

    @Query("SELECT * FROM kospi_index ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentKospiIndex(limit: Int): List<KospiIndexEntity>

    @Query("SELECT COUNT(*) FROM kospi_index")
    suspend fun getKospiIndexCount(): Int

    @Query("SELECT MAX(date) FROM kospi_index")
    suspend fun getLatestKospiDate(): String?

    @Query("DELETE FROM kospi_index WHERE date < :cutoffDate")
    suspend fun deleteOldKospiIndex(cutoffDate: String)

    // ─── Regime State ───

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegimeState(state: RegimeStateEntity)

    @Query("SELECT * FROM regime_state WHERE id = 'market_regime'")
    suspend fun getRegimeState(): RegimeStateEntity?

    @Query("DELETE FROM regime_state")
    suspend fun clearRegimeState()
}
