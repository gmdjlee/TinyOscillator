package com.tinyoscillator.domain.model

data class EstimatedEarningsInfo(
    val ticker: String,
    val stockName: String,
    val analystName: String,
    val estimateDate: String,
    val recommendation: String,
    val targetPrice: String,
)

data class EstimatedEarningsRow(
    val label: String,
    val data1: String,
    val data2: String,
    val data3: String,
    val data4: String,
    val data5: String,
)

data class EstimatedEarningsSummary(
    val info: EstimatedEarningsInfo,
    val earningsData: List<EstimatedEarningsRow>,
    val valuationData: List<EstimatedEarningsRow>,
    val periods: List<String>,
)
