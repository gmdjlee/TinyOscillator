package com.tinyoscillator.data.engine.macro

import com.tinyoscillator.data.engine.regime.RegimeWeightTable
import com.tinyoscillator.domain.model.MacroEnvironment
import com.tinyoscillator.domain.model.MacroSignalResult
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class MacroRegimeOverlayTest {

    private lateinit var overlay: MacroRegimeOverlay

    @Before
    fun setup() {
        overlay = MacroRegimeOverlay()
    }

    private fun makeMacroSignal(
        baseRateYoy: Double = 0.0,
        m2Yoy: Double = 5.0,
        iipYoy: Double = 2.0,
        usdKrwYoy: Double = 3.0,
        cpiYoy: Double = 2.0
    ) = MacroSignalResult(
        baseRateYoy = baseRateYoy,
        m2Yoy = m2Yoy,
        iipYoy = iipYoy,
        usdKrwYoy = usdKrwYoy,
        cpiYoy = cpiYoy,
        macroEnv = ""
    )

    // ─── classifyMacroEnvironment tests ───

    @Test
    fun `classify returns TIGHTENING when base rate YoY above threshold`() {
        val signal = makeMacroSignal(baseRateYoy = 0.75)
        val result = overlay.classifyMacroEnvironment(signal)
        assertEquals(MacroEnvironment.TIGHTENING, result)
    }

    @Test
    fun `classify returns EASING when base rate YoY below threshold`() {
        val signal = makeMacroSignal(baseRateYoy = -0.75)
        val result = overlay.classifyMacroEnvironment(signal)
        assertEquals(MacroEnvironment.EASING, result)
    }

    @Test
    fun `classify returns STAGFLATION when IIP drops and CPI rises`() {
        val signal = makeMacroSignal(
            baseRateYoy = 0.2,  // not high enough for TIGHTENING
            iipYoy = -7.0,      // below -5%
            cpiYoy = 4.0        // above 3%
        )
        val result = overlay.classifyMacroEnvironment(signal)
        assertEquals(MacroEnvironment.STAGFLATION, result)
    }

    @Test
    fun `classify returns NEUTRAL when no condition met`() {
        val signal = makeMacroSignal(baseRateYoy = 0.2, iipYoy = 1.0, cpiYoy = 2.0)
        val result = overlay.classifyMacroEnvironment(signal)
        assertEquals(MacroEnvironment.NEUTRAL, result)
    }

    @Test
    fun `classify STAGFLATION takes priority over TIGHTENING`() {
        val signal = makeMacroSignal(
            baseRateYoy = 0.75,  // qualifies for TIGHTENING
            iipYoy = -6.0,       // qualifies for STAGFLATION
            cpiYoy = 4.0         // qualifies for STAGFLATION
        )
        val result = overlay.classifyMacroEnvironment(signal)
        assertEquals(MacroEnvironment.STAGFLATION, result)
    }

    @Test
    fun `classify boundary base_rate exactly at threshold returns NEUTRAL`() {
        // exactly at 0.5 is not > 0.5
        val signal = makeMacroSignal(baseRateYoy = 0.5)
        val result = overlay.classifyMacroEnvironment(signal)
        assertEquals(MacroEnvironment.NEUTRAL, result)
    }

    // ─── adjustRegimeWeights tests ───

    @Test
    fun `adjust NEUTRAL returns original weights unchanged`() {
        val weights = RegimeWeightTable.equalWeights()
        val adjusted = overlay.adjustRegimeWeights(weights, MacroEnvironment.NEUTRAL)
        assertEquals(weights, adjusted)
    }

    @Test
    fun `adjust weights always sum to 1_0`() {
        val baseWeights = RegimeWeightTable.getWeights("BULL_LOW_VOL")
        for (env in MacroEnvironment.entries) {
            val adjusted = overlay.adjustRegimeWeights(baseWeights, env)
            val sum = adjusted.values.sum()
            assertTrue("Sum should be 1.0 for $env, got $sum", abs(sum - 1.0) < 1e-6)
        }
    }

    @Test
    fun `adjust weights sum to 1_0 for all regimes and environments`() {
        for (regimeName in listOf("BULL_LOW_VOL", "BEAR_HIGH_VOL", "SIDEWAYS", "CRISIS")) {
            val baseWeights = RegimeWeightTable.getWeights(regimeName)
            for (env in MacroEnvironment.entries) {
                val adjusted = overlay.adjustRegimeWeights(baseWeights, env)
                val sum = adjusted.values.sum()
                assertTrue(
                    "Sum should be 1.0 for regime=$regimeName, env=$env, got $sum",
                    abs(sum - 1.0) < 1e-6
                )
            }
        }
    }

    @Test
    fun `adjust TIGHTENING reduces momentum and boosts HMM`() {
        val baseWeights = RegimeWeightTable.getWeights("BULL_LOW_VOL")
        val adjusted = overlay.adjustRegimeWeights(baseWeights, MacroEnvironment.TIGHTENING)

        // After normalization, relative order should change
        // HMM should have higher proportion than in base
        val baseHmmRatio = baseWeights[RegimeWeightTable.ALGO_HMM]!! / baseWeights.values.sum()
        val adjHmmRatio = adjusted[RegimeWeightTable.ALGO_HMM]!! / adjusted.values.sum()
        assertTrue("HMM weight should increase in TIGHTENING", adjHmmRatio > baseHmmRatio)
    }

    @Test
    fun `adjust EASING boosts momentum`() {
        val baseWeights = RegimeWeightTable.getWeights("SIDEWAYS")
        val adjusted = overlay.adjustRegimeWeights(baseWeights, MacroEnvironment.EASING)

        val basePatternRatio = baseWeights[RegimeWeightTable.ALGO_PATTERN_SCAN]!! / baseWeights.values.sum()
        val adjPatternRatio = adjusted[RegimeWeightTable.ALGO_PATTERN_SCAN]!! / adjusted.values.sum()
        assertTrue("PatternScan weight should increase in EASING", adjPatternRatio > basePatternRatio)
    }

    @Test
    fun `adjust STAGFLATION boosts DartEvent and OrderFlow`() {
        val baseWeights = RegimeWeightTable.getWeights("BEAR_HIGH_VOL")
        val adjusted = overlay.adjustRegimeWeights(baseWeights, MacroEnvironment.STAGFLATION)

        val baseDartRatio = baseWeights[RegimeWeightTable.ALGO_DART_EVENT]!! / baseWeights.values.sum()
        val adjDartRatio = adjusted[RegimeWeightTable.ALGO_DART_EVENT]!! / adjusted.values.sum()
        assertTrue("DartEvent weight should increase in STAGFLATION", adjDartRatio > baseDartRatio)
    }

    @Test
    fun `adjust with empty weights returns empty`() {
        val adjusted = overlay.adjustRegimeWeights(emptyMap(), MacroEnvironment.TIGHTENING)
        assertTrue(adjusted.isEmpty())
    }

    @Test
    fun `all adjusted weights are positive`() {
        val baseWeights = RegimeWeightTable.getWeights("CRISIS")
        for (env in MacroEnvironment.entries) {
            val adjusted = overlay.adjustRegimeWeights(baseWeights, env)
            for ((algo, weight) in adjusted) {
                assertTrue("Weight for $algo in $env should be positive, got $weight", weight > 0)
            }
        }
    }

    // ─── normalize tests ───

    @Test
    fun `normalize produces sum of 1_0`() {
        val input = mapOf("a" to 0.3, "b" to 0.5, "c" to 0.2)
        val normalized = overlay.normalize(input)
        assertEquals(1.0, normalized.values.sum(), 1e-6)
    }

    // ─── applyClassification tests ───

    @Test
    fun `applyClassification sets macroEnv field`() {
        val signal = makeMacroSignal(baseRateYoy = 1.0)
        val classified = overlay.applyClassification(signal)
        assertEquals("TIGHTENING", classified.macroEnv)
    }

    @Test
    fun `MacroEnvironment fromString round-trips correctly`() {
        for (env in MacroEnvironment.entries) {
            assertEquals(env, MacroEnvironment.fromString(env.name))
        }
    }

    @Test
    fun `MacroEnvironment fromString returns NEUTRAL for unknown`() {
        assertEquals(MacroEnvironment.NEUTRAL, MacroEnvironment.fromString("UNKNOWN"))
    }
}
