package com.tinyoscillator.feature.bearsignal.domain.repository

import com.tinyoscillator.feature.bearsignal.domain.model.AiContextClaim
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextFetchResult
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextSectionKey
import java.time.LocalDate

/**
 * §4.7 정적 참조 콘텐츠 동적 갱신 인터페이스 (TASK_bear_signal_console.md §4.7, Phase 7-2).
 *
 * §4.5 [com.tinyoscillator.feature.bearsignal.domain.repository.SuggestionRepository]와 동일한
 * "조회는 저장하지 않는다" 원칙 — [fetchUpdates]는 검증 통과 클레임(승인 대기)만 반환하고, 사용자가
 * 명시적으로 [approve]를 호출해야만 `bear_signal_ai_context`에 반영된다(§4.7 "승인 없이는 표시
 * 콘텐츠 불변"). 자동 fetch·워커 편입은 어떤 구현체에서도 금지된다(§4.5 대원칙 계승).
 */
interface AiContextRepository {

    /**
     * §4.7 그룹④⑤⑥(monitor/cases/history_current)을 조회한다. 저장하지 않는다.
     *
     * @param today STALE 판정 기준일(테스트에서만 오버라이드).
     * @return 최상위 [Result.failure]는 §4.5와 동일하게 Claude/Gemini API 키 미설정 등 호출 자체를
     * 시도할 수 없는 경우다. 개별 그룹 실패는 [AiContextFetchResult]의 그룹별 `error`로 표현된다.
     */
    suspend fun fetchUpdates(today: LocalDate): Result<AiContextFetchResult>

    /**
     * 승인된 클레임을 `section_key`별로 묶어 `bear_signal_ai_context`에 upsert한다("적용" 버튼,
     * P7-3 몫). [provider]는 이 클레임들을 수집한 제공자("claude"|"gemini") — 한 번의
     * [fetchUpdates] 호출은 항상 단일 제공자를 쓰므로 호출측이 그대로 전달한다.
     */
    suspend fun approve(claims: List<AiContextClaim>, provider: String, now: Long)

    /** 저장된 섹션별 승인 클레임을 로드한다(P7-3 렌더 소비) — 승인 캐시가 없는 섹션은 결과 맵에서 생략된다. */
    suspend fun getApproved(): Map<AiContextSectionKey, List<AiContextClaim>>
}
