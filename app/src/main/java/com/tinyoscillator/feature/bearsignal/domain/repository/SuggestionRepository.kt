package com.tinyoscillator.feature.bearsignal.domain.repository

import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.SuggestionFetchResult

/**
 * §4.5 웹/LLM 3-tier 수집 인터페이스 (TASK_bear_signal_console.md §4.5, Phase 4).
 *
 * Tier 1([A])·Tier 2([B])와 달리 Tier 3(웹/LLM)은 이 인터페이스가 반환하는 제안을 사용자가
 * 승인해야만 상태가 바뀐다([com.tinyoscillator.feature.bearsignal.domain.usecase.ApplySuggestionUseCase]
 * 경유) — [fetchSuggestions] 자체는 Room·병합 입력·판정 결과 어디에도 쓰기 작업을 하지 않는다.
 */
interface SuggestionRepository {

    /**
     * §4.5 그룹①②③(rate/dir, bigDeal/lossRatio, credit)을 조회해 제안 목록을 반환한다.
     *
     * @param current 병합된 현재 입력값(§4.5 "급변 재확인" 비교 기준 + 제안 패널 "현재값" 표시용).
     * @return 최상위 [Result.failure]는 Claude API 키 미설정/제공자 불일치 등 그룹 호출 자체를
     * 시도할 수 없는 경우다. 개별 그룹 호출 실패(네트워크·파싱)는 [Result.success]로 감싼
     * [SuggestionFetchResult]의 그룹별 `error` 필드로 표현된다(부분 실패 격리).
     */
    suspend fun fetchSuggestions(current: BearSignalInputs): Result<SuggestionFetchResult>
}
