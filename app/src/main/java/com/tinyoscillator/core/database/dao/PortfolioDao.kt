package com.tinyoscillator.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.tinyoscillator.core.database.entity.PortfolioEntity
import com.tinyoscillator.core.database.entity.PortfolioHoldingEntity
import com.tinyoscillator.core.database.entity.PortfolioTransactionEntity
import kotlinx.coroutines.flow.Flow

data class HoldingSummaryRow(
    @ColumnInfo(name = "holdingId") val holdingId: Long,
    @ColumnInfo(name = "ticker") val ticker: String,
    @ColumnInfo(name = "stockName") val stockName: String,
    @ColumnInfo(name = "market") val market: String,
    @ColumnInfo(name = "sector") val sector: String,
    @ColumnInfo(name = "lastPrice") val lastPrice: Int,
    @ColumnInfo(name = "priceUpdatedAt") val priceUpdatedAt: Long,
    @ColumnInfo(name = "targetPrice") val targetPrice: Int,
    @ColumnInfo(name = "totalShares") val totalShares: Int,
    @ColumnInfo(name = "totalInvested") val totalInvested: Long,
    @ColumnInfo(name = "totalBuyShares") val totalBuyShares: Int,
    @ColumnInfo(name = "totalBuyAmount") val totalBuyAmount: Long,
    @ColumnInfo(name = "totalSellShares") val totalSellShares: Int,
    @ColumnInfo(name = "totalSellAmount") val totalSellAmount: Long
)

@Dao
interface PortfolioDao {

    // Portfolio CRUD
    @Query("SELECT * FROM portfolios ORDER BY created_at ASC")
    fun getAllPortfolios(): Flow<List<PortfolioEntity>>

    @Query("SELECT * FROM portfolios ORDER BY created_at ASC")
    suspend fun getAllPortfoliosList(): List<PortfolioEntity>

    @Query("SELECT * FROM portfolios WHERE id = :id")
    suspend fun getPortfolio(id: Long): PortfolioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPortfolio(portfolio: PortfolioEntity): Long

    @Update
    suspend fun updatePortfolio(portfolio: PortfolioEntity)

    @Query("DELETE FROM portfolios WHERE id = :id")
    suspend fun deletePortfolio(id: Long)

    // Holdings CRUD
    @Query("SELECT * FROM portfolio_holdings WHERE portfolio_id = :portfolioId ORDER BY stock_name ASC")
    fun getHoldingsForPortfolio(portfolioId: Long): Flow<List<PortfolioHoldingEntity>>

    @Query("SELECT * FROM portfolio_holdings WHERE portfolio_id = :portfolioId ORDER BY stock_name ASC")
    suspend fun getHoldingsListForPortfolio(portfolioId: Long): List<PortfolioHoldingEntity>

    @Query("SELECT * FROM portfolio_holdings WHERE id = :id")
    suspend fun getHolding(id: Long): PortfolioHoldingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHolding(holding: PortfolioHoldingEntity): Long

    @Query("DELETE FROM portfolio_holdings WHERE id = :id")
    suspend fun deleteHolding(id: Long)

    @Query("UPDATE portfolio_holdings SET last_price = :price, price_updated_at = :updatedAt WHERE id = :holdingId")
    suspend fun updateHoldingPrice(holdingId: Long, price: Int, updatedAt: Long)

    @Query("UPDATE portfolio_holdings SET stock_name = :stockName, market = :market, sector = :sector, target_price = :targetPrice WHERE id = :holdingId")
    suspend fun updateHoldingInfo(holdingId: Long, stockName: String, market: String, sector: String, targetPrice: Int)

    // Transactions CRUD
    @Query("SELECT * FROM portfolio_transactions WHERE holding_id = :holdingId ORDER BY date DESC, created_at DESC")
    fun getTransactionsForHolding(holdingId: Long): Flow<List<PortfolioTransactionEntity>>

    @Query("SELECT * FROM portfolio_transactions WHERE holding_id = :holdingId ORDER BY date DESC, created_at DESC")
    suspend fun getTransactionsListForHolding(holdingId: Long): List<PortfolioTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: PortfolioTransactionEntity)

    @Query("UPDATE portfolio_transactions SET date = :date, shares = :shares, price_per_share = :pricePerShare, memo = :memo WHERE id = :id")
    suspend fun updateTransaction(id: Long, date: String, shares: Int, pricePerShare: Int, memo: String)

    @Query("DELETE FROM portfolio_transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Long)

    // Aggregate query: holding summary with total shares and invested amount
    @Query("""
        SELECT h.id AS holdingId, h.ticker, h.stock_name AS stockName,
               h.market, h.sector, h.last_price AS lastPrice,
               h.price_updated_at AS priceUpdatedAt,
               h.target_price AS targetPrice,
               COALESCE(SUM(t.shares), 0) AS totalShares,
               COALESCE(SUM(t.shares * t.price_per_share), 0) AS totalInvested,
               COALESCE(SUM(CASE WHEN t.shares > 0 THEN t.shares ELSE 0 END), 0) AS totalBuyShares,
               COALESCE(SUM(CASE WHEN t.shares > 0 THEN t.shares * t.price_per_share ELSE 0 END), 0) AS totalBuyAmount,
               COALESCE(SUM(CASE WHEN t.shares < 0 THEN -t.shares ELSE 0 END), 0) AS totalSellShares,
               COALESCE(SUM(CASE WHEN t.shares < 0 THEN -t.shares * t.price_per_share ELSE 0 END), 0) AS totalSellAmount
        FROM portfolio_holdings h
        LEFT JOIN portfolio_transactions t ON t.holding_id = h.id
        WHERE h.portfolio_id = :portfolioId
        GROUP BY h.id
        ORDER BY h.stock_name ASC
    """)
    suspend fun getHoldingSummaries(portfolioId: Long): List<HoldingSummaryRow>

    // CASCADE deletes (explicit, not FK-based — existing pattern)
    @Transaction
    suspend fun deletePortfolioWithData(id: Long) {
        val holdings = getHoldingsListForPortfolio(id)
        for (holding in holdings) {
            deleteTransactionsForHolding(holding.id)
        }
        deleteHoldingsForPortfolio(id)
        deletePortfolio(id)
    }

    @Transaction
    suspend fun deleteHoldingWithTransactions(holdingId: Long) {
        deleteTransactionsForHolding(holdingId)
        deleteHolding(holdingId)
    }

    @Query("DELETE FROM portfolio_transactions WHERE holding_id = :holdingId")
    suspend fun deleteTransactionsForHolding(holdingId: Long)

    @Query("DELETE FROM portfolio_holdings WHERE portfolio_id = :portfolioId")
    suspend fun deleteHoldingsForPortfolio(portfolioId: Long)

    // For backup: get all transactions for a portfolio
    @Query("""
        SELECT t.* FROM portfolio_transactions t
        INNER JOIN portfolio_holdings h ON t.holding_id = h.id
        WHERE h.portfolio_id = :portfolioId
        ORDER BY t.date ASC
    """)
    suspend fun getAllTransactionsForPortfolio(portfolioId: Long): List<PortfolioTransactionEntity>
}
