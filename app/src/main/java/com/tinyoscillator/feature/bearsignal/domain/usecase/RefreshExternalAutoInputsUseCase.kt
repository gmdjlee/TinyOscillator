package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.repository.BearSignalRepository

/**
 * [B] 등급 스칼라 자동 지표 수집 UseCase (TASK.md §2, §4, Phase 2).
 *
 * 관세청 무역통계(semi/buffer), FRED(rate), ECOS(dir), Stooq(etf) — 실제 수집·지표별 best-effort
 * 폴백은 [BearSignalRepository] 구현체(data 계층)가 담당한다. 본 UseCase는 프레젠테이션 계층에
 * 얇은 진입점만 제공한다. [RefreshAutoInputsUseCase]([A] 등급)와 별도 진입점으로 분리해, [A] 등급
 * 갱신 실패가 [B] 등급 조회를 막지 않도록(또는 그 반대) 한다.
 */
class RefreshExternalAutoInputsUseCase(
    private val repository: BearSignalRepository
) {
    suspend operator fun invoke(): Result<AutoBearSignalInputs> = repository.refreshExternalAutoInputs()
}
