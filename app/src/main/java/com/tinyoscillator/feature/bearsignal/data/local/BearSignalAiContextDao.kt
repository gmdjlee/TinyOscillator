package com.tinyoscillator.feature.bearsignal.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/**
 * [BearSignalAiContextEntity]의 DAO (TASK_bear_signal_console.md §4.7, Phase 7-1).
 *
 * 갱신 패턴: 승인 미리보기(P7-3)에서 사용자가 "적용"을 누르면 [upsert]로 섹션별 최신 승인 콘텐츠를
 * 대체한다. 조회 패턴: 화면 렌더 시 [getBySectionKey]로 캐시 존재 여부를 확인 — 존재하면 AI 배지 +
 * as_of + STALE 오버레이, 없으면 정적 fallback([com.tinyoscillator.feature.bearsignal.domain.model.BearSignalStaticContent])
 * 그대로 렌더한다(§4.7 "렌더" 절).
 */
@Dao
interface BearSignalAiContextDao {

    @Upsert
    suspend fun upsert(entity: BearSignalAiContextEntity)

    @Query("SELECT * FROM bear_signal_ai_context WHERE section_key = :sectionKey")
    suspend fun getBySectionKey(sectionKey: String): BearSignalAiContextEntity?

    @Query("SELECT * FROM bear_signal_ai_context")
    suspend fun getAll(): List<BearSignalAiContextEntity>

    @Query("DELETE FROM bear_signal_ai_context")
    suspend fun clearAll()
}
