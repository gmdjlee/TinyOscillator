package com.tinyoscillator.feature.bearsignal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalReportBaseline
import com.tinyoscillator.feature.bearsignal.domain.model.ManualBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.ManualFieldUpdate
import com.tinyoscillator.feature.bearsignal.domain.repository.BearSignalRepository
import com.tinyoscillator.feature.bearsignal.domain.usecase.MergeBearSignalInputsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.ResetToReportBaselineUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.UpdateManualInputUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [C]/[D] 등급 수동 입력 상태(Phase 3) — BottomSheet가 구독하는 최소 UI 상태.
 *
 * 전체 화면(헤더·카드·표) 조립은 Phase 4 범위다. 여기서는 "입력 → 상태 반영 → 병합 → persistence"
 * 파이프라인만 다룬다(TASK.md Phase 3 구현 항목 3).
 *
 * @param manual 현재 수동 오버라이드(미설정 필드는 null)
 * @param merged AUTO ⊕ MANUAL ⊕ 리포트 기준값 병합 결과 — 미리보기/검증용
 */
data class ManualInputUiState(
    val manual: ManualBearSignalInputs = ManualBearSignalInputs(),
    val merged: BearSignalInputs = BearSignalReportBaseline.toInputs()
)

@HiltViewModel
class ManualInputViewModel @Inject constructor(
    private val repository: BearSignalRepository,
    private val updateManualInputUseCase: UpdateManualInputUseCase,
    private val resetToReportBaselineUseCase: ResetToReportBaselineUseCase,
    private val mergeBearSignalInputsUseCase: MergeBearSignalInputsUseCase
) : ViewModel() {

    val uiState: StateFlow<ManualInputUiState> = combine(
        repository.observeAutoInputs(),
        repository.observeManualInputs(),
        repository.observeMarketReturns(),
        repository.observeManualMarketReturns()
    ) { auto, manual, marketsSnapshot, manualMarkets ->
        ManualInputUiState(
            manual = manual,
            merged = mergeBearSignalInputsUseCase(auto, manual, marketsSnapshot, manualMarkets)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManualInputUiState())

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

    private fun dispatch(update: ManualFieldUpdate) {
        viewModelScope.launch { updateManualInputUseCase(update) }
    }
}
