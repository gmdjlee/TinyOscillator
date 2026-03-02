package com.tinyoscillator.data.dto

import com.tinyoscillator.domain.model.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ========== KIS Financial API Response Envelope ==========

@Serializable
data class KisFinancialApiResponse(
    @SerialName("rt_cd") val rtCd: String = "",
    @SerialName("msg_cd") val msgCd: String = "",
    @SerialName("msg1") val msg1: String = "",
    val output: List<Map<String, String?>>? = null,
    val output1: List<Map<String, String?>>? = null
) {
    val actualOutput: List<Map<String, String?>>?
        get() = output ?: output1
}

// ========== Numeric Parsing Utility ==========

fun parseNumericLong(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    val cleaned = value.trim().replace(",", "").replace(" ", "")
    return cleaned.toDoubleOrNull()?.toLong() ?: cleaned.toLongOrNull()
}

fun parseNumericDouble(value: String?): Double? {
    if (value.isNullOrBlank()) return null
    val cleaned = value.trim().replace(",", "").replace(" ", "")
    return cleaned.toDoubleOrNull()
}

// ========== DTO Mappers ==========

fun mapToBalanceSheet(item: Map<String, String?>): BalanceSheet? {
    val yearMonth = item["stac_yymm"] ?: return null
    return BalanceSheet(
        period = FinancialPeriod.fromYearMonth(yearMonth),
        currentAssets = parseNumericLong(item["cras"]),
        fixedAssets = parseNumericLong(item["fxas"]),
        totalAssets = parseNumericLong(item["total_aset"]),
        currentLiabilities = parseNumericLong(item["flow_lblt"]),
        fixedLiabilities = parseNumericLong(item["fix_lblt"]),
        totalLiabilities = parseNumericLong(item["total_lblt"]),
        capital = parseNumericLong(item["cpfn"]),
        capitalSurplus = parseNumericLong(item["cfp_surp"]),
        retainedEarnings = parseNumericLong(item["rere"]),
        totalEquity = parseNumericLong(item["total_cptl"])
    )
}

fun mapToIncomeStatement(item: Map<String, String?>): IncomeStatement? {
    val yearMonth = item["stac_yymm"] ?: return null
    return IncomeStatement(
        period = FinancialPeriod.fromYearMonth(yearMonth),
        revenue = parseNumericLong(item["sale_account"]),
        costOfSales = parseNumericLong(item["sale_cost"]),
        grossProfit = parseNumericLong(item["sale_totl_prfi"]),
        operatingProfit = parseNumericLong(item["bsop_prti"]),
        ordinaryProfit = parseNumericLong(item["op_prfi"]),
        netIncome = parseNumericLong(item["thtr_ntin"])
    )
}

fun mapToProfitabilityRatios(item: Map<String, String?>): ProfitabilityRatios? {
    val yearMonth = item["stac_yymm"] ?: return null
    return ProfitabilityRatios(
        period = FinancialPeriod.fromYearMonth(yearMonth),
        operatingMargin = parseNumericDouble(item["bsop_prfi_rate"]),
        netMargin = parseNumericDouble(item["ntin_rate"]),
        roe = parseNumericDouble(item["roe_val"]),
        roa = parseNumericDouble(item["roa_val"])
    )
}

fun mapToStabilityRatios(item: Map<String, String?>): StabilityRatios? {
    val yearMonth = item["stac_yymm"] ?: return null
    return StabilityRatios(
        period = FinancialPeriod.fromYearMonth(yearMonth),
        debtRatio = parseNumericDouble(item["lblt_rate"]),
        currentRatio = parseNumericDouble(item["crnt_rate"]),
        quickRatio = parseNumericDouble(item["quck_rate"]),
        borrowingDependency = parseNumericDouble(item["bram_depn"]),
        interestCoverageRatio = parseNumericDouble(item["inte_cvrg_rate"])
    )
}

fun mapToGrowthRatios(item: Map<String, String?>): GrowthRatios? {
    val yearMonth = item["stac_yymm"] ?: return null
    return GrowthRatios(
        period = FinancialPeriod.fromYearMonth(yearMonth),
        revenueGrowth = parseNumericDouble(item["grs"]),
        operatingProfitGrowth = parseNumericDouble(item["bsop_prfi_inrt"]),
        netIncomeGrowth = parseNumericDouble(item["ntin_inrt"])
            ?: parseNumericDouble(item["thtr_ntin_inrt"]),
        equityGrowth = parseNumericDouble(item["equt_inrt"])
            ?: parseNumericDouble(item["cptl_ntin_rate"]),
        totalAssetsGrowth = parseNumericDouble(item["totl_aset_inrt"])
            ?: parseNumericDouble(item["total_aset_inrt"])
    )
}
