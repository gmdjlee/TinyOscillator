package com.tinyoscillator.domain

import com.tinyoscillator.domain.model.AnalysisStep
import com.tinyoscillator.domain.model.ProgressiveAnalysisState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressiveAnalysisStateTest {

    @Test
    fun `initially all steps are null`() {
        val state = ProgressiveAnalysisState("005930")
        assertNull(state.priceData)
        assertNull(state.technicalData)
        assertNull(state.ensembleData)
        assertNull(state.externalData)
        assertFalse(state.isFullyComplete)
    }

    @Test
    fun `priceData extracted correctly from steps`() {
        val priceStep = AnalysisStep.PriceData(
            ticker = "005930", currentPrice = 73500L, isComplete = true,
        )
        val state = ProgressiveAnalysisState("005930", steps = listOf(priceStep))
        assertNotNull(state.priceData)
        assertEquals(73500L, state.priceData?.currentPrice)
    }

    @Test
    fun `steps accumulate in order`() {
        val price = AnalysisStep.PriceData(isComplete = true)
        val tech = AnalysisStep.TechnicalIndicators(isComplete = true)
        var state = ProgressiveAnalysisState("A")
        state = state.copy(steps = state.steps + price)
        state = state.copy(steps = state.steps + tech)
        assertEquals(2, state.steps.size)
        assertTrue(state.steps[0] is AnalysisStep.PriceData)
        assertTrue(state.steps[1] is AnalysisStep.TechnicalIndicators)
    }

    @Test
    fun `isFullyComplete false until explicitly set`() {
        val state = ProgressiveAnalysisState(
            "A",
            steps = listOf(
                AnalysisStep.PriceData(isComplete = true),
                AnalysisStep.TechnicalIndicators(isComplete = true),
                AnalysisStep.EnsembleSignal(isComplete = true),
            ),
        )
        assertFalse(state.isFullyComplete)
    }

    @Test
    fun `isFullyComplete true after explicit set`() {
        val state = ProgressiveAnalysisState("A").copy(isFullyComplete = true)
        assertTrue(state.isFullyComplete)
    }

    @Test
    fun `ensemble score in valid range`() {
        val ensemble = AnalysisStep.EnsembleSignal(
            ensembleScore = 0.72f, isComplete = true,
        )
        assertTrue(ensemble.ensembleScore in 0f..1f)
    }
}
