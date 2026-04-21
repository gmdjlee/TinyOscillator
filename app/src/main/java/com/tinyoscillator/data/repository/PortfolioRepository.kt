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

    suspend fun updateHoldingInfo(
        holdingId: Long,
        stockName: String,
        market: String,
        sector: String,
        targetPrice: Int
    ) = portfolioDao.updateHoldingInfo(holdingId, stockName, market, sector, targetPrice)

    suspend fun fetchAndUpdatePrice(
        holdingId: Long,
        ticker: String,
        config: KiwoomApiKeyConfig
    ): Long {
        try {
            val result = stockRepository.fetchCurrentPrice(ticker, config)
            result.onSuccess { price ->
                if (price > 0) {
                    portfolioDao.updateHoldingPrice(holdingId, price.toInt(), System.currentTimeMillis())
                    return price
                }
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("가격 조회 실패: $ticker - ${e.message}")
        }
        return 0L
    }

    suspend fun deleteHoldingWithTransactions(holdingId: Long) =
        portfolioDao.deleteHoldingWithTransactions(holdingId)

    // Transactions CRUD
    fun getTransactionsForHolding(holdingId: Long): Flow<List<PortfolioTransactionEntity>> =
        portfolioDao.getTransactionsForHolding(holdingId)

    suspend fun insertTransaction(transaction: PortfolioTransactionEntity) =
        portfolioDao.insertTransaction(transaction)

    suspend fun updateTransaction(id: Long, date: String, shares: Int, pricePerShare: Int, memo: String) =
        portfolioDao.updateTransaction(id, date, shares, pricePerShare, memo)

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
        maxWeightPercent: Int,
        totalAssets: Long? = null
    ): Pair<PortfolioSummary, List<PortfolioHoldingItem>> {
        val summaries = portfolioDao.getHoldingSummaries(portfolioId)
        if (summaries.isEmpty()) {
            return PortfolioSummary(0, 0, 0, 0.0, 0, 0, totalAssets ?: 0) to emptyList()
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

        return calculatePortfolio(summaries, priceMap, maxWeightPercent, totalAssets)
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
                val result = stockRepository.fetchCurrentPrice(holding.ticker, config)
                result.onSuccess { price ->
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
        maxWeightPercent: Int,
        totalAssets: Long? = null
    ): Pair<PortfolioSummary, List<PortfolioHoldingItem>> {
        // Filter out holdings with zero shares
        val activeHoldings = summaries.filter { it.totalShares > 0 }

        val totalEvaluation = activeHoldings.sumOf { row ->
            val price = priceMap[row.holdingId] ?: 0L
            price * row.totalShares
        }

        // Weight calculation base: use totalAssets if set, otherwise use totalEvaluation
        val weightBase = if (totalAssets != null && totalAssets > 0) totalAssets else totalEvaluation

        val items = activeHoldings.map { row ->
            val currentPrice = priceMap[row.holdingId] ?: 0L
            val evalAmount = currentPrice * row.totalShares
            val weightPercent = if (weightBase > 0) {
                evalAmount.toDouble() / weightBase * 100.0
            } else 0.0

            val isOverWeight = weightPercent > maxWeightPercent

            // Average buy price from buy transactions only
            val avgBuyPrice = if (row.totalBuyShares > 0) {
                (row.totalBuyAmount / row.totalBuyShares).toInt()
            } else 0

            // Rebalance calculation: how many shares to sell to reach max weight
            val rebalanceShares = if (isOverWeight && currentPrice > 0 && weightBase > 0) {
                val targetAmount = weightBase * maxWeightPercent / 100.0
                val excessAmount = evalAmount - targetAmount
                (excessAmount / currentPrice).toInt()
            } else 0

            val rebalanceAmount = rebalanceShares * currentPrice

            // Unrealized P&L: (currentPrice - avgBuyPrice) * currently held shares
            val profitLossAmount = if (row.totalShares > 0 && avgBuyPrice > 0) {
                (currentPrice - avgBuyPrice) * row.totalShares.toLong()
            } else 0L

            val profitLossPercent = if (avgBuyPrice > 0) {
                (currentPrice - avgBuyPrice).toDouble() / avgBuyPrice * 100.0
            } else 0.0

            // Realized P&L: sellAmount - (avgBuyPrice * sellShares)
            val realizedProfitLoss = if (row.totalSellShares > 0 && avgBuyPrice > 0) {
                row.totalSellAmount - avgBuyPrice.toLong() * row.totalSellShares
            } else 0L

            PortfolioHoldingItem(
                holdingId = row.holdingId,
                ticker = row.ticker,
                stockName = row.stockName,
                market = row.market,
                sector = row.sector,
                totalShares = row.totalShares,
                avgBuyPrice = avgBuyPrice,
                currentPrice = currentPrice,
                targetPrice = row.targetPrice,
                weightPercent = weightPercent,
                isOverWeight = isOverWeight,
                rebalanceShares = rebalanceShares,
                rebalanceAmount = rebalanceAmount,
                profitLossPercent = profitLossPercent,
                profitLossAmount = profitLossAmount,
                realizedProfitLoss = realizedProfitLoss
            )
        }

        val totalBuyAmount = activeHoldings.sumOf { it.totalBuyAmount }
        val totalProfitLoss = totalEvaluation - totalBuyAmount + items.sumOf { it.realizedProfitLoss }
        val totalProfitLossPercent = if (totalBuyAmount > 0) {
            totalProfitLoss.toDouble() / totalBuyAmount * 100.0
        } else 0.0
        val totalRealizedProfitLoss = items.sumOf { it.realizedProfitLoss }

        val summary = PortfolioSummary(
            totalEvaluation = totalEvaluation,
            totalInvested = totalBuyAmount,
            totalProfitLoss = totalProfitLoss,
            totalProfitLossPercent = totalProfitLossPercent,
            totalRealizedProfitLoss = totalRealizedProfitLoss,
            holdingsCount = activeHoldings.size,
            totalAssets = if (totalAssets != null && totalAssets > 0) totalAssets else totalEvaluation
        )

        return summary to items
    }

    // Build transaction items with profit/loss
    suspend fun getTransactionItems(holdingId: Long, currentPrice: Long): List<TransactionItem> {
        // Query returns DESC order; sort ASC for running avg calculation
        val transactions = portfolioDao.getTransactionsListForHolding(holdingId)
        val chronological = transactions.sortedWith(compareBy({ it.date }, { it.createdAt }))

        // Calculate running average buy price for realized P&L on sells
        var totalBuyShares = 0L
        var totalBuyCost = 0L
        val itemMap = mutableMapOf<Long, TransactionItem>()

        for (tx in chronological) {
            if (tx.shares > 0) {
                // Buy: update running average
                totalBuyShares += tx.shares
                totalBuyCost += tx.shares.toLong() * tx.pricePerShare

                val plAmount = (currentPrice - tx.pricePerShare) * tx.shares.toLong()
                val plPercent = if (tx.pricePerShare > 0) {
                    (currentPrice - tx.pricePerShare).toDouble() / tx.pricePerShare * 100.0
                } else 0.0

                itemMap[tx.id] = TransactionItem(
                    id = tx.id,
                    date = tx.date,
                    shares = tx.shares,
                    pricePerShare = tx.pricePerShare,
                    memo = tx.memo,
                    currentPrice = currentPrice,
                    profitLossPercent = plPercent,
                    profitLossAmount = plAmount
                )
            } else {
                // Sell: realized P&L = (sellPrice - avgBuyPrice) * |shares|
                val avgBuyPrice = if (totalBuyShares > 0) totalBuyCost / totalBuyShares else 0L
                val sellShares = abs(tx.shares).toLong()
                val plAmount = (tx.pricePerShare - avgBuyPrice) * sellShares
                val plPercent = if (avgBuyPrice > 0) {
                    (tx.pricePerShare - avgBuyPrice).toDouble() / avgBuyPrice * 100.0
                } else 0.0

                // Adjust running totals after sell
                val sharesToDeduct = minOf(sellShares, totalBuyShares)
                if (totalBuyShares > 0) {
                    totalBuyCost -= avgBuyPrice * sharesToDeduct
                    totalBuyShares -= sharesToDeduct
                }

                itemMap[tx.id] = TransactionItem(
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

        // Return in original DESC order. itemMap is populated from the same list via chronological sort,
        // so every tx.id is present. Using getValue to surface a clear NoSuchElementException if the
        // invariant ever breaks (e.g. due to a future refactor or duplicate ids).
        return transactions.map { itemMap.getValue(it.id) }
    }

    // For backup
    suspend fun getPortfolioDao(): PortfolioDao = portfolioDao
}
