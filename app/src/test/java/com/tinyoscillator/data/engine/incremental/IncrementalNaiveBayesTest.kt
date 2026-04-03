package com.tinyoscillator.data.engine.incremental

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class IncrementalNaiveBayesTest {

    private val algoNames = listOf("NaiveBayes", "Logistic", "HMM", "PatternScan",
        "SignalScoring", "BayesianUpdate", "OrderFlow", "DartEvent", "Correlation")

    private lateinit var model: IncrementalNaiveBayes

    @Before
    fun setUp() {
        model = IncrementalNaiveBayes(featureNames = algoNames, alpha = 0.5)
    }

    @Test
    fun `warmStart fits model correctly`() {
        val signals = generateBullishSignals(100) + generateBearishSignals(100)
        val labels = List(100) { 1 } + List(100) { 0 }

        model.warmStart(signals, labels)

        assertTrue(model.isFitted)
        val state = model.saveState()
        assertEquals(200, state.totalSamples)
    }

    @Test
    fun `warmStart followed by 10 updates produces stable predictions`() {
        val signals = generateBullishSignals(100) + generateBearishSignals(100)
        val labels = List(100) { 1 } + List(100) { 0 }

        model.warmStart(signals, labels)

        // 10 incremental updates
        for (i in 0 until 10) {
            val newSignal = generateBullishSignals(1).first()
            model.update(newSignal, 1)
        }

        val bullishProb = model.predictProba(generateBullishSignals(1).first())
        val bearishProb = model.predictProba(generateBearishSignals(1).first())

        // Bullish signals should predict higher UP probability
        assertTrue("bullish=$bullishProb should be > bearish=$bearishProb",
            bullishProb > bearishProb)
        assertTrue(bullishProb in 0.001..0.999)
        assertTrue(bearishProb in 0.001..0.999)
    }

    @Test
    fun `predict_proba returns 0_5 when not fitted`() {
        val signal = algoNames.associateWith { 0.7 }
        assertEquals(0.5, model.predictProba(signal), 0.001)
    }

    @Test
    fun `predict_proba bounded between 0_001 and 0_999`() {
        model.warmStart(generateBullishSignals(50) + generateBearishSignals(50),
            List(50) { 1 } + List(50) { 0 })

        val extremeBullish = algoNames.associateWith { 0.99 }
        val extremeBearish = algoNames.associateWith { 0.01 }

        val prob1 = model.predictProba(extremeBullish)
        val prob2 = model.predictProba(extremeBearish)

        assertTrue(prob1 >= 0.001)
        assertTrue(prob1 <= 0.999)
        assertTrue(prob2 >= 0.001)
        assertTrue(prob2 <= 0.999)
    }

    @Test
    fun `saveState and loadState roundtrip produces identical predictions`() {
        model.warmStart(generateBullishSignals(80) + generateBearishSignals(80),
            List(80) { 1 } + List(80) { 0 })

        val testSignal = algoNames.associateWith { 0.6 }
        val predBefore = model.predictProba(testSignal)

        val state = model.saveState()

        // Create new model and load state
        val restored = IncrementalNaiveBayes(featureNames = algoNames, alpha = 0.5)
        restored.loadState(state)

        val predAfter = restored.predictProba(testSignal)

        assertEquals(predBefore, predAfter, 1e-10)
    }

    @Test
    fun `discretize bins signals correctly`() {
        assertEquals("LOW", model.discretize(0.0))
        assertEquals("LOW", model.discretize(0.32))
        assertEquals("MED", model.discretize(0.33))
        assertEquals("MED", model.discretize(0.66))
        assertEquals("HIGH", model.discretize(0.67))
        assertEquals("HIGH", model.discretize(1.0))
    }

    @Test
    fun `update increments total samples`() {
        model.warmStart(generateBullishSignals(50), List(50) { 1 })
        assertEquals(50, model.saveState().totalSamples)

        model.update(generateBullishSignals(1).first(), 1)
        assertEquals(51, model.saveState().totalSamples)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `warmStart rejects mismatched sizes`() {
        model.warmStart(generateBullishSignals(5), List(3) { 1 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `update rejects invalid label`() {
        model.warmStart(generateBullishSignals(10), List(10) { 1 })
        model.update(generateBullishSignals(1).first(), 2)
    }

    @Test
    fun `missing feature values default to 0_5`() {
        model.warmStart(generateBullishSignals(50) + generateBearishSignals(50),
            List(50) { 1 } + List(50) { 0 })

        // Partial signal map
        val partialSignal = mapOf("NaiveBayes" to 0.8, "Logistic" to 0.7)
        val prob = model.predictProba(partialSignal)
        assertTrue(prob in 0.001..0.999)
    }

    // ─── Helpers ───

    private fun generateBullishSignals(n: Int): List<Map<String, Double>> {
        return (0 until n).map {
            algoNames.associateWith { 0.6 + Math.random() * 0.3 } // 0.6-0.9
        }
    }

    private fun generateBearishSignals(n: Int): List<Map<String, Double>> {
        return (0 until n).map {
            algoNames.associateWith { 0.1 + Math.random() * 0.3 } // 0.1-0.4
        }
    }
}
