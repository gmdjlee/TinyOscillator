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

    // ── 국가별 지수 수익률(도표48, Phase 2) ─────────────────────────────

    @Query("SELECT * FROM bear_signal_country_return")
    fun observeCountryReturns(): Flow<List<BearSignalCountryReturnEntity>>

    @Query("SELECT * FROM bear_signal_country_return")
    suspend fun getCountryReturns(): List<BearSignalCountryReturnEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCountryReturns(entities: List<BearSignalCountryReturnEntity>)

    // ── 수동 오버라이드([C]/[D] 등급 스칼라, Phase 3) ─────────────────────

    @Query("SELECT * FROM bear_signal_manual_input")
    fun observeManualInputs(): Flow<List<BearSignalManualInputEntity>>

    @Query("SELECT * FROM bear_signal_manual_input")
    suspend fun getManualInputs(): List<BearSignalManualInputEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertManualInput(entity: BearSignalManualInputEntity)

    /** [com.tinyoscillator.feature.bearsignal.domain.usecase.ResetToReportBaselineUseCase]가 사용 — 수동 오버라이드 전체 삭제 */
    @Query("DELETE FROM bear_signal_manual_input")
    suspend fun clearManualInputs()

    // ── 국가별 지수 수익률 수동 오버라이드(Phase 3) ────────────────────────

    @Query("SELECT * FROM bear_signal_manual_country_return")
    fun observeManualCountryReturns(): Flow<List<BearSignalManualCountryReturnEntity>>

    @Query("SELECT * FROM bear_signal_manual_country_return")
    suspend fun getManualCountryReturns(): List<BearSignalManualCountryReturnEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertManualCountryReturn(entity: BearSignalManualCountryReturnEntity)

    @Query("DELETE FROM bear_signal_manual_country_return")
    suspend fun clearManualCountryReturns()
}
