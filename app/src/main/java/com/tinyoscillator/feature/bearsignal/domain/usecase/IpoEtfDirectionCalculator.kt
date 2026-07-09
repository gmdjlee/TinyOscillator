package com.tinyoscillator.feature.bearsignal.domain.usecase

/**
 * §3.3 신호3 `etf`(Renaissance IPO ETF, 티커 `IPO`) 방향 입력 계산 (TASK.md §4 "IPO ETF 방향").
 *
 * **계산 방식 결정 근거**: 프로토타입 `bear_signal_dashboard.jsx`(SSOT)는 `etf` 값을 UI에서 직접
 * 선택하는 SegmentedButton 상수일 뿐 산출 로직을 포함하지 않는다(P1의 `VolatilityStatsCalculator`와
 * 동일 상황). TASK.md §4 "최근 고점 대비 방향 up/flat/down" 문구에 따라, 최근
 * [LOOKBACK_TRADING_DAYS]거래일(~3개월) 종가 중 최고가 대비 현재 종가의 괴리율로 판정한다:
 * IPO 시장이 활황(고점 근접 유지)이면 "up", 고점에서 뚜렷이 밀려났으면 "down", 그 사이는 "flat".
 * 경계값은 구현 정의이며 리포트 근거가 쌓이면 조정 가능(현재는 v1 캘리브레이션 값).
 *
 * 순수 산술만 수행 — 안드로이드/IO 의존성 0 (JVM 단위테스트 대상).
 */
object IpoEtfDirectionCalculator {

    const val DIR_UP = "up"
    const val DIR_FLAT = "flat"
    const val DIR_DOWN = "down"

    /** "최근 고점" 산출 조회창(거래일, ~3개월) */
    const val LOOKBACK_TRADING_DAYS = 60

    /** 고점 대비 괴리율(%)이 이 값 이상이면 "up"(고점 근접 유지) */
    const val UP_THRESHOLD_PCT = -5.0

    /** 고점 대비 괴리율(%)이 이 값 이하이면 "down"(고점에서 뚜렷이 하락) */
    const val DOWN_THRESHOLD_PCT = -15.0

    /**
     * @param closesAscending 종가 리스트 — 오래된순(ascending) 정렬, 마지막 원소가 최신 종가
     * @return null — 데이터가 비었거나 최근 고점이 0 이하인 비정상 데이터인 경우
     */
    fun computeDirection(closesAscending: List<Double>): String? {
        if (closesAscending.isEmpty()) return null
        val window = if (closesAscending.size > LOOKBACK_TRADING_DAYS) {
            closesAscending.takeLast(LOOKBACK_TRADING_DAYS)
        } else {
            closesAscending
        }
        val recentHigh = window.max()
        if (recentHigh <= 0.0) return null
        val latest = closesAscending.last()
        val pct = (latest - recentHigh) / recentHigh * 100.0
        return when {
            pct >= UP_THRESHOLD_PCT -> DIR_UP
            pct <= DOWN_THRESHOLD_PCT -> DIR_DOWN
            else -> DIR_FLAT
        }
    }
}
