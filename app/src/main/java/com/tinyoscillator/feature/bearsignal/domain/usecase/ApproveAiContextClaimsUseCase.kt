package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.AiContextClaim
import com.tinyoscillator.feature.bearsignal.domain.repository.AiContextRepository

/**
 * §4.7 클레임 승인 반영 — 개별/일괄 승인 시에만 `bear_signal_ai_context`에 쓴다(TASK_bear_signal_console.md
 * §4.7 "저장" 절). 이 UseCase가 호출되지 않는 한(사용자가 명시적으로 승인 버튼을 누르지 않는 한)
 * 조회된 클레임은 미리보기 상태로만 남고 Room·화면 표시 콘텐츠 어디에도 영향을 주지 않는다.
 */
class ApproveAiContextClaimsUseCase(private val repository: AiContextRepository) {

    suspend operator fun invoke(
        claims: List<AiContextClaim>,
        provider: String,
        now: Long = System.currentTimeMillis()
    ) {
        repository.approve(claims, provider, now)
    }
}
