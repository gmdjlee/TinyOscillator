package com.tinyoscillator.domain.model

data class PortfolioHoldingItem(
    val holdingId: Long,
    val ticker: String,
    val stockName: String,
    val market: String,
    val sector: String,
    val totalShares: Int,
    val avgBuyPrice: Int,
    val currentPrice: Long,
    val weightPercent: Double,
    val isOverWeight: Boolean,
    val rebalanceShares: Int,
    val rebalanceAmount: Long,
    val profitLossPercent: Double,
    val profitLossAmount: Long
)

data class PortfolioSummary(
    val totalEvaluation: Long,
    val totalInvested: Long,
    val totalProfitLoss: Long,
    val totalProfitLossPercent: Double,
    val holdingsCount: Int
)

data class TransactionItem(
    val id: Long,
    val date: String,
    val shares: Int,
    val pricePerShare: Int,
    val memo: String,
    val currentPrice: Long,
    val profitLossPercent: Double,
    val profitLossAmount: Long
)

sealed class PortfolioUiState {
    data object Idle : PortfolioUiState()
    data class Loading(val message: String) : PortfolioUiState()
    data class Success(
        val summary: PortfolioSummary,
        val holdings: List<PortfolioHoldingItem>
    ) : PortfolioUiState()
    data class Error(val message: String) : PortfolioUiState()
}
