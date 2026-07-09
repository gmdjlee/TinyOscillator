package com.tinyoscillator.feature.bearsignal.domain.model

/**
 * 국가별 지수 수익률(도표48) 자동 수집 커버리지 (TASK.md §4 "해외 19개 지수", §8 리스크).
 *
 * KIS 해외시세는 롱테일 거래소(RTS·SET·JKSE 등) 커버리지가 제한적이므로, 자동 수집이 가능한
 * 지수만 [AUTO]로 표시하고 나머지는 [MANUAL_REQUIRED]로 표시해 수동 입력을 요청한다(§1.1 각주1).
 */
enum class MarketCoverage { AUTO, MANUAL_REQUIRED }

/**
 * 국가별 지수 수익률 자동 수집 한 행 — [com.tinyoscillator.feature.bearsignal.domain.model.MarketReturns]에
 * 출처·갱신시각·커버리지 상태를 더한 데이터 계층 산출물.
 *
 * @param r 누적수익률 [−12M, −6M, −3M, −1M] (%). 미수집(커버리지 없음/데이터 부족)은 null.
 * @param coverage [MarketCoverage.MANUAL_REQUIRED]면 [r]는 전부 null — UI가 수동 입력을 요청해야 함(§5.3).
 */
data class AutoMarketReturn(
    val name: String,
    val r: List<Double?>,
    val lead: Boolean = false,
    val coverage: MarketCoverage,
    val updatedAt: Long
) {
    fun toMarketReturns(): MarketReturns = MarketReturns(name = name, r = r, lead = lead)
}

/**
 * 도표48 국가별 수익률 자동 수집 스냅샷 — 20개 지수(코스피 포함) 전량을 담되, 자동 수집이
 * 불가능한 지수는 [MarketCoverage.MANUAL_REQUIRED]로 표시한다.
 */
data class MarketReturnsSnapshot(
    val markets: List<AutoMarketReturn>
) {
    /** §3.1 `analyzeMarkets` 입력용 변환 — 커버리지 상태와 무관하게 전량 포함(미수집은 r=null 그대로) */
    fun toMarketReturnsList(): List<MarketReturns> = markets.map { it.toMarketReturns() }

    /** 수동 입력이 필요한 지수명 목록(§5.3 "MANUAL 요청 플래그") */
    val manualRequiredNames: List<String>
        get() = markets.filter { it.coverage == MarketCoverage.MANUAL_REQUIRED }.map { it.name }
}
