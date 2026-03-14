package com.tinyoscillator.data.repository

import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.HoldingSummaryRow
import com.tinyoscillator.core.database.dao.PortfolioDao
import com.tinyoscillator.core.database.entity.PortfolioEntity
import com.tinyoscillator.core.database.entity.PortfolioHoldingEntity
import com.tinyoscillator.core.database.entity.PortfolioTransactionEntity
import com.tinyoscillator.domain.model.PortfolioHoldingItem
import com.tinyoscillator.domain.model.PortfolioSummary
import com.tinyoscillator.domain.model.TransactionItem
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import kotlin.math.abs

class PortfolioRepository(
    private val portfolioDao: PortfolioDao,
    private val analysisCacheDao: AnalysisCacheDao,
    private val stockRepository: StockRepository
) {

    // Portfolio CRUD
    fun getAllPortfolios(): Flow<List<PortfolioEntity>> = portfolioDao.getAllPortfolios()

    suspend fun getPortfolio(id: Long): PortfolioEntity? = portfolioDao.getPortfolio(id)

    suspend fun insertPortfolio(portfolio: PortfolioEntity): Long =
        portfolioDao.insertPortfolio(portfolio)

    suspend fun updatePortfolio(portfolio: PortfolioEntity) =
        portfolioDao.updatePortfolio(portfolio)

    suspend fun deletePortfolioWithData(id: Long) =
        portfolioDao.deletePortfolioWithData(id)

    // Holdings CRUD
    fun getHoldingsForPortfolio(portfolioId: Long): Flow<List<PortfolioHoldingEntity>> =
        portfolioDao.getHoldingsForPortfolio(portfolioId)

    suspend fun insertHolding(holding: PortfolioHoldingEntity): Long =
        portfolioDao.insertHolding(holding)

    suspend fun deleteHoldingWithTransactions(holdingId: Long) =
        portfolioDao.deleteHoldingWithTransactions(holdingId)

    // Transactions CRUD
    fun getTransactionsForHolding(holdingId: Long): Flow<List<PortfolioTransactionEntity>> =
        portfolioDao.getTransactionsForHolding(holdingId)

    suspend fun insertTransaction(transaction: PortfolioTransactionEntity) =
        portfolioDao.insertTransaction(transaction)

    suspend fun deleteTransaction(id: Long) =
        portfolioDao.deleteTransaction(id)

    // Ensure default portfolio exists
    suspend fun ensureDefaultPortfolio(): Long {
        val portfolios = portfolioDao.getAllPortfoliosList()
        if (portfolios.isNotEmpty()) return portfolios.first().id
        return portfolioDao.insertPortfolio(PortfolioEntity())
    }

    // Load portfolio with calculated holdings
    suspend fun loadPortfolioHoldings(
        portfolioId: Long,
        maxWeightPercent: Int
    ): Pair<PortfolioSummary, List<PortfolioHoldingItem>> {
        val summaries = portfolioDao.getHoldingSummaries(portfolioId)
        if (summaries.isEmpty()) {
            return PortfolioSummary(0, 0, 0, 0.0, 0) to emptyList()
        }

        // Resolve current prices: use lastPrice from DB, or fallback to AnalysisCache close price
        val priceMap = mutableMapOf<Long, Long>() // holdingId -> currentPrice
        for (row in summaries) {
            val price = if (row.lastPrice > 0) {
                row.lastPrice.toLong()
            } else {
                getLastClosePrice(row.ticker)
            }
            priceMap[row.holdingId] = price
        }

        return calculatePortfolio(summaries, priceMap, maxWeightPercent)
    }

    // Refresh prices from API (user-triggered only)
    suspend fun refreshCurrentPrices(
        portfolioId: Long,
        config: KiwoomApiKeyConfig
    ): Map<String, Long> {
        val holdings = portfolioDao.getHoldingsListForPortfolio(portfolioId)
        val priceMap = mutableMapOf<String, Long>()
        val now = System.currentTimeMillis()

        for (holding in holdings) {
            try {
                val result = stockRepository.fetchRealtimeSupply(holding.ticker, config, useCache = false)
                result.onSuccess { data ->
                    val price = abs(data.currentPrice)
                    if (price > 0) {
                        priceMap[holding.ticker] = price
                        portfolioDao.updateHoldingPrice(holding.id, price.toInt(), now)
                        Timber.d("가격 갱신: ${holding.stockName} (${holding.ticker}) = $price")
                    }
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("가격 조회 실패: ${holding.ticker} - ${e.message}")
            }
        }

        return priceMap
    }

    // Get last close price from AnalysisCache
    private suspend fun getLastClosePrice(ticker: String): Long {
        val latestDate = analysisCacheDao.getLatestDate(ticker) ?: return 0L
        val entries = analysisCacheDao.getByTickerDateRange(ticker, latestDate, latestDate)
        return entries.firstOrNull()?.closePrice?.toLong() ?: 0L
    }

    // Calculate portfolio summary and holding items
    private fun calculatePortfolio(
        summaries: List<HoldingSummaryRow>,
        priceMap: Map<Long, Long>,
        maxWeightPercent: Int
    ): Pair<PortfolioSummary, List<PortfolioHoldingItem>> {
        // Filter out holdings with zero shares
        val activeHoldings = summaries.filter { it.totalShares > 0 }

        val totalEvaluation = activeHoldings.sumOf { row ->
            val price = priceMap[row.holdingId] ?: 0L
            price * row.totalShares
        }

        val items = activeHoldings.map { row ->
            val currentPrice = priceMap[row.holdingId] ?: 0L
            val evalAmount = currentPrice * row.totalShares
            val weightPercent = if (totalEvaluation > 0) {
                evalAmount.toDouble() / totalEvaluation * 100.0
            } else 0.0

            val isOverWeight = weightPercent > maxWeightPercent
            val avgBuyPrice = if (row.totalShares > 0) {
                (row.totalInvested / row.totalShares).toInt()
            } else 0

            // Rebalance calculation: how many shares to sell to reach max weight
            val rebalanceShares = if (isOverWeight && currentPrice > 0 && totalEvaluation > 0) {
                val targetAmount = totalEvaluation * maxWeightPercent / 100.0
                val excessAmount = evalAmount - targetAmount
                (excessAmount / currentPrice).toInt()
            } else 0

            val rebalanceAmount = rebalanceShares * currentPrice

            val profitLossAmount = if (row.totalShares > 0) {
                (currentPrice - avgBuyPrice) * row.totalShares.toLong()
            } else 0L

            val profitLossPercent = if (avgBuyPrice > 0) {
                (currentPrice - avgBuyPrice).toDouble() / avgBuyPrice * 100.0
            } else 0.0

            PortfolioHoldingItem(
                holdingId = row.holdingId,
                ticker = row.ticker,
                stockName = row.stockName,
                market = row.market,
                sector = row.sector,
                totalShares = row.totalShares,
                avgBuyPrice = avgBuyPrice,
                currentPrice = currentPrice,
                weightPercent = weightPercent,
                isOverWeight = isOverWeight,
                rebalanceShares = rebalanceShares,
                rebalanceAmount = rebalanceAmount,
                profitLossPercent = profitLossPercent,
                profitLossAmount = profitLossAmount
            )
        }

        val totalInvested = activeHoldings.sumOf { it.totalInvested }
        val totalProfitLoss = totalEvaluation - totalInvested
        val totalProfitLossPercent = if (totalInvested > 0) {
            totalProfitLoss.toDouble() / totalInvested * 100.0
        } else 0.0

        val summary = PortfolioSummary(
            totalEvaluation = totalEvaluation,
            totalInvested = totalInvested,
            totalProfitLoss = totalProfitLoss,
            totalProfitLossPercent = totalProfitLossPercent,
            holdingsCount = activeHoldings.size
        )

        return summary to items
    }

    // Build transaction items with profit/loss
    suspend fun getTransactionItems(holdingId: Long, currentPrice: Long): List<TransactionItem> {
        val transactions = portfolioDao.getTransactionsListForHolding(holdingId)
        return transactions.map { tx ->
            val plAmount = if (tx.shares > 0) {
                (currentPrice - tx.pricePerShare) * tx.shares.toLong()
            } else {
                // For sell transactions: profit = (sellPrice - avgBuy) * |shares|
                // But we don't track avg buy at sell time, so show as-is
                (tx.pricePerShare - currentPrice) * abs(tx.shares).toLong()
            }
            val plPercent = if (tx.pricePerShare > 0) {
                (currentPrice - tx.pricePerShare).toDouble() / tx.pricePerShare * 100.0
            } else 0.0

            TransactionItem(
                id = tx.id,
                date = tx.date,
                shares = tx.shares,
                pricePerShare = tx.pricePerShare,
                memo = tx.memo,
                currentPrice = currentPrice,
                profitLossPercent = plPercent,
                profitLossAmount = plAmount
            )
        }
    }

    // For backup
    suspend fun getPortfolioDao(): PortfolioDao = portfolioDao
}
