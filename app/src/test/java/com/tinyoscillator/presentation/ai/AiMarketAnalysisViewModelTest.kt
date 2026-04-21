package com.tinyoscillator.presentation.ai

import com.tinyoscillator.core.api.AiApiClient
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.data.repository.MarketIndicatorRepository
import com.tinyoscillator.domain.model.AiAnalysisResult
import com.tinyoscillator.domain.model.AiAnalysisState
import com.tinyoscillator.domain.model.AiAnalysisType
import com.tinyoscillator.domain.model.AiApiKeyConfig
import com.tinyoscillator.domain.model.AiProvider
import com.tinyoscillator.domain.model.MarketOscillator
import com.tinyoscillator.domain.usecase.AiAnalysisPreparer
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiMarketAnalysisViewModelTest {

    private lateinit var marketIndicatorRepository: MarketIndicatorRepository
    private lateinit var aiApiClient: AiApiClient
    private lateinit var aiPreparer: AiAnalysisPreparer
    private lateinit var apiConfigProvider: ApiConfigProvider
    private lateinit var viewModel: AiMarketAnalysisViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val validAiConfig = AiApiKeyConfig(
        provider = AiProvider.CLAUDE,
        apiKey = "test-ai-key",
        modelId = "claude-sonnet-4-6"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        marketIndicatorRepository = mockk(relaxed = true)
        aiApiClient = mockk(relaxed = true)
        aiPreparer = AiAnalysisPreparer()
        apiConfigProvider = mockk(relaxed = true)

        coEvery { apiConfigProvider.getAiConfig() } returns validAiConfig

        viewModel = AiMarketAnalysisViewModel(
            marketIndicatorRepository, aiApiClient, aiPreparer, apiConfigProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `초기 상태는 Idle이다`() = runTest {
        advanceUntilIdle()
        assertEquals(AiAnalysisState.Idle, viewModel.marketAiState.value)
        assertEquals(false, viewModel.marketDataPrepared.value)
        assertEquals("", viewModel.marketDataSummary.value)
        assertEquals(false, viewModel.marketDataLoading.value)
        assertTrue(viewModel.marketChatMessages.value.isEmpty())
    }

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
            provider = AiProvider.CLAUDE,
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
            AiApiKeyConfig(provider = AiProvider.CLAUDE, apiKey = "")

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

    @Test
    fun `prepareMarketData_setsPrepared`() = runTest {
        advanceUntilIdle()

        coEvery { marketIndicatorRepository.getRecentData("KOSPI", 14) } returns listOf(
            MarketOscillator("id1", "KOSPI", "2026-03-05", 2500.0, 30.0, System.currentTimeMillis())
        )
        coEvery { marketIndicatorRepository.getRecentData("KOSDAQ", 14) } returns emptyList()
        coEvery { marketIndicatorRepository.getRecentDeposits(10) } returns emptyList()

        viewModel.prepareMarketData()
        advanceUntilIdle()

        assertTrue(viewModel.marketDataPrepared.value)
        assertTrue(viewModel.marketDataSummary.value.isNotBlank())
        assertEquals(false, viewModel.marketDataLoading.value)
    }

    @Test
    fun `sendMarketChat_noPreparedData_ignores`() = runTest {
        advanceUntilIdle()

        viewModel.sendMarketChat("hello")
        advanceUntilIdle()

        assertTrue(viewModel.marketChatMessages.value.isEmpty())
    }
}
