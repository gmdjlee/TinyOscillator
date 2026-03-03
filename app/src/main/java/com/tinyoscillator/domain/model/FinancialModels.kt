package com.tinyoscillator.domain.model

import kotlinx.serialization.Serializable
import timber.log.Timber

// ========== Domain Enums ==========

enum class FinancialTab(val label: String) {
    PROFITABILITY("수익성"),
    STABILITY("안정성")
}

// ========== Domain Models ==========

data class FinancialPeriod(
    val yearMonth: String,
    val year: Int,
    val quarter: Int
) {
    fun toDisplayString(short: Boolean = false): String {
        if (yearMonth.length < 6) return yearMonth
        val y = if (short) yearMonth.substring(2, 4) else yearMonth.substring(0, 4)
        val m = yearMonth.substring(4, 6)
        return "$y.$m"
    }

    companion object {
        fun fromYearMonth(ym: String): FinancialPeriod {
            if (ym.length < 6) return FinancialPeriod(ym, 0, 0)
            val year = ym.substring(0, 4).toIntOrNull() ?: 0
            val month = ym.substring(4, 6).toIntOrNull() ?: 0
            val quarter = when (month) {
                3 -> 1; 6 -> 2; 9 -> 3; 12 -> 4; else -> 0
            }
            return FinancialPeriod(ym, year, quarter)
        }
    }
}

data class BalanceSheet(
    val period: FinancialPeriod,
    val currentAssets: Long?,
    val fixedAssets: Long?,
    val totalAssets: Long?,
    val currentLiabilities: Long?,
    val fixedLiabilities: Long?,
    val totalLiabilities: Long?,
    val capital: Long?,
    val capitalSurplus: Long?,
    val retainedEarnings: Long?,
    val totalEquity: Long?
)

data class IncomeStatement(
    val period: FinancialPeriod,
    val revenue: Long?,
    val costOfSales: Long?,
    val grossProfit: Long?,
    val operatingProfit: Long?,
    val ordinaryProfit: Long?,
    val netIncome: Long?
)

data class ProfitabilityRatios(
    val period: FinancialPeriod,
    val operatingMargin: Double?,
    val netMargin: Double?,
    val roe: Double?,
    val roa: Double?
)

data class StabilityRatios(
    val period: FinancialPeriod,
    val debtRatio: Double?,
    val currentRatio: Double?,
    val quickRatio: Double?,
    val borrowingDependency: Double?,
    val interestCoverageRatio: Double?
)

data class GrowthRatios(
    val period: FinancialPeriod,
    val revenueGrowth: Double?,
    val operatingProfitGrowth: Double?,
    val netIncomeGrowth: Double?,
    val equityGrowth: Double?,
    val totalAssetsGrowth: Double?
)

// ========== Aggregate Model ==========

data class FinancialData(
    val ticker: String,
    val name: String,
    val periods: List<String>,
    val balanceSheets: Map<String, BalanceSheet>,
    val incomeStatements: Map<String, IncomeStatement>,
    val profitabilityRatios: Map<String, ProfitabilityRatios>,
    val stabilityRatios: Map<String, StabilityRatios>,
    val growthRatios: Map<String, GrowthRatios>
)

// ========== UI-Ready Model ==========

data class FinancialSummary(
    val ticker: String,
    val name: String,
    val periods: List<String>,
    val displayPeriods: List<String>,
    val revenues: List<Long>,
    val operatingProfits: List<Long>,
    val netIncomes: List<Long>,
    val revenueGrowthRates: List<Double>,
    val operatingProfitGrowthRates: List<Double>,
    val netIncomeGrowthRates: List<Double>,
    val equityGrowthRates: List<Double>,
    val totalAssetsGrowthRates: List<Double>,
    val debtRatios: List<Double>,
    val currentRatios: List<Double>,
    val borrowingDependencies: List<Double>
) {
    val latestRevenue: Long? get() = revenues.lastOrNull()
    val latestOperatingProfit: Long? get() = operatingProfits.lastOrNull()
    val latestNetIncome: Long? get() = netIncomes.lastOrNull()
    val latestDebtRatio: Double? get() = debtRatios.lastOrNull()
    val latestCurrentRatio: Double? get() = currentRatios.lastOrNull()

    val hasProfitabilityData: Boolean
        get() = revenues.any { it != 0L } ||
                operatingProfits.any { it != 0L } ||
                netIncomes.any { it != 0L }

    val hasGrowthData: Boolean
        get() = revenueGrowthRates.any { it != 0.0 } ||
                operatingProfitGrowthRates.any { it != 0.0 } ||
                netIncomeGrowthRates.any { it != 0.0 }

    val hasAssetGrowthData: Boolean
        get() = equityGrowthRates.any { it != 0.0 } ||
                totalAssetsGrowthRates.any { it != 0.0 }

    val hasStabilityData: Boolean
        get() = debtRatios.any { it != 0.0 } ||
                currentRatios.any { it != 0.0 } ||
                borrowingDependencies.any { it != 0.0 }

    fun trimToLast(count: Int): FinancialSummary {
        val n = count.coerceAtLeast(MIN_DISPLAY_QUARTERS)
        if (n >= periods.size) return this
        return copy(
            periods = periods.takeLast(n),
            displayPeriods = displayPeriods.takeLast(n),
            revenues = revenues.takeLast(n),
            operatingProfits = operatingProfits.takeLast(n),
            netIncomes = netIncomes.takeLast(n),
            revenueGrowthRates = revenueGrowthRates.takeLast(n),
            operatingProfitGrowthRates = operatingProfitGrowthRates.takeLast(n),
            netIncomeGrowthRates = netIncomeGrowthRates.takeLast(n),
            equityGrowthRates = equityGrowthRates.takeLast(n),
            totalAssetsGrowthRates = totalAssetsGrowthRates.takeLast(n),
            debtRatios = debtRatios.takeLast(n),
            currentRatios = currentRatios.takeLast(n),
            borrowingDependencies = borrowingDependencies.takeLast(n),
        )
    }

    companion object {
        const val MIN_DISPLAY_QUARTERS = 4
    }
}

// ========== UI State ==========

sealed class FinancialState {
    data object NoStock : FinancialState()
    data object Loading : FinancialState()
    data object NoApiKey : FinancialState()
    data class Success(val summary: FinancialSummary) : FinancialState()
    data class Error(val message: String) : FinancialState()
}

// ========== Cache Serialization Models ==========

@Serializable
data class FinancialDataCache(
    val ticker: String,
    val name: String,
    val periods: List<String>,
    val balanceSheets: List<BalanceSheetCache>,
    val incomeStatements: List<IncomeStatementCache>,
    val profitabilityRatios: List<ProfitabilityRatiosCache>,
    val stabilityRatios: List<StabilityRatiosCache>,
    val growthRatios: List<GrowthRatiosCache>
)

@Serializable
data class BalanceSheetCache(
    val yearMonth: String,
    val currentAssets: Long? = null,
    val fixedAssets: Long? = null,
    val totalAssets: Long? = null,
    val currentLiabilities: Long? = null,
    val fixedLiabilities: Long? = null,
    val totalLiabilities: Long? = null,
    val capital: Long? = null,
    val capitalSurplus: Long? = null,
    val retainedEarnings: Long? = null,
    val totalEquity: Long? = null
)

@Serializable
data class IncomeStatementCache(
    val yearMonth: String,
    val revenue: Long? = null,
    val costOfSales: Long? = null,
    val grossProfit: Long? = null,
    val operatingProfit: Long? = null,
    val ordinaryProfit: Long? = null,
    val netIncome: Long? = null
)

@Serializable
data class ProfitabilityRatiosCache(
    val yearMonth: String,
    val operatingMargin: Double? = null,
    val netMargin: Double? = null,
    val roe: Double? = null,
    val roa: Double? = null
)

@Serializable
data class StabilityRatiosCache(
    val yearMonth: String,
    val debtRatio: Double? = null,
    val currentRatio: Double? = null,
    val quickRatio: Double? = null,
    val borrowingDependency: Double? = null,
    val interestCoverageRatio: Double? = null
)

@Serializable
data class GrowthRatiosCache(
    val yearMonth: String,
    val revenueGrowth: Double? = null,
    val operatingProfitGrowth: Double? = null,
    val netIncomeGrowth: Double? = null,
    val equityGrowth: Double? = null,
    val totalAssetsGrowth: Double? = null
)

// ========== Cache Conversion ==========

fun FinancialData.toCache(): FinancialDataCache = FinancialDataCache(
    ticker = ticker,
    name = name,
    periods = periods,
    balanceSheets = balanceSheets.values.map { bs ->
        BalanceSheetCache(
            yearMonth = bs.period.yearMonth,
            currentAssets = bs.currentAssets, fixedAssets = bs.fixedAssets,
            totalAssets = bs.totalAssets, currentLiabilities = bs.currentLiabilities,
            fixedLiabilities = bs.fixedLiabilities, totalLiabilities = bs.totalLiabilities,
            capital = bs.capital, capitalSurplus = bs.capitalSurplus,
            retainedEarnings = bs.retainedEarnings, totalEquity = bs.totalEquity
        )
    },
    incomeStatements = incomeStatements.values.map { is_ ->
        IncomeStatementCache(
            yearMonth = is_.period.yearMonth,
            revenue = is_.revenue, costOfSales = is_.costOfSales,
            grossProfit = is_.grossProfit, operatingProfit = is_.operatingProfit,
            ordinaryProfit = is_.ordinaryProfit, netIncome = is_.netIncome
        )
    },
    profitabilityRatios = profitabilityRatios.values.map { pr ->
        ProfitabilityRatiosCache(
            yearMonth = pr.period.yearMonth,
            operatingMargin = pr.operatingMargin, netMargin = pr.netMargin,
            roe = pr.roe, roa = pr.roa
        )
    },
    stabilityRatios = stabilityRatios.values.map { sr ->
        StabilityRatiosCache(
            yearMonth = sr.period.yearMonth,
            debtRatio = sr.debtRatio, currentRatio = sr.currentRatio,
            quickRatio = sr.quickRatio, borrowingDependency = sr.borrowingDependency,
            interestCoverageRatio = sr.interestCoverageRatio
        )
    },
    growthRatios = growthRatios.values.map { gr ->
        GrowthRatiosCache(
            yearMonth = gr.period.yearMonth,
            revenueGrowth = gr.revenueGrowth, operatingProfitGrowth = gr.operatingProfitGrowth,
            netIncomeGrowth = gr.netIncomeGrowth, equityGrowth = gr.equityGrowth,
            totalAssetsGrowth = gr.totalAssetsGrowth
        )
    }
)

fun FinancialDataCache.toData(): FinancialData = FinancialData(
    ticker = ticker,
    name = name,
    periods = periods,
    balanceSheets = balanceSheets.associate { c ->
        val period = FinancialPeriod.fromYearMonth(c.yearMonth)
        c.yearMonth to BalanceSheet(
            period = period,
            currentAssets = c.currentAssets, fixedAssets = c.fixedAssets,
            totalAssets = c.totalAssets, currentLiabilities = c.currentLiabilities,
            fixedLiabilities = c.fixedLiabilities, totalLiabilities = c.totalLiabilities,
            capital = c.capital, capitalSurplus = c.capitalSurplus,
            retainedEarnings = c.retainedEarnings, totalEquity = c.totalEquity
        )
    },
    incomeStatements = incomeStatements.associate { c ->
        val period = FinancialPeriod.fromYearMonth(c.yearMonth)
        c.yearMonth to IncomeStatement(
            period = period,
            revenue = c.revenue, costOfSales = c.costOfSales,
            grossProfit = c.grossProfit, operatingProfit = c.operatingProfit,
            ordinaryProfit = c.ordinaryProfit, netIncome = c.netIncome
        )
    },
    profitabilityRatios = profitabilityRatios.associate { c ->
        val period = FinancialPeriod.fromYearMonth(c.yearMonth)
        c.yearMonth to ProfitabilityRatios(
            period = period,
            operatingMargin = c.operatingMargin, netMargin = c.netMargin,
            roe = c.roe, roa = c.roa
        )
    },
    stabilityRatios = stabilityRatios.associate { c ->
        val period = FinancialPeriod.fromYearMonth(c.yearMonth)
        c.yearMonth to StabilityRatios(
            period = period,
            debtRatio = c.debtRatio, currentRatio = c.currentRatio,
            quickRatio = c.quickRatio, borrowingDependency = c.borrowingDependency,
            interestCoverageRatio = c.interestCoverageRatio
        )
    },
    growthRatios = growthRatios.associate { c ->
        val period = FinancialPeriod.fromYearMonth(c.yearMonth)
        c.yearMonth to GrowthRatios(
            period = period,
            revenueGrowth = c.revenueGrowth, operatingProfitGrowth = c.operatingProfitGrowth,
            netIncomeGrowth = c.netIncomeGrowth, equityGrowth = c.equityGrowth,
            totalAssetsGrowth = c.totalAssetsGrowth
        )
    }
)

// ========== Domain Transformation ==========

fun FinancialData.toSummary(): FinancialSummary {
    val sortedPeriods = periods.sorted()

    val rawRevenues = sortedPeriods.map { incomeStatements[it]?.revenue ?: 0L }
    val rawOperatingProfits = sortedPeriods.map { incomeStatements[it]?.operatingProfit ?: 0L }
    val rawNetIncomes = sortedPeriods.map { incomeStatements[it]?.netIncome ?: 0L }

    val revenues = convertYtdToQuarterly(sortedPeriods, rawRevenues)
    val operatingProfits = convertYtdToQuarterly(sortedPeriods, rawOperatingProfits)
    val netIncomes = convertYtdToQuarterly(sortedPeriods, rawNetIncomes)

    val apiNetIncomeGrowths = sortedPeriods.map { growthRatios[it]?.netIncomeGrowth ?: 0.0 }
    val netIncomeGrowthRates = if (apiNetIncomeGrowths.all { it == 0.0 } && netIncomes.any { it != 0L }) {
        calculateYoYGrowthRates(netIncomes, sortedPeriods)
    } else {
        apiNetIncomeGrowths
    }

    return FinancialSummary(
        ticker = ticker,
        name = name,
        periods = sortedPeriods,
        displayPeriods = sortedPeriods.map { FinancialPeriod.fromYearMonth(it).toDisplayString(short = true) },
        revenues = revenues,
        operatingProfits = operatingProfits,
        netIncomes = netIncomes,
        revenueGrowthRates = sortedPeriods.map { growthRatios[it]?.revenueGrowth ?: 0.0 },
        operatingProfitGrowthRates = sortedPeriods.map { growthRatios[it]?.operatingProfitGrowth ?: 0.0 },
        netIncomeGrowthRates = netIncomeGrowthRates,
        equityGrowthRates = sortedPeriods.map { growthRatios[it]?.equityGrowth ?: 0.0 },
        totalAssetsGrowthRates = sortedPeriods.map { growthRatios[it]?.totalAssetsGrowth ?: 0.0 },
        debtRatios = sortedPeriods.map { stabilityRatios[it]?.debtRatio ?: 0.0 },
        currentRatios = sortedPeriods.map { stabilityRatios[it]?.currentRatio ?: 0.0 },
        borrowingDependencies = sortedPeriods.map { stabilityRatios[it]?.borrowingDependency ?: 0.0 }
    )
}

private fun convertYtdToQuarterly(
    periods: List<String>,
    ytdValues: List<Long>
): List<Long> {
    val result = mutableListOf<Long>()
    val prevYtdByYear = mutableMapOf<Int, Pair<Int, Long>>()

    for (i in periods.indices) {
        val fp = FinancialPeriod.fromYearMonth(periods[i])
        val year = fp.year
        val quarter = fp.quarter
        val ytdValue = ytdValues[i]

        val standalone = when (quarter) {
            1 -> ytdValue
            2, 3, 4 -> {
                val prev = prevYtdByYear[year]
                if (prev != null) {
                    if (quarter - prev.first > 1) {
                        Timber.w("Non-consecutive quarters: Q%d -> Q%d for year %d", prev.first, quarter, year)
                    }
                    ytdValue - prev.second
                } else {
                    Timber.w("Missing previous quarter for %s (Q%d). Using YTD value.", periods[i], quarter)
                    ytdValue
                }
            }
            else -> ytdValue
        }

        result.add(standalone)
        if (quarter in 1..4) {
            prevYtdByYear[year] = quarter to ytdValue
        }
    }

    return result
}

private fun calculateYoYGrowthRates(
    quarterlyValues: List<Long>,
    periods: List<String>
): List<Double> {
    val periodInfos = periods.map { FinancialPeriod.fromYearMonth(it) }
    return quarterlyValues.mapIndexed { i, current ->
        val currentPeriod = periodInfos[i]
        val prevIndex = periodInfos.indexOfFirst {
            it.year == currentPeriod.year - 1 && it.quarter == currentPeriod.quarter
        }
        if (prevIndex >= 0) {
            val previous = quarterlyValues[prevIndex]
            if (previous != 0L) {
                (current.toDouble() - previous.toDouble()) / kotlin.math.abs(previous.toDouble()) * 100.0
            } else 0.0
        } else 0.0
    }
}

// ========== Formatting Utilities ==========

fun formatNumber(value: Long): String {
    val absValue = kotlin.math.abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        absValue >= 10_000L -> {
            val jo = absValue / 10_000
            val remainder = (absValue % 10_000) / 1_000
            "${sign}${jo}.${remainder}조"
        }
        absValue >= 1_000L -> {
            val cheonEok = absValue / 1_000
            val remainder = (absValue % 1_000) / 100
            "${sign}${cheonEok}.${remainder}천억"
        }
        else -> "${sign}${absValue}억"
    }
}

fun formatPercent(value: Double): String = "${"%.1f".format(value)}%"
