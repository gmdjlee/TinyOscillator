package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.SuggestionFetchResult
import com.tinyoscillator.feature.bearsignal.domain.repository.SuggestionRepository

/**
 * §4.5 웹/LLM 제안 조회 진입점 — 사용자의 명시적 액션("AI 제안 가져오기")에서만 호출된다(화면
 * 진입/init에서 자동 호출 금지, §4.5 비용 고려). 조회 자체는 어떤 상태도 변경하지 않는다
 * ([com.tinyoscillator.feature.bearsignal.domain.usecase.ApplySuggestionUseCase]만 상태를 바꾼다).
 */
class FetchSuggestionsUseCase(private val repository: SuggestionRepository) {

    suspend operator fun invoke(current: BearSignalInputs): Result<SuggestionFetchResult> =
        repository.fetchSuggestions(current)
}
