package com.tinyoscillator.feature.bearsignal.domain.model

import java.time.LocalDate

/**
 * §4.6 스냅샷 계약의 Room 영속화 도메인 표현 (TASK_bear_signal_console.md §6.1 Phase 3.5-1).
 *
 * [day]는 §4.6 `as_of`와 동일한 의미이며 Room Entity의 기본키다 — 같은 날 여러 차례
 * 저장해도(재계산·재수집) 최신 값으로 덮어쓴다(일 단위 upsert).
 *
 * [inputsJson]/[fieldMetaJson]은 §4.6 `inputs`/`field_meta` 서브 스키마를 그대로 직렬화한
 * 문자열이다(별도 규약 금지) — [com.tinyoscillator.feature.bearsignal.domain.usecase.BuildBearSnapshotUseCase] 참조.
 */
data class BearSnapshot(
    val day: String,
    val phase: BearPhase,
    val lead: Int,
    val gate: Int,
    val s1: Int,
    val s2: Int,
    val s3: Int,
    val amp: Double,
    val configBasis: String,
    val inputsJson: String,
    val fieldMetaJson: String,
    val createdAt: Long
)

/** 연속 스냅샷 사이에서 감지된 전이 종류 (§6.1 `DetectTransitionsUseCase` 의사코드). */
sealed interface TransitionKind

/** 국면(BearPhase) 전이 — 방향(개선/악화) 무관하게 값이 바뀌면 기록한다. */
data class PhaseChange(val from: BearPhase, val to: BearPhase) : TransitionKind

/** 방아쇠(gate) 레벨 **상승**만 기록한다(§6.1 의사코드 `b.gate > a.gate` — 하락은 전이로 취급하지 않음). */
data class GateAdvance(val gate: Int) : TransitionKind

/**
 * 국면·방아쇠 전이 로그 한 건 (§5.2 헤더 "TransitionLog", 예: "6/30 GREEN→AMBER · 방아쇠 경계 접근").
 *
 * @param asOf 전이가 감지된 스냅샷의 day("YYYY-MM-DD")
 */
data class Transition(val asOf: String, val kind: TransitionKind)

/**
 * §4.6 값 출처 우선순위 — `MANUAL 〉 SNAPSHOT 〉 AUTO 〉 BASELINE`(ordinal = 우선순위, 작을수록 강함).
 *
 * v1.2 Phase 3.5-1 구현 범위에서는 [BuildBearSnapshotUseCase]가 [AUTO]/[MANUAL]/[BASELINE] 3종만
 * 산출한다 — [SNAPSHOT](자기 앱 파이프라인이 "오늘" 직접 수집한 값, §4.6 예시의 `s2_up`)과 외부
 * 일반 [AUTO](예: FRED)의 세분화는 §4.5(웹/LLM 3-tier, Phase 4)에서 다룰 예정이며 현재 도메인
 * 모델([AutoIndicator.source]가 AUTO/MANUAL 2종만 구분)로는 구분할 근거가 없어 보류한다.
 */
enum class ValueSource { MANUAL, SNAPSHOT, AUTO, BASELINE }

/** §4.6 필드별 출처 메타데이터. */
data class FieldSource(
    val source: ValueSource,
    val asOf: LocalDate?,
    val origin: String?
)

/**
 * 세션 진입 시 "최신 스냅샷 as_of가 로컬(오늘)보다 오래되면 갱신 제안만 표면화"(§6.1) 결과.
 *
 * 이 값이 존재한다는 사실 자체가 "제안"이며, [com.tinyoscillator.feature.bearsignal.domain.usecase.EvaluateSnapshotFreshnessUseCase]는
 * 어떤 Room 캐시도 갱신하지 않는다(승인 원칙 — 자동 반영 금지, §4.5).
 */
data class SnapshotUpdateSuggestion(
    val latestAsOf: String,
    val today: String
)

/**
 * Sparkline 표시용 선행점수 백분율(0~100) — Room에는 원시 합계([BearSnapshot.lead], 0..9)만
 * 저장하고 표시 시점에만 환산해 중복 저장을 피한다.
 *
 * [com.tinyoscillator.feature.bearsignal.domain.usecase.ComputeBearSignalUseCase]가 이미 동일한
 * 산식(`Math.round(lead / 9.0 * 100)`)을 사용하며, 그 KDoc이 명시하듯 분모 `9.0`은 §3.0 주입
 * 대상이 아닌 구조 상수(선행 신호 합의 이론적 최댓값 s1+s2+s3=3+3+3)다 — 이 프로퍼티는 그 구조
 * 상수를 재사용할 뿐 새로운 임계치를 도입하지 않는다.
 */
val BearSnapshot.leadPct: Int
    get() = Math.round(lead / 9.0 * 100).toInt()
