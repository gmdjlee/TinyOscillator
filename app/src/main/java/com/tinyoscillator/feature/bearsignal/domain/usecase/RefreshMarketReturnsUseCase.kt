package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.MarketReturnsSnapshot
import com.tinyoscillator.feature.bearsignal.domain.repository.BearSignalRepository

/**
 * 도표48 국가별 지수 4기간 수익률 자동 수집 UseCase (TASK.md §2, §4 "해외 19개 지수", Phase 2).
 *
 * 코스피(kotlin_krx) + 해외지수(Stooq 커버 대상, [com.tinyoscillator.feature.bearsignal.domain.model.GlobalIndexRegistry])
 * 수집·지수별 best-effort 폴백은 [BearSignalRepository] 구현체(data 계층)가 담당한다. 본 UseCase는
 * 프레젠테이션 계층에 얇은 진입점만 제공한다.
 */
class RefreshMarketReturnsUseCase(
    private val repository: BearSignalRepository
) {
    suspend operator fun invoke(): Result<MarketReturnsSnapshot> = repository.refreshMarketReturns()
}
