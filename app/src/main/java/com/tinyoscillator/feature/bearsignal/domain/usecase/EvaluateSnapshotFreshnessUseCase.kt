package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.BearSnapshot
import com.tinyoscillator.feature.bearsignal.domain.model.SnapshotUpdateSuggestion
import java.time.LocalDate

/**
 * 세션 진입 시 "최신 스냅샷 `as_of`가 로컬(오늘)보다 오래되면 갱신 제안만 표면화"
 * (TASK_bear_signal_console.md §6.1 "state:latest 로드").
 *
 * 어떤 Room 캐시도 갱신하지 않는 순수 비교 함수다 — 반환값이 non-null이라는 사실 자체가
 * "제안"이며, 실제 갱신은 사용자가 명시적으로 승인(Pull-to-refresh 등 기존 경로)해야 한다
 * (승인 원칙 — §4.5 "자동 반영 금지"와 동일한 정신). 자동/수동 Room 캐시(WEB/MANUAL 출처·수동값)는
 * 이 UseCase가 건드리지 않으므로 그대로 유지된다.
 *
 * 안드로이드 의존성 0(JVM 단위테스트 대상).
 */
class EvaluateSnapshotFreshnessUseCase {

    /**
     * @param latest [SnapshotRepository.latestOrNull]의 결과(이력이 없으면 null)
     * @param today 기준 날짜(기본값 시스템 오늘 — 테스트에서는 고정값 주입)
     * @return 최신 스냅샷이 [today]보다 오래됐을 때만 제안 반환, 그 외(이력 없음/오늘과 같음/미래)는 null
     */
    operator fun invoke(latest: BearSnapshot?, today: LocalDate = LocalDate.now()): SnapshotUpdateSuggestion? {
        if (latest == null) return null
        val latestDay = LocalDate.parse(latest.day)
        if (!latestDay.isBefore(today)) return null
        return SnapshotUpdateSuggestion(latestAsOf = latest.day, today = today.toString())
    }
}
