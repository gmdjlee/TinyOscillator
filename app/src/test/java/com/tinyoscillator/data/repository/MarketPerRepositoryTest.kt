package com.tinyoscillator.data.repository

import com.krxkt.KrxIndex
import com.krxkt.model.IndexFundamentalHistory
import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.core.database.dao.MarketPerDao
import com.tinyoscillator.core.database.entity.MarketPerEntity
import com.tinyoscillator.core.util.DateFormats
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MarketPerRepositoryTest {

    private lateinit var marketPerDao: MarketPerDao
    private lateinit var krxApiClient: KrxApiClient
    private lateinit var krxIndex: KrxIndex
    private lateinit var repository: MarketPerRepository
    private val testDispatcher = StandardTestDispatcher()
    private val fmt = DateFormats.yyyyMMdd

    private val today = LocalDate.now().format(fmt)
    private val startDate = LocalDate.now().minusYears(1).format(fmt)
    private val testId = "testUser"
    private val testPw = "testPass"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        marketPerDao = mockk(relaxed = true)
        krxApiClient = mockk(relaxed = true)
        krxIndex = mockk(relaxed = true)
        repository = MarketPerRepository(marketPerDao, krxApiClient)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createCacheEntity(
        market: String = "KOSPI",
        date: String = today,
        per: Double = 12.5
    ) = MarketPerEntity(
        market = market, date = date,
        closeIndex = 2650.0, per = per,
        pbr = 1.1, dividendYield = 1.8
    )

    private fun createApiResult(date: String = today) = IndexFundamentalHistory(
        date = date, close = 2650.0, per = 12.5,
        pbr = 1.1, dividendYield = 1.8
    )

    @Test
    fun `cache hit - returns cached data when latest date is today`() = runTest {
        coEvery { marketPerDao.getLatestDate("KOSPI") } returns today
        coEvery {
            marketPerDao.getByMarketDateRange("KOSPI", startDate, today)
        } returns listOf(createCacheEntity())

        val result = repository.getMarketPerHistory("KOSPI", startDate, today, testId, testPw)

        assertEquals(1, result.rows.size)
        assertEquals(12.5, result.rows[0].per, 0.001)
        assertEquals("KOSPI", result.market)
        // Should not login when cache is fresh
        coVerify(exactly = 0) { krxApiClient.login(any(), any()) }
    }

    @Test
    fun `cache miss - logs in and fetches from API`() = runTest {
        coEvery { marketPerDao.getLatestDate("KOSPI") } returns null
        coEvery { krxApiClient.login(testId, testPw) } returns true
        every { krxApiClient.getKrxIndex() } returns krxIndex
        coEvery {
            krxIndex.getIndexFundamental(startDate, today, "1001")
        } returns listOf(createApiResult())
        coEvery {
            marketPerDao.getByMarketDateRange("KOSPI", startDate, today)
        } returns listOf(createCacheEntity())

        val result = repository.getMarketPerHistory("KOSPI", startDate, today, testId, testPw)

        assertEquals(1, result.rows.size)
        coVerify { krxApiClient.login(testId, testPw) }
        coVerify { marketPerDao.insertAndCleanup(any(), "KOSPI", any()) }
    }

    @Test
    fun `incremental fetch - only fetches new data after latest cached date`() = runTest {
        val yesterday = LocalDate.now().minusDays(1).format(fmt)
        coEvery { marketPerDao.getLatestDate("KOSPI") } returns yesterday
        coEvery { krxApiClient.login(testId, testPw) } returns true
        every { krxApiClient.getKrxIndex() } returns krxIndex
        coEvery {
            krxIndex.getIndexFundamental(today, today, "1001")
        } returns listOf(createApiResult())
        coEvery {
            marketPerDao.getByMarketDateRange("KOSPI", startDate, today)
        } returns listOf(createCacheEntity(date = yesterday), createCacheEntity(date = today))

        val result = repository.getMarketPerHistory("KOSPI", startDate, today, testId, testPw)

        assertEquals(2, result.rows.size)
        coVerify { krxIndex.getIndexFundamental(today, today, "1001") }
    }

    @Test
    fun `krx login failure - returns cached data`() = runTest {
        coEvery { marketPerDao.getLatestDate("KOSPI") } returns null
        coEvery { krxApiClient.login(testId, testPw) } returns false
        coEvery {
            marketPerDao.getByMarketDateRange("KOSPI", startDate, today)
        } returns emptyList()

        val result = repository.getMarketPerHistory("KOSPI", startDate, today, testId, testPw)

        assertEquals(0, result.rows.size)
        coVerify(exactly = 0) { krxIndex.getIndexFundamental(any(), any(), any()) }
    }

    @Test
    fun `api failure - returns cached data gracefully`() = runTest {
        coEvery { marketPerDao.getLatestDate("KOSPI") } returns null
        coEvery { krxApiClient.login(testId, testPw) } returns true
        every { krxApiClient.getKrxIndex() } returns krxIndex
        coEvery {
            krxIndex.getIndexFundamental(any(), any(), any())
        } throws RuntimeException("Network error")
        coEvery {
            marketPerDao.getByMarketDateRange("KOSPI", startDate, today)
        } returns emptyList()

        val result = repository.getMarketPerHistory("KOSPI", startDate, today, testId, testPw)

        assertEquals(0, result.rows.size)
    }

    @Test
    fun `kosdaq market uses correct ticker 2001`() = runTest {
        coEvery { marketPerDao.getLatestDate("KOSDAQ") } returns null
        coEvery { krxApiClient.login(testId, testPw) } returns true
        every { krxApiClient.getKrxIndex() } returns krxIndex
        coEvery {
            krxIndex.getIndexFundamental(startDate, today, "2001")
        } returns listOf(createApiResult())
        coEvery {
            marketPerDao.getByMarketDateRange("KOSDAQ", startDate, today)
        } returns listOf(createCacheEntity(market = "KOSDAQ"))

        val result = repository.getMarketPerHistory("KOSDAQ", startDate, today, testId, testPw)

        assertEquals("KOSDAQ", result.market)
        coVerify { krxIndex.getIndexFundamental(startDate, today, "2001") }
    }

    @Test
    fun `cooldown prevents repeated API calls within 1 hour`() = runTest {
        // First call triggers API
        coEvery { marketPerDao.getLatestDate("KOSPI") } returns startDate
        coEvery { krxApiClient.login(testId, testPw) } returns true
        every { krxApiClient.getKrxIndex() } returns krxIndex
        coEvery {
            krxIndex.getIndexFundamental(any(), any(), any())
        } returns listOf(createApiResult())
        coEvery {
            marketPerDao.getByMarketDateRange("KOSPI", startDate, today)
        } returns listOf(createCacheEntity())

        repository.getMarketPerHistory("KOSPI", startDate, today, testId, testPw)

        // Second call within cooldown should not trigger API
        repository.getMarketPerHistory("KOSPI", startDate, today, testId, testPw)

        coVerify(exactly = 1) { krxIndex.getIndexFundamental(any(), any(), any()) }
    }
}
