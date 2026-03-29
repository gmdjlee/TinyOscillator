package com.tinyoscillator.presentation.viewmodel

import com.tinyoscillator.data.local.llm.CachedModel
import com.tinyoscillator.data.local.llm.ModelManager
import com.tinyoscillator.domain.model.AlgorithmInsight
import com.tinyoscillator.domain.model.AnalysisState
import com.tinyoscillator.domain.model.StatisticalResult
import com.tinyoscillator.domain.model.StockAnalysis
import com.tinyoscillator.domain.repository.LlmRepository
import com.tinyoscillator.domain.usecase.AnalyzeStockProbabilityUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StockAnalysisViewModelTest {

    private lateinit var analyzeUseCase: AnalyzeStockProbabilityUseCase
    private lateinit var llmRepository: LlmRepository
    private lateinit var modelManager: ModelManager
    private lateinit var viewModel: StockAnalysisViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        analyzeUseCase = mockk()
        llmRepository = mockk()
        modelManager = mockk()

        // 기본 isModelLoaded Flow
        every { llmRepository.isModelLoaded } returns flowOf(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): StockAnalysisViewModel {
        return StockAnalysisViewModel(analyzeUseCase, llmRepository, modelManager)
    }

    // ─── 초기 상태 ───

    @Test
    fun `초기 상태는 Idle이다`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue("초기 상태 Idle", viewModel.uiState.value is AnalysisUiState.Idle)
    }

    @Test
    fun `초기 streamingText는 빈 문자열이다`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("", viewModel.streamingText.value)
    }

    @Test
    fun `모델 미로드 시 isModelLoaded는 false`() = runTest {
        every { llmRepository.isModelLoaded } returns flowOf(false)
        viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.isModelLoaded.value)
    }

    @Test
    fun `모델 로드 시 isModelLoaded는 true`() = runTest {
        every { llmRepository.isModelLoaded } returns flowOf(true)
        viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.isModelLoaded.value)
    }

    // ─── analyzeStock 상태 전이 ───

    @Test
    fun `analyzeStock 호출 시 Loading 상태로 전환`() = runTest {
        // UseCase가 끝나지 않는 Flow 반환
        coEvery { analyzeUseCase.execute("005930") } returns flow {
            emit(AnalysisState.Loading)
            // 무한 대기하여 Loading 상태 유지
            kotlinx.coroutines.awaitCancellation()
        }
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.analyzeStock("005930")
        advanceUntilIdle()

        assertTrue("Loading 상태", viewModel.uiState.value is AnalysisUiState.Loading)
    }

    @Test
    fun `analyzeStock Computing 상태 전이`() = runTest {
        coEvery { analyzeUseCase.execute("005930") } returns flow {
            emit(AnalysisState.Computing("계산 중...", 0.5f))
            kotlinx.coroutines.awaitCancellation()
        }
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.analyzeStock("005930")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Computing 상태", state is AnalysisUiState.Computing)
        assertEquals("계산 중...", (state as AnalysisUiState.Computing).message)
        assertEquals(0.5f, state.progress, 0.001f)
    }

    @Test
    fun `analyzeStock LlmProcessing 상태 전이`() = runTest {
        coEvery { analyzeUseCase.execute("005930") } returns flow {
            emit(AnalysisState.LlmProcessing("LLM 처리 중..."))
            kotlinx.coroutines.awaitCancellation()
        }
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.analyzeStock("005930")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("LlmProcessing 상태", state is AnalysisUiState.LlmProcessing)
        assertEquals("LLM 처리 중...", (state as AnalysisUiState.LlmProcessing).message)
    }

    @Test
    fun `analyzeStock Streaming 상태에서 streamingText 업데이트`() = runTest {
        coEvery { analyzeUseCase.execute("005930") } returns flow {
            emit(AnalysisState.Streaming("분석 결과가"))
            kotlinx.coroutines.awaitCancellation()
        }
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.analyzeStock("005930")
        advanceUntilIdle()

        assertTrue("Streaming 상태", viewModel.uiState.value is AnalysisUiState.Streaming)
        assertEquals("분석 결과가", viewModel.streamingText.value)
    }

    @Test
    fun `analyzeStock Success 상태 전이`() = runTest {
        val analysis = StockAnalysis(
            overallAssessment = "매수",
            confidence = 0.75,
            insights = listOf(
                AlgorithmInsight("Bayes", "상승 확률 높음", "HIGH")
            ),
            conflicts = emptyList(),
            risks = listOf("변동성 주의"),
            action = "매수 추천",
            summary = "종합 분석 결과"
        )
        val statisticalResult = StatisticalResult(
            ticker = "005930",
            stockName = "삼성전자"
        )

        coEvery { analyzeUseCase.execute("005930") } returns flow {
            emit(AnalysisState.Success(analysis, statisticalResult))
        }
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.analyzeStock("005930")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Success 상태", state is AnalysisUiState.Success)
        assertEquals(analysis, (state as AnalysisUiState.Success).analysis)
        assertEquals(statisticalResult, state.statisticalResult)
    }

    @Test
    fun `analyzeStock Error 상태 전이`() = runTest {
        coEvery { analyzeUseCase.execute("005930") } returns flow {
            emit(AnalysisState.Error("분석 실패"))
        }
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.analyzeStock("005930")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Error 상태", state is AnalysisUiState.Error)
        assertEquals("분석 실패", (state as AnalysisUiState.Error).message)
    }

    @Test
    fun `analyzeStock 호출 시 streamingText 초기화`() = runTest {
        coEvery { analyzeUseCase.execute("005930") } returns flow {
            emit(AnalysisState.Streaming("첫번째 분석"))
            kotlinx.coroutines.awaitCancellation()
        }
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.analyzeStock("005930")
        advanceUntilIdle()
        assertEquals("첫번째 분석", viewModel.streamingText.value)

        // 두번째 분석 시작 시 streamingText 초기화
        coEvery { analyzeUseCase.execute("005930") } returns flow {
            emit(AnalysisState.Loading)
            kotlinx.coroutines.awaitCancellation()
        }
        viewModel.analyzeStock("005930")
        advanceUntilIdle()
        assertEquals("", viewModel.streamingText.value)
    }

    // ─── loadModel ───

    @Test
    fun `loadModel 호출 시 LlmRepository에 위임`() = runTest {
        coEvery { llmRepository.loadModel(any()) } just Runs
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadModel("/path/to/model.gguf")
        advanceUntilIdle()

        coVerify { llmRepository.loadModel("/path/to/model.gguf") }
    }

    // ─── getCachedModels ───

    @Test
    fun `getCachedModels 반환 확인`() = runTest {
        val models = listOf(
            CachedModel("/path/model1.gguf", "model1.gguf", 1000000L, "Model1"),
            CachedModel("/path/model2.gguf", "model2.gguf", 2000000L, "Model2")
        )
        coEvery { modelManager.getCachedModels() } returns models
        viewModel = createViewModel()
        advanceUntilIdle()

        val result = viewModel.getCachedModels()

        assertEquals(2, result.size)
        assertEquals("Model1", result[0].modelName)
    }

    @Test
    fun `getCachedModels 빈 목록 반환`() = runTest {
        coEvery { modelManager.getCachedModels() } returns emptyList()
        viewModel = createViewModel()
        advanceUntilIdle()

        val result = viewModel.getCachedModels()

        assertTrue("빈 모델 목록", result.isEmpty())
    }
}
