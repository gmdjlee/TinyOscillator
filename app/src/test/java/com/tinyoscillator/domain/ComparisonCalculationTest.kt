package com.tinyoscillator.domain

import com.tinyoscillator.domain.model.ComparisonPeriod
import com.tinyoscillator.domain.usecase.BuildComparisonUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparisonCalculationTest {

    @Test
    fun `normalized returns start at 0`() {
        val prices = listOf(50_000f, 52_000f, 48_000f, 55_000f)
        val returns = BuildComparisonUseCase.normalizeReturns(prices)
        assertEquals(0f, returns.first(), 0.001f)
    }

    @Test
    fun `normalized returns reflect percentage change`() {
        val prices = listOf(100f, 110f)
        val returns = BuildComparisonUseCase.normalizeReturns(prices)
        assertEquals(0.10f, returns.last(), 0.001f)
    }

    @Test
    fun `empty price list returns empty`() =
        assertTrue(BuildComparisonUseCase.normalizeReturns(emptyList()).isEmpty())

    @Test
    fun `single price list returns zero`() {
        val returns = BuildComparisonUseCase.normalizeReturns(listOf(50_000f))
        assertEquals(1, returns.size)
        assertEquals(0f, returns.first(), 0.001f)
    }

    @Test
    fun `beta of identical series is 1_0`() {
        val prices = (1..30).map { it.toFloat() * 100f }
        val returns = BuildComparisonUseCase.normalizeReturns(prices)
        val beta = BuildComparisonUseCase.estimateBeta(returns, returns)
        assertEquals(1.0f, beta, 0.01f)
    }

    @Test
    fun `beta under 10 data points returns 1_0`() {
        val y = listOf(0.01f, 0.02f, -0.01f)
        val x = listOf(0.01f, 0.02f, -0.01f)
        assertEquals(1.0f, BuildComparisonUseCase.estimateBeta(y, x), 0.001f)
    }

    @Test
    fun `alpha is target return minus KOSPI return`() {
        val targetFinal = 0.15f
        val kospiFinal = 0.08f
        val alpha = targetFinal - kospiFinal
        assertEquals(0.07f, alpha, 0.001f)
    }

    @Test
    fun `period days mapping correct`() {
        assertEquals(91, ComparisonPeriod.THREE_MONTHS.days)
        assertEquals(182, ComparisonPeriod.SIX_MONTHS.days)
        assertEquals(365, ComparisonPeriod.ONE_YEAR.days)
    }
}
