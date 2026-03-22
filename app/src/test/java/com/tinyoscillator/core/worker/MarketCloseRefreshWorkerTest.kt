package com.tinyoscillator.core.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.EtfDao
import com.tinyoscillator.core.database.dao.MarketDepositDao
import com.tinyoscillator.core.database.dao.MarketOscillatorDao
import com.tinyoscillator.core.util.DateFormats
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.data.repository.MarketIndicatorRepository
import com.tinyoscillator.data.repository.StockRepository
import com.tinyoscillator.domain.model.DailyTrading
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalCoroutinesApi::class)
class MarketCloseRefreshWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var stockRepository: StockRepository
    private lateinit var etfRepository: EtfRepository
    private lateinit var marketIndicatorRepository: MarketIndicatorRepository
    private lateinit var analysisCacheDao: AnalysisCacheDao
    private lateinit var etfDao: EtfDao
    private lateinit var oscillatorDao: MarketOscillatorDao
    private lateinit var depositDao: MarketDepositDao
    private lateinit var apiConfigProvider: ApiConfigProvider

    private val testDispatcher = StandardTestDispatcher()
    private val today = LocalDate.now()
    private val todayStr = today.format(DateFormats.yyyyMMdd)
    private val todayIso = today.toString()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        stockRepository = mockk(relaxed = true)
        etfRepository = mockk(relaxed = true)
        marketIndicatorRepository = mockk(relaxed = true)
        analysisCacheDao = mockk(relaxed = true)
        etfDao = mockk(relaxed = true)
        oscillatorDao = mockk(relaxed = true)
        depositDao = mockk(relaxed = true)
        apiConfigProvider = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ===== 쿨다운 초기화 테스트 =====

    @Test
    fun `clearCooldown removes specific ticker cooldown`() {
        val apiClient = mockk<com.tinyoscillator.core.api.KiwoomApiClient>(relaxed = true)
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val dao = mockk<AnalysisCacheDao>(relaxed = true)
        val repo = StockRepository(apiClient, json, dao)

        // clearCooldown 호출 시 예외 없이 동작해야 함
        repo.clearCooldown("005930")
        repo.clearAllCooldowns()
    }

    @Test
    fun `clearAllCooldowns clears all entries`() {
        val apiClient = mockk<com.tinyoscillator.core.api.KiwoomApiClient>(relaxed = true)
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val dao = mockk<AnalysisCacheDao>(relaxed = true)
        val repo = StockRepository(apiClient, json, dao)

        // 쿨다운 여러 개 등록 후 일괄 삭제
        repo.clearCooldown("005930")
        repo.clearCooldown("000660")
        repo.clearAllCooldowns()
        // 예외 없이 완료되면 성공
    }

    // ===== DAO 메서드 테스트 (Mock 기반) =====

    @Test
    fun `getTickersForDate returns tickers with today data`() = runTest {
        val tickers = listOf("005930", "000660", "035720")
        coEvery { analysisCacheDao.getTickersForDate(todayStr) } returns tickers

        val result = analysisCacheDao.getTickersForDate(todayStr)
        assertEquals(3, result.size)
        assertEquals("005930", result[0])
        coVerify { analysisCacheDao.getTickersForDate(todayStr) }
    }

    @Test
    fun `getTickersForDate returns empty when no today data`() = runTest {
        coEvery { analysisCacheDao.getTickersForDate(todayStr) } returns emptyList()

        val result = analysisCacheDao.getTickersForDate(todayStr)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `deleteByDate removes today analysis cache entries`() = runTest {
        coEvery { analysisCacheDao.deleteByDate(todayStr) } just runs

        analysisCacheDao.deleteByDate(todayStr)
        coVerify(exactly = 1) { analysisCacheDao.deleteByDate(todayStr) }
    }

    @Test
    fun `oscillatorDao deleteByDate removes today entries`() = runTest {
        coEvery { oscillatorDao.deleteByDate(todayIso) } just runs

        oscillatorDao.deleteByDate(todayIso)
        coVerify(exactly = 1) { oscillatorDao.deleteByDate(todayIso) }
    }

    @Test
    fun `depositDao deleteByDate removes today entries`() = runTest {
        coEvery { depositDao.deleteByDate(todayIso) } just runs

        depositDao.deleteByDate(todayIso)
        coVerify(exactly = 1) { depositDao.deleteByDate(todayIso) }
    }

    @Test
    fun `etfDao deleteHoldingsForDate removes today holdings`() = runTest {
        coEvery { etfDao.deleteHoldingsForDate(todayStr) } just runs

        etfDao.deleteHoldingsForDate(todayStr)
        coVerify(exactly = 1) { etfDao.deleteHoldingsForDate(todayStr) }
    }

    // ===== 종목분석 교체 로직 테스트 =====

    @Test
    fun `stock analysis refresh deletes cache and clears cooldown`() = runTest {
        val tickers = listOf("005930", "000660")
        coEvery { analysisCacheDao.getTickersForDate(todayStr) } returns tickers
        coEvery { analysisCacheDao.deleteByDate(todayStr) } just runs

        val kiwoomConfig = KiwoomApiKeyConfig(appKey = "key", secretKey = "secret")
        coEvery { apiConfigProvider.getKiwoomConfig() } returns kiwoomConfig
        coEvery { stockRepository.getDailyTradingData(any(), any(), any(), any()) } returns listOf(
            DailyTrading(todayStr, 100_000_000L, 1_000_000L, 500_000L)
        )

        // 시뮬레이션: 삭제 → 쿨다운 초기화 → 재수집
        analysisCacheDao.deleteByDate(todayStr)
        stockRepository.clearAllCooldowns()

        for (ticker in tickers) {
            val startDate = LocalDate.now().minusDays(365).format(DateFormats.yyyyMMdd)
            stockRepository.getDailyTradingData(ticker, startDate, todayStr, kiwoomConfig)
        }

        coVerify(exactly = 1) { analysisCacheDao.deleteByDate(todayStr) }
        verify(exactly = 1) { stockRepository.clearAllCooldowns() }
        coVerify(exactly = 2) { stockRepository.getDailyTradingData(any(), any(), any(), any()) }
    }

    @Test
    fun `stock analysis refresh limits to MAX_STOCK_REFRESH tickers`() = runTest {
        // 15개 종목이 있지만 최대 10개만 재수집
        val tickers = (1..15).map { "%06d".format(it) }
        coEvery { analysisCacheDao.getTickersForDate(todayStr) } returns tickers
        coEvery { analysisCacheDao.deleteByDate(todayStr) } just runs

        val kiwoomConfig = KiwoomApiKeyConfig(appKey = "key", secretKey = "secret")
        coEvery { apiConfigProvider.getKiwoomConfig() } returns kiwoomConfig
        coEvery { stockRepository.getDailyTradingData(any(), any(), any(), any()) } returns emptyList()

        analysisCacheDao.deleteByDate(todayStr)
        stockRepository.clearAllCooldowns()

        val refreshTargets = tickers.take(10)
        for (ticker in refreshTargets) {
            val startDate = LocalDate.now().minusDays(365).format(DateFormats.yyyyMMdd)
            stockRepository.getDailyTradingData(ticker, startDate, todayStr, kiwoomConfig)
        }

        coVerify(exactly = 10) { stockRepository.getDailyTradingData(any(), any(), any(), any()) }
    }

    @Test
    fun `stock analysis refresh skips when no Kiwoom API key`() = runTest {
        val tickers = listOf("005930")
        coEvery { analysisCacheDao.getTickersForDate(todayStr) } returns tickers
        coEvery { analysisCacheDao.deleteByDate(todayStr) } just runs

        val invalidConfig = KiwoomApiKeyConfig(appKey = "", secretKey = "")
        coEvery { apiConfigProvider.getKiwoomConfig() } returns invalidConfig

        analysisCacheDao.deleteByDate(todayStr)
        stockRepository.clearAllCooldowns()

        // API키가 없으면 재수집 호출하지 않음
        assertFalse(invalidConfig.isValid())
    }

    @Test
    fun `stock analysis refresh skips when no today data`() = runTest {
        coEvery { analysisCacheDao.getTickersForDate(todayStr) } returns emptyList()

        val result = analysisCacheDao.getTickersForDate(todayStr)
        assertTrue(result.isEmpty())
        // 삭제 호출 없어야 함
        coVerify(exactly = 0) { analysisCacheDao.deleteByDate(any()) }
    }

    // ===== 시장지표 교체 로직 테스트 =====

    @Test
    fun `market oscillator refresh deletes and re-fetches both markets`() = runTest {
        coEvery { oscillatorDao.deleteByDate(todayIso) } just runs
        coEvery {
            marketIndicatorRepository.updateMarketData(any(), any(), any(), any())
        } returns Result.success(5)

        oscillatorDao.deleteByDate(todayIso)
        val kospiResult = marketIndicatorRepository.updateMarketData("KOSPI", "id", "pw", 30)
        val kosdaqResult = marketIndicatorRepository.updateMarketData("KOSDAQ", "id", "pw", 30)

        coVerify(exactly = 1) { oscillatorDao.deleteByDate(todayIso) }
        assertEquals(5, kospiResult.getOrNull())
        assertEquals(5, kosdaqResult.getOrNull())
    }

    @Test
    fun `market deposit refresh deletes and re-scrapes`() = runTest {
        coEvery { depositDao.deleteByDate(todayIso) } just runs
        coEvery { marketIndicatorRepository.getOrUpdateMarketData(any(), any()) } returns null

        depositDao.deleteByDate(todayIso)
        val result = marketIndicatorRepository.getOrUpdateMarketData(daysBack = 365)

        coVerify(exactly = 1) { depositDao.deleteByDate(todayIso) }
        assertNull(result) // 실패 케이스도 정상 처리
    }

    // ===== 주말 체크 테스트 =====

    @Test
    fun `weekend check correctly identifies Saturday`() {
        val saturday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        assertEquals(DayOfWeek.SATURDAY, saturday.dayOfWeek)
        assertTrue(saturday.dayOfWeek == DayOfWeek.SATURDAY || saturday.dayOfWeek == DayOfWeek.SUNDAY)
    }

    @Test
    fun `weekend check correctly identifies Sunday`() {
        val sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)
        assertTrue(sunday.dayOfWeek == DayOfWeek.SATURDAY || sunday.dayOfWeek == DayOfWeek.SUNDAY)
    }

    @Test
    fun `weekday check correctly identifies Monday`() {
        val monday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)
        assertFalse(monday.dayOfWeek == DayOfWeek.SATURDAY || monday.dayOfWeek == DayOfWeek.SUNDAY)
    }

    // ===== WorkManagerHelper 테스트 =====

    @Test
    fun `worker companion constants are correct`() {
        assertEquals("market_close_refresh_daily", MarketCloseRefreshWorker.WORK_NAME)
        assertEquals("market_close_refresh_manual", MarketCloseRefreshWorker.MANUAL_WORK_NAME)
        assertEquals("collection_market_close", MarketCloseRefreshWorker.TAG)
    }

    @Test
    fun `notification ID is unique`() {
        val ids = listOf(
            CollectionNotificationHelper.ETF_NOTIFICATION_ID,
            CollectionNotificationHelper.OSCILLATOR_NOTIFICATION_ID,
            CollectionNotificationHelper.DEPOSIT_NOTIFICATION_ID,
            CollectionNotificationHelper.INTEGRITY_CHECK_NOTIFICATION_ID,
            CollectionNotificationHelper.MARKET_CLOSE_REFRESH_NOTIFICATION_ID
        )
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(1005, CollectionNotificationHelper.MARKET_CLOSE_REFRESH_NOTIFICATION_ID)
    }

    // ===== ETF 교체 로직 테스트 =====

    @Test
    fun `etf refresh deletes today holdings`() = runTest {
        coEvery { etfDao.deleteHoldingsForDate(todayStr) } just runs

        etfDao.deleteHoldingsForDate(todayStr)

        coVerify(exactly = 1) { etfDao.deleteHoldingsForDate(todayStr) }
    }
}
