package com.tinyoscillator.data.engine.ensemble

import com.tinyoscillator.data.engine.regime.RegimeWeightTable
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class StackingEnsembleTest {

    private lateinit var ensemble: StackingEnsemble

    private val algoNames = RegimeWeightTable.ALL_ALGOS

    @Before
    fun setup() {
        ensemble = StackingEnsemble(algoNames, metaC = 0.5)
    }

    // ─── TimeSeriesSplit ───

    @Test
    fun `timeSeriesSplit produces no index overlap between folds`() {
        val n = 100
        val splits = ensemble.timeSeriesSplit(n, cv = 5)

        assertTrue("Should produce at least 2 splits", splits.size >= 2)

        for ((train, test) in splits) {
            val trainSet = train.toSet()
            val testSet = test.toSet()
            val overlap = trainSet.intersect(testSet)
            assertTrue("Train/test should not overlap, but found: $overlap", overlap.isEmpty())
        }
    }

    @Test
    fun `timeSeriesSplit train indices always before test indices`() {
        val n = 120
        val splits = ensemble.timeSeriesSplit(n, cv = 5)

        for ((train, test) in splits) {
            val maxTrain = train.max()
            val minTest = test.min()
            assertTrue("Max train index ($maxTrain) should be < min test index ($minTest)",
                maxTrain < minTest)
        }
    }

    @Test
    fun `timeSeriesSplit covers all test samples without duplication across folds`() {
        val n = 100
        val splits = ensemble.timeSeriesSplit(n, cv = 5)

        val allTestIndices = mutableListOf<Int>()
        for ((_, test) in splits) {
            allTestIndices.addAll(test.toList())
        }

        // No duplicates in test sets across folds
        assertEquals("Test indices should not repeat", allTestIndices.size, allTestIndices.toSet().size)
    }

    // ─── OOF predictions ───

    @Test
    fun `collectOofPredictions returns predictions in 0-1 range`() {
        val (signals, labels) = generateSyntheticData(80)
        val oof = ensemble.collectOofPredictions(signals, labels, cv = 5)

        assertEquals(80, oof.size)
        oof.forEach { p ->
            assertTrue("OOF prediction $p should be in [0,1]", p in 0.0..1.0)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `collectOofPredictions rejects insufficient samples`() {
        val (signals, labels) = generateSyntheticData(30) // < MIN_SAMPLES=60
        ensemble.collectOofPredictions(signals, labels)
    }

    // ─── fit/predict ───

    @Test
    fun `fit on synthetic data produces fitted state`() {
        val (signals, labels) = generateSyntheticData(100)

        assertFalse(ensemble.isFitted)
        ensemble.fit(signals, labels)
        assertTrue(ensemble.isFitted)
    }

    @Test
    fun `predict_proba returns value in 0-1 range`() {
        val (signals, labels) = generateSyntheticData(100)
        ensemble.fit(signals, labels)

        val currentSignals = algoNames.associateWith { 0.6 }
        val prob = ensemble.predictProba(currentSignals)
        assertTrue("Probability $prob should be in [0,1]", prob in 0.0..1.0)
    }

    @Test
    fun `predict_proba differentiates bullish and bearish signals`() {
        val (signals, labels) = generateSyntheticData(200, separation = 0.3)
        ensemble.fit(signals, labels)

        val bullish = algoNames.associateWith { 0.9 }
        val bearish = algoNames.associateWith { 0.1 }

        val probBull = ensemble.predictProba(bullish)
        val probBear = ensemble.predictProba(bearish)

        assertTrue("Bullish ($probBull) should be > bearish ($probBear)", probBull > probBear)
    }

    @Test(expected = IllegalStateException::class)
    fun `predict_proba throws when not fitted`() {
        val signals = algoNames.associateWith { 0.5 }
        ensemble.predictProba(signals)
    }

    // ─── Feature importance ───

    @Test
    fun `featureImportance sums to 1 when fitted`() {
        val (signals, labels) = generateSyntheticData(100)
        ensemble.fit(signals, labels)

        val importance = ensemble.featureImportance()
        assertEquals(algoNames.size, importance.size)

        val sum = importance.values.sum()
        assertTrue("Feature importance sum ($sum) should be ~1.0", abs(sum - 1.0f) < 0.01f)

        importance.values.forEach { w ->
            assertTrue("All weights should be >= 0", w >= 0f)
        }
    }

    @Test
    fun `featureImportance returns empty when not fitted`() {
        assertTrue(ensemble.featureImportance().isEmpty())
    }

    // ─── save/load state ───

    @Test
    fun `save-load state produces identical predictions`() {
        val (signals, labels) = generateSyntheticData(100)
        ensemble.fit(signals, labels)

        val testSignals = algoNames.associateWith { Random.nextDouble(0.0, 1.0) }
        val probBefore = ensemble.predictProba(testSignals)

        val state = ensemble.saveState()

        val newEnsemble = StackingEnsemble(algoNames, metaC = 0.5)
        newEnsemble.loadState(state)

        val probAfter = newEnsemble.predictProba(testSignals)
        assertEquals("Prediction should be identical after save/load",
            probBefore, probAfter, 1e-10)
    }

    @Test
    fun `loadState with empty coefficients results in unfitted`() {
        val state = ensemble.saveState()
        assertTrue(state.coefficients.isEmpty())

        val newEnsemble = StackingEnsemble(algoNames)
        newEnsemble.loadState(state)
        assertFalse(newEnsemble.isFitted)
    }

    // ─── getStatus ───

    @Test
    fun `getStatus shows unfitted when not trained`() {
        val status = ensemble.getStatus()
        assertFalse(status.isFitted)
        assertEquals(0, status.nTrainingSamples)
        assertEquals("", status.topAlgo)
    }

    @Test
    fun `getStatus shows fitted after training`() {
        val (signals, labels) = generateSyntheticData(80)
        ensemble.fit(signals, labels)

        val status = ensemble.getStatus()
        assertTrue(status.isFitted)
        assertEquals(80, status.nTrainingSamples)
        assertTrue(status.topAlgo.isNotEmpty())
        assertTrue(status.topAlgoWeight > 0f)
        assertTrue(status.lastFitDate.isNotEmpty())
    }

    // ─── Cold start: minimum samples ───

    @Test(expected = IllegalArgumentException::class)
    fun `fit rejects less than 60 samples`() {
        val (signals, labels) = generateSyntheticData(59)
        ensemble.fit(signals, labels)
    }

    @Test
    fun `fit accepts exactly 60 samples`() {
        val (signals, labels) = generateSyntheticData(60)
        ensemble.fit(signals, labels)
        assertTrue(ensemble.isFitted)
    }

    // ─── Helper: generate synthetic data ───

    private fun generateSyntheticData(
        n: Int,
        separation: Double = 0.2
    ): Pair<Array<DoubleArray>, IntArray> {
        val rng = Random(42)
        val signals = Array(n) { i ->
            val label = if (i < n / 2) 0 else 1
            DoubleArray(algoNames.size) { j ->
                val base = if (label == 1) 0.5 + separation else 0.5 - separation
                (base + rng.nextDouble(-0.2, 0.2)).coerceIn(0.0, 1.0)
            }
        }
        val labels = IntArray(n) { if (it < n / 2) 0 else 1 }
        return signals to labels
    }
}
