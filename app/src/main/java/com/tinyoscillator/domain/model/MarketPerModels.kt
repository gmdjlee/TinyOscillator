package com.tinyoscillator.domain.model

/**
 * 시장 지수 PER 추이 차트 데이터
 */
data class MarketPerChartData(
    val market: String,
    val rows: List<MarketPerRow>
)

data class MarketPerRow(
    val date: String,           // "yyyyMMdd"
    val closeIndex: Double,
    val per: Double,
    val pbr: Double,
    val dividendYield: Double
)

enum class MarketPerDateRange(val label: String, val days: Long) {
    THREE_MONTHS("3M", 90),
    SIX_MONTHS("6M", 180),
    ONE_YEAR("1Y", 365),
    TWO_YEARS("2Y", 730)
}
