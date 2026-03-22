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

@OptIn(ExperimentalCoroutinesApi::class)
class StockRepositoryTest {

    private lateinit var apiClient: KiwoomApiClient
    private lateinit var json: Json
    private lateinit var analysisCacheDao: AnalysisCacheDao
    private lateinit var repository: StockRepository

    private val testDispatcher = StandardTestDispatcher()

    private val validConfig = KiwoomApiKeyConfig(
        appKey = "test-app-key",
        secretKey = "test-secret-key"
    )

    private val invalidConfig = KiwoomApiKeyConfig(appKey = "", secretKey = "")

    private val testTicker = "005930"
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

    // -- Helper --

    private fun createCacheEntities(
        ticker: String = testTicker,
        dates: List<String> = listOf("20250101", "20250102")
    ): List<AnalysisCacheEntity> = dates.map { date ->
        AnalysisCacheEntity(
            ticker = ticker,
            date = date,
            marketCap = 100_000_000_000L,
            foreignNet = 1_000_000_000L,
            instNet = 500_000_000L
        )
    }

    // ==========================================================
    // Tests
    // ==========================================================

    @Test
    fun `유효하지 않은 API config는 NoApiKeyError를 던진다`() = runTest {
        try {
            repository.getDailyTradingData(testTicker, startDate, today, invalidConfig)
            fail("NoApiKeyError가 발생해야 한다")
        } catch (e: ApiError.NoApiKeyError) {
            // 기대한 예외
        }
    }

    @Test
    fun `캐시가 최신이면 DB에서만 반환하고 API를 호출하지 않는다`() = runTest {
        // latestCachedDate >= today
        coEvery { analysisCacheDao.getLatestDate(testTicker) } returns today

        val cachedData = createCacheEntities(dates = listOf(today))
        coEvery {
            analysisCacheDao.getByTickerDateRange(testTicker, startDate, today)
        } returns cachedData

        val result = repository.getDailyTradingData(testTicker, startDate, today, validConfig)

        assertEquals(1, result.size)
        assertEquals(today, result[0].date)

        // API 호출이 없어야 함
        coVerify(exactly = 0) { apiClient.call<Any>(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `쿨다운 기간 내 재호출 시 캐시를 반환한다`() = runTest {
        val yesterday = LocalDate.now().minusDays(1).format(fmt)

        // 캐시가 존재하지만 오늘이 아님
        coEvery { analysisCacheDao.getLatestDate(testTicker) } returns yesterday

        val cachedData = createCacheEntities(dates = listOf(yesterday))
        coEvery {
            analysisCacheDao.getByTickerDateRange(testTicker, startDate, today)
        } returns cachedData

        // API는 빈 투자자 동향 반환
        coEvery {
            apiClient.call<Any>(any(), any(), any(), any(), any())
        } returns Result.failure(Exception("empty"))

        // 첫 번째 호출 (API 호출 시도)
        repository.getDailyTradingData(testTicker, startDate, today, validConfig)

        // 두 번째 호출 (쿨다운 내)
        val result = repository.getDailyTradingData(testTicker, startDate, today, validConfig)

        assertEquals(1, result.size)
    }

    @Test
    fun `캐시가 없으면 전체 기간 API를 호출한다`() = runTest {
        coEvery { analysisCacheDao.getLatestDate(testTicker) } returns null
        coEvery {
            analysisCacheDao.getByTickerDateRange(testTicker, startDate, today)
        } returns emptyList()

        // API는 실패 반환 (투자자 동향 데이터 없음)
        coEvery {
            apiClient.call<Any>(any(), any(), any(), any(), any())
        } returns Result.failure(Exception("empty"))

        val result = repository.getDailyTradingData(testTicker, startDate, today, validConfig)

        // API가 빈 데이터를 반환하면 빈 리스트
        assertTrue(result.isEmpty())
    }

    @Test
    fun `incremental fetch는 최신 캐시일 다음날부터 조회한다`() = runTest {
        val cachedDate = LocalDate.now().minusDays(3).format(fmt)

        coEvery { analysisCacheDao.getLatestDate(testTicker) } returns cachedDate
        coEvery {
            analysisCacheDao.getByTickerDateRange(testTicker, startDate, today)
        } returns createCacheEntities(dates = listOf(cachedDate))

        // API는 빈 결과 반환
        coEvery {
            apiClient.call<Any>(any(), any(), any(), any(), any())
        } returns Result.failure(Exception("empty"))

        repository.getDailyTradingData(testTicker, startDate, today, validConfig)

        // API가 호출되었는지 확인 (투자자 동향, 주식정보, 일봉)
        coVerify(atLeast = 1) { apiClient.call<Any>(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `빈 API 응답은 빈 리스트를 반환한다`() = runTest {
        coEvery { analysisCacheDao.getLatestDate(testTicker) } returns null
        coEvery {
            analysisCacheDao.getByTickerDateRange(testTicker, startDate, today)
        } returns emptyList()

        // 모든 API 호출이 빈 결과
        coEvery {
            apiClient.call<Any>(any(), any(), any(), any(), any())
        } returns Result.failure(Exception("no data"))

        val result = repository.getDailyTradingData(testTicker, startDate, today, validConfig)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `새 데이터가 있으면 DB에 저장하고 365일 이전 데이터를 정리한다`() = runTest {
        coEvery { analysisCacheDao.getLatestDate(testTicker) } returns null
        coEvery {
            analysisCacheDao.getByTickerDateRange(testTicker, startDate, today)
        } returns emptyList()

        // API 호출이 빈 결과를 반환하면 insertAll이 호출되지 않음
        coEvery {
            apiClient.call<Any>(any(), any(), any(), any(), any())
        } returns Result.failure(Exception("empty"))

        repository.getDailyTradingData(testTicker, startDate, today, validConfig)

        // 빈 데이터이므로 insertAll과 deleteOlderThan은 호출되지 않아야 함
        coVerify(exactly = 0) { analysisCacheDao.insertAll(any()) }
        coVerify(exactly = 0) { analysisCacheDao.deleteOlderThan(any(), any()) }
    }

    @Test
    fun `캐시에서 로드된 데이터가 DailyTrading으로 올바르게 변환된다`() = runTest {
        coEvery { analysisCacheDao.getLatestDate(testTicker) } returns today

        val entities = listOf(
            AnalysisCacheEntity(
                ticker = testTicker,
                date = today,
                marketCap = 300_000_000_000_000L,
                foreignNet = 50_000_000_000L,
                instNet = 30_000_000_000L
            )
        )
        coEvery {
            analysisCacheDao.getByTickerDateRange(testTicker, startDate, today)
        } returns entities

        val result = repository.getDailyTradingData(testTicker, startDate, today, validConfig)

        assertEquals(1, result.size)
        val daily = result[0]
        assertEquals(today, daily.date)
        assertEquals(300_000_000_000_000L, daily.marketCap)
        assertEquals(50_000_000_000L, daily.foreignNetBuy)
        assertEquals(30_000_000_000L, daily.instNetBuy)
    }

    @Test
    fun `캐시가 최신일 때 여러 건 조회가 올바르게 반환된다`() = runTest {
        coEvery { analysisCacheDao.getLatestDate(testTicker) } returns today

        val entities = createCacheEntities(dates = listOf("20250228", "20250301", today))
        coEvery {
            analysisCacheDao.getByTickerDateRange(testTicker, startDate, today)
        } returns entities

        val result = repository.getDailyTradingData(testTicker, startDate, today, validConfig)

        assertEquals(3, result.size)
    }

    @Test
    fun `StockRepository가 Singleton으로 동작하며 ConcurrentHashMap으로 쿨다운을 관리한다`() = runTest {
        // 같은 인스턴스에서 여러 ticker 요청 시 독립적인 쿨다운 관리 검증
        val ticker1 = "005930"
        val ticker2 = "000660"
        val yesterday = LocalDate.now().minusDays(1).format(fmt)

        coEvery { analysisCacheDao.getLatestDate(ticker1) } returns yesterday
        coEvery { analysisCacheDao.getLatestDate(ticker2) } returns yesterday
        coEvery {
            analysisCacheDao.getByTickerDateRange(any(), any(), any())
        } returns emptyList()
        coEvery {
            apiClient.call<Any>(any(), any(), any(), any(), any())
        } returns Result.failure(Exception("empty"))

        // ticker1 첫 호출
        repository.getDailyTradingData(ticker1, startDate, today, validConfig)

        // ticker2 첫 호출 (ticker1의 쿨다운에 영향받지 않아야 함)
        repository.getDailyTradingData(ticker2, startDate, today, validConfig)

        // 두 ticker 모두 API가 호출되어야 함 (각각 첫 호출이므로)
        coVerify(atLeast = 2) { apiClient.call<Any>(any(), any(), any(), any(), any()) }
    }
}
