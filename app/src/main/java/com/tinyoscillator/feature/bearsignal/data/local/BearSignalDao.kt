package com.tinyoscillator.feature.bearsignal.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * [BearSignalAutoCacheEntity]의 DAO.
 *
 * 갱신 패턴: [com.tinyoscillator.feature.bearsignal.data.repository.BearSignalRepositoryImpl]가
 * 자동 수집 성공 시 [upsertAll]로 지표별 upsert(전체 교체 아님 — 미수집 지표는 기존 값 유지).
 * 조회 패턴: [observeAutoCache]를 구독해 캐시 우선 렌더(오프라인 우선, TASK.md §5.4).
 */
@Dao
interface BearSignalDao {

    @Query("SELECT * FROM bear_signal_auto_cache")
    fun observeAutoCache(): Flow<List<BearSignalAutoCacheEntity>>

    @Query("SELECT * FROM bear_signal_auto_cache")
    suspend fun getAutoCache(): List<BearSignalAutoCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<BearSignalAutoCacheEntity>)
}
