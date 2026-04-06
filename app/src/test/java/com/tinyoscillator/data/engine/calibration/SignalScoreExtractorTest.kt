package com.tinyoscillator.data.engine.calibration

import com.tinyoscillator.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class SignalScoreExtractorTest {

    @Test
    fun `extracts scores from all non-null engine results`() {
        val result = StatisticalResult(
            ticker = "005930",
            stockName = "삼성전자",
            bayesResult = BayesResult(
                upProbability = 0.65,
                downProbability = 0.20,
                sidewaysProbability = 0.15,
                dominantFeatures = emptyList(),
                sampleCount = 100
            ),
            logisticResult = LogisticResult(
                probability = 0.72,
                weights = emptyMap(),
                featureValues = emptyMap(),
                score0to100 = 72
            ),
            hmmResult = HmmResult(
                currentRegime = 0,
                regimeProbabilities = doubleArrayOf(0.7, 0.1, 0.1, 0.1),
                transitionProbabilities = emptyMap(),
                recentRegimePath = listOf(0, 0, 0),
                regimeDescription = "저변동 상승"
            ),
            signalScoringResult = SignalScoringResult(
                totalScore = 68,
                contributions = emptyList(),
                conflictingSignals = emptyList(),
                dominantDirection = "BULLISH"
            ),
            bayesianUpdateResult = BayesianUpdateResult(
                finalPosterior = 0.58,
                priorProbability = 0.50,
                updateHistory = emptyList()
            )
        )

        val scores = SignalScoreExtractor.extract(result)

        // Should have 5 scores (no PatternScan since null, no Correlation ever)
        assertEquals(5, scores.size)

        val scoreMap = scores.associateBy { it.algoName }
        assertEquals(0.65, scoreMap["NaiveBayes"]!!.rawScore, 1e-10)
        assertEquals(0.72, scoreMap["Logistic"]!!.rawScore, 1e-10)
        assertEquals(0.68, scoreMap["SignalScoring"]!!.rawScore, 1e-10)
        assertEquals(0.58, scoreMap["BayesianUpdate"]!!.rawScore, 1e-10)
        // HMM: 0.7*0.75 + 0.1*0.50 + 0.1*0.65 + 0.1*0.20 = 0.525 + 0.05 + 0.065 + 0.02 = 0.66
        assertEquals(0.66, scoreMap["HMM"]!!.rawScore, 1e-10)
    }

    @Test
    fun `extracts PatternScan when active patterns exist`() {
        val result = StatisticalResult(
            ticker = "005930",
            stockName = "삼성전자",
            patternAnalysis = PatternAnalysis(
                allPatterns = emptyList(),
                activePatterns = listOf(
                    PatternMatch(
                        patternName = "MACD Golden Cross",
                        patternDescription = "",
                        isActive = true,
                        occurrences = emptyList(),
                        winRate5d = 0.60, winRate10d = 0.65, winRate20d = 0.70,
                        avgReturn5d = 0.01, avgReturn10d = 0.02, avgReturn20d = 0.03,
                        avgMdd20d = -0.02, totalOccurrences = 10
                    ),
                    PatternMatch(
                        patternName = "Supply Buy",
                        patternDescription = "",
                        isActive = true,
                        occurrences = emptyList(),
                        winRate5d = 0.55, winRate10d = 0.60, winRate20d = 0.65,
                        avgReturn5d = 0.005, avgReturn10d = 0.01, avgReturn20d = 0.015,
                        avgMdd20d = -0.01, totalOccurrences = 8
                    )
                ),
                totalHistoricalDays = 250
            )
        )

        val scores = SignalScoreExtractor.extract(result)
        val patternScore = scores.find { it.algoName == "PatternScan" }
        assertNotNull(patternScore)
        // Average winRate20d: (0.70 + 0.65) / 2 = 0.675
        assertEquals(0.675, patternScore!!.rawScore, 1e-10)
    }

    @Test
    fun `skips PatternScan when no active patterns`() {
        val result = StatisticalResult(
            ticker = "005930",
            stockName = "삼성전자",
            patternAnalysis = PatternAnalysis(
                allPatterns = emptyList(),
                activePatterns = emptyList(),
                totalHistoricalDays = 250
            )
        )

        val scores = SignalScoreExtractor.extract(result)
        assertNull(scores.find { it.algoName == "PatternScan" })
    }

    @Test
    fun `returns empty list for all-null result`() {
        val result = StatisticalResult(ticker = "005930", stockName = "삼성전자")
        val scores = SignalScoreExtractor.extract(result)
        assertTrue(scores.isEmpty())
    }

    @Test
    fun `all scores are in 0-1 range`() {
        val result = StatisticalResult(
            ticker = "005930",
            stockName = "삼성전자",
            bayesResult = BayesResult(0.99, 0.005, 0.005, emptyList(), 50),
            logisticResult = LogisticResult(0.01, emptyMap(), emptyMap(), 1),
            hmmResult = HmmResult(3, doubleArrayOf(0.0, 0.0, 0.0, 1.0), emptyMap(), listOf(3), "고변동 하락"),
            signalScoringResult = SignalScoringResult(100, emptyList(), emptyList(), "BULLISH"),
            bayesianUpdateResult = BayesianUpdateResult(0.999, 0.5, emptyList())
        )

        val scores = SignalScoreExtractor.extract(result)
        for (score in scores) {
            assertTrue("${score.algoName} score ${score.rawScore} should be in [0,1]",
                score.rawScore in 0.0..1.0)
        }
    }
}
