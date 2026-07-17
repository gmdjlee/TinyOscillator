package com.tinyoscillator.presentation.report

import androidx.lifecycle.SavedStateHandle
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.core.api.InvestmentMode
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.entity.AnalysisCacheEntity
import com.tinyoscillator.data.repository.ConsensusRepository
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.data.repository.FinancialRepository
import com.tinyoscillator.data.repository.StockRepository
import com.tinyoscillator.domain.model.*
import com.tinyoscillator.domain.usecase.CalcOscillatorUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var consensusRepository: ConsensusRepository
    private lateinit var analysisCacheDao: AnalysisCacheDao
    private lateinit var stockRepository: StockRepository
    private lateinit var calcOscillator: CalcOscillatorUseCase
    private lateinit var financialRepository: FinancialRepository
    private lateinit var etfRepository: EtfRepository
    private lateinit var apiConfigProvider: ApiConfigProvider

    private val testTicker = "005930"
    private val testWriteDate = "2026-03-23"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        consensusRepository = mockk(relaxed = true)
        analysisCacheDao = mockk(relaxed = true)
        stockRepository = mockk(relaxed = true)
        calcOscillator = mockk(relaxed = true)
        financialRepository = mockk(relaxed = true)
        etfRepository = mockk(relaxed = true)
        apiConfigProvider = mockk(relaxed = true)

        // Default: invalid API configs (skip chart/financial)
        coEvery { apiConfigProvider.getKiwoomConfig() } returns KiwoomApiKeyConfig("", "")
        coEvery { apiConfigProvider.getKisConfig() } returns KisApiKeyConfig("", "")
        coEvery { analysisCacheDao.getLatestDate(any()) } returns null
        coEvery { etfRepository.getLatestDate() } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(
        ticker: String = testTicker,
        writeDate: String = testWriteDate
    ): ReportDetailViewModel {
        val savedState = SavedStateHandle(mapOf("ticker" to ticker, "writeDate" to writeDate))
        return ReportDetailViewModel(
            consensusRepository, analysisCacheDao, stockRepository,
            calcOscillator, financialRepository, etfRepository,
            apiConfigProvider, savedState
        )
    }

    private fun createReport(
        ticker: String = testTicker,
        writeDate: String = testWriteDate,
        targetPrice: Long = 300000L
    ) = ConsensusReport(
        writeDate = writeDate,
        category = "IT",
        prevOpinion = "Hold",
        opinion = "Buy",
        title = "테스트 리포트",
        stockTicker = ticker,
        stockName = "삼성전자",
        author = "홍길동",
        institution = "미래에셋",
        targetPrice = targetPrice,
        currentPrice = 212000L,
        divergenceRate = 41.51
    )

    @Test
    fun `valid ticker - loads report successfully`() = runTest {
        val report = createReport()
        coEvery { consensusRepository.getReportsByTicker(testTicker) } returns listOf(report)

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.headerLoaded)
        assertNull(state.error)
        assertEquals(report, state.report)
        assertEquals("삼성전자", state.report?.stockName)
    }

    @Test
    fun `blank initial selection - skips load and keeps default state without crash`() = runTest {
        val vm = createViewModel(ticker = "", writeDate = "")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(ReportDetailUiState(), state)
        assertFalse(state.headerLoaded)
        assertFalse(state.chartLoaded)
        assertFalse(state.financialLoaded)
        assertFalse(state.etfLoaded)
        assertNull(state.error)
        coVerify(exactly = 0) { consensusRepository.getReportsByTicker(any()) }
    }

    @Test
    fun `blank ticker only - skips load and keeps default state`() = runTest {
        val vm = createViewModel(ticker = "", writeDate = testWriteDate)
        advanceUntilIdle()

        assertEquals(ReportDetailUiState(), vm.uiState.value)
    }

    @Test
    fun `blank writeDate only - skips load and keeps default state`() = runTest {
        val vm = createViewModel(ticker = testTicker, writeDate = "")
        advanceUntilIdle()

        assertEquals(ReportDetailUiState(), vm.uiState.value)
    }

    @Test
    fun `report not found for writeDate - falls back to first report`() = runTest {
        val otherReport = createReport(writeDate = "2026-03-20")
        coEvery { consensusRepository.getReportsByTicker(testTicker) } returns listOf(otherReport)

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNotNull(state.report)
        assertEquals("2026-03-20", state.report?.writeDate)
    }

    @Test
    fun `loads price and marketCap from cache`() = runTest {
        coEvery { consensusRepository.getReportsByTicker(testTicker) } returns listOf(createReport())
        coEvery { analysisCacheDao.getLatestDate(testTicker) } returns "20260323"
        coEvery {
            analysisCacheDao.getByTickerDateRange(testTicker, "20260323", "20260323")
        } returns listOf(
            AnalysisCacheEntity(
                ticker = testTicker,
                date = "20260323",
                marketCap = 500_000_000_000_000L,
                foreignNet = 0L,
                instNet = 0L,
                closePrice = 75000
            )
        )

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(75000, state.currentPrice)
        assertEquals(500_000_000_000_000L, state.marketCap)
    }

    @Test
    fun `divergenceRate calculation - positive when target above current`() = runTest {
        val report = createReport(targetPrice = 100000L)
        coEvery { consensusRepository.getReportsByTicker(testTicker) } returns listOf(report)
        coEvery { analysisCacheDao.getLatestDate(testTicker) } returns "20260323"
        coEvery {
            analysisCacheDao.getByTickerDateRange(testTicker, "20260323", "20260323")
        } returns listOf(
            AnalysisCacheEntity(testTicker, "20260323", 0L, 0L, 0L, 50000)
        )

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        // (100000 - 50000) / 50000 * 100 = 100.0
        assertEquals(100.0, state.divergenceRate, 0.01)
    }

    @Test
    fun `divergenceRate fallback - uses report price when no cache`() = runTest {
        val report = createReport(targetPrice = 100000L)
        coEvery { consensusRepository.getReportsByTicker(testTicker) } returns listOf(report)

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        // 캐시 없으면 리포트 현재가(212000) 사용: (100000-212000)/212000*100 = -52.83
        assertEquals(212000, state.currentPrice)
        assertEquals(-52.83, state.divergenceRate, 0.01)
    }

    @Test
    fun `divergenceRate - uses report divergenceRate when no prices available`() = runTest {
        val report = ConsensusReport(
            writeDate = testWriteDate, category = "IT", prevOpinion = "", opinion = "Buy",
            title = "테스트", stockTicker = testTicker, stockName = "삼성전자",
            author = "홍길동", institution = "미래에셋",
            targetPrice = 0L, currentPrice = 0L, divergenceRate = 15.5
        )
        coEvery { consensusRepository.getReportsByTicker(testTicker) } returns listOf(report)

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(15.5, vm.uiState.value.divergenceRate, 0.01)
    }

    @Test
    fun `financial data failure - shows null financialSummary`() = runTest {
        coEvery { consensusRepository.getReportsByTicker(testTicker) } returns listOf(createReport())
        coEvery { apiConfigProvider.getKisConfig() } returns KisApiKeyConfig("key", "secret", InvestmentMode.MOCK)
        coEvery { financialRepository.getFinancialData(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("API error"))

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.financialSummary)
        assertNull(state.latestStability)
        assertNull(state.error) // should not set error for partial failure
        // 실패한 섹션의 코루틴이 끝까지 실행돼 로딩 카드가 걷혔음을 보장 (섹션 자체가 안 돌면 기본값과 구분 불가)
        assertTrue(state.financialLoaded)
    }

    @Test
    fun `oscillator data failure - shows null chartData`() = runTest {
        coEvery { consensusRepository.getReportsByTicker(testTicker) } returns listOf(createReport())
        coEvery { apiConfigProvider.getKiwoomConfig() } returns KiwoomApiKeyConfig("key", "secret")
        coEvery { stockRepository.getDailyTradingData(any(), any(), any(), any()) } throws
            RuntimeException("Network error")

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.chartData)
        assertNull(state.error) // should not set error for partial failure
        // 실패한 섹션의 코루틴이 끝까지 실행돼 로딩 카드가 걷혔음을 보장 (섹션 자체가 안 돌면 기본값과 구분 불가)
        assertTrue(state.chartLoaded)
    }

    @Test
    fun `ETF holding count - returns correct count`() = runTest {
        coEvery { consensusRepository.getReportsByTicker(testTicker) } returns listOf(createReport())
        coEvery { etfRepository.getLatestDate() } returns "20260323"
        coEvery { etfRepository.getEtfsHoldingStock(testTicker, "20260323") } returns
            listOf(mockk(), mockk(), mockk()) // 3 ETFs

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(3, vm.uiState.value.etfHoldingCount)
    }

    @Test
    fun `ETF holding count - zero when no date available`() = runTest {
        coEvery { consensusRepository.getReportsByTicker(testTicker) } returns listOf(createReport())
        coEvery { etfRepository.getLatestDate() } returns null

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.etfHoldingCount)
    }

    // --- 2-Pane selectReport 배선 ---

    @Test
    fun `selectReport with different report - resets state then reloads`() = runTest {
        val reportA = createReport(ticker = testTicker, writeDate = testWriteDate)
        val otherTicker = "000660"
        val otherWriteDate = "2026-04-01"
        val reportB = createReport(ticker = otherTicker, writeDate = otherWriteDate)
        coEvery { consensusRepository.getReportsByTicker(testTicker) } returns listOf(reportA)
        coEvery { consensusRepository.getReportsByTicker(otherTicker) } returns listOf(reportB)

        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(reportA, vm.uiState.value.report)

        vm.selectReport(otherTicker, otherWriteDate)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.headerLoaded)
        assertEquals(reportB, state.report)
    }

    @Test
    fun `selectReport with same values - is a no-op, does not reload`() = runTest {
        coEvery { consensusRepository.getReportsByTicker(testTicker) } returns listOf(createReport())

        val vm = createViewModel()
        advanceUntilIdle()
        coVerify(exactly = 1) { consensusRepository.getReportsByTicker(testTicker) }

        // 동일 ticker/writeDate 재호출 — StateFlow 동등성으로 재방출 없음
        vm.selectReport(testTicker, testWriteDate)
        advanceUntilIdle()

        coVerify(exactly = 1) { consensusRepository.getReportsByTicker(testTicker) }
    }

    @Test
    fun `rapid selectReport calls - cancels stale load, final state matches latest selection`() = runTest {
        val slowTicker = "111111"
        val slowWriteDate = "2026-05-01"
        val slowReport = createReport(ticker = slowTicker, writeDate = slowWriteDate)
        val fastTicker = "222222"
        val fastWriteDate = "2026-05-02"
        val fastReport = createReport(ticker = fastTicker, writeDate = fastWriteDate)

        coEvery { consensusRepository.getReportsByTicker(testTicker) } returns listOf(createReport())
        // 완료 플래그 — StateFlow conflation만으로도 최종 상태는 fastReport가 되므로,
        // 취소 자체는 "느린 로드 본문이 delay 이후를 실행하지 못했다"로 관측해야 한다.
        // collectLatest를 collect로 바꾸는 회귀 시 delay가 끝까지 실행되어 이 플래그가 true가 된다.
        var slowCompleted = false
        coEvery { consensusRepository.getReportsByTicker(slowTicker) } coAnswers {
            delay(5_000)
            slowCompleted = true
            listOf(slowReport)
        }
        coEvery { consensusRepository.getReportsByTicker(fastTicker) } returns listOf(fastReport)

        val vm = createViewModel()
        advanceUntilIdle()

        // slowTicker 선택 — 로드가 delay(5000)에서 멈춘 상태로 진행시킨다
        vm.selectReport(slowTicker, slowWriteDate)
        testDispatcher.scheduler.runCurrent()

        // 완료 전에 fastTicker로 전환 — collectLatest가 slowTicker 로드를 취소해야 한다
        vm.selectReport(fastTicker, fastWriteDate)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(fastReport, state.report)
        assertTrue(state.headerLoaded)
        assertFalse("stale 로드는 취소되어 delay 이후 본문이 실행되면 안 된다", slowCompleted)
    }

    @Test
    fun `selectReport - stale marketCap from previous selection does not leak into new report`() = runTest {
        // A(testTicker)는 캐시 시총 500조 보유, B(000660)는 캐시 없음.
        // 리셋(_uiState = ReportDetailUiState())이 빠지면 B 헤더가
        // `if (cachedMarketCap > 0) cachedMarketCap else it.marketCap`에서 A의 500조를 보존한다.
        val reportA = createReport(ticker = testTicker, writeDate = testWriteDate)
        val otherTicker = "000660"
        val otherWriteDate = "2026-04-01"
        val reportB = createReport(ticker = otherTicker, writeDate = otherWriteDate)
        coEvery { consensusRepository.getReportsByTicker(testTicker) } returns listOf(reportA)
        coEvery { consensusRepository.getReportsByTicker(otherTicker) } returns listOf(reportB)
        coEvery { analysisCacheDao.getLatestDate(testTicker) } returns "20260323"
        coEvery {
            analysisCacheDao.getByTickerDateRange(testTicker, "20260323", "20260323")
        } returns listOf(
            AnalysisCacheEntity(testTicker, "20260323", 500_000_000_000_000L, 0L, 0L, 75000)
        )
        coEvery { analysisCacheDao.getLatestDate(otherTicker) } returns null

        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(500_000_000_000_000L, vm.uiState.value.marketCap)
        assertEquals(75000, vm.uiState.value.currentPrice)

        vm.selectReport(otherTicker, otherWriteDate)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(reportB, state.report)
        assertEquals("이전 선택의 시총이 리셋 없이 잔존하면 안 된다", 0L, state.marketCap)
        assertEquals("캐시 없으면 리포트 현재가 폴백", 212000, state.currentPrice)
    }

    // --- marketCap 우선순위: 캐시 > 차트 (양방향 도착 순서) ---

    private fun stubValidChart(marketCapFromChart: Long, chartDelayMs: Long = 0L) {
        coEvery { apiConfigProvider.getKiwoomConfig() } returns KiwoomApiKeyConfig("key", "secret")
        coEvery { stockRepository.getDailyTradingData(any(), any(), any(), any()) } coAnswers {
            if (chartDelayMs > 0) delay(chartDelayMs)
            listOf(mockk(relaxed = true))
        }
        every { calcOscillator.execute(any(), any()) } returns listOf(
            mockk(relaxed = true) { every { marketCap } returns marketCapFromChart }
        )
    }

    @Test
    fun `marketCap priority - cache wins even when chart arrives first`() = runTest {
        coEvery { consensusRepository.getReportsByTicker(testTicker) } returns listOf(createReport())
        stubValidChart(marketCapFromChart = 111L)
        // 헤더 경로를 지연시켜 차트가 먼저 시총을 채우게 한다
        coEvery { analysisCacheDao.getLatestDate(testTicker) } coAnswers {
            delay(1_000)
            "20260323"
        }
        coEvery {
            analysisCacheDao.getByTickerDateRange(testTicker, "20260323", "20260323")
        } returns listOf(
            AnalysisCacheEntity(testTicker, "20260323", 222L, 0L, 0L, 75000)
        )

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.chartLoaded)
        assertTrue(state.headerLoaded)
        assertEquals("차트가 먼저 채워도 캐시 시총이 이겨야 한다", 222L, state.marketCap)
    }

    @Test
    fun `marketCap priority - late chart does not overwrite cache value`() = runTest {
        coEvery { consensusRepository.getReportsByTicker(testTicker) } returns listOf(createReport())
        stubValidChart(marketCapFromChart = 111L, chartDelayMs = 1_000)
        coEvery { analysisCacheDao.getLatestDate(testTicker) } returns "20260323"
        coEvery {
            analysisCacheDao.getByTickerDateRange(testTicker, "20260323", "20260323")
        } returns listOf(
            AnalysisCacheEntity(testTicker, "20260323", 222L, 0L, 0L, 75000)
        )

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.chartLoaded)
        assertEquals("늦게 도착한 차트가 캐시 시총을 덮으면 안 된다", 222L, state.marketCap)
    }
}
