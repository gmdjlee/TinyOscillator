package com.tinyoscillator.feature.bearsignal.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * [AiContextClaim]의 JSON 직렬화 표현 (TASK_bear_signal_console.md §4.7, Phase 7-2) —
 * [com.tinyoscillator.feature.bearsignal.data.local.BearSignalAiContextEntity.contentJson]에 섹션별
 * 배열로 저장된다(§4.7 "저장" 절 "content_json(승인 클레임 배열)"). [AiContextClaim.sourceDate]는
 * ISO-8601 문자열로 직렬화한다([SnapshotFieldMetaEntry]의 `as_of` 관례와 동일 — kotlinx.serialization은
 * `LocalDate`를 기본 지원하지 않으므로 문자열 경유).
 *
 * 필드명은 §4.7 클레임 스키마(TASK_bear_signal_console.md §4.7 라인 337~345)의 프롬프트-JSON 키와
 * 1:1 매칭해 향후 재파싱·디버깅 시 혼동을 줄인다. 순수 데이터 클래스(안드로이드 의존성 0) —
 * 도메인 ↔ payload 변환은 [com.tinyoscillator.feature.bearsignal.data.mapper.AiContextClaimMapper]가 담당한다.
 */
@Serializable
data class AiContextClaimPayload(
    @SerialName("section_key") val sectionKey: String,
    val text: String,
    val type: String,
    @SerialName("source_url") val sourceUrl: String,
    @SerialName("source_title") val sourceTitle: String,
    @SerialName("source_date") val sourceDate: String,
    val quote: String?
)
