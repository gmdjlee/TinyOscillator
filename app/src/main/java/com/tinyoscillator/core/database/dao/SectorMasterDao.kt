package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tinyoscillator.core.database.entity.SectorMasterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SectorMasterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sectors: List<SectorMasterEntity>)

    @Query("SELECT * FROM sector_master ORDER BY level ASC, code ASC")
    fun observeAll(): Flow<List<SectorMasterEntity>>

    @Query("SELECT * FROM sector_master WHERE level = :level ORDER BY code ASC")
    fun observeByLevel(level: Int): Flow<List<SectorMasterEntity>>

    @Query("SELECT * FROM sector_master WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): SectorMasterEntity?

    @Query("SELECT COUNT(*) FROM sector_master")
    suspend fun count(): Int

    @Query("SELECT MAX(last_updated) FROM sector_master")
    suspend fun lastUpdatedAt(): Long?

    @Query("DELETE FROM sector_master")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(sectors: List<SectorMasterEntity>) {
        deleteAll()
        insertAll(sectors)
    }
}
