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
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(report, state.report)
        assertEquals("삼성전자", state.report?.stockName)
    }

    @Test
    fun `empty ticker - shows error state`() = runTest {
        val vm = createViewModel(ticker = "", writeDate = testWriteDate)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
    }

    @Test
    fun `empty writeDate - shows error state`() = runTest {
        val vm = createViewModel(ticker = testTicker, writeDate = "")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
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
}
