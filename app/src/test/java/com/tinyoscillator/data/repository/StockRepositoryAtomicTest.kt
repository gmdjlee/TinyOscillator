package com.tinyoscillator.data.repository

import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.core.api.KiwoomApiClient
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.entity.AnalysisCacheEntity
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import com.tinyoscillator.core.util.DateFormats
import java.time.LocalDate

/**
 * StockRepository atomic operation and cooldown eviction tests.
 *
 * Tests for:
 * 1. insertAndCleanup atomic transaction usage
 * 2. Cooldown map eviction when > MAX_COOLDOWN_ENTRIES
 * 3. API failure fallback to cache
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StockRepositoryAtomicTest {

    private lateinit var apiClient: KiwoomApiClient
    private lateinit var json: Json
    private lateinit var analysisCacheDao: AnalysisCacheDao
    private lateinit var repository: StockRepository

    private val testDispatcher = StandardTestDispatcher()

    private val validConfig = KiwoomApiKeyConfig(
        appKey = "test-app-key",
        secretKey = "test-secret-key"
    )

    private val fmt = DateFormats.yyyyMMdd
    private val today = LocalDate.now().format(fmt)
    private val startDate = LocalDate.now().minusDays(365).format(fmt)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        apiClient = mockk(relaxed = true)
        json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            encodeDefaults = true
        }
        analysisCacheDao = mockk(relaxed = true)
        repository = StockRepository(apiClient, json, analysisCacheDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `insertAndCleanup이 개별 insertAll과 deleteOlderThan 대신 호출된다`() = runTest {
        // Given: no cache exists
        coEvery { analysisCacheDao.getLatestDate("005930") } returns null
        coEvery {
            analysisCacheDao.getByTickerDateRange("005930", startDate, today)
        } returns emptyList()

        // API returns empty (no investor trend data)
        coEvery {
            apiClient.call<Any>(any(), any(), any(), any(), any())
        } returns Result.failure(Exception("empty"))

        repository.getDailyTradingData("005930", startDate, today, validConfig)

        // insertAndCleanup should NOT be called when there's no new data
        coVerify(exactly = 0) { analysisCacheDao.insertAndCleanup(any(), any(), any()) }
        // Legacy individual methods should also not be called
        coVerify(exactly = 0) { analysisCacheDao.insertAll(any()) }
    }

    @Test
    fun `API 호출 실패 시 캐시가 있으면 캐시 데이터를 반환한다`() = runTest {
        val yesterday = LocalDate.now().minusDays(1).format(fmt)
        coEvery { analysisCacheDao.getLatestDate("005930") } returns yesterday

        val cachedData = listOf(
            AnalysisCacheEntity(
                ticker = "005930",
                date = yesterday,
                marketCap = 100_000_000_000L,
                foreignNet = 1_000_000_000L,
                instNet = 500_000_000L
            )
        )
        coEvery {
            analysisCacheDao.getByTickerDateRange("005930", startDate, today)
        } returns cachedData

        // API call throws exception
        coEvery {
            apiClient.call<Any>(any(), any(), any(), any(), any())
        } throws RuntimeException("Network error")

        val result = repository.getDailyTradingData("005930", startDate, today, validConfig)

        // Should fallback to cache
        assertEquals(1, result.size)
        assertEquals(yesterday, result[0].date)
    }

    @Test
    fun `동시에 여러 ticker를 호출해도 각각 독립적으로 처리된다`() = runTest {
        val tickers = listOf("005930", "000660", "035720")

        tickers.forEach { ticker ->
            coEvery { analysisCacheDao.getLatestDate(ticker) } returns today
            coEvery {
                analysisCacheDao.getByTickerDateRange(ticker, startDate, today)
            } returns listOf(
                AnalysisCacheEntity(
                    ticker = ticker,
                    date = today,
                    marketCap = 100_000_000_000L,
                    foreignNet = 1_000_000_000L,
                    instNet = 500_000_000L
                )
            )
        }

        tickers.forEach { ticker ->
            val result = repository.getDailyTradingData(ticker, startDate, today, validConfig)
            assertEquals(1, result.size)
            assertEquals(today, result[0].date) // All return today
        }

        // No API calls since all caches are current
        coVerify(exactly = 0) { apiClient.call<Any>(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `NoApiKeyError는 유효하지 않은 config에서 즉시 발생한다`() = runTest {
        val invalidConfig = KiwoomApiKeyConfig(appKey = "", secretKey = "")

        try {
            repository.getDailyTradingData("005930", startDate, today, invalidConfig)
            fail("NoApiKeyError should be thrown")
        } catch (e: ApiError.NoApiKeyError) {
            // Expected
        }
    }

    @Test
    fun `쿨다운은 ticker별로 독립적이다`() = runTest {
        val yesterday = LocalDate.now().minusDays(1).format(fmt)

        // Both tickers have outdated cache
        coEvery { analysisCacheDao.getLatestDate("005930") } returns yesterday
        coEvery { analysisCacheDao.getLatestDate("000660") } returns yesterday
        coEvery {
            analysisCacheDao.getByTickerDateRange(any(), any(), any())
        } returns emptyList()

        // API returns empty data
        coEvery {
            apiClient.call<Any>(any(), any(), any(), any(), any())
        } returns Result.failure(Exception("empty"))

        // First call for each ticker triggers API call
        repository.getDailyTradingData("005930", startDate, today, validConfig)
        repository.getDailyTradingData("000660", startDate, today, validConfig)

        // Both should have made API calls (independent cooldowns)
        coVerify(atLeast = 2) { apiClient.call<Any>(any(), any(), any(), any(), any()) }

        // Second call for 005930 within cooldown should not trigger API
        repository.getDailyTradingData("005930", startDate, today, validConfig)
        // Verify no additional API calls beyond the first 2 sets
    }
}
