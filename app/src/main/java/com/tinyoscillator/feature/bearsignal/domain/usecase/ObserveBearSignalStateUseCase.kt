package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalReportBaseline
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalResult
import com.tinyoscillator.feature.bearsignal.domain.model.ManualBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.ManualMarketReturn
import com.tinyoscillator.feature.bearsignal.domain.model.MarketReturnsSnapshot
import com.tinyoscillator.feature.bearsignal.domain.repository.BearSignalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * 화면 조립용 종합 상태 스트림 UseCase (TASK.md §2 아키텍처, Phase 4).
 *
 * Room 캐시(자동 수집 + 수동 오버라이드 + 국가별 수익률 자동/수동)를 구독해
 * [MergeBearSignalInputsUseCase]로 [BearSignalInputs]를 조립하고, [ComputeBearSignalUseCase]로
 * [BearSignalResult]를 산출한다. §3 스코어링 SSOT는 그대로([ComputeBearSignalUseCase]) — 이
 * UseCase는 4개의 Room Flow + 사용자가 선택한 기간(§5.3 FilterChip)을 하나의 화면 상태로 합성하는
 * 조립 계층일 뿐이다. 안드로이드 의존성 0(Repository 인터페이스만 참조, JVM 단위테스트 대상).
 */
class ObserveBearSignalStateUseCase(
    private val repository: BearSignalRepository,
    private val mergeBearSignalInputsUseCase: MergeBearSignalInputsUseCase,
    private val computeBearSignalUseCase: ComputeBearSignalUseCase
) {

    /**
     * @param inputs 병합된 스코어링 입력([MergeBearSignalInputsUseCase] 산출물)
     * @param result 종합 판정 결과([ComputeBearSignalUseCase] 산출물)
     * @param auto Room 캐시 자동 수집값([A]/[B] 등급, 없으면 null — 배지·소스 표시용)
     * @param manual Room 캐시 수동 오버라이드([C]/[D] 등급, 미설정 필드는 내부 null)
     * @param marketsSnapshot 국가별 지수 자동 수집 스냅샷(없으면 null)
     * @param manualMarkets 국가별 지수 수동 오버라이드 목록
     */
    data class State(
        val inputs: BearSignalInputs,
        val result: BearSignalResult,
        val auto: AutoBearSignalInputs?,
        val manual: ManualBearSignalInputs,
        val marketsSnapshot: MarketReturnsSnapshot?,
        val manualMarkets: List<ManualMarketReturn>
    )

    /**
     * @param periodIdx 신호1 이탈 판정 기준 기간(§5.3 FilterChip 선택, 0=12M..3=1M).
     * 기본값은 리포트 기준값(부록 C)의 1M — 사용자가 선택하지 않아도 골든 케이스가 재현된다.
     */
    operator fun invoke(
        periodIdx: Flow<Int> = flowOf(BearSignalReportBaseline.PERIOD_IDX)
    ): Flow<State> = combine(
        repository.observeAutoInputs(),
        repository.observeManualInputs(),
        repository.observeMarketReturns(),
        repository.observeManualMarketReturns(),
        periodIdx
    ) { auto, manual, marketsSnapshot, manualMarkets, period ->
        val inputs = mergeBearSignalInputsUseCase(auto, manual, marketsSnapshot, manualMarkets, period)
        val result = computeBearSignalUseCase(inputs)
        State(
            inputs = inputs,
            result = result,
            auto = auto,
            manual = manual,
            marketsSnapshot = marketsSnapshot,
            manualMarkets = manualMarkets
        )
    }
}
