package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.repository.BearSignalRepository

/**
 * 리포트 기준값(부록 C, 2026.6.30) 리셋 UseCase (TASK.md §5.4 "리셋 → 리포트 기준값", 부록 B #8).
 *
 * **범위 결정**: 이 리셋은 사용자가 BottomSheet로 편집한 **수동 오버라이드만** 지운다([A]/[B] 등급
 * 자동 수집 캐시는 건드리지 않는다). 근거:
 * - `loss`/`big`/`credit`/`margin`/미커버 해외지수는 자동 수집이 존재하지 않는 필드이므로, 수동
 *   오버라이드를 지우면 [MergeBearSignalInputsUseCase]가 곧바로 리포트 기준값으로 폴백한다 —
 *   즉 이 필드들에 한해서는 "리셋 = 리포트 기준값 재현"이 정확히 성립한다.
 * - `dir`(정책 방향)처럼 자동 수집도 있는 필드는, 수동 오버라이드를 지운 뒤 최신 자동 수집값이
 *   다시 반영된다 — 살아있는 [A]/[B] 데이터를 리셋 버튼이 파괴하지 않도록 하기 위함이다(최소
 *   부작용 원칙). 전체를 문자 그대로 리포트 스냅샷과 동일하게 만들고 싶다면 자동 갱신 이력이
 *   없는 상태(신규 설치 등)에서 리셋해야 한다 — 이 경우 완전히 [BearSignalReportBaseline]과
 *   일치한다(단위테스트로 검증).
 */
class ResetToReportBaselineUseCase(
    private val repository: BearSignalRepository
) {
    suspend operator fun invoke() = repository.resetToReportBaseline()
}
