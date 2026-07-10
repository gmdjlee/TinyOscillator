package com.tinyoscillator.feature.bearsignal.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.network.NetworkUtils
import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalReportBaseline
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalResult
import com.tinyoscillator.feature.bearsignal.domain.model.ManualBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.ManualFieldUpdate
import com.tinyoscillator.feature.bearsignal.domain.model.ManualMarketReturn
import com.tinyoscillator.feature.bearsignal.domain.model.MarketReturnsSnapshot
import com.tinyoscillator.feature.bearsignal.domain.usecase.ComputeBearSignalUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.ObserveBearSignalStateUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshAutoInputsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshExternalAutoInputsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshMarketReturnsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.ResetToReportBaselineUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.UpdateManualInputUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private val DEFAULT_INPUTS: BearSignalInputs = BearSignalReportBaseline.toInputs()
private val DEFAULT_RESULT: BearSignalResult = ComputeBearSignalUseCase()(DEFAULT_INPUTS)

/**
 * BearSignalScreen(Phase 4) 화면 상태 — TASK.md §5.2 7섹션 조립에 필요한 모든 값을 담는다.
 *
 * @param periodIdx 현재 선택된 신호1 판정 기간(§5.3 FilterChip) — [inputs.periodIdx]와 항상 동일.
 * @param lastUpdatedAt 자동/수동 전 지표 중 가장 최근 `updatedAt` (§5.2 섹션7 "전체 최신 갱신일").
 * @param errorMessage 최근 갱신 실패 메시지(있으면 Snackbar로 1회성 노출).
 * @param isLoading Room 캐시(자동/수동/국가별 수익률 4-Flow)의 최초 방출 전 상태 — 이 구간만 shimmer
 * 스켈레톤을 노출한다(§5.4 "성능: shimmer 로딩"). `stateIn`의 초기값(`BearSignalUiState()`)에서만
 * `true`이고, [ObserveBearSignalStateUseCase] 결과가 한 번이라도 합성되면 항상 `false`로 고정된다.
 * @param isOffline 네트워크 미연결로 자동 갱신을 시도하지 않고 건너뛴 상태(§5.4 "오프라인 우선 렌더").
 * 캐시 데이터는 이미 [inputs]/[result]에 반영돼 있으므로 화면은 그대로 렌더하고 배너로만 안내한다.
 */
data class BearSignalUiState(
    val inputs: BearSignalInputs = DEFAULT_INPUTS,
    val result: BearSignalResult = DEFAULT_RESULT,
    val auto: AutoBearSignalInputs? = null,
    val manual: ManualBearSignalInputs = ManualBearSignalInputs(),
    val marketsSnapshot: MarketReturnsSnapshot? = null,
    val manualMarkets: List<ManualMarketReturn> = emptyList(),
    val periodIdx: Int = BearSignalReportBaseline.PERIOD_IDX,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val lastUpdatedAt: Long? = null,
    val isLoading: Boolean = true,
    val isOffline: Boolean = false
)

@HiltViewModel
class BearSignalViewModel @Inject constructor(
    private val observeBearSignalStateUseCase: ObserveBearSignalStateUseCase,
    private val refreshAutoInputsUseCase: RefreshAutoInputsUseCase,
    private val refreshExternalAutoInputsUseCase: RefreshExternalAutoInputsUseCase,
    private val refreshMarketReturnsUseCase: RefreshMarketReturnsUseCase,
    private val updateManualInputUseCase: UpdateManualInputUseCase,
    private val resetToReportBaselineUseCase: ResetToReportBaselineUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val periodIdx = MutableStateFlow(BearSignalReportBaseline.PERIOD_IDX)
    private val isRefreshing = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val isOffline = MutableStateFlow(false)

    val uiState: StateFlow<BearSignalUiState> = combine(
        observeBearSignalStateUseCase(periodIdx),
        isRefreshing,
        errorMessage,
        isOffline
    ) { state, refreshing, error, offline ->
        BearSignalUiState(
            inputs = state.inputs,
            result = state.result,
            auto = state.auto,
            manual = state.manual,
            marketsSnapshot = state.marketsSnapshot,
            manualMarkets = state.manualMarkets,
            periodIdx = state.inputs.periodIdx,
            isRefreshing = refreshing,
            errorMessage = error,
            lastUpdatedAt = lastUpdatedAt(state),
            // Room 4-Flow가 최소 한 번 합성됐다는 뜻 — 초기값(BearSignalUiState())에서만 true로 남는다.
            isLoading = false,
            isOffline = offline
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BearSignalUiState())

    /**
     * Pull-to-refresh(§5.4) → [A]/[B] 자동 지표 + 국가별 지수 3계열을 병렬 아님(직렬, 소스 독립) 갱신.
     * 오프라인이면 API 호출을 시도하지 않고 즉시 [isOffline]만 설정한다(§5.4 "오프라인 우선 렌더" —
     * 화면은 이미 Room 캐시로 렌더돼 있으므로 배너로만 안내, 기존 [NetworkUtils] 관례 재사용).
     */
    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch {
            isRefreshing.value = true
            if (!NetworkUtils.isNetworkAvailable(context)) {
                isOffline.value = true
                isRefreshing.value = false
                return@launch
            }
            isOffline.value = false
            val failed = mutableListOf<String>()
            refreshAutoInputsUseCase().onFailure { failed += "자동 지표" }
            refreshExternalAutoInputsUseCase().onFailure { failed += "외부 지표" }
            refreshMarketReturnsUseCase().onFailure { failed += "해외 지수" }
            errorMessage.value = if (failed.isEmpty()) null else "일부 갱신 실패: ${failed.joinToString()}"
            isRefreshing.value = false
        }
    }

    /** §5.3 FilterChip 기간 선택 — 신호1 판정에 즉시 반영 */
    fun selectPeriod(idx: Int) {
        periodIdx.value = idx
    }

    /** §5.3 국가별 수익률 인라인 편집(수동 갱신) */
    fun updateMarketReturn(name: String, r: List<Double?>) {
        dispatch(ManualFieldUpdate.MarketReturn(name, r))
    }

    fun updateLoss(value: Double) = dispatch(ManualFieldUpdate.Loss(value))
    fun updateBig(value: String) = dispatch(ManualFieldUpdate.Big(value))
    fun updateIssueRatio(value: Double) = dispatch(ManualFieldUpdate.IssueRatio(value))
    fun updateCredit(value: Double) = dispatch(ManualFieldUpdate.Credit(value))
    fun updateMargin(value: Boolean) = dispatch(ManualFieldUpdate.Margin(value))
    fun updateDir(value: String) = dispatch(ManualFieldUpdate.Dir(value))

    /** 리포트 기준값(2026.6.30)으로 리셋 — 수동 오버라이드 삭제(§5.4 "리셋", 부록 B #8) */
    fun reset() {
        viewModelScope.launch { resetToReportBaselineUseCase() }
    }

    fun clearError() {
        errorMessage.value = null
    }

    private fun dispatch(update: ManualFieldUpdate) {
        viewModelScope.launch { updateManualInputUseCase(update) }
    }

    private fun lastUpdatedAt(state: ObserveBearSignalStateUseCase.State): Long? {
        val timestamps = buildList {
            state.auto?.let { auto ->
                add(auto.up3.updatedAt)
                add(auto.down3.updatedAt)
                add(auto.up4.updatedAt)
                add(auto.down4.updatedAt)
                add(auto.kospi2.updatedAt)
                auto.semi?.updatedAt?.let(::add)
                auto.buffer?.updatedAt?.let(::add)
                auto.rate?.updatedAt?.let(::add)
                auto.dir?.updatedAt?.let(::add)
                auto.etf?.updatedAt?.let(::add)
            }
            state.manual.loss?.updatedAt?.let(::add)
            state.manual.big?.updatedAt?.let(::add)
            state.manual.issueRatio?.updatedAt?.let(::add)
            state.manual.credit?.updatedAt?.let(::add)
            state.manual.margin?.updatedAt?.let(::add)
            state.manual.dir?.updatedAt?.let(::add)
            state.marketsSnapshot?.markets?.forEach { add(it.updatedAt) }
            state.manualMarkets.forEach { add(it.updatedAt) }
        }
        return timestamps.maxOrNull()
    }
}
