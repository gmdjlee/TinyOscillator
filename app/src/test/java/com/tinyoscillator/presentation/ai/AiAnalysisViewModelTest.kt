package com.tinyoscillator.presentation.ai

import android.app.Application
import com.tinyoscillator.core.api.AiApiClient
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.core.database.entity.StockMasterEntity
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.data.repository.FinancialRepository
import com.tinyoscillator.data.repository.MarketIndicatorRepository
import com.tinyoscillator.data.repository.StockRepository
import com.tinyoscillator.domain.model.*
import com.tinyoscillator.domain.usecase.AiAnalysisPreparer
import com.tinyoscillator.domain.usecase.CalcDemarkTDUseCase
import com.tinyoscillator.domain.usecase.CalcOscillatorUseCase
import com.tinyoscillator.domain.usecase.SearchStocksUseCase
import com.tinyoscillator.core.config.ApiConfigProvider
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiAnalysisViewModelTest {

    private lateinit var application: Application
    private lateinit var stockRepository: StockRepository
    private lateinit var financialRepository: FinancialRepository
    private lateinit var etfRepository: EtfRepository
    private lateinit var marketIndicatorRepository: MarketIndicatorRepository
    private lateinit var calcOscillator: CalcOscillatorUseCase
    private lateinit var calcDemarkTD: CalcDemarkTDUseCase
    private lateinit var searchStocksUseCase: SearchStocksUseCase
    private lateinit var aiApiClient: AiApiClient
    private lateinit var aiPreparer: AiAnalysisPreparer
    private lateinit var apiConfigProvider: ApiConfigProvider
    private lateinit var viewModel: AiAnalysisViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val validKiwoomConfig = KiwoomApiKeyConfig(appKey = "test-key", secretKey = "test-secret")
    private val validAiConfig = AiApiKeyConfig(provider = AiProvider.CLAUDE_HAIKU, apiKey = "test-ai-key")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        stockRepository = mockk(relaxed = true)
        financialRepository = mockk(relaxed = true)
        etfRepository = mockk(relaxed = true)
        marketIndicatorRepository = mockk(relaxed = true)
        calcOscillator = CalcOscillatorUseCase(OscillatorConfig())
        calcDemarkTD = CalcDemarkTDUseCase()
        searchStocksUseCase = mockk(relaxed = true)
        aiApiClient = mockk(relaxed = true)
        aiPreparer = AiAnalysisPreparer()
        apiConfigProvider = mockk(relaxed = true)

        coEvery { apiConfigProvider.getKiwoomConfig() } returns validKiwoomConfig
        coEvery { apiConfigProvider.getKisConfig() } returns
            com.tinyoscillator.core.api.KisApiKeyConfig(appKey = "kis-key", appSecret = "kis-secret")
        coEvery { apiConfigProvider.getAiConfig() } returns validAiConfig

        every { searchStocksUseCase(any()) } returns flowOf(emptyList())

        val statisticalAnalysisEngine = mockk<com.tinyoscillator.data.engine.StatisticalAnalysisEngine>(relaxed = true)
        viewModel = AiAnalysisViewModel(
            application, stockRepository, financialRepository, etfRepository,
            marketIndicatorRepository, calcOscillator, calcDemarkTD,
            searchStocksUseCase, aiApiClient, aiPreparer, apiConfigProvider,
            statisticalAnalysisEngine
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createDailyTradingList(count: Int = 30): List<DailyTrading> {
        return (1..count).map { i ->
            DailyTrading(
                date = "2024${String.format("%02d", (i % 12) + 1)}${String.format("%02d", (i % 28) + 1)}",
                marketCap = 300_000_000_000_000L + i * 1_000_000_000L,
                foreignNetBuy = (i * 1_000_000L) * if (i % 3 == 0) -1 else 1,
                instNetBuy = (i * 500_000L) * if (i % 4 == 0) -1 else 1
            )
        }
    }

    // ==========================================================
    // 초기 상태 테스트
    // ==========================================================

    @Test
    fun `초기 상태는 Idle이다`() = runTest {
        advanceUntilIdle()
        assertEquals(AiTab.MARKET, viewModel.selectedTab.value)
        assertEquals(AiAnalysisState.Idle, viewModel.marketAiState.value)
        assertEquals(AiAnalysisState.Idle, viewModel.stockAiState.value)
        assertEquals(StockDataState.Idle, viewModel.stockDataState.value)
        assertNull(viewModel.selectedStock.value)
    }

    @Test
    fun `탭 변경이 동작한다`() = runTest {
        viewModel.selectTab(AiTab.STOCK)
        assertEquals(AiTab.STOCK, viewModel.selectedTab.value)

        viewModel.selectTab(AiTab.MARKET)
        assertEquals(AiTab.MARKET, viewModel.selectedTab.value)
    }

    // ==========================================================
    // 시장 탭 테스트
    // ==========================================================

    @Test
    fun `analyzeMarket_success`() = runTest {
        advanceUntilIdle()

        coEvery { marketIndicatorRepository.getRecentData("KOSPI", 14) } returns listOf(
            MarketOscillator("id1", "KOSPI", "2026-03-05", 2500.0, 30.0, System.currentTimeMillis())
        )
        coEvery { marketIndicatorRepository.getRecentData("KOSDAQ", 14) } returns emptyList()
        coEvery { marketIndicatorRepository.getRecentDeposits(10) } returns emptyList()

        val aiResult = AiAnalysisResult(
            type = AiAnalysisType.MARKET_OVERVIEW,
            provider = AiProvider.CLAUDE_HAIKU,
            content = "시장 분석 결과",
            inputTokens = 100,
            outputTokens = 200
        )
        coEvery { aiApiClient.analyze(any(), any(), any(), any(), any(), any()) } returns Result.success(aiResult)

        viewModel.analyzeMarketWithAi()
        advanceUntilIdle()

        val state = viewModel.marketAiState.value
        assertTrue("Expected Success but got $state", state is AiAnalysisState.Success)
        assertEquals("시장 분석 결과", (state as AiAnalysisState.Success).result.content)
    }

    @Test
    fun `analyzeMarket_noApiKey`() = runTest {
        advanceUntilIdle()

        coEvery { apiConfigProvider.getAiConfig() } returns
            AiApiKeyConfig(provider = AiProvider.CLAUDE_HAIKU, apiKey = "")

        viewModel.analyzeMarketWithAi()
        advanceUntilIdle()

        assertEquals(AiAnalysisState.NoApiKey, viewModel.marketAiState.value)
    }

    @Test
    fun `analyzeMarket_error`() = runTest {
        advanceUntilIdle()

        coEvery { marketIndicatorRepository.getRecentData(any(), any()) } throws RuntimeException("DB error")

        viewModel.analyzeMarketWithAi()
        advanceUntilIdle()

        val state = viewModel.marketAiState.value
        assertTrue("Expected Error but got $state", state is AiAnalysisState.Error)
    }

    @Test
    fun `dismissMarket_resetsIdle`() = runTest {
        advanceUntilIdle()

        coEvery { marketIndicatorRepository.getRecentData(any(), any()) } throws RuntimeException("test")
        viewModel.analyzeMarketWithAi()
        advanceUntilIdle()
        assertTrue(viewModel.marketAiState.value is AiAnalysisState.Error)

        viewModel.dismissMarketAi()
        assertEquals(AiAnalysisState.Idle, viewModel.marketAiState.value)
    }

    // ==========================================================
    // 종목 탭 테스트
    // ==========================================================

    @Test
    fun `selectStock_loadsParallel`() = runTest {
        advanceUntilIdle()

        coEvery { stockRepository.getDailyTradingData(any(), any(), any(), any()) } returns createDailyTradingList(30)
        coEvery { financialRepository.getFinancialData(any(), any(), any()) } returns Result.failure(RuntimeException("no data"))
        coEvery { etfRepository.getStockAggregatedTrend(any()) } returns emptyList()

        viewModel.selectStock("005930", "삼성전자", "KOSPI", "반도체")
        advanceUntilIdle()

        val stock = viewModel.selectedStock.value
        assertNotNull(stock)
        assertEquals("005930", stock!!.ticker)
        assertEquals("삼성전자", stock.name)

        val dataState = viewModel.stockDataState.value
        assertTrue("Expected Loaded but got $dataState", dataState is StockDataState.Loaded)
        val loaded = dataState as StockDataState.Loaded
        assertTrue(loaded.oscillatorRows.isNotEmpty())
        assertTrue(loaded.signals.isNotEmpty())
        assertTrue(loaded.demarkRows.isNotEmpty())
        assertNull(loaded.financialData) // failed gracefully
        assertTrue(loaded.etfAggregated.isEmpty())
    }

    @Test
    fun `selectStock_partialFailure`() = runTest {
        advanceUntilIdle()

        // Only oscillator data succeeds
        coEvery { stockRepository.getDailyTradingData(any(), any(), any(), any()) } returns createDailyTradingList(20)
        coEvery { financialRepository.getFinancialData(any(), any(), any()) } returns Result.failure(RuntimeException("fail"))
        coEvery { etfRepository.getStockAggregatedTrend(any()) } throws RuntimeException("ETF fail")

        viewModel.selectStock("005930", "삼성전자", "KOSPI", null)
        advanceUntilIdle()

        val dataState = viewModel.stockDataState.value
        assertTrue("Expected Loaded but got $dataState", dataState is StockDataState.Loaded)
        val loaded = dataState as StockDataState.Loaded
        assertTrue(loaded.oscillatorRows.isNotEmpty())
        assertNull(loaded.financialData)
        assertTrue(loaded.etfAggregated.isEmpty())
    }

    @Test
    fun `analyzeStock_success`() = runTest {
        advanceUntilIdle()

        coEvery { stockRepository.getDailyTradingData(any(), any(), any(), any()) } returns createDailyTradingList(30)
        coEvery { financialRepository.getFinancialData(any(), any(), any()) } returns Result.failure(RuntimeException("no"))
        coEvery { etfRepository.getStockAggregatedTrend(any()) } returns emptyList()

        viewModel.selectStock("005930", "삼성전자", "KOSPI", "반도체")
        advanceUntilIdle()

        val aiResult = AiAnalysisResult(
            type = AiAnalysisType.COMPREHENSIVE_STOCK,
            provider = AiProvider.CLAUDE_HAIKU,
            content = "종합 분석 결과",
            inputTokens = 300,
            outputTokens = 500
        )
        coEvery { aiApiClient.analyze(any(), any(), any(), any(), any(), any()) } returns Result.success(aiResult)

        viewModel.analyzeStockWithAi()
        advanceUntilIdle()

        val state = viewModel.stockAiState.value
        assertTrue("Expected Success but got $state", state is AiAnalysisState.Success)
        assertEquals("종합 분석 결과", (state as AiAnalysisState.Success).result.content)
    }

    @Test
    fun `analyzeStock_noApiKey`() = runTest {
        advanceUntilIdle()

        coEvery { stockRepository.getDailyTradingData(any(), any(), any(), any()) } returns createDailyTradingList(30)
        coEvery { financialRepository.getFinancialData(any(), any(), any()) } returns Result.failure(RuntimeException("no"))
        coEvery { etfRepository.getStockAggregatedTrend(any()) } returns emptyList()

        viewModel.selectStock("005930", "삼성전자", null, null)
        advanceUntilIdle()

        coEvery { apiConfigProvider.getAiConfig() } returns
            AiApiKeyConfig(provider = AiProvider.CLAUDE_HAIKU, apiKey = "")

        viewModel.analyzeStockWithAi()
        advanceUntilIdle()

        assertEquals(AiAnalysisState.NoApiKey, viewModel.stockAiState.value)
    }

    @Test
    fun `analyzeStock_noData_doesNothing`() = runTest {
        advanceUntilIdle()

        // No stock selected, no data loaded
        viewModel.analyzeStockWithAi()
        advanceUntilIdle()

        // Should remain Idle since stockDataState is Idle
        assertEquals(AiAnalysisState.Idle, viewModel.stockAiState.value)
    }

    @Test
    fun `analyzeStock_error`() = runTest {
        advanceUntilIdle()

        coEvery { stockRepository.getDailyTradingData(any(), any(), any(), any()) } returns createDailyTradingList(30)
        coEvery { financialRepository.getFinancialData(any(), any(), any()) } returns Result.failure(RuntimeException("no"))
        coEvery { etfRepository.getStockAggregatedTrend(any()) } returns emptyList()

        viewModel.selectStock("005930", "삼성전자", null, null)
        advanceUntilIdle()

        coEvery { aiApiClient.analyze(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("API error"))

        viewModel.analyzeStockWithAi()
        advanceUntilIdle()

        val state = viewModel.stockAiState.value
        assertTrue("Expected Error but got $state", state is AiAnalysisState.Error)
    }

    @Test
    fun `dismissStock_resetsIdle`() = runTest {
        advanceUntilIdle()

        coEvery { stockRepository.getDailyTradingData(any(), any(), any(), any()) } returns createDailyTradingList(30)
        coEvery { financialRepository.getFinancialData(any(), any(), any()) } returns Result.failure(RuntimeException("no"))
        coEvery { etfRepository.getStockAggregatedTrend(any()) } returns emptyList()
        coEvery { aiApiClient.analyze(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("API error"))

        viewModel.selectStock("005930", "삼성전자", null, null)
        advanceUntilIdle()
        viewModel.analyzeStockWithAi()
        advanceUntilIdle()

        assertTrue(viewModel.stockAiState.value is AiAnalysisState.Error)

        viewModel.dismissStockAi()
        assertEquals(AiAnalysisState.Idle, viewModel.stockAiState.value)
    }

    @Test
    fun `selectNewStock_resetsPreviousAi`() = runTest {
        advanceUntilIdle()

        coEvery { stockRepository.getDailyTradingData(any(), any(), any(), any()) } returns createDailyTradingList(30)
        coEvery { financialRepository.getFinancialData(any(), any(), any()) } returns Result.failure(RuntimeException("no"))
        coEvery { etfRepository.getStockAggregatedTrend(any()) } returns emptyList()

        val aiResult = AiAnalysisResult(
            type = AiAnalysisType.COMPREHENSIVE_STOCK,
            provider = AiProvider.CLAUDE_HAIKU,
            content = "결과",
            inputTokens = 100,
            outputTokens = 200
        )
        coEvery { aiApiClient.analyze(any(), any(), any(), any(), any(), any()) } returns Result.success(aiResult)

        viewModel.selectStock("005930", "삼성전자", null, null)
        advanceUntilIdle()
        viewModel.analyzeStockWithAi()
        advanceUntilIdle()
        assertTrue(viewModel.stockAiState.value is AiAnalysisState.Success)

        // Select new stock should reset AI state
        viewModel.selectStock("000660", "SK하이닉스", null, null)
        assertEquals(AiAnalysisState.Idle, viewModel.stockAiState.value)
    }

    @Test
    fun `searchStock_debounces`() = runTest {
        advanceUntilIdle()
        viewModel.searchStock("삼성")
        advanceUntilIdle()
        // Just verify no exception
    }
}
