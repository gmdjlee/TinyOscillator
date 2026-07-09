package com.tinyoscillator.feature.bearsignal.domain.usecase

/**
 * §3.4 금리 방아쇠 `dir`(한국은행 기준금리 방향) 입력 계산 (TASK.md §4 "한은 기준금리").
 *
 * `rate`(§3.4 `scoreGate`의 기준금리 상단 입력)는 미 연준 목표금리 상단(FRED `DFEDTARU`)을 그대로
 * 사용한다 — 리포트 기준값(RATE=3.75, 2026.6.30)이 한국은행 기준금리 실측 범위(2020년대 최대
 * 3.5%)보다 "임계 4.5%=진짜 긴축"(§3.4 주석) 문턱에 근접한 미 연준 금리대와 정합적이기 때문이다.
 * `dir`은 한국은행 자체 정책 방향을 반영해 결정타 판정을 보조한다(§3.4 "credit"/"margin"과 함께
 * 부차 가중 요인).
 *
 * 순수 산술만 수행 — 안드로이드/IO 의존성 0 (JVM 단위테스트 대상).
 */
object RateGateInputCalculator {

    /** §3.4 `dir` 값 상수 — [부록 A]/`scoreGate` 문자열과 동일해야 한다(SSOT). */
    const val DIR_HIKE = "hike"
    const val DIR_HOLD = "hold"
    const val DIR_EASE = "ease"

    /**
     * 최신·직전 한국은행 기준금리 값으로 정책 방향을 판정한다.
     *
     * @param latest 최신 기준금리(%)
     * @param previous 직전 기준금리(%)
     * @return "hike" — 인상, "ease" — 인하, "hold" — 동결
     */
    fun computeDirection(latest: Double, previous: Double): String = when {
        latest > previous -> DIR_HIKE
        latest < previous -> DIR_EASE
        else -> DIR_HOLD
    }
}
