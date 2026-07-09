package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.IpoBigConsumption
import com.tinyoscillator.feature.bearsignal.domain.model.ManualFieldUpdate
import com.tinyoscillator.feature.bearsignal.domain.repository.BearSignalRepository

/**
 * [C]/[D] 등급 수동 입력 반영 UseCase (TASK.md §2, §4, Phase 3).
 *
 * BottomSheet(Stepper/SegmentedButton/Slider)에서 만든 [ManualFieldUpdate]를 검증한 뒤
 * [BearSignalRepository.updateManualInput]에 위임한다 — 실제 Room upsert(`source=MANUAL`,
 * `updatedAt=now` 부착)는 data 계층이 담당한다.
 *
 * 열거형처럼 취급되는 문자열 필드([ManualFieldUpdate.Big]/[ManualFieldUpdate.Dir])는 §3 스코어링이
 * 인식하는 값만 허용해 잘못된 문자열이 캐시에 저장되는 것을 사전 차단한다.
 */
class UpdateManualInputUseCase(
    private val repository: BearSignalRepository
) {
    suspend operator fun invoke(update: ManualFieldUpdate) {
        validate(update)
        repository.updateManualInput(update)
    }

    private fun validate(update: ManualFieldUpdate) {
        when (update) {
            is ManualFieldUpdate.Big -> require(update.value in IpoBigConsumption.VALID) {
                "big는 ${IpoBigConsumption.VALID} 중 하나여야 합니다: ${update.value}"
            }
            is ManualFieldUpdate.Dir -> require(update.value in VALID_DIRECTIONS) {
                "dir는 $VALID_DIRECTIONS 중 하나여야 합니다: ${update.value}"
            }
            is ManualFieldUpdate.MarketReturn -> require(update.r.size == 4) {
                "국가별 수익률은 4기간([-12M,-6M,-3M,-1M])이어야 합니다: 실제 ${update.r.size}개"
            }
            is ManualFieldUpdate.Loss,
            is ManualFieldUpdate.IssueRatio,
            is ManualFieldUpdate.Credit,
            is ManualFieldUpdate.Margin -> Unit
        }
    }

    companion object {
        private val VALID_DIRECTIONS = setOf(
            RateGateInputCalculator.DIR_EASE,
            RateGateInputCalculator.DIR_HOLD,
            RateGateInputCalculator.DIR_HIKE
        )
    }
}
