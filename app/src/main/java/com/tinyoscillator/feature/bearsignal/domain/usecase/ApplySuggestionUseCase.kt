package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.Suggestion
import com.tinyoscillator.feature.bearsignal.domain.repository.BearSignalRepository

/**
 * §4.5 제안 승인 반영 — 개별/일괄 승인 시에만 `source=AUTO`로 Room에 쓴다(TASK_bear_signal_console.md
 * §4.5 항목3, §7 "승인 흐름"). 이 UseCase가 호출되지 않는 한(사용자가 명시적으로 승인 버튼을 누르지
 * 않는 한) 제안은 목록 표시 상태로만 남고 Room·병합 입력·판정 결과 어디에도 영향을 주지 않는다.
 */
class ApplySuggestionUseCase(private val repository: BearSignalRepository) {

    /** 제안 하나를 승인한다. */
    suspend operator fun invoke(suggestion: Suggestion, now: Long = System.currentTimeMillis()) {
        repository.applySuggestion(suggestion.field, suggestion.nextValue, now)
    }

    /** 여러 제안을 일괄 승인한다(§5.2 "일괄 승인"). */
    suspend fun applyAll(suggestions: List<Suggestion>, now: Long = System.currentTimeMillis()) {
        suggestions.forEach { repository.applySuggestion(it.field, it.nextValue, now) }
    }
}
