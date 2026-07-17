package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.AiContextFetchResult
import com.tinyoscillator.feature.bearsignal.domain.repository.AiContextRepository
import java.time.LocalDate

/**
 * §4.7 "정세 업데이트" 조회 진입점 — 섹션 헤더의 명시적 버튼 액션에서만 호출된다(TASK_bear_signal_console.md
 * §4.7 "트리거 · 승인 · 저장 · 렌더" — 화면 진입/init에서 자동 호출 금지, §4.5 대원칙 계승). 조회
 * 자체는 어떤 상태도 변경하지 않는다([ApproveAiContextClaimsUseCase]만 상태를 바꾼다).
 */
class FetchAiContextUpdatesUseCase(private val repository: AiContextRepository) {

    suspend operator fun invoke(today: LocalDate = LocalDate.now()): Result<AiContextFetchResult> =
        repository.fetchUpdates(today)
}
