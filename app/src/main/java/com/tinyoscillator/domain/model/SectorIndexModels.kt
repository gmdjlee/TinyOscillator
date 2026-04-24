package com.tinyoscillator.domain.model

/** 업종지수 분류 레벨 */
enum class SectorLevel(val code: Int, val label: String) {
    LARGE(1, "대분류"),
    MIDDLE(2, "중분류"),
    SMALL(3, "소분류"),
    ;

    companion object {
        fun fromCode(code: Int): SectorLevel =
            entries.firstOrNull { it.code == code } ?: SMALL
    }
}

/** UI용 업종 항목 */
data class SectorIndex(
    val code: String,
    val name: String,
    val level: SectorLevel,
    val parentCode: String?,
)

/** 업종지수 캔들 (일봉) */
data class SectorIndexCandle(
    /** YYYYMMDD */
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
)

/** 업종지수 현재 시세 스냅샷 (inquire-daily-indexchartprice output1) */
data class SectorIndexQuote(
    val currentPrice: Double,
    val priorDiff: Double,
    val priorRatePercent: Double,
)

/** 업종지수 차트 조회 결과 (헤더 + 캔들 리스트) */
data class SectorIndexChart(
    val code: String,
    val name: String,
    val quote: SectorIndexQuote?,
    val candles: List<SectorIndexCandle>,
)

enum class SectorChartPeriod(val apiCode: String, val label: String, val days: Int) {
    DAILY("D", "일", 120),
    WEEKLY("W", "주", 365),
    MONTHLY("M", "월", 365 * 3),
    ;

    companion object {
        fun default(): SectorChartPeriod = DAILY
    }
}
