package com.tinyoscillator.feature.bearsignal.domain.usecase

/**
 * §3.1 신호1 도표48 국가별 지수 4기간(−12M/−6M/−3M/−1M) 누적수익률 계산
 * (TASK.md §4 "해외 19개 지수", "코스피 지수 시세").
 *
 * 달력월 대신 **거래일 근사**(1개월≈21영업일, 3개월≈63, 6개월≈126, 12개월≈252)를 사용한다 —
 * 해외지수마다 휴장일이 달라 달력 기준 매칭이 어렵고, 종가 시계열만으로 결정적으로 재현
 * 가능해야 하기 때문이다(구현 결정, §3 스코어링 SSOT와 무관 — 입력값 산출 규칙일 뿐).
 *
 * 순수 산술만 수행 — 안드로이드/IO 의존성 0 (JVM 단위테스트 대상).
 */
object GlobalIndexReturnCalculator {

    const val LOOKBACK_12M = 252
    const val LOOKBACK_6M = 126
    const val LOOKBACK_3M = 63
    const val LOOKBACK_1M = 21

    /** 최소 이 거래일 수(1개월분) 미만이면 전 기간 계산을 포기하고 전부 null 반환 */
    const val MIN_TRADING_DAYS = LOOKBACK_1M + 1

    /**
     * @param closesAscending 종가 리스트 — 오래된순(ascending) 정렬, 마지막 원소가 최신 종가
     * @return [−12M, −6M, −3M, −1M] 누적수익률(%) 리스트(순서 고정, TASK.md §2 `MarketReturns.r`와 동일).
     *   특정 기간의 기준 종가가 없으면(데이터 부족) 해당 원소만 null.
     */
    fun computeReturns(closesAscending: List<Double>): List<Double?> {
        if (closesAscending.size < MIN_TRADING_DAYS) return listOf(null, null, null, null)
        val latest = closesAscending.last()
        return listOf(
            returnFor(closesAscending, latest, LOOKBACK_12M),
            returnFor(closesAscending, latest, LOOKBACK_6M),
            returnFor(closesAscending, latest, LOOKBACK_3M),
            returnFor(closesAscending, latest, LOOKBACK_1M)
        )
    }

    private fun returnFor(closesAscending: List<Double>, latest: Double, lookbackTradingDays: Int): Double? {
        val idx = closesAscending.size - 1 - lookbackTradingDays
        if (idx < 0) return null
        val base = closesAscending[idx]
        if (base == 0.0) return null
        return (latest - base) / base * 100.0
    }
}
