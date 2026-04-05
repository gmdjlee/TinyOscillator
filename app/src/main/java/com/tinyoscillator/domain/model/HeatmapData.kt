package com.tinyoscillator.domain.model

/**
 * 신호 강도 히트맵 데이터.
 * 행 = 종목, 열 = 날짜, 셀 = 앙상블 평균 점수 (0~1).
 */
data class HeatmapData(
    val tickers: List<String>,
    val tickerNames: Map<String, String>,
    val dates: List<Long>,
    val dateLabels: List<String>,
    val scores: Map<String, List<Float>>,
) {
    /** 특정 셀의 점수 (없으면 0.5 = 중립) */
    fun scoreAt(ticker: String, dateIndex: Int): Float =
        scores[ticker]?.getOrNull(dateIndex) ?: 0.5f
}
