package com.tinyoscillator.presentation.ai

import com.tinyoscillator.core.api.AiApiClient
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.data.repository.FinancialRepository
import com.tinyoscillator.data.repository.StockRepository
import com.tinyoscillator.domain.model.AiAnalysisResult
import com.tinyoscillator.domain.model.AiAnalysisState
import com.tinyoscillator.domain.model.AiAnalysisType
import com.tinyoscillator.domain.model.AiApiKeyConfig
import com.tinyoscillator.domain.model.AiProvider
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.OscillatorConfig
import com.tinyoscillator.domain.usecase.AiAnalysisPreparer
import com.tinyoscillator.domain.usecase.CalcDemarkTDUseCase
import com.tinyoscillator.domain.usecase.CalcOscillatorUseCase
import com.tinyoscillator.domain.usecase.SearchStocksUseCase
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiStockAnalysisViewModelTest {

    private lateinit var stockRepository: StockRepository
    private lateinit var financialRepository: FinancialRepository
    private lateinit var etfRepository: EtfRepository
    private lateinit var calcOscillator: CalcOscillatorUseCase
    private lateinit var calcDemarkTD: CalcDemarkTDUseCase
    private lateinit var searchStocksUseCase: SearchStocksUseCase
    private lateinit var aiApiClient: AiApiClient
    private lateinit var aiPreparer: AiAnalysisPreparer
    private lateinit var apiConfigProvider: ApiConfigProvider
    private lateinit var viewModel: AiStockAnalysisViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val validKiwoomConfig = KiwoomApiKeyConfig(appKey = "test-key", secretKey = "test-secret")
    private val validAiConfig = AiApiKeyConfig(
        provider = AiProvider.CLAUDE,
        apiKey = "test-ai-key",
        modelId = "claude-sonnet-4-6"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        stockRepository = mockk(relaxed = true)
        financialRepository = mockk(relaxed = true)
        etfRepository = mockk(relaxed = true)
        calcOscillator = CalcOscillatorUseCase(OscillatorConfig())
        calcDemarkTD = CalcDemarkTDUseCase()
        searchStocksUseCase = mockk(relaxed = true)
        aiApiClient = mockk(relaxed = true)
        aiPreparer = AiAnalysisPreparer()
        apiConfigProvider = mockk(relaxed = true)

        coEvery { apiConfigProvider.getKiwoomConfig() } returns validKiwoomConfig
        coEvery { apiConfigProvider.getKisConfig() } returns
            KisApiKeyConfig(appKey = "kis-key", appSecret = "kis-secret")
        coEvery { apiConfigProvider.getAiConfig() } returns validAiConfig
        coEvery { searchStocksUseCase.searchWithChosung(any()) } returns emptyList()

        viewModel = AiStockAnalysisViewModel(
            stockRepository, financialRepository, etfRepository,
            calcOscillator, calcDemarkTD, searchStocksUseCase,
            aiApiClient, aiPreparer, apiConfigProvider
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

    @Test
    fun `초기 상태는 Idle이다`() = runTest {
        advanceUntilIdle()
        assertEquals(AiAnalysisState.Idle, viewModel.stockAiState.value)
        assertEquals(StockDataState.Idle, viewModel.stockDataState.value)
        assertNull(viewModel.selectedStock.value)
    }

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
        assertNull(loaded.financialData)
        assertTrue(loaded.etfAggregated.isEmpty())
    }

    @Test
    fun `selectStock_partialFailure`() = runTest {
        advanceUntilIdle()

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
            provider = AiProvider.CLAUDE,
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
            AiApiKeyConfig(provider = AiProvider.CLAUDE, apiKey = "")

        viewModel.analyzeStockWithAi()
        advanceUntilIdle()

        assertEquals(AiAnalysisState.NoApiKey, viewModel.stockAiState.value)
    }

    @Test
    fun `analyzeStock_noData_doesNothing`() = runTest {
        advanceUntilIdle()

        viewModel.analyzeStockWithAi()
        advanceUntilIdle()

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
            provider = AiProvider.CLAUDE,
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

        viewModel.selectStock("000660", "SK하이닉스", null, null)
        assertEquals(AiAnalysisState.Idle, viewModel.stockAiState.value)
    }

    @Test
    fun `searchStock_debounces`() = runTest {
        advanceUntilIdle()
        viewModel.searchStock("삼성")
        advanceUntilIdle()
    }
}
