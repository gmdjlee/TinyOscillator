package com.tinyoscillator.data.engine.ensemble

import com.tinyoscillator.data.engine.regime.RegimeWeightTable
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class RegimeStackingEnsembleTest {

    private lateinit var regimeEnsemble: RegimeStackingEnsemble
    private val algoNames = RegimeWeightTable.ALL_ALGOS

    @Before
    fun setup() {
        regimeEnsemble = RegimeStackingEnsemble(algoNames, metaC = 0.5)
    }

    @Test
    fun `fitRegime with sufficient samples creates regime-specific model`() {
        val (signals, labels) = generateSyntheticData(80)

        regimeEnsemble.fitRegime("BULL_LOW_VOL", signals, labels)
        assertTrue(regimeEnsemble.isRegimeFitted("BULL_LOW_VOL"))
        assertTrue(regimeEnsemble.fittedRegimes().contains("BULL_LOW_VOL"))
    }

    @Test
    fun `fitRegime with insufficient samples does not create model`() {
        val (signals, labels) = generateSyntheticData(30)

        regimeEnsemble.fitRegime("BEAR_HIGH_VOL", signals, labels)
        assertFalse(regimeEnsemble.isRegimeFitted("BEAR_HIGH_VOL"))
    }

    @Test
    fun `predictProba uses regime-specific model when available`() {
        // Fit global with bearish data
        val (globalSig, globalLab) = generateSyntheticData(100, bullishBias = -0.15)
        regimeEnsemble.fit(globalSig, globalLab)

        // Fit BULL regime with bullish data
        val (bullSig, bullLab) = generateSyntheticData(80, bullishBias = 0.15)
        regimeEnsemble.fitRegime("BULL_LOW_VOL", bullSig, bullLab)

        val signals = algoNames.associateWith { 0.7 }

        val probBull = regimeEnsemble.predictProba(signals, "BULL_LOW_VOL")
        val probGlobal = regimeEnsemble.predictProba(signals, null)

        // Regime-specific and global should differ (trained on different data)
        assertNotEquals("Regime vs global should differ", probBull, probGlobal, 0.001)
    }

    @Test
    fun `predictProba falls back to global when regime not fitted`() {
        val (signals, labels) = generateSyntheticData(100)
        regimeEnsemble.fit(signals, labels)

        val testSignals = algoNames.associateWith { 0.6 }

        // "CRISIS" not fitted, should fall back to global
        val probCrisis = regimeEnsemble.predictProba(testSignals, "CRISIS")
        val probGlobal = regimeEnsemble.predictProba(testSignals, null)

        assertEquals("Should use global fallback for unfitted regime",
            probGlobal, probCrisis, 1e-10)
    }

    @Test
    fun `saveRegimeStates and loadRegimeStates roundtrip`() {
        val (sig1, lab1) = generateSyntheticData(80)
        val (sig2, lab2) = generateSyntheticData(70)
        regimeEnsemble.fitRegime("BULL_LOW_VOL", sig1, lab1)
        regimeEnsemble.fitRegime("SIDEWAYS", sig2, lab2)

        val states = regimeEnsemble.saveRegimeStates()
        assertEquals(2, states.size)

        val newEnsemble = RegimeStackingEnsemble(algoNames)
        newEnsemble.loadRegimeStates(states)

        assertTrue(newEnsemble.isRegimeFitted("BULL_LOW_VOL"))
        assertTrue(newEnsemble.isRegimeFitted("SIDEWAYS"))
        assertFalse(newEnsemble.isRegimeFitted("CRISIS"))
    }

    @Test
    fun `fittedRegimes returns only fitted regime ids`() {
        val (signals, labels) = generateSyntheticData(80)
        regimeEnsemble.fitRegime("BULL_LOW_VOL", signals, labels)
        regimeEnsemble.fitRegime("CRISIS", signals, labels)

        val fitted = regimeEnsemble.fittedRegimes()
        assertEquals(setOf("BULL_LOW_VOL", "CRISIS"), fitted)
    }

    private fun generateSyntheticData(
        n: Int,
        bullishBias: Double = 0.2
    ): Pair<Array<DoubleArray>, IntArray> {
        val rng = Random(42)
        val signals = Array(n) { i ->
            val label = if (i < n / 2) 0 else 1
            DoubleArray(algoNames.size) {
                val base = if (label == 1) 0.5 + bullishBias else 0.5 - bullishBias
                (base + rng.nextDouble(-0.2, 0.2)).coerceIn(0.0, 1.0)
            }
        }
        val labels = IntArray(n) { if (it < n / 2) 0 else 1 }
        return signals to labels
    }
}
