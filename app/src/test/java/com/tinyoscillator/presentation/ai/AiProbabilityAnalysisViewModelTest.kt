package com.tinyoscillator.presentation.ai

import com.tinyoscillator.core.api.AiApiClient
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.core.database.dao.AnalysisSnapshotDao
import com.tinyoscillator.core.database.entity.AnalysisSnapshotEntity
import com.tinyoscillator.data.mapper.AnalysisResponseParser
import com.tinyoscillator.data.repository.SignalHistoryRepository
import com.tinyoscillator.domain.model.AiApiKeyConfig
import com.tinyoscillator.domain.model.MetaLearnerStatus
import com.tinyoscillator.domain.model.StatisticalResult
import com.tinyoscillator.domain.model.StockAnalysis
import com.tinyoscillator.domain.usecase.AiAnalysisPreparer
import com.tinyoscillator.domain.usecase.ProbabilityAnalysisUseCase
import com.tinyoscillator.domain.usecase.ProbabilityInterpreter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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

/**
 * [AiProbabilityAnalysisViewModel] 스트리밍/해석 오케스트레이션 검증 (P8-4).
 *
 * 통계 엔진 실행 → 상태 전이·스냅샷 저장·로컬 해석·AI 해석(NoApiKey/캐시 복원)·dismiss 커버.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiProbabilityAnalysisViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val aiApiClient: AiApiClient = mockk()
    private val aiPreparer: AiAnalysisPreparer = mockk(relaxed = true)
    private val apiConfigProvider: ApiConfigProvider = mockk()
    private val useCase: ProbabilityAnalysisUseCase = mockk()
    private val interpreter: ProbabilityInterpreter = mockk(relaxed = true)
    private val signalHistoryRepository: SignalHistoryRepository = mockk()
    private val snapshotDao: AnalysisSnapshotDao = mockk(relaxed = true)
    private val parser: AnalysisResponseParser = mockk()

    private val result: StatisticalResult = mockk(relaxed = true)
    private val stock = SelectedStockInfo("005930", "삼성전자", "KOSPI", "반도체")

    private lateinit var vm: AiProbabilityAnalysisViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { useCase.totalEngines } returns 11
        every { useCase.cacheStats } returns kotlinx.coroutines.flow.emptyFlow()
        every { useCase.buildAlgoRationales(any()) } returns emptyMap()
        every { useCase.getMetaLearnerStatus() } returns MetaLearnerStatus()
        every { useCase.getEnsembleProbability(any()) } returns 0.7
        every { useCase.buildSnapshot(any(), any(), any()) } returns snapshotEntity(id = 0)
        coEvery { useCase.analyze(any(), any()) } returns result
        coEvery { signalHistoryRepository.getAccuracy(any(), any()) } returns emptyMap()
        coEvery { snapshotDao.insert(any()) } returns 42L
        coEvery { snapshotDao.getRecentByTicker(any(), any()) } returns emptyList()
        every { interpreter.summarize(any()) } returns "로컬 요약"

        vm = AiProbabilityAnalysisViewModel(
            aiApiClient, aiPreparer, apiConfigProvider, useCase,
            interpreter, signalHistoryRepository, snapshotDao, parser
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun snapshotEntity(id: Long, ai: String? = null) = AnalysisSnapshotEntity(
        id = id, ticker = "005930", name = "삼성전자",
        analyzedAt = System.currentTimeMillis(), ensembleScore = 0.7,
        algoScores = "{}", algoRationales = "{}", aiInterpretation = ai
    )

    // ── 확률분석 실행 ──

    @Test
    fun `분석 성공 시 Success 상태·스냅샷 저장·앙상블 계산`() = runTest {
        vm.analyzeProbability(stock)
        advanceUntilIdle()

        assertTrue(vm.probabilityState.value is ProbabilityAnalysisState.Success)
        assertEquals(0.7, vm.ensembleProbability.value)
        coVerify { snapshotDao.insert(any()) }
        coVerify { snapshotDao.deleteOldSnapshots("005930", 20) }
    }

    @Test
    fun `분석 실패 시 Error 상태`() = runTest {
        coEvery { useCase.analyze(any(), any()) } throws RuntimeException("엔진 붕괴")

        vm.analyzeProbability(stock)
        advanceUntilIdle()

        val state = vm.probabilityState.value
        assertTrue(state is ProbabilityAnalysisState.Error)
        assertEquals("엔진 붕괴", (state as ProbabilityAnalysisState.Error).message)
    }

    // ── 로컬 해석 ──

    @Test
    fun `로컬 해석은 provider LOCAL·요약 채움`() = runTest {
        vm.analyzeProbability(stock)
        advanceUntilIdle()

        vm.interpretLocal()

        val s = vm.interpretationState.value
        assertTrue(s is InterpretationState.Success)
        s as InterpretationState.Success
        assertEquals(InterpretationProvider.LOCAL, s.provider)
        assertEquals("로컬 요약", s.summary)
    }

    @Test
    fun `Success 상태 아니면 로컬 해석 무시`() = runTest {
        // 분석 실행 전 → Idle
        vm.interpretLocal()
        assertTrue(vm.interpretationState.value is InterpretationState.Idle)
    }

    // ── AI 해석: 키 없음 / 캐시 복원 ──

    @Test
    fun `AI 키 무효 시 NoApiKey`() = runTest {
        vm.analyzeProbability(stock)
        advanceUntilIdle()
        every { apiConfigProviderConfig().isValid() } returns false
        coEvery { apiConfigProvider.getAiConfig() } returns apiConfigProviderConfig()

        vm.interpretWithAi(force = false)
        advanceUntilIdle()

        assertTrue(vm.interpretationState.value is InterpretationState.NoApiKey)
    }

    @Test
    fun `신선한 캐시가 있으면 AI 호출 없이 복원`() = runTest {
        vm.analyzeProbability(stock)
        advanceUntilIdle()

        val cfg = apiConfigProviderConfig()
        every { cfg.isValid() } returns true
        coEvery { apiConfigProvider.getAiConfig() } returns cfg
        coEvery { snapshotDao.getRecentByTicker("005930", 1) } returns
            listOf(snapshotEntity(id = 42, ai = "{\"cached\":true}"))
        val structured = mockk<StockAnalysis>()
        every { structured.summary } returns "캐시 요약"
        every { parser.parseOrNull("{\"cached\":true}") } returns structured

        vm.interpretWithAi(force = false)
        advanceUntilIdle()

        val s = vm.interpretationState.value
        assertTrue(s is InterpretationState.Success)
        s as InterpretationState.Success
        assertEquals(InterpretationProvider.AI, s.provider)
        assertTrue(s.fromCache)
        assertEquals("캐시 요약", s.summary)
        // AI API 미호출
        coVerify(exactly = 0) { aiApiClient.analyzeStructured(any(), any(), any(), any()) }
    }

    // ── dismiss ──

    @Test
    fun `dismissInterpretation은 해석만 Idle로`() = runTest {
        vm.analyzeProbability(stock)
        advanceUntilIdle()
        vm.interpretLocal()

        vm.dismissInterpretation()

        assertTrue(vm.interpretationState.value is InterpretationState.Idle)
        assertTrue(vm.probabilityState.value is ProbabilityAnalysisState.Success)
    }

    @Test
    fun `dismissProbability는 분석·해석 모두 Idle로`() = runTest {
        vm.analyzeProbability(stock)
        advanceUntilIdle()

        vm.dismissProbability()

        assertTrue(vm.probabilityState.value is ProbabilityAnalysisState.Idle)
        assertTrue(vm.interpretationState.value is InterpretationState.Idle)
    }

    // 단일 AiApiKeyConfig 목 재사용 (isValid 스텁 대상)
    private val sharedConfig: AiApiKeyConfig = mockk()
    private fun apiConfigProviderConfig(): AiApiKeyConfig = sharedConfig
}
