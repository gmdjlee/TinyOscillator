package com.tinyoscillator.data.repository

import com.krxkt.KrxStock
import com.krxkt.model.StockFundamentalHistory
import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.core.database.dao.FundamentalCacheDao
import com.tinyoscillator.core.database.entity.FundamentalCacheEntity
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import com.tinyoscillator.core.util.DateFormats
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class FundamentalHistoryRepositoryTest {

    private lateinit var fundamentalCacheDao: FundamentalCacheDao
    private lateinit var krxApiClient: KrxApiClient
    private lateinit var krxStock: KrxStock
    private lateinit var repository: FundamentalHistoryRepository
    private val testDispatcher = StandardTestDispatcher()
    private val fmt = DateFormats.yyyyMMdd

    private val testTicker = "005930"
    private val today = LocalDate.now().format(fmt)
    private val startDate = LocalDate.now().minusYears(1).format(fmt)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fundamentalCacheDao = mockk(relaxed = true)
        krxApiClient = mockk(relaxed = true)
        krxStock = mockk(relaxed = true)
        repository = FundamentalHistoryRepository(fundamentalCacheDao, krxApiClient)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createCacheEntity(
        ticker: String = testTicker,
        date: String = today,
        per: Double = 10.0,
        pbr: Double = 1.5
    ) = FundamentalCacheEntity(
        ticker = ticker, date = date,
        close = 70000L, eps = 5000L, per = per,
        bps = 40000L, pbr = pbr, dps = 1416L, dividendYield = 2.0
    )

    private fun createApiResult(date: String = today) = StockFundamentalHistory(
        date = date, close = 70000L, eps = 5000L, per = 10.0,
        bps = 40000L, pbr = 1.5, dps = 1416L, dividendYield = 2.0
    )

    @Test
    fun `cache hit - returns cached data when latest date is today`() = runTest {
        coEvery { fundamentalCacheDao.getLatestDate(testTicker) } returns today
        coEvery {
            fundamentalCacheDao.getByTickerDateRange(testTicker, startDate, today)
        } returns listOf(createCacheEntity())

        val result = repository.getFundamentalHistory(testTicker, startDate, today)

        assertEquals(1, result.size)
        assertEquals(10.0, result[0].per, 0.001)
        coVerify(exactly = 0) { krxApiClient.getKrxStock() }
    }

    @Test
    fun `cache miss - fetches from API when no cache`() = runTest {
        coEvery { fundamentalCacheDao.getLatestDate(testTicker) } returns null
        every { krxApiClient.getKrxStock() } returns krxStock
        coEvery {
            krxStock.getFundamentalByTicker(startDate, today, testTicker)
        } returns listOf(createApiResult())
        coEvery {
            fundamentalCacheDao.getByTickerDateRange(testTicker, startDate, today)
        } returns listOf(createCacheEntity())

        val result = repository.getFundamentalHistory(testTicker, startDate, today)

        assertEquals(1, result.size)
        coVerify { fundamentalCacheDao.insertAndCleanup(any(), testTicker, any()) }
    }

    @Test
    fun `incremental fetch - only fetches new dates`() = runTest {
        val cachedDate = LocalDate.now().minusDays(5).format(fmt)
        val nextDay = LocalDate.now().minusDays(4).format(fmt)

        coEvery { fundamentalCacheDao.getLatestDate(testTicker) } returns cachedDate
        every { krxApiClient.getKrxStock() } returns krxStock
        coEvery {
            krxStock.getFundamentalByTicker(nextDay, today, testTicker)
        } returns listOf(createApiResult())
        coEvery {
            fundamentalCacheDao.getByTickerDateRange(testTicker, startDate, today)
        } returns listOf(createCacheEntity())

        repository.getFundamentalHistory(testTicker, startDate, today)

        coVerify { krxStock.getFundamentalByTicker(nextDay, today, testTicker) }
    }

    @Test
    fun `API failure - returns cache as fallback`() = runTest {
        val cachedDate = LocalDate.now().minusDays(5).format(fmt)
        coEvery { fundamentalCacheDao.getLatestDate(testTicker) } returns cachedDate
        every { krxApiClient.getKrxStock() } returns krxStock
        coEvery {
            krxStock.getFundamentalByTicker(any(), any(), any())
        } throws RuntimeException("Network error")
        coEvery {
            fundamentalCacheDao.getByTickerDateRange(testTicker, startDate, today)
        } returns listOf(createCacheEntity(date = cachedDate))

        val result = repository.getFundamentalHistory(testTicker, startDate, today)

        assertEquals(1, result.size)
    }

    @Test
    fun `KRX not logged in - returns empty cache`() = runTest {
        coEvery { fundamentalCacheDao.getLatestDate(testTicker) } returns null
        every { krxApiClient.getKrxStock() } returns null
        coEvery {
            fundamentalCacheDao.getByTickerDateRange(testTicker, startDate, today)
        } returns emptyList()

        val result = repository.getFundamentalHistory(testTicker, startDate, today)

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { fundamentalCacheDao.insertAndCleanup(any(), any(), any()) }
    }
}
