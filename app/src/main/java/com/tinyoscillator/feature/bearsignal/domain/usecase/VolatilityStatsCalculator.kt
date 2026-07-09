package com.tinyoscillator.feature.bearsignal.domain.usecase

import kotlin.math.sqrt

/**
 * §3.2 신호2 입력 계산 — 코스피 일별 종가에서 ±3σ/±4σ 급변일 카운트를 산출한다 (TASK.md §3.2, §4).
 *
 * **계산 방식 결정 근거** (TASK.md §3.2 주석 "직전 6M 코스피 일별 로그·단순수익률에서 μ, σ 산출"
 * 중 택1 지시에 따름): 근거 프로토타입 `bear_signal_dashboard.jsx`(SSOT)를 확인한 결과 `s2_up`/
 * `s2_down` 값은 UI Stepper로 수동 입력받는 상수(`s2_up: 14, s2_down: 12`)일 뿐, σ 계산 로직 자체를
 * 포함하지 않는다(2026-07-09 확인, jsx L82~84·700~706). 즉 계산부의 SSOT가 존재하지 않으므로 본
 * 구현이 계산 방식을 최초로 결정하며, 검증·재현이 쉬운 **단순수익률**을 채택한다:
 * ```
 * r_t = (close_t − close_{t-1}) / close_{t-1} × 100   (%)
 * ```
 * 표준편차는 **표본표준편차**(ddof=1, `n−1`로 나눔 — Excel `STDEV`/pandas `.std()` 기본값과 동일한
 * 금융권 관행)를 사용한다.
 *
 * **경계 정의**: `|r_t|`가 `threshold·σ`를 **초과(strict `>`)**할 때만 카운트한다. 즉 값이 정확히
 * `threshold·σ`에 도달한 날은 "무게중심 이탈"로 집계하지 않는다 — 구현 정의이며
 * `VolatilityStatsCalculatorTest`의 경계 케이스로 고정한다.
 *
 * 순수 산술만 수행 — 안드로이드/IO 의존성 0 (JVM 단위테스트 대상).
 */
object VolatilityStatsCalculator {

    /**
     * ±3σ/±4σ 카운트 산출에 필요한 최소 수익률 표본 수.
     * 약 1개월 영업일(20일) 미만은 표준편차 신뢰도가 낮아 계산을 포기한다.
     */
    const val MIN_RETURNS = 20

    private const val SIGMA_3 = 3.0
    private const val SIGMA_4 = 4.0

    data class Result(
        val up3: Int,
        val down3: Int,
        val up4: Int,
        val down4: Int,
        val mean: Double,
        val stdDev: Double,
        val sampleCount: Int
    )

    /**
     * @param closes 종가 리스트 — 오래된순(ascending) 정렬. 최근 ~6개월(130영업일) 권장(TASK.md §4).
     * @return null — 데이터 부족([MIN_RETURNS] 미만 수익률) 시
     */
    fun compute(closes: List<Double>): Result? {
        if (closes.size < 2) return null
        val returns = mutableListOf<Double>()
        for (i in 1 until closes.size) {
            val prev = closes[i - 1]
            if (prev == 0.0) continue // 종가 0(비정상 데이터)은 수익률 계산에서 제외
            returns.add((closes[i] - prev) / prev * 100.0)
        }
        return computeFromReturns(returns)
    }

    /**
     * [compute]의 내부 계산 단계 — 이미 산출된 일별 수익률 리스트에서 평균·표준편차·카운트를 계산한다.
     * `internal` 가시성으로 테스트에서 직접 검증할 수 있도록 노출(closes→returns 변환 없이 결정적
     * 샘플을 바로 투입하기 위함).
     */
    internal fun computeFromReturns(returns: List<Double>): Result? {
        if (returns.size < MIN_RETURNS) return null
        val mean = returns.average()
        val variance = returns.sumOf { (it - mean) * (it - mean) } / (returns.size - 1)
        val stdDev = sqrt(variance)
        if (stdDev <= 0.0 || stdDev.isNaN()) {
            // 전 기간 수익률이 동일(변동성 0) — 급변일 카운트는 0
            return Result(0, 0, 0, 0, mean, 0.0, returns.size)
        }
        val (up3, down3) = countBreaches(returns, mean, stdDev, SIGMA_3)
        val (up4, down4) = countBreaches(returns, mean, stdDev, SIGMA_4)
        return Result(
            up3 = up3,
            down3 = down3,
            up4 = up4,
            down4 = down4,
            mean = mean,
            stdDev = stdDev,
            sampleCount = returns.size
        )
    }

    /**
     * 평균·표준편차 대비 임계 배수(σ) 초과 카운트 (up=상승 초과, down=하락 초과).
     *
     * `strict >` 비교 — 정확히 `sigmaThreshold`에 도달한 값은 미포함(구현 정의, 테스트로 고정).
     * `mean`/`stdDev`를 파라미터로 받아 자기참조(값 자체가 통계량에 영향) 없이 경계값을 직접
     * 검증할 수 있도록 `internal`로 노출한다.
     */
    internal fun countBreaches(
        values: List<Double>,
        mean: Double,
        stdDev: Double,
        sigmaThreshold: Double
    ): Pair<Int, Int> {
        var up = 0
        var down = 0
        for (v in values) {
            val z = (v - mean) / stdDev
            if (z > sigmaThreshold) up++
            if (z < -sigmaThreshold) down++
        }
        return up to down
    }
}
