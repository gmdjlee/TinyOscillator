package com.tinyoscillator.feature.bearsignal.domain.repository

import com.tinyoscillator.feature.bearsignal.domain.model.BearSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * BearSignal 스냅샷 이력 Repository (TASK_bear_signal_console.md §6.1 Phase 3.5-1).
 *
 * [com.tinyoscillator.feature.bearsignal.domain.repository.BearSignalRepository]([A]/[B]/[C]/[D]
 * 등급 "현재값" 캐시)와는 별개의 책임 — 이 인터페이스는 일자별 스코어링 스냅샷 **이력**을
 * 영속화해 국면·방아쇠 전이 감지([com.tinyoscillator.feature.bearsignal.domain.usecase.DetectTransitionsUseCase])와
 * 세션 진입 시 최신 스냅샷 신선도 확인([com.tinyoscillator.feature.bearsignal.domain.usecase.EvaluateSnapshotFreshnessUseCase])에
 * 사용된다.
 */
interface SnapshotRepository {

    /**
     * 스냅샷을 upsert한다 — [BearSnapshot.day]가 기본키이므로 같은 날 다시 호출하면 최신 값으로
     * 덮어쓴다("오늘" 하루에 여러 번 갱신·재계산해도 이력은 일 단위로만 쌓인다).
     */
    suspend fun upsertToday(snapshot: BearSnapshot)

    /** 가장 최근(day 기준 내림차순) 스냅샷 스트림 — 이력이 없으면 null. */
    fun observeLatest(): Flow<BearSnapshot?>

    /** [from, to] 구간(day, "YYYY-MM-DD", 양끝 포함) 스냅샷 스트림 — day 오름차순(Sparkline/TransitionLog 입력 순서). */
    fun observeRange(from: String, to: String): Flow<List<BearSnapshot>>

    /** 가장 최근 스냅샷 1회 조회 — 세션 진입 시 신선도 확인([EvaluateSnapshotFreshnessUseCase])에 사용. */
    suspend fun latestOrNull(): BearSnapshot?
}
