package com.tinyoscillator.domain.usecase

import com.tinyoscillator.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProbabilityInterpreterTest {

    private lateinit var interpreter: ProbabilityInterpreter

    @Before
    fun setup() {
        interpreter = ProbabilityInterpreter()
    }

    // --- summarize ---

    @Test
    fun `summarize - bullish result produces upward summary`() {
        val result = createResult(
            bayesUp = 0.6, bayesDown = 0.2, bayesSideways = 0.2,
            signalScore = 72,
            bayesianPrior = 0.5, bayesianPosterior = 0.65,
            hmmRegime = HmmResult.REGIME_LOW_VOL_UP
        )
        val summary = interpreter.summarize(result)
        assertTrue(summary.contains("상승"))
        assertTrue(summary.contains("강한 매수"))
        assertTrue(summary.contains("상향"))
    }

    @Test
    fun `summarize - bearish result produces downward summary`() {
        val result = createResult(
            bayesUp = 0.15, bayesDown = 0.7, bayesSideways = 0.15,
            signalScore = 25,
            bayesianPrior = 0.5, bayesianPosterior = 0.3,
            hmmRegime = HmmResult.REGIME_HIGH_VOL_DOWN
        )
        val summary = interpreter.summarize(result)
        assertTrue(summary.contains("하락"))
        assertTrue(summary.contains("강한 매도"))
    }

    @Test
    fun `summarize - empty result returns fallback message`() {
        val result = StatisticalResult(ticker = "005930", stockName = "삼성전자")
        val summary = interpreter.summarize(result)
        assertEquals("분석 결과를 해석할 수 없습니다.", summary)
    }

    // --- interpretBayes ---

    @Test
    fun `interpretBayes - clear direction shows strong signal`() {
        val bayes = BayesResult(
            upProbability = 0.7, downProbability = 0.15, sidewaysProbability = 0.15,
            dominantFeatures = listOf(
                FeatureContribution("외국인 수급", 2.5),
                FeatureContribution("MACD", 1.8)
            ),
            sampleCount = 200
        )
        val text = interpreter.interpretBayes(bayes)
        assertTrue(text.contains("상승"))
        assertTrue(text.contains("200개"))
        assertTrue(text.contains("강한 영향"))
    }

    @Test
    fun `interpretBayes - even distribution shows unclear signal`() {
        val bayes = BayesResult(
            upProbability = 0.35, downProbability = 0.33, sidewaysProbability = 0.32,
            dominantFeatures = emptyList(),
            sampleCount = 100
        )
        val text = interpreter.interpretBayes(bayes)
        assertTrue(text.contains("불명확"))
    }

    // --- interpretLogistic ---

    @Test
    fun `interpretLogistic - high score shows positive sentiment`() {
        val lr = LogisticResult(
            probability = 0.78, weights = emptyMap(),
            featureValues = mapOf("MACD" to 0.5, "RSI" to -0.2),
            score0to100 = 78
        )
        val text = interpreter.interpretLogistic(lr)
        assertTrue(text.contains("매우 긍정적"))
        assertTrue(text.contains("78/100"))
    }

    @Test
    fun `interpretLogistic - low score shows negative sentiment`() {
        val lr = LogisticResult(
            probability = 0.2, weights = emptyMap(),
            featureValues = mapOf("MACD" to -0.5),
            score0to100 = 20
        )
        val text = interpreter.interpretLogistic(lr)
        assertTrue(text.contains("매우 부정적"))
    }

    // --- interpretHmm ---

    @Test
    fun `interpretHmm - stable regime path notes trend continuation`() {
        val hmm = HmmResult(
            currentRegime = HmmResult.REGIME_LOW_VOL_UP,
            regimeProbabilities = doubleArrayOf(0.7, 0.1, 0.1, 0.1),
            transitionProbabilities = emptyMap(),
            recentRegimePath = listOf(0, 0, 0, 0, 0),
            regimeDescription = "저변동 상승 (안정적 상승 추세)"
        )
        val text = interpreter.interpretHmm(hmm)
        assertTrue(text.contains("안정적으로 유지"))
        assertTrue(text.contains("추세 추종"))
    }

    @Test
    fun `interpretHmm - regime transition warns of change`() {
        val hmm = HmmResult(
            currentRegime = HmmResult.REGIME_HIGH_VOL_DOWN,
            regimeProbabilities = doubleArrayOf(0.1, 0.1, 0.2, 0.6),
            transitionProbabilities = emptyMap(),
            recentRegimePath = listOf(0, 2, 3),
            regimeDescription = "고변동 하락 (급락 구간)"
        )
        val text = interpreter.interpretHmm(hmm)
        assertTrue(text.contains("전환이 감지"))
        assertTrue(text.contains("방어적"))
    }

    // --- interpretPattern ---

    @Test
    fun `interpretPattern - active patterns with high win rate show reliability`() {
        val pa = PatternAnalysis(
            allPatterns = emptyList(),
            activePatterns = listOf(
                PatternMatch(
                    patternName = "goldenCross", patternDescription = "골든크로스",
                    isActive = true, occurrences = emptyList(),
                    winRate5d = 0.6, winRate10d = 0.65, winRate20d = 0.7,
                    avgReturn5d = 0.02, avgReturn10d = 0.035, avgReturn20d = 0.05,
                    avgMdd20d = -0.03, totalOccurrences = 25
                )
            ),
            totalHistoricalDays = 365
        )
        val text = interpreter.interpretPattern(pa)
        assertTrue(text.contains("높은 신뢰도"))
        assertTrue(text.contains("골든크로스"))
    }

    @Test
    fun `interpretPattern - no active patterns shows absence`() {
        val pa = PatternAnalysis(
            allPatterns = emptyList(),
            activePatterns = emptyList(),
            totalHistoricalDays = 365
        )
        val text = interpreter.interpretPattern(pa)
        assertTrue(text.contains("활성화된 패턴이 없어"))
    }

    // --- interpretSignalScoring ---

    @Test
    fun `interpretSignalScoring - high score shows buy signal`() {
        val ss = SignalScoringResult(
            totalScore = 75,
            contributions = listOf(
                SignalContribution("MACD", 0.3, 1, 1, 35.0),
                SignalContribution("RSI", 0.2, 1, 1, 25.0)
            ),
            conflictingSignals = emptyList(),
            dominantDirection = "매수"
        )
        val text = interpreter.interpretSignalScoring(ss)
        assertTrue(text.contains("강한 매수"))
        assertTrue(text.contains("75/100"))
    }

    @Test
    fun `interpretSignalScoring - conflicts are highlighted`() {
        val ss = SignalScoringResult(
            totalScore = 50,
            contributions = emptyList(),
            conflictingSignals = listOf(
                SignalConflict("MACD", "RSI", "매수", "매도", "MACD 매수 vs RSI 매도")
            ),
            dominantDirection = "중립"
        )
        val text = interpreter.interpretSignalScoring(ss)
        assertTrue(text.contains("충돌 신호"))
        assertTrue(text.contains("리스크"))
    }

    // --- interpretCorrelation ---

    @Test
    fun `interpretCorrelation - strong correlations are highlighted`() {
        val ca = CorrelationAnalysis(
            correlations = listOf(
                CorrelationResult("외인수급", "주가", 0.85, CorrelationStrength.STRONG_POSITIVE)
            ),
            leadLagResults = listOf(
                LeadLagResult("외인수급", "주가", 3, 0.8, "외인수급이 주가를 3일 선행")
            )
        )
        val text = interpreter.interpretCorrelation(ca)
        assertTrue(text.contains("강한 상관관계"))
        assertTrue(text.contains("예측력"))
    }

    // --- interpretBayesianUpdate ---

    @Test
    fun `interpretBayesianUpdate - large upward shift detected`() {
        val bu = BayesianUpdateResult(
            finalPosterior = 0.75,
            priorProbability = 0.5,
            updateHistory = listOf(
                ProbabilityUpdate("MACD 골든크로스", 0.5, 0.65, 0.15, 1.8),
                ProbabilityUpdate("외인 매수", 0.65, 0.75, 0.10, 1.5)
            )
        )
        val text = interpreter.interpretBayesianUpdate(bu)
        assertTrue(text.contains("대폭"))
        assertTrue(text.contains("상향"))
        assertTrue(text.contains("MACD 골든크로스"))
    }

    // --- buildPromptForAi ---

    @Test
    fun `buildPromptForAi - includes all engine results`() {
        val result = createResult(
            bayesUp = 0.6, bayesDown = 0.2, bayesSideways = 0.2,
            signalScore = 70,
            bayesianPrior = 0.5, bayesianPosterior = 0.65,
            hmmRegime = HmmResult.REGIME_LOW_VOL_UP
        )
        val prompt = interpreter.buildPromptForAi(result)
        assertTrue(prompt.contains("나이브 베이즈"))
        assertTrue(prompt.contains("신호점수"))
        assertTrue(prompt.contains("베이지안"))
        assertTrue(prompt.contains("HMM"))
    }

    // --- Helper ---

    private fun createResult(
        bayesUp: Double, bayesDown: Double, bayesSideways: Double,
        signalScore: Int,
        bayesianPrior: Double, bayesianPosterior: Double,
        hmmRegime: Int
    ): StatisticalResult {
        return StatisticalResult(
            ticker = "005930",
            stockName = "삼성전자",
            bayesResult = BayesResult(
                upProbability = bayesUp, downProbability = bayesDown,
                sidewaysProbability = bayesSideways,
                dominantFeatures = listOf(FeatureContribution("외국인 수급", 2.0)),
                sampleCount = 200
            ),
            logisticResult = LogisticResult(
                probability = bayesUp, weights = emptyMap(),
                featureValues = mapOf("MACD" to 0.3),
                score0to100 = (bayesUp * 100).toInt()
            ),
            hmmResult = HmmResult(
                currentRegime = hmmRegime,
                regimeProbabilities = doubleArrayOf(0.7, 0.1, 0.1, 0.1),
                transitionProbabilities = emptyMap(),
                recentRegimePath = listOf(hmmRegime, hmmRegime, hmmRegime),
                regimeDescription = HmmResult.regimeToDescription(hmmRegime)
            ),
            signalScoringResult = SignalScoringResult(
                totalScore = signalScore,
                contributions = listOf(
                    SignalContribution("MACD", 0.3, 1, 1, 40.0)
                ),
                conflictingSignals = emptyList(),
                dominantDirection = if (signalScore >= 55) "매수" else "매도"
            ),
            bayesianUpdateResult = BayesianUpdateResult(
                finalPosterior = bayesianPosterior,
                priorProbability = bayesianPrior,
                updateHistory = listOf(
                    ProbabilityUpdate("MACD", bayesianPrior, bayesianPosterior,
                        bayesianPosterior - bayesianPrior, 1.5)
                )
            )
        )
    }
}
