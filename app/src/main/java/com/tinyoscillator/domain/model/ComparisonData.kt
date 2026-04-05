package com.tinyoscillator.domain.model

/**
 * 수익률 비교 차트용 데이터 모델.
 * 선택 종목·섹터 평균·KOSPI의 정규화 수익률 + 신호 강도 시계열.
 */
data class ComparisonSeries(
    val label:   String,
    val color:   Int,         // ARGB
    val returns: List<Float>, // 정규화 수익률 (시작=0f)
    val dates:   List<Long>,  // epoch ms
)

data class ComparisonData(
    val targetSeries:  ComparisonSeries,
    val sectorSeries:  ComparisonSeries?,
    val kospiSeries:   ComparisonSeries,
    val signalHistory: List<Pair<Long, Float>>,   // (date epoch ms, score)
    val alphaFinal:    Float,                     // 선택 기간 초과수익률
    val betaEstimate:  Float,                     // KOSPI 대비 베타
)

enum class ComparisonPeriod(val days: Int, val label: String) {
    THREE_MONTHS(91,  "3M"),
    SIX_MONTHS  (182, "6M"),
    ONE_YEAR    (365, "1Y"),
    CUSTOM      (-1,  "직접"),
}
