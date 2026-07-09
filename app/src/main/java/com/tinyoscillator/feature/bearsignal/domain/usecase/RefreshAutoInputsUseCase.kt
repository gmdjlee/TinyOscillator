package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.repository.BearSignalRepository

/**
 * [A]/[B] 등급 자동 지표 수집 UseCase (TASK.md §2).
 *
 * Phase 1은 [A] 등급 2지표([신호2 통계][VolatilityStatsCalculator] + [코스피 2사 비중][Kospi2Calculator])
 * 만 대상이며, 실제 KRX 수집·계산·Room 캐시 저장은 [BearSignalRepository] 구현체(data 계층)가
 * 담당한다. 본 UseCase는 프레젠테이션 계층에 얇은 진입점만 제공한다.
 */
class RefreshAutoInputsUseCase(
    private val repository: BearSignalRepository
) {
    suspend operator fun invoke(): Result<AutoBearSignalInputs> = repository.refreshAutoInputs()
}
