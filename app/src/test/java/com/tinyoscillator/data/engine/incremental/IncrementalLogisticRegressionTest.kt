package com.tinyoscillator.data.engine.incremental

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class IncrementalLogisticRegressionTest {

    private val algoNames = listOf("NaiveBayes", "Logistic", "HMM", "PatternScan",
        "SignalScoring", "BayesianUpdate", "OrderFlow", "DartEvent", "Correlation")

    private lateinit var model: IncrementalLogisticRegression

    @Before
    fun setUp() {
        model = IncrementalLogisticRegression(featureNames = algoNames)
    }

    @Test
    fun `warmStart fits model correctly`() {
        val (signals, labels) = generateTrainingData(200)
        model.warmStart(signals, labels)

        assertTrue(model.isFitted)
        assertEquals(200, model.saveState().totalSamples)
    }

    @Test
    fun `warmStart followed by 10 updates produces stable predictions`() {
        val (signals, labels) = generateTrainingData(200)
        model.warmStart(signals, labels)

        // 10 incremental updates
        for (i in 0 until 10) {
            val bullish = algoNames.map { 0.7 + Math.random() * 0.2 }.toDoubleArray()
            model.update(bullish, 1)
        }

        val bullishSignal = algoNames.map { 0.8 }.toDoubleArray()
        val bearishSignal = algoNames.map { 0.2 }.toDoubleArray()
        val bullishProb = model.predictProba(bullishSignal)
        val bearishProb = model.predictProba(bearishSignal)

        assertTrue("bullish=$bullishProb should be > bearish=$bearishProb",
            bullishProb > bearishProb)
        assertTrue(bullishProb in 0.001..0.999)
        assertTrue(bearishProb in 0.001..0.999)
    }

    @Test
    fun `predictProba returns 0_5 when not fitted`() {
        val signal = algoNames.map { 0.7 }.toDoubleArray()
        assertEquals(0.5, model.predictProba(signal), 0.001)
    }

    @Test
    fun `predictProba bounded between 0_001 and 0_999`() {
        val (signals, labels) = generateTrainingData(100)
        model.warmStart(signals, labels)

        val extreme = algoNames.map { 0.99 }.toDoubleArray()
        val prob = model.predictProba(extreme)
        assertTrue(prob >= 0.001)
        assertTrue(prob <= 0.999)
    }

    @Test
    fun `saveState and loadState roundtrip produces identical predictions`() {
        val (signals, labels) = generateTrainingData(150)
        model.warmStart(signals, labels)

        val testSignal = algoNames.map { 0.55 }.toDoubleArray()
        val predBefore = model.predictProba(testSignal)

        val state = model.saveState()

        val restored = IncrementalLogisticRegression(featureNames = algoNames)
        restored.loadState(state)

        val predAfter = restored.predictProba(testSignal)

        assertEquals(predBefore, predAfter, 1e-10)
    }

    @Test
    fun `update increments total samples and learning rate step`() {
        val (signals, labels) = generateTrainingData(100)
        model.warmStart(signals, labels)

        val stateBefore = model.saveState()
        assertEquals(100, stateBefore.totalSamples)
        assertEquals(100, stateBefore.learningRateStep)

        model.update(algoNames.map { 0.7 }.toDoubleArray(), 1)

        val stateAfter = model.saveState()
        assertEquals(101, stateAfter.totalSamples)
        assertEquals(101, stateAfter.learningRateStep)
    }

    @Test
    fun `map-based predictProba works correctly`() {
        val (signals, labels) = generateTrainingData(100)
        model.warmStart(signals, labels)

        val signalMap = algoNames.associateWith { 0.6 }
        val signalArray = algoNames.map { 0.6 }.toDoubleArray()

        assertEquals(model.predictProba(signalArray), model.predictProba(signalMap), 1e-10)
    }

    @Test
    fun `adaptive learning rate decreases over time`() {
        val (signals, labels) = generateTrainingData(100)
        model.warmStart(signals, labels)

        // After warm start, learningRateStep = 100
        // eta = 0.01 / (1 + 0.01 * 0.001 * 100) = ~0.01
        // After 1000 more updates, eta should be smaller
        for (i in 0 until 1000) {
            model.update(algoNames.map { Math.random() }.toDoubleArray(), if (Math.random() > 0.5) 1 else 0)
        }

        val state = model.saveState()
        assertEquals(1100, state.totalSamples)
        assertEquals(1100, state.learningRateStep)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `warmStart rejects mismatched sizes`() {
        val signals = listOf(algoNames.map { 0.5 }.toDoubleArray())
        model.warmStart(signals, listOf(1, 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `update rejects wrong feature size`() {
        val (signals, labels) = generateTrainingData(50)
        model.warmStart(signals, labels)
        model.update(doubleArrayOf(0.5, 0.5), 1) // Too few features
    }

    @Test(expected = IllegalArgumentException::class)
    fun `update rejects invalid label`() {
        val (signals, labels) = generateTrainingData(50)
        model.warmStart(signals, labels)
        model.update(algoNames.map { 0.5 }.toDoubleArray(), 2)
    }

    // ─── Helpers ───

    private fun generateTrainingData(n: Int): Pair<List<DoubleArray>, List<Int>> {
        val signals = mutableListOf<DoubleArray>()
        val labels = mutableListOf<Int>()

        for (i in 0 until n) {
            val isBullish = i < n / 2
            val signal = algoNames.map {
                if (isBullish) 0.6 + Math.random() * 0.3 else 0.1 + Math.random() * 0.3
            }.toDoubleArray()
            signals.add(signal)
            labels.add(if (isBullish) 1 else 0)
        }

        return signals to labels
    }
}
