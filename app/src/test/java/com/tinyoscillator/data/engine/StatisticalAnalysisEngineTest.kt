package com.tinyoscillator.data.engine

import android.content.SharedPreferences
import com.tinyoscillator.core.database.dao.CalibrationDao
import com.tinyoscillator.core.database.dao.FeatureCacheDao
import com.tinyoscillator.data.engine.calibration.SignalCalibrator
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.DemarkTDRow
import com.tinyoscillator.domain.model.OscillatorRow
import com.tinyoscillator.domain.repository.EtfAmountPoint
import com.tinyoscillator.domain.repository.FundamentalSnapshot
import com.tinyoscillator.domain.repository.SectorEtfReturn
import com.tinyoscillator.domain.repository.StatisticalRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * StatisticalAnalysisEngine 통합 테스트
 * — 7개 엔진을 실제로 병렬 실행하고 결과를 검증
 */
class StatisticalAnalysisEngineTest {

    private lateinit var engine: StatisticalAnalysisEngine
    private lateinit var repository: StatisticalRepository

    @Before
    fun setup() {
        repository = mockk()

        val prefs = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { prefs.edit() } returns editor
        every { editor.putFloat(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { prefs.getBoolean(any(), any()) } returns false
        every { prefs.getFloat(any(), any()) } answers { secondArg() }

        val calibrationDao = mockk<CalibrationDao>(relaxed = true)

        engine = StatisticalAnalysisEngine(
            repository = repository,
            naiveBayesEngine = NaiveBayesEngine(),
            logisticScoringEngine = LogisticScoringEngine(prefs),
            hmmRegimeEngine = HmmRegimeEngine(),
            patternScanEngine = PatternScanEngine(),
            signalScoringEngine = SignalScoringEngine(),
            correlationEngine = CorrelationEngine(),
            bayesianUpdateEngine = BayesianUpdateEngine(),
            orderFlowEngine = OrderFlowEngine(),
            dartEventEngine = DartEventEngine(
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
    }

    @Test
    fun `전체 분석이 정상 완료된다`() = runTest {
        setupMockData(200)

        val result = engine.analyze("005930")

        assertEquals("005930", result.ticker)
        assertEquals("삼성전자", result.stockName)
        assertTrue("totalTimeMs > 0", result.executionMetadata.totalTimeMs > 0)
    }

    @Test
    fun `7개 엔진 결과가 모두 존재한다`() = runTest {
        setupMockData(200)

        val result = engine.analyze("005930")

        assertNotNull("bayesResult", result.bayesResult)
        assertNotNull("logisticResult", result.logisticResult)
        assertNotNull("hmmResult", result.hmmResult)
        assertNotNull("patternAnalysis", result.patternAnalysis)
        assertNotNull("signalScoringResult", result.signalScoringResult)
        assertNotNull("correlationAnalysis", result.correlationAnalysis)
        assertNotNull("bayesianUpdateResult", result.bayesianUpdateResult)
    }

    @Test
    fun `실행 메타데이터에 각 엔진 타이밍이 있다`() = runTest {
        setupMockData(200)

        val result = engine.analyze("005930")

        val timings = result.executionMetadata.engineTimings
        assertTrue("NaiveBayes 타이밍 존재", timings.containsKey("NaiveBayes"))
        assertTrue("Logistic 타이밍 존재", timings.containsKey("Logistic"))
        assertTrue("HMM 타이밍 존재", timings.containsKey("HMM"))
        assertTrue("PatternScan 타이밍 존재", timings.containsKey("PatternScan"))
        assertTrue("SignalScoring 타이밍 존재", timings.containsKey("SignalScoring"))
        assertTrue("Correlation 타이밍 존재", timings.containsKey("Correlation"))
        assertTrue("BayesianUpdate 타이밍 존재", timings.containsKey("BayesianUpdate"))
    }

    @Test
    fun `failedEngines가 비어있다`() = runTest {
        setupMockData(200)

        val result = engine.analyze("005930")

        assertTrue("실패한 엔진이 없어야 함", result.executionMetadata.failedEngines.isEmpty())
    }

    @Test
    fun `Bayes 확률 합이 1이다`() = runTest {
        setupMockData(200)

        val result = engine.analyze("005930")
        val bayes = result.bayesResult!!

        val sum = bayes.upProbability + bayes.downProbability + bayes.sidewaysProbability
        assertEquals("확률 합 = 1.0", 1.0, sum, 0.01)
    }

    @Test
    fun `Logistic 점수가 0에서 100이다`() = runTest {
        setupMockData(200)

        val result = engine.analyze("005930")
        val logistic = result.logisticResult!!

        assertTrue("score >= 0", logistic.score0to100 >= 0)
        assertTrue("score <= 100", logistic.score0to100 <= 100)
        assertTrue("probability 0~1", logistic.probability in 0.0..1.0)
    }

    @Test
    fun `HMM 레짐이 유효하다`() = runTest {
        setupMockData(200)

        val result = engine.analyze("005930")
        val hmm = result.hmmResult!!

        assertTrue("currentRegime 0~3", hmm.currentRegime in 0..3)
        assertEquals("레짐 확률 합 = 1.0", 1.0, hmm.regimeProbabilities.sum(), 0.01)
        assertTrue("recentRegimePath 비어있지 않음", hmm.recentRegimePath.isNotEmpty())
    }

    @Test
    fun `패턴 분석에 8개 패턴이 있다`() = runTest {
        setupMockData(200)

        val result = engine.analyze("005930")
        val patterns = result.patternAnalysis!!

        assertEquals("8개 패턴", 8, patterns.allPatterns.size)
        assertTrue("totalHistoricalDays > 0", patterns.totalHistoricalDays > 0)
    }

    @Test
    fun `신호 점수가 0에서 100이다`() = runTest {
        setupMockData(200)

        val result = engine.analyze("005930")
        val signal = result.signalScoringResult!!

        assertTrue("totalScore >= 0", signal.totalScore >= 0)
        assertTrue("totalScore <= 100", signal.totalScore <= 100)
    }

    @Test
    fun `베이지안 posterior가 0에서 1이다`() = runTest {
        setupMockData(200)

        val result = engine.analyze("005930")
        val bu = result.bayesianUpdateResult!!

        assertTrue("posterior 0~1", bu.finalPosterior in 0.0..1.0)
        assertTrue("prior 0~1", bu.priorProbability in 0.0..1.0)
        assertTrue("갱신 히스토리 존재", bu.updateHistory.isNotEmpty())
    }

    @Test
    fun `ETF 데이터가 CorrelationEngine에 전달된다`() = runTest {
        setupMockData(200)

        val result = engine.analyze("005930")

        val ca = result.correlationAnalysis
        assertNotNull("correlationAnalysis", ca)
        val etfCorr = ca!!.correlations.find { it.indicator2 == "ETF자금흐름" }
        assertNotNull("ETF자금흐름 상관 결과 존재", etfCorr)
    }

    @Test
    fun `데이터 부족 시 일부 엔진이 null일 수 있다`() = runTest {
        setupMockData(10) // 매우 적은 데이터

        val result = engine.analyze("005930")

        // 일부 엔진은 데이터 부족으로 실패할 수 있지만 앱은 크래시하지 않아야 함
        assertNotNull("result", result)
        assertEquals("005930", result.ticker)
    }

    @Test
    fun `빈 데이터에서도 크래시하지 않는다`() = runTest {
        coEvery { repository.getDailyPrices(any(), any()) } returns emptyList()
        coEvery { repository.getStockName(any()) } returns "테스트"
        coEvery { repository.getOscillatorData(any(), any()) } returns emptyList()
        coEvery { repository.getDemarkData(any(), any()) } returns emptyList()
        coEvery { repository.getFundamentalData(any(), any()) } returns emptyList()
        coEvery { repository.getEtfHoldingCount(any()) } returns 0
        coEvery { repository.getEtfAmountTrend(any()) } returns emptyList()
        coEvery { repository.getSectorEtfReturns(any(), any()) } returns emptyList()

        val result = engine.analyze("000000")

        assertNotNull("result", result)
        // 대부분의 엔진이 실패하지만 앱은 정상
        assertTrue("failedEngines 존재 가능", true)
    }

    // ─── Mock 데이터 설정 ───

    private fun setupMockData(days: Int) {
        val prices = (0 until days).map { i ->
            val variation = ((i % 7) - 3) * 500
            DailyTrading(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = (50000 + variation) * 1000000L,
                foreignNetBuy = (variation * 1000).toLong(),
                instNetBuy = (-variation * 500).toLong(),
                closePrice = 50000 + variation
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
                closePrice = 50000 + (i % 7 - 3) * 500,
                marketCapTril = 50.0,
                tdSellCount = i % 10,
                tdBuyCount = (9 - i % 10).coerceAtLeast(0)
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
        val etfAmountTrend = (0 until days).map { i ->
            EtfAmountPoint(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                totalAmount = 1000000L + i * 10000L + ((i % 5 - 2) * 50000L),
                etfCount = 5 + (i % 3)
            )
        }

        val sectorEtfReturns = (1 until days).map { i ->
            SectorEtfReturn(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                etfTicker = "AGG_ETF_FLOW",
                etfName = "ETF자금흐름",
                dailyReturn = ((i % 5) - 2) * 0.01
            )
        }

        coEvery { repository.getEtfHoldingCount(any()) } returns 5
        coEvery { repository.getEtfAmountTrend(any()) } returns etfAmountTrend
        coEvery { repository.getSectorEtfReturns(any(), any()) } returns sectorEtfReturns
    }
}
