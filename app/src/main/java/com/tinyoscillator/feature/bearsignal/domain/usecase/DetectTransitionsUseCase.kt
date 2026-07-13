package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.BearSnapshot
import com.tinyoscillator.feature.bearsignal.domain.model.GateAdvance
import com.tinyoscillator.feature.bearsignal.domain.model.PhaseChange
import com.tinyoscillator.feature.bearsignal.domain.model.Transition

/**
 * 연속 스냅샷에서 국면·방아쇠 전이를 산출한다 (TASK_bear_signal_console.md §6.1 의사코드 1:1).
 *
 * - 인접한 두 스냅샷의 [BearSnapshot.phase]가 다르면 [PhaseChange] 기록(방향 무관 — GREEN→AMBER든
 *   AMBER→GREEN이든 "국면이 바뀌었다"는 사실 자체가 로그 대상).
 * - [BearSnapshot.gate]가 **상승**했을 때만 [GateAdvance] 기록(하락은 전이로 취급하지 않음 —
 *   §6.1 의사코드 `if (b.gate > a.gate)`).
 * - 한 인덱스에서 두 조건이 동시에 성립하면 같은 `asOf`로 전이 2건이 함께 반환된다.
 *
 * [series]는 day 오름차순으로 정렬돼 있다고 가정한다([SnapshotRepository.observeRange] 계약).
 * 순수 함수, 안드로이드/IO 의존성 0(JVM 단위테스트 대상).
 */
class DetectTransitionsUseCase {

    operator fun invoke(series: List<BearSnapshot>): List<Transition> = buildList {
        for (i in 1 until series.size) {
            val a = series[i - 1]
            val b = series[i]
            if (a.phase != b.phase) add(Transition(b.day, PhaseChange(a.phase, b.phase)))
            if (b.gate > a.gate) add(Transition(b.day, GateAdvance(b.gate)))
        }
    }
}
