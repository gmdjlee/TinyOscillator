package com.tinyoscillator.feature.bearsignal.domain.model

import kotlinx.serialization.Serializable

/**
 * 스코어링 임계치 (TASK_bear_signal_console.md §3.0 — v1.2 임계치 외부화).
 *
 * `bear_thresholds.json`(리포지토리 루트 SSOT, 앱 사본 `app/src/main/assets/bear_thresholds.json`)과
 * 필드 1:1. [com.tinyoscillator.feature.bearsignal.domain.usecase.ComputeBearSignalUseCase]가 이
 * 값을 생성자 주입으로 받아 §3 스코어링에서 참조한다 — 코드에 숫자 임계치를 하드코딩하지 않는다.
 *
 * domain 계층은 안드로이드 무의존을 유지하되(§0 제약), kotlinx.serialization 코어 어노테이션은
 * 프레임워크 의존이 아니므로 허용한다(순수 Kotlin 라이브러리, JVM 단위테스트 대상).
 *
 * 값 변경은 리포트 근거(신영증권 「주도주의 물리학」 개정) + 골든 테스트 갱신 동반 시에만 허용된다
 * (§8 리스크 — "임계치 임의 변경" 대응).
 */
@Serializable
data class BearThresholds(
    val version: String,
    val basis: String,
    val s1: S1,
    val s2: S2,
    val s3: S3,
    val gate: Gate,
    val amp: Amp,
    val phase: PhaseCfg
) {
    /** §3.1 신호1(주변부 압착) 임계치 — 이탈국 수·낙폭 심도 경계 */
    @Serializable
    data class S1(
        val manyCountries: Int,
        val deepPct: Double,
        val deepeningPct: Double
    )

    /** §3.2 신호2(변동성 무게중심) 임계치 — ±3σ 하락/상승일 비율 경계 */
    @Serializable
    data class S2(
        val redLine: Double,
        val warnLine: Double,
        val watchLine: Double
    )

    /** §3.3 신호3(IPO 질) 임계치 — 적자 상장 비중 사다리 */
    @Serializable
    data class S3(
        val loss1: Double,
        val loss2: Double,
        val loss3: Double
    )

    /** §3.4 신호4(금리 방아쇠 GATE) 임계치 */
    @Serializable
    data class Gate(
        val critical: Double,
        val approach: Double,
        val creditWarn: Double
    )

    /** §3.5 증폭·집중(AMP) 임계치·가중치 */
    @Serializable
    data class Amp(
        val semiExport: Double,
        val kospi2: Double,
        val wSemi: Double,
        val wKospi2: Double,
        val wNoBuffer: Double,
        val cap: Double
    )

    /** §3.6 종합 국면 판정 임계치 — 선행 신호 합(lead) 경계 */
    @Serializable
    data class PhaseCfg(
        val leadOrange: Int,
        val leadAmber: Int
    )
}
