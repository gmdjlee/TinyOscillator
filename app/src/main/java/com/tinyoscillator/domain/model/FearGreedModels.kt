package com.tinyoscillator.domain.model

/**
 * Fear & Greed 차트 표시용 데이터.
 */
data class FearGreedChartData(
    val market: String,
    val rows: List<FearGreedRow>
)

data class FearGreedRow(
    val date: String,
    val indexValue: Double,
    val fearGreedValue: Double,
    val oscillator: Double
)

/**
 * Fear & Greed 상태 판정 — fearGreedValue 기준 (0.0~1.0).
 */
fun Double.toFearGreedStatus(): String = when {
    this >= 0.8 -> "극단적 탐욕"
    this >= 0.6 -> "탐욕"
    this >= 0.4 -> "중립"
    this >= 0.2 -> "공포"
    else -> "극단적 공포"
}

/**
 * 시장 DeMark TD용 데이터 행.
 * 기존 DemarkTDRow의 marketCapTril 대신 indexValue(시장 지수)를 사용한다.
 */
data class MarketDemarkRow(
    val date: String,
    val indexValue: Double,
    val tdSellCount: Int,
    val tdBuyCount: Int
)

data class MarketDemarkChartData(
    val market: String,
    val rows: List<MarketDemarkRow>,
    val periodType: DemarkPeriodType
)
