package com.tinyoscillator.domain.model

// Room query result POJOs
data class AmountRankingRow(
    val stock_ticker: String,
    val stock_name: String,
    val totalAmount: Long,
    val etfCount: Int,
    val maxWeight: Double? = null,
    val avgWeight: Double? = null
)

data class CashDepositRow(
    val date: String,
    val totalAmount: Long,
    val etfCount: Int
)

data class StockInEtfRow(
    val etf_ticker: String,
    val etfName: String,
    val stock_ticker: String,
    val stock_name: String,
    val weight: Double?,
    val shares: Long,
    val amount: Long,
    val date: String
)

data class StockSearchResult(
    val stock_ticker: String,
    val stock_name: String,
    val market: String? = null,
    val sector: String? = null
)

// Computed models
enum class ChangeType { NEW, REMOVED, INCREASED, DECREASED }
enum class WeightTrend { UP, DOWN, FLAT, NONE }

data class StockChange(
    val stockTicker: String,
    val stockName: String,
    val etfTicker: String,
    val etfName: String,
    val previousWeight: Double?,
    val currentWeight: Double?,
    val previousAmount: Long,
    val currentAmount: Long,
    val changeType: ChangeType,
    val market: String? = null,
    val sector: String? = null
)

data class AmountRankingItem(
    val rank: Int,
    val stockTicker: String,
    val stockName: String,
    val totalAmountBillion: Double,
    val etfCount: Int,
    val newCount: Int = 0,
    val increasedCount: Int = 0,
    val decreasedCount: Int = 0,
    val removedCount: Int = 0,
    val market: String? = null,
    val sector: String? = null,
    val maxWeight: Double? = null,
    val maxWeightTrend: WeightTrend = WeightTrend.NONE,
    val avgWeight: Double? = null,
    val avgWeightTrend: WeightTrend = WeightTrend.NONE
)

// Stock trend time series models
data class HoldingTimeSeries(val date: String, val weight: Double?, val amount: Long)

data class StockAggregatedTimePoint(
    val date: String,
    val totalAmount: Long,
    val etfCount: Int,
    val maxWeight: Double?,
    val avgWeight: Double?
)

enum class DateRange(val label: String, val days: Int) {
    WEEK_1("1주", 7),
    MONTH_1("1개월", 30),
    MONTH_3("3개월", 90),
    MONTH_6("6개월", 180),
    YEAR_1("1년", 365),
    ALL("전체", Int.MAX_VALUE);

    fun getCutoffDate(): String {
        if (days == Int.MAX_VALUE) return "19700101"
        return java.time.LocalDate.now().minusDays(days.toLong())
            .format(com.tinyoscillator.core.util.DateFormats.yyyyMMdd)
    }
}
