package com.tinyoscillator.feature.bearsignal.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * [BearSnapshotEntity]의 DAO (TASK_bear_signal_console.md §6.1 Phase 3.5-1 코드 블록 그대로).
 *
 * [com.tinyoscillator.feature.bearsignal.data.local.BearSignalDao]("현재값" 자동/수동 캐시)와
 * 분리된 전용 Dao — 이 Dao는 일자별 이력을 다룬다.
 */
@Dao
interface BearSnapshotDao {

    @Upsert
    suspend fun upsert(e: BearSnapshotEntity)

    @Query("SELECT * FROM bear_snapshot ORDER BY day DESC LIMIT 1")
    fun observeLatest(): Flow<BearSnapshotEntity?>

    @Query("SELECT * FROM bear_snapshot WHERE day BETWEEN :from AND :to ORDER BY day ASC")
    fun observeRange(from: String, to: String): Flow<List<BearSnapshotEntity>>

    @Query("SELECT * FROM bear_snapshot ORDER BY day DESC LIMIT 1")
    suspend fun latest(): BearSnapshotEntity?
}
