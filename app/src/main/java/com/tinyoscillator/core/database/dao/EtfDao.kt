package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tinyoscillator.core.database.entity.EtfEntity
import com.tinyoscillator.core.database.entity.EtfHoldingEntity
import com.tinyoscillator.domain.model.AmountRankingRow
import com.tinyoscillator.domain.model.CashDepositRow
import com.tinyoscillator.domain.model.StockInEtfRow
import com.tinyoscillator.domain.model.StockSearchResult
import kotlinx.coroutines.flow.Flow

@Dao
interface EtfDao {

    @Query("SELECT * FROM etfs ORDER BY name ASC")
    fun getAllEtfs(): Flow<List<EtfEntity>>

    @Query("SELECT * FROM etfs WHERE ticker = :ticker")
    suspend fun getEtf(ticker: String): EtfEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEtfs(etfs: List<EtfEntity>)

    @Query("SELECT * FROM etf_holdings WHERE etf_ticker = :etfTicker AND date = :date ORDER BY weight DESC")
    suspend fun getHoldings(etfTicker: String, date: String): List<EtfHoldingEntity>

    @Query("SELECT MAX(date) FROM etf_holdings")
    suspend fun getLatestDate(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHoldings(holdings: List<EtfHoldingEntity>)

    @Query("DELETE FROM etfs WHERE ticker NOT IN (:tickers)")
    suspend fun deleteEtfsNotIn(tickers: List<String>)

    @Query("DELETE FROM etf_holdings WHERE etf_ticker NOT IN (:tickers)")
    suspend fun deleteHoldingsForEtfsNotIn(tickers: List<String>)

    @Query("DELETE FROM etf_holdings WHERE date < :beforeDate")
    suspend fun deleteHoldingsBeforeDate(beforeDate: String)

    @Query("SELECT DISTINCT date FROM etf_holdings ORDER BY date DESC")
    suspend fun getAllDates(): List<String>

    @Query("""
        SELECT stock_ticker, stock_name,
               SUM(amount) AS totalAmount,
               COUNT(DISTINCT etf_ticker) AS etfCount
        FROM etf_holdings
        WHERE date = :date
        GROUP BY stock_ticker
        ORDER BY totalAmount DESC
    """)
    suspend fun getAmountRanking(date: String): List<AmountRankingRow>

    @Query("""
        SELECT stock_ticker, stock_name,
               SUM(amount) AS totalAmount,
               COUNT(DISTINCT etf_ticker) AS etfCount
        FROM etf_holdings
        WHERE date = :date AND etf_ticker NOT IN (:excludedTickers)
        GROUP BY stock_ticker
        ORDER BY totalAmount DESC
    """)
    suspend fun getAmountRankingExcluding(date: String, excludedTickers: List<String>): List<AmountRankingRow>

    @Query("SELECT * FROM etf_holdings WHERE date = :date")
    suspend fun getAllHoldingsForDate(date: String): List<EtfHoldingEntity>

    @Query("SELECT * FROM etf_holdings WHERE date = :date AND etf_ticker NOT IN (:excludedTickers)")
    suspend fun getAllHoldingsForDateExcluding(date: String, excludedTickers: List<String>): List<EtfHoldingEntity>

    @Query("SELECT * FROM etf_holdings ORDER BY date DESC")
    suspend fun getAllHoldings(): List<EtfHoldingEntity>

    @Query("SELECT * FROM etf_holdings WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getHoldingsByDateRange(startDate: String, endDate: String): List<EtfHoldingEntity>

    @Query("SELECT * FROM etfs")
    suspend fun getAllEtfsList(): List<EtfEntity>

    @Query("""
        SELECT date, SUM(amount) AS totalAmount, COUNT(DISTINCT etf_ticker) AS etfCount
        FROM etf_holdings
        WHERE stock_name LIKE '%원화예금%' OR stock_name LIKE '%현금%'
        GROUP BY date
        ORDER BY date ASC
    """)
    suspend fun getCashDepositTrend(): List<CashDepositRow>

    @Query("""
        SELECT date, SUM(amount) AS totalAmount, COUNT(DISTINCT etf_ticker) AS etfCount
        FROM etf_holdings
        WHERE (stock_name LIKE '%원화예금%' OR stock_name LIKE '%현금%')
          AND etf_ticker NOT IN (:excludedTickers)
        GROUP BY date
        ORDER BY date ASC
    """)
    suspend fun getCashDepositTrendExcluding(excludedTickers: List<String>): List<CashDepositRow>

    @Query("""
        SELECT h.etf_ticker, e.name AS etfName,
               h.stock_ticker, h.stock_name, h.weight, h.shares, h.amount, h.date
        FROM etf_holdings h
        INNER JOIN etfs e ON h.etf_ticker = e.ticker
        WHERE h.stock_ticker = :stockTicker AND h.date = :date
        ORDER BY h.amount DESC
    """)
    suspend fun getEtfsHoldingStock(stockTicker: String, date: String): List<StockInEtfRow>

    @Query("""
        SELECT h.etf_ticker, e.name AS etfName,
               h.stock_ticker, h.stock_name, h.weight, h.shares, h.amount, h.date
        FROM etf_holdings h
        INNER JOIN etfs e ON h.etf_ticker = e.ticker
        WHERE h.stock_ticker = :stockTicker AND h.date = :date
          AND h.etf_ticker NOT IN (:excludedTickers)
        ORDER BY h.amount DESC
    """)
    suspend fun getEtfsHoldingStockExcluding(stockTicker: String, date: String, excludedTickers: List<String>): List<StockInEtfRow>

    @Query("""
        SELECT DISTINCT stock_ticker, stock_name
        FROM etf_holdings
        WHERE date = :date
          AND (stock_name LIKE '%' || :query || '%' OR stock_ticker LIKE '%' || :query || '%')
        ORDER BY stock_name ASC
        LIMIT 20
    """)
    suspend fun searchStocksInHoldings(date: String, query: String): List<StockSearchResult>

    @Query("""
        SELECT DISTINCT stock_ticker, stock_name
        FROM etf_holdings
        WHERE date = :date
          AND etf_ticker NOT IN (:excludedTickers)
          AND (stock_name LIKE '%' || :query || '%' OR stock_ticker LIKE '%' || :query || '%')
        ORDER BY stock_name ASC
        LIMIT 20
    """)
    suspend fun searchStocksInHoldingsExcluding(date: String, query: String, excludedTickers: List<String>): List<StockSearchResult>
}
