package com.tinyoscillator.data.repository

import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.ConsensusReportDao
import com.tinyoscillator.core.database.entity.AnalysisCacheEntity
import com.tinyoscillator.core.database.entity.ConsensusReportEntity
import com.tinyoscillator.core.scraper.EquityReportScraper
import com.tinyoscillator.domain.model.ConsensusFilter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ConsensusRepositoryTest {

    private lateinit var dao: ConsensusReportDao
    private lateinit var scraper: EquityReportScraper
    private lateinit var analysisCacheDao: AnalysisCacheDao
    private lateinit var repository: ConsensusRepository

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        scraper = mockk(relaxed = true)
        analysisCacheDao = mockk(relaxed = true)
        repository = ConsensusRepository(dao, scraper, analysisCacheDao)
    }

    private fun createEntity(
        ticker: String = "005930",
        date: String = "2026-03-23",
        category: String = "IT",
        opinion: String = "Buy",
        prevOpinion: String = "Hold",
        targetPrice: Long = 300000L,
        currentPrice: Long = 212000L
    ) = ConsensusReportEntity(
        writeDate = date,
        category = category,
        prevOpinion = prevOpinion,
        opinion = opinion,
        title = "테스트($ticker)",
        stockTicker = ticker,
        author = "홍길동",
        institution = "미래에셋",
        targetPrice = targetPrice,
        currentPrice = currentPrice,
        divergenceRate = if (currentPrice > 0) (targetPrice - currentPrice).toDouble() / currentPrice * 100.0 else 0.0
    )

    // ========== getReports ==========

    @Test
    fun `getReports - category filter returns matching reports only`() = runTest {
        val entities = listOf(
            createEntity(category = "IT"),
            createEntity(category = "바이오", ticker = "068270")
        )
        coEvery { dao.getAll() } returns entities

        val result = repository.getReports(ConsensusFilter(category = "IT"))
        assertEquals(1, result.size)
        assertEquals("IT", result[0].category)
    }

    @Test
    fun `getReports - opinion filter returns matching reports only`() = runTest {
        val entities = listOf(
            createEntity(opinion = "Buy"),
            createEntity(opinion = "Sell", ticker = "068270")
        )
        coEvery { dao.getAll() } returns entities

        val result = repository.getReports(ConsensusFilter(opinion = "Buy"))
        assertEquals(1, result.size)
        assertEquals("Buy", result[0].opinion)
    }

    @Test
    fun `getReports - combined filters narrow results`() = runTest {
        val entities = listOf(
            createEntity(category = "IT", opinion = "Buy"),
            createEntity(category = "IT", opinion = "Sell", ticker = "068270"),
            createEntity(category = "바이오", opinion = "Buy", ticker = "035420")
        )
        coEvery { dao.getAll() } returns entities

        val result = repository.getReports(ConsensusFilter(category = "IT", opinion = "Buy"))
        assertEquals(1, result.size)
        assertEquals("005930", result[0].stockTicker)
    }

    // ========== getReportsByTicker ==========

    @Test
    fun `getReportsByTicker - returns mapped domain objects`() = runTest {
        val entities = listOf(
            createEntity(date = "2026-03-20"),
            createEntity(date = "2026-03-21")
        )
        coEvery { dao.getByTicker("005930") } returns entities

        val result = repository.getReportsByTicker("005930")
        assertEquals(2, result.size)
        assertEquals("2026-03-20", result[0].writeDate)
        assertEquals("2026-03-21", result[1].writeDate)
        assertEquals("005930", result[0].stockTicker)
    }

    // ========== getFilterOptions ==========

    @Test
    fun `getFilterOptions - returns correct options from dao`() = runTest {
        coEvery { dao.getDistinctCategories() } returns listOf("IT", "바이오")
        coEvery { dao.getDistinctPrevOpinions() } returns listOf("Hold")
        coEvery { dao.getDistinctOpinions() } returns listOf("Buy", "Sell")
        coEvery { dao.getDistinctAuthors() } returns listOf("홍길동")
        coEvery { dao.getDistinctInstitutions() } returns listOf("미래에셋")

        val options = repository.getFilterOptions()
        assertEquals(listOf("IT", "바이오"), options.categories)
        assertEquals(listOf("Buy", "Sell"), options.opinions)
        assertEquals(listOf("홍길동"), options.authors)
    }

    // ========== getConsensusChartData ==========

    @Test
    fun `getConsensusChartData - returns merged chart data`() = runTest {
        val reports = listOf(
            createEntity(date = "2026-03-20", targetPrice = 300000L),
            createEntity(date = "2026-03-23", targetPrice = 310000L)
        )
        coEvery { dao.getByTicker("005930") } returns reports

        val cacheEntries = listOf(
            AnalysisCacheEntity("005930", "20260320", 500_000_000_000L, 0L, 0L, 0),
            AnalysisCacheEntity("005930", "20260323", 520_000_000_000L, 0L, 0L, 0)
        )
        coEvery { analysisCacheDao.getByTickerDateRange("005930", "20260320", "20260323") } returns cacheEntries

        val result = repository.getConsensusChartData("005930", "삼성전자")
        assertNotNull(result)
        assertEquals("005930", result!!.ticker)
        assertEquals("삼성전자", result.stockName)
        assertEquals(2, result.dates.size)
        assertEquals("2026-03-20", result.dates[0])
        assertEquals(2, result.reportTargetPrices.size)
    }

    @Test
    fun `getConsensusChartData - empty reports returns null`() = runTest {
        coEvery { dao.getByTicker("999999") } returns emptyList()

        val result = repository.getConsensusChartData("999999", "없는종목")
        assertNull(result)
    }

    // ========== getCount ==========

    @Test
    fun `getCount - delegates to dao`() = runTest {
        coEvery { dao.getCount() } returns 42

        val result = repository.getCount()
        assertEquals(42, result)
        coVerify { dao.getCount() }
    }
}
