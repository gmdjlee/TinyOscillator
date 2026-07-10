package com.tinyoscillator.feature.bearsignal.data.remote

/**
 * 지수·ETF 일별 종가 한 건 — [StooqCsvClient]/[YahooChartApiClient] 공용 응답 모델
 * (날짜 오름차순 정렬은 호출자 책임).
 */
data class IndexDailyBar(val date: String, val close: Double)
