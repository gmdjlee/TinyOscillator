package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tinyoscillator.core.database.entity.SectorIndexCandleEntity

@Dao
interface SectorIndexCandleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(candles: List<SectorIndexCandleEntity>)

    @Query(
        """
        SELECT * FROM sector_index_candle
        WHERE code = :code
          AND date BETWEEN :fromDate AND :toDate
        ORDER BY date ASC
        """
    )
    suspend fun getRange(code: String, fromDate: String, toDate: String): List<SectorIndexCandleEntity>

    @Query("SELECT MAX(date) FROM sector_index_candle WHERE code = :code")
    suspend fun getLatestDate(code: String): String?

    @Query("SELECT MAX(cached_at) FROM sector_index_candle WHERE code = :code")
    suspend fun getLatestCachedAt(code: String): Long?

    @Query("DELETE FROM sector_index_candle WHERE code = :code")
    suspend fun deleteByCode(code: String)
}
