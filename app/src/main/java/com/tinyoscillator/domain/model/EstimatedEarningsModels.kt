package com.tinyoscillator.domain.model

data class EstimatedEarningsInfo(
    val ticker: String,
    val stockName: String,
    val currentPrice: String,
    val priceChange: String,
    val changeSign: String,
    val changeRate: String,
    val volume: String,
)

data class EstimatedEarningsRow(
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
