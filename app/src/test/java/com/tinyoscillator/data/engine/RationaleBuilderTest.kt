package com.tinyoscillator.data.engine

import com.tinyoscillator.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class RationaleBuilderTest {

    private val fullResult = StatisticalResult(
        ticker = "005930",
        stockName = "삼성전자",
        bayesResult = BayesResult(
            upProbability = 0.65,
            downProbability = 0.20,
            sidewaysProbability = 0.15,
            dominantFeatures = listOf(
                FeatureContribution("RSI_14", 2.1)
            ),
            sampleCount = 100
        ),
        logisticResult = LogisticResult(
            probability = 0.72,
            weights = mapOf("ema_cross" to 0.8),
            featureValues = mapOf("ema_cross" to 1.23),
            score0to100 = 72
        ),
        hmmResult = HmmResult(
            currentRegime = 0,
            regimeProbabilities = doubleArrayOf(0.7, 0.1, 0.1, 0.1),
            transitionProbabilities = emptyMap(),
            recentRegimePath = listOf(0, 0, 0),
            regimeDescription = "저변동 상승"
        ),
        patternAnalysis = PatternAnalysis(
            allPatterns = listOf(samplePattern()),
            activePatterns = listOf(samplePattern()),
            totalHistoricalDays = 200
        ),
        signalScoringResult = SignalScoringResult(
            totalScore = 68,
            contributions = listOf(
                SignalContribution("RSI", 0.2, 1, 1, 35.0)
            ),
            conflictingSignals = emptyList(),
            dominantDirection = "BULLISH"
        ),
        correlationAnalysis = CorrelationAnalysis(
            correlations = emptyList(),
            leadLagResults = emptyList()
        ),
        bayesianUpdateResult = BayesianUpdateResult(
            finalPosterior = 0.58,
            priorProbability = 0.50,
            updateHistory = listOf(
                ProbabilityUpdate("RSI", 0.50, 0.58, 0.08, 1.5)
            )
        ),
        orderFlowResult = OrderFlowResult(
            buyerDominanceScore = 0.65,
            ofi5d = 0.3,
            ofi20d = 0.15,
            institutionalDivergence = 0.2,
            foreignBuyPressure = 0.4,
            signalScore = 0.65,
            flowDirection = "BUY",
            flowStrength = "MODERATE",
            trendAlignment = 0.7,
            meanReversionSignal = 0.3
        )
    )

    @Test
    fun `build returns AlgoResult for each non-null engine`() {
        val results = RationaleBuilder.build(fullResult)
        // 7 engines: Bayes, Logistic, HMM, PatternScan, SignalScoring, BayesianUpdate, OrderFlow
        // (Correlation excluded by SignalScoreExtractor, Dart/Korea5Factor/SectorCorr are null)
        assertEquals(7, results.size)
    }

    @Test
    fun `all rationale strings are non-blank`() {
        val results = RationaleBuilder.build(fullResult)
        results.values.forEach { r ->
            assertTrue(
                "${r.algoName} has blank rationale",
                r.rationale.isNotBlank()
            )
        }
    }

    @Test
    fun `all rationale strings are 50 chars or less`() {
        val results = RationaleBuilder.build(fullResult)
        results.values.forEach { r ->
            assertTrue(
                "${r.algoName} rationale too long: ${r.rationale.length} chars '${r.rationale}'",
                r.rationale.length <= 50
            )
        }
    }

    @Test
    fun `all rationale strings contain numbers`() {
        val results = RationaleBuilder.build(fullResult)
        results.values.forEach { r ->
            assertTrue(
                "${r.algoName} rationale has no numbers: '${r.rationale}'",
                r.rationale.any { it.isDigit() }
            )
        }
    }

    @Test
    fun `all rationale strings contain direction keyword`() {
        val directionKeywords = listOf("강세", "약세", "중립")
        val results = RationaleBuilder.build(fullResult)
        results.values.forEach { r ->
            assertTrue(
                "${r.algoName} rationale missing direction: '${r.rationale}'",
                directionKeywords.any { r.rationale.contains(it) }
            )
        }
    }

    @Test
    fun `scores are in 0 to 1 range`() {
        val results = RationaleBuilder.build(fullResult)
        results.values.forEach { r ->
            assertTrue(
                "${r.algoName} score out of range: ${r.score}",
                r.score in 0f..1f
            )
        }
    }

    @Test
    fun `weights are non-negative`() {
        val results = RationaleBuilder.build(fullResult)
        results.values.forEach { r ->
            assertTrue(
                "${r.algoName} weight is negative: ${r.weight}",
                r.weight >= 0f
            )
        }
    }

    @Test
    fun `empty result returns empty map`() {
        val emptyResult = StatisticalResult(ticker = "000000", stockName = "테스트")
        val results = RationaleBuilder.build(emptyResult)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `partial result returns only available engines`() {
        val partial = StatisticalResult(
            ticker = "005930",
            stockName = "삼성전자",
            bayesResult = BayesResult(0.6, 0.3, 0.1, emptyList(), 50),
            hmmResult = HmmResult(
                currentRegime = 3,
                regimeProbabilities = doubleArrayOf(0.05, 0.05, 0.1, 0.8),
                transitionProbabilities = emptyMap(),
                recentRegimePath = listOf(3, 3),
                regimeDescription = "고변동 하락"
            )
        )
        val results = RationaleBuilder.build(partial)
        assertEquals(2, results.size)
        assertNotNull(results["NaiveBayes"])
        assertNotNull(results["HMM"])
    }

    @Test
    fun `bearish HMM regime produces 약세 rationale`() {
        val bearish = StatisticalResult(
            ticker = "005930",
            stockName = "삼성전자",
            hmmResult = HmmResult(
                currentRegime = 3,
                regimeProbabilities = doubleArrayOf(0.05, 0.05, 0.1, 0.8),
                transitionProbabilities = emptyMap(),
                recentRegimePath = listOf(3),
                regimeDescription = "고변동 하락"
            )
        )
        val results = RationaleBuilder.build(bearish)
        assertTrue(results["HMM"]!!.rationale.contains("약세"))
    }

    @Test
    fun `bayesian update shows prior to posterior change`() {
        val results = RationaleBuilder.build(fullResult)
        val rationale = results["BayesianUpdate"]!!.rationale
        assertTrue("Contains arrow: $rationale", rationale.contains("→"))
        assertTrue("Contains 사전: $rationale", rationale.contains("사전"))
        assertTrue("Contains 사후: $rationale", rationale.contains("사후"))
    }

    @Test
    fun `order flow buy direction produces 강세`() {
        val results = RationaleBuilder.build(fullResult)
        assertTrue(results["OrderFlow"]!!.rationale.contains("강세"))
    }

    private fun samplePattern() = PatternMatch(
        patternName = "golden_cross",
        patternDescription = "골든크로스",
        isActive = true,
        occurrences = emptyList(),
        winRate5d = 0.60,
        winRate10d = 0.62,
        winRate20d = 0.65,
        avgReturn5d = 0.02,
        avgReturn10d = 0.03,
        avgReturn20d = 0.05,
        avgMdd20d = -0.03,
        totalOccurrences = 15
    )
}
