package com.tinyoscillator.domain.model

/** DeMark TD Setup 지표 일별/주봉 행 */
data class DemarkTDRow(
    val date: String,           // "yyyyMMdd"
    val closePrice: Int,        // 종가 (원)
    val marketCapTril: Double,  // 시가총액 (조원)
    val tdSellCount: Int,       // 매도 피로 카운트 (0~13+)
    val tdBuyCount: Int         // 매수 피로 카운트 (0~13+)
)

/** DeMark TD 차트 표시용 데이터 */
data class DemarkTDChartData(
    val stockName: String,
    val ticker: String,
    val rows: List<DemarkTDRow>,
    val periodType: DemarkPeriodType
)

/** 일봉/주봉 기간 타입 */
enum class DemarkPeriodType(val label: String) {
    DAILY("일봉"),
    WEEKLY("주봉")
}
