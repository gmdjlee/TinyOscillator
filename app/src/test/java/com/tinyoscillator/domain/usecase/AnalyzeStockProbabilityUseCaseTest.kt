package com.tinyoscillator.domain.usecase

import com.tinyoscillator.core.database.dao.CalibrationDao
import com.tinyoscillator.core.database.dao.FeatureCacheDao
import com.tinyoscillator.data.engine.*
import com.tinyoscillator.data.engine.calibration.SignalCalibrator
import kotlinx.serialization.json.Json
import com.tinyoscillator.data.mapper.AnalysisResponseParser
import com.tinyoscillator.data.mapper.ProbabilisticPromptBuilder
import com.tinyoscillator.domain.model.AnalysisState
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.DemarkTDRow
import com.tinyoscillator.domain.model.OscillatorRow
import com.tinyoscillator.domain.repository.FundamentalSnapshot
import com.tinyoscillator.domain.repository.LlmRepository
import com.tinyoscillator.domain.repository.StatisticalRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * AnalyzeStockProbabilityUseCase 통합 테스트
 * — 전체 파이프라인(통계→프롬프트→LLM→파싱)을 검증
 */
class AnalyzeStockProbabilityUseCaseTest {

    private lateinit var useCase: AnalyzeStockProbabilityUseCase
    private lateinit var repository: StatisticalRepository
    private lateinit var llmRepository: LlmRepository

    @Before
    fun setup() {
        repository = mockk()
        llmRepository = mockk()

        val prefs = mockk<android.content.SharedPreferences>(relaxed = true)
        val editor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
        every { prefs.edit() } returns editor
        every { editor.putFloat(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { prefs.getBoolean(any(), any()) } returns false
        every { prefs.getFloat(any(), any()) } answers { secondArg() }

        val calibrationDao = mockk<CalibrationDao>(relaxed = true)
        val statisticalEngine = StatisticalAnalysisEngine(
            repository = repository,
            naiveBayesEngine = NaiveBayesEngine(),
            logisticScoringEngine = LogisticScoringEngine(prefs),
            hmmRegimeEngine = HmmRegimeEngine(),
            patternScanEngine = PatternScanEngine(),
            signalScoringEngine = SignalScoringEngine(),
            correlationEngine = CorrelationEngine(),
            bayesianUpdateEngine = BayesianUpdateEngine(),
            orderFlowEngine = OrderFlowEngine(),
            dartEventEngine = com.tinyoscillator.data.engine.DartEventEngine(
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true)
            ),
            signalCalibrator = SignalCalibrator(),
            calibrationDao = calibrationDao,
            marketRegimeClassifier = com.tinyoscillator.data.engine.regime.MarketRegimeClassifier(),
            macroRegimeOverlay = com.tinyoscillator.data.engine.macro.MacroRegimeOverlay(),
            featureStore = FeatureStore(
                mockk<FeatureCacheDao>(relaxed = true).also {
                    every { it.count() } returns flowOf(0)
                },
                Json { ignoreUnknownKeys = true }
            ),
            apiConfigProvider = mockk(relaxed = true),
            signalHistoryStore = com.tinyoscillator.data.engine.ensemble.SignalHistoryStore(
                mockk<com.tinyoscillator.core.database.dao.EnsembleHistoryDao>(relaxed = true)
            )
        )

        useCase = AnalyzeStockProbabilityUseCase(
            statisticalEngine = statisticalEngine,
            promptBuilder = ProbabilisticPromptBuilder(),
            responseParser = AnalysisResponseParser(),
            llmRepository = llmRepository
        )
    }

    @Test
    fun `파이프라인이 Computing 상태를 거쳐 Success에 도달한다`() = runTest {
        setupMockData(200)

        // LLM이 유효한 JSON 응답 반환
        val llmResponse = """{"overall_assessment":"매수","confidence":0.7,"insights":[],"conflicts":[],"risks":[],"action":"분할 매수"}"""
        every { llmRepository.generate(any(), any(), any()) } returns flowOf(llmResponse)
        coEvery { llmRepository.isModelLoaded } returns MutableStateFlow(true)

        val states = useCase.execute("005930").toList()

        // Computing 상태가 있어야 함
        assertTrue("Computing 상태 존재",
            states.any { it is AnalysisState.Computing })

        // 마지막이 Success여야 함
        val lastState = states.last()
        assertTrue("Success 상태로 종료", lastState is AnalysisState.Success)

        val success = lastState as AnalysisState.Success
        assertEquals("매수", success.result.overallAssessment)
        assertEquals(0.7, success.result.confidence, 0.01)
        assertNotNull("statisticalResult 존재", success.statisticalResult)
    }

    @Test
    fun `LLM 비정상 응답 시에도 Success 반환 - fallback`() = runTest {
        setupMockData(200)

        // LLM이 비정상 텍스트 반환
        every { llmRepository.generate(any(), any(), any()) } returns flowOf("분석할 수 없습니다.")
        coEvery { llmRepository.isModelLoaded } returns MutableStateFlow(true)

        val states = useCase.execute("005930").toList()

        val lastState = states.last()
        assertTrue("Success 상태 (fallback)", lastState is AnalysisState.Success)

        val success = lastState as AnalysisState.Success
        assertEquals("LLM 응답 파싱 실패", success.result.overallAssessment)
    }

    @Test
    fun `데이터 부족 시에도 크래시하지 않는다`() = runTest {
        setupMockData(10)

        every { llmRepository.generate(any(), any(), any()) } returns flowOf("{\"overall_assessment\":\"데이터 부족\",\"confidence\":0.1,\"action\":\"대기\"}")
        coEvery { llmRepository.isModelLoaded } returns MutableStateFlow(true)

        val states = useCase.execute("005930").toList()

        // Error 또는 Success 상태 — 크래시하지 않으면 성공
        val lastState = states.last()
        assertTrue("정상 종료",
            lastState is AnalysisState.Success || lastState is AnalysisState.Error)
    }

    @Test
    fun `LLM 예외 시 Error 상태 반환`() = runTest {
        setupMockData(200)

        every { llmRepository.generate(any(), any(), any()) } throws RuntimeException("LLM 오류")
        coEvery { llmRepository.isModelLoaded } returns MutableStateFlow(true)

        val states = useCase.execute("005930").toList()

        val lastState = states.last()
        assertTrue("Error 상태", lastState is AnalysisState.Error)
    }

    @Test
    fun `statisticalResult에 7개 엔진 결과가 포함된다`() = runTest {
        setupMockData(200)

        val json = """{"overall_assessment":"테스트","confidence":0.5,"action":"대기"}"""
        every { llmRepository.generate(any(), any(), any()) } returns flowOf(json)
        coEvery { llmRepository.isModelLoaded } returns MutableStateFlow(true)

        val states = useCase.execute("005930").toList()
        val success = states.last() as AnalysisState.Success
        val sr = success.statisticalResult

        assertNotNull("bayesResult", sr.bayesResult)
        assertNotNull("logisticResult", sr.logisticResult)
        assertNotNull("hmmResult", sr.hmmResult)
        assertNotNull("patternAnalysis", sr.patternAnalysis)
        assertNotNull("signalScoringResult", sr.signalScoringResult)
        assertNotNull("correlationAnalysis", sr.correlationAnalysis)
        assertNotNull("bayesianUpdateResult", sr.bayesianUpdateResult)
    }

    // ─── Mock 데이터 ───

    private fun setupMockData(days: Int) {
        val prices = (0 until days).map { i ->
            val v = ((i % 7) - 3) * 500
            DailyTrading(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = (50000 + v) * 1000000L,
                foreignNetBuy = (v * 1000).toLong(),
                instNetBuy = (-v * 500).toLong(),
                closePrice = 50000 + v
            )
        }

        val oscillators = (0 until days).map { i ->
            val osc = ((i % 10) - 5) * 0.0001
            OscillatorRow(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = 50000000000L, marketCapTril = 50.0,
                foreign5d = 1000000L, inst5d = -500000L, supplyRatio = 0.001,
                ema12 = 0.001 + osc, ema26 = 0.001, macd = osc,
                signal = osc * 0.5, oscillator = osc * 0.5
            )
        }

        val demarks = (0 until days).map { i ->
            DemarkTDRow(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                closePrice = 50000 + (i % 7 - 3) * 500, marketCapTril = 50.0,
                tdSellCount = i % 10, tdBuyCount = (9 - i % 10).coerceAtLeast(0)
            )
        }

        val fundamentals = (0 until days).map { i ->
            FundamentalSnapshot(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                close = 50000L, per = 10.0, pbr = 0.8,
                eps = 5000L, bps = 62500L, dividendYield = 2.0
            )
        }

        coEvery { repository.getDailyPrices(any(), any()) } returns prices
        coEvery { repository.getStockName(any()) } returns "삼성전자"
        coEvery { repository.getOscillatorData(any(), any()) } returns oscillators
        coEvery { repository.getDemarkData(any(), any()) } returns demarks
        coEvery { repository.getFundamentalData(any(), any()) } returns fundamentals
        coEvery { repository.getEtfHoldingCount(any()) } returns 5
        coEvery { repository.getEtfAmountTrend(any()) } returns emptyList()
        coEvery { repository.getSectorEtfReturns(any(), any()) } returns emptyList()
    }
}
