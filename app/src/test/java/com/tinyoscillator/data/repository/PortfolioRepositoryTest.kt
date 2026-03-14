package com.tinyoscillator.data.repository

import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.HoldingSummaryRow
import com.tinyoscillator.core.database.dao.PortfolioDao
import com.tinyoscillator.core.database.entity.AnalysisCacheEntity
import com.tinyoscillator.core.database.entity.PortfolioEntity
import com.tinyoscillator.core.database.entity.PortfolioHoldingEntity
import com.tinyoscillator.core.database.entity.PortfolioTransactionEntity
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PortfolioRepositoryTest {

    private lateinit var portfolioDao: PortfolioDao
    private lateinit var analysisCacheDao: AnalysisCacheDao
    private lateinit var stockRepository: StockRepository
    private lateinit var repository: PortfolioRepository

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        portfolioDao = mockk(relaxed = true)
        analysisCacheDao = mockk(relaxed = true)
        stockRepository = mockk(relaxed = true)
        repository = PortfolioRepository(portfolioDao, analysisCacheDao, stockRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -- Helpers --

    private fun createSummaryRow(
        holdingId: Long,
        ticker: String,
        stockName: String,
        lastPrice: Int = 0,
        totalShares: Int = 100,
        totalInvested: Long = 7_000_000,
        totalBuyShares: Int = 100,
        totalBuyAmount: Long = 7_000_000,
        totalSellShares: Int = 0,
        totalSellAmount: Long = 0
    ) = HoldingSummaryRow(
        holdingId = holdingId,
        ticker = ticker,
        stockName = stockName,
        market = "KOSPI",
        sector = "반도체",
        lastPrice = lastPrice,
        priceUpdatedAt = 0L,
        targetPrice = 0,
        totalShares = totalShares,
        totalInvested = totalInvested,
        totalBuyShares = totalBuyShares,
        totalBuyAmount = totalBuyAmount,
        totalSellShares = totalSellShares,
        totalSellAmount = totalSellAmount
    )

    // -- Tests --

    @Test
    fun `ensureDefaultPortfolio - 기존 포트폴리오 있으면 첫번째 반환`() = runTest {
        val existing = PortfolioEntity(id = 5, name = "기본 포트폴리오")
        coEvery { portfolioDao.getAllPortfoliosList() } returns listOf(existing)

        val id = repository.ensureDefaultPortfolio()
        assertEquals(5L, id)
        coVerify(exactly = 0) { portfolioDao.insertPortfolio(any()) }
    }

    @Test
    fun `ensureDefaultPortfolio - 없으면 새로 생성`() = runTest {
        coEvery { portfolioDao.getAllPortfoliosList() } returns emptyList()
        coEvery { portfolioDao.insertPortfolio(any()) } returns 1L

        val id = repository.ensureDefaultPortfolio()
        assertEquals(1L, id)
        coVerify { portfolioDao.insertPortfolio(any()) }
    }

    @Test
    fun `loadPortfolioHoldings - 빈 포트폴리오`() = runTest {
        coEvery { portfolioDao.getHoldingSummaries(1L) } returns emptyList()

        val (summary, holdings) = repository.loadPortfolioHoldings(1L, 30)
        assertEquals(0L, summary.totalEvaluation)
        assertEquals(0L, summary.totalInvested)
        assertEquals(0, summary.holdingsCount)
        assertTrue(holdings.isEmpty())
    }

    @Test
    fun `loadPortfolioHoldings - lastPrice 사용`() = runTest {
        val rows = listOf(
            createSummaryRow(1, "005930", "삼성전자", lastPrice = 75000, totalShares = 100, totalInvested = 7_000_000)
        )
        coEvery { portfolioDao.getHoldingSummaries(1L) } returns rows

        val (summary, holdings) = repository.loadPortfolioHoldings(1L, 30)
        assertEquals(1, holdings.size)
        assertEquals(75000L, holdings[0].currentPrice)
        assertEquals(7_500_000L, summary.totalEvaluation)  // 75000 * 100
        assertEquals(7_000_000L, summary.totalInvested)
        assertEquals(500_000L, summary.totalProfitLoss)
    }

    @Test
    fun `loadPortfolioHoldings - AnalysisCache fallback`() = runTest {
        val rows = listOf(
            createSummaryRow(1, "005930", "삼성전자", lastPrice = 0, totalShares = 100, totalInvested = 7_000_000)
        )
        coEvery { portfolioDao.getHoldingSummaries(1L) } returns rows
        coEvery { analysisCacheDao.getLatestDate("005930") } returns "20260314"
        coEvery { analysisCacheDao.getByTickerDateRange("005930", "20260314", "20260314") } returns listOf(
            AnalysisCacheEntity("005930", "20260314", 0L, 0L, 0L, closePrice = 72000)
        )

        val (_, holdings) = repository.loadPortfolioHoldings(1L, 30)
        assertEquals(72000L, holdings[0].currentPrice)
    }

    @Test
    fun `loadPortfolioHoldings - 비중 초과 리밸런싱 계산`() = runTest {
        val rows = listOf(
            createSummaryRow(1, "005930", "삼성전자", lastPrice = 100, totalShares = 60, totalInvested = 4800),
            createSummaryRow(2, "000660", "SK하이닉스", lastPrice = 100, totalShares = 40, totalInvested = 3200)
        )
        coEvery { portfolioDao.getHoldingSummaries(1L) } returns rows

        // 삼성전자: 6000 / 10000 = 60% → 30% 초과
        // SK하이닉스: 4000 / 10000 = 40% → 30% 초과
        val (summary, holdings) = repository.loadPortfolioHoldings(1L, 30)
        assertEquals(10000L, summary.totalEvaluation)
        assertEquals(2, holdings.size)

        val samsung = holdings.find { it.ticker == "005930" }!!
        assertTrue(samsung.isOverWeight)
        assertEquals(60.0, samsung.weightPercent, 0.1)
        // Target: 30% of 10000 = 3000, excess = 3000, rebalanceShares = 3000/100 = 30
        assertEquals(30, samsung.rebalanceShares)
    }

    @Test
    fun `loadPortfolioHoldings - zero shares 필터링`() = runTest {
        val rows = listOf(
            createSummaryRow(1, "005930", "삼성전자", lastPrice = 75000, totalShares = 0, totalInvested = 0),
            createSummaryRow(2, "000660", "SK하이닉스", lastPrice = 150000, totalShares = 50, totalInvested = 6_000_000)
        )
        coEvery { portfolioDao.getHoldingSummaries(1L) } returns rows

        val (summary, holdings) = repository.loadPortfolioHoldings(1L, 30)
        assertEquals(1, holdings.size)
        assertEquals("000660", holdings[0].ticker)
        assertEquals(1, summary.holdingsCount)
    }

    @Test
    fun `loadPortfolioHoldings - 수익률 계산`() = runTest {
        val rows = listOf(
            createSummaryRow(1, "005930", "삼성전자", lastPrice = 80000, totalShares = 100, totalInvested = 7_000_000)
        )
        coEvery { portfolioDao.getHoldingSummaries(1L) } returns rows

        val (_, holdings) = repository.loadPortfolioHoldings(1L, 30)
        val item = holdings[0]
        assertEquals(70000, item.avgBuyPrice) // 7_000_000 / 100
        assertEquals(80000L, item.currentPrice)
        // profitLossPercent = (80000 - 70000) / 70000 * 100 = 14.28...
        assertEquals(14.28, item.profitLossPercent, 0.1)
        // profitLossAmount = (80000 - 70000) * 100 = 1_000_000
        assertEquals(1_000_000L, item.profitLossAmount)
    }

    @Test
    fun `getTransactionItems - 거래 내역 변환`() = runTest {
        val transactions = listOf(
            PortfolioTransactionEntity(id = 1, holdingId = 1, date = "20260314", shares = 100, pricePerShare = 70000, createdAt = 100L),
            PortfolioTransactionEntity(id = 2, holdingId = 1, date = "20260315", shares = -50, pricePerShare = 80000, createdAt = 200L)
        )
        coEvery { portfolioDao.getTransactionsListForHolding(1L) } returns transactions

        val items = repository.getTransactionItems(1L, 75000L)
        assertEquals(2, items.size)

        // 매수 거래: (75000 - 70000) / 70000 * 100 = 7.14%
        val buy = items[0]
        assertEquals(100, buy.shares)
        assertEquals(7.14, buy.profitLossPercent, 0.1)
        assertEquals(500_000L, buy.profitLossAmount) // (75000 - 70000) * 100

        // 매도 거래
        val sell = items[1]
        assertEquals(-50, sell.shares)
    }

    @Test
    fun `CRUD 위임 - insertHolding`() = runTest {
        val holding = PortfolioHoldingEntity(
            portfolioId = 1, ticker = "005930", stockName = "삼성전자",
            market = "KOSPI", sector = "반도체"
        )
        coEvery { portfolioDao.insertHolding(holding) } returns 10L

        val id = repository.insertHolding(holding)
        assertEquals(10L, id)
        coVerify { portfolioDao.insertHolding(holding) }
    }

    @Test
    fun `CRUD 위임 - deleteHoldingWithTransactions`() = runTest {
        repository.deleteHoldingWithTransactions(5L)
        coVerify { portfolioDao.deleteHoldingWithTransactions(5L) }
    }

    @Test
    fun `CRUD 위임 - insertTransaction`() = runTest {
        val tx = PortfolioTransactionEntity(
            holdingId = 1, date = "20260314", shares = 50, pricePerShare = 75000
        )
        repository.insertTransaction(tx)
        coVerify { portfolioDao.insertTransaction(tx) }
    }

    @Test
    fun `CRUD 위임 - deleteTransaction`() = runTest {
        repository.deleteTransaction(3L)
        coVerify { portfolioDao.deleteTransaction(3L) }
    }

    @Test
    fun `getAllPortfolios returns flow`() = runTest {
        val portfolios = listOf(PortfolioEntity(id = 1, name = "기본"))
        coEvery { portfolioDao.getAllPortfolios() } returns flowOf(portfolios)

        val flow = repository.getAllPortfolios()
        assertNotNull(flow)
    }
}
