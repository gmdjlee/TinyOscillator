package com.tinyoscillator.domain.model

data class FundamentalHistoryData(
    val date: String,       // yyyyMMdd
    val close: Long,
    val eps: Long,
    val per: Double,
    val bps: Long,
    val pbr: Double,
    val dps: Long,
    val dividendYield: Double
)

sealed class FundamentalHistoryState {
    data object NoStock : FundamentalHistoryState()
    data object Loading : FundamentalHistoryState()
    data object NoKrxLogin : FundamentalHistoryState()
    data class Success(
        val ticker: String,
        val stockName: String,
        val data: List<FundamentalHistoryData>
    ) : FundamentalHistoryState()
    data class Error(val message: String) : FundamentalHistoryState()
}
