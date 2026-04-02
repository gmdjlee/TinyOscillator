package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tinyoscillator.core.database.entity.FeatureCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeatureCacheDao {

    @Query("SELECT * FROM feature_cache WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): FeatureCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: FeatureCacheEntity)

    /** 만료된 엔트리 삭제 (computed_at + ttl_ms < now) */
    @Query("DELETE FROM feature_cache WHERE computed_at + ttl_ms < :nowMs")
    suspend fun evictExpired(nowMs: Long)

    /** 특정 종목의 모든 캐시 삭제 */
    @Query("DELETE FROM feature_cache WHERE ticker = :ticker")
    suspend fun evictByTicker(ticker: String)

    /** 전체 캐시 삭제 */
    @Query("DELETE FROM feature_cache")
    suspend fun evictAll()

    /** 캐시 엔트리 수 (실시간 관찰용) */
    @Query("SELECT COUNT(*) FROM feature_cache")
    fun count(): Flow<Int>
}
