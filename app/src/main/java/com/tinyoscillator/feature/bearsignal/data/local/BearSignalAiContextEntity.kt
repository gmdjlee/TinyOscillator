package com.tinyoscillator.feature.bearsignal.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * §4.7 "정적 참조 콘텐츠 동적 갱신" 승인 캐시 (TASK_bear_signal_console.md §4.7, Phase 7-1, Room v37→v38).
 *
 * `bear_signal_auto_cache`/`bear_signal_manual_input`("현재값" 지표 캐시)와는 완전히 별개 계층이다 —
 * 이 테이블은 §3 스코어링에 유입되지 않는 **표시 전용(display-only) 텍스트 콘텐츠**(유형별 모니터링
 * 체크리스트·사례·역사 검증 "현재 비교" 문단)의 AI 갱신 승인 결과만 담는다.
 *
 * [sectionKey]가 기본키 — [com.tinyoscillator.feature.bearsignal.domain.model.AiContextSectionKey.key]
 * 값 하나당 최신 승인 스냅샷 1건만 유지한다(승인 시 upsert로 이전 승인을 대체 — 이력 보관은 범위 밖).
 *
 * @property sectionKey 갱신 대상 섹션(`type0_monitor` 등, [com.tinyoscillator.feature.bearsignal.domain.model.AiContextSectionKey.key] 문자열)
 * @property contentJson 승인된 [com.tinyoscillator.feature.bearsignal.domain.model.AiContextClaim] 목록을 직렬화한 JSON 배열
 * @property asOf 클레임 기준일("YYYY-MM-DD") — 여러 클레임이 섞여 있으면 최신값을 저장(표시용)
 * @property provider 수집 제공자("claude" | "gemini") — §4.5/§4.7 제공자 정책과 동일 규약
 * @property approvedAt 사용자 승인 시각(epoch millis)
 */
@Entity(tableName = "bear_signal_ai_context")
data class BearSignalAiContextEntity(
    @PrimaryKey
    @ColumnInfo(name = "section_key")
    val sectionKey: String,
    @ColumnInfo(name = "content_json")
    val contentJson: String,
    @ColumnInfo(name = "as_of")
    val asOf: String,
    val provider: String,
    @ColumnInfo(name = "approved_at")
    val approvedAt: Long
)
