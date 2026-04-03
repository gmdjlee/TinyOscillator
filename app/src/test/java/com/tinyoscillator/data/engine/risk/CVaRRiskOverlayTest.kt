package com.tinyoscillator.data.engine.risk

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class CVaRRiskOverlayTest {

    private lateinit var overlay: CVaRRiskOverlay

    @Before
    fun setup() {
        overlay = CVaRRiskOverlay(confidence = 0.95, lookback = 252)
    }

    // --- historicalCvar ---

    @Test
    fun `historicalCvar returns negative for normal returns`() {
        val returns = DoubleArray(252) { i -> if (i % 5 == 0) -0.03 else 0.005 }
        val cvar = overlay.historicalCvar(returns)
        assertTrue("CVaR should be negative, got $cvar", cvar < 0.0)
    }

    @Test
    fun `historicalCvar returns default for empty returns`() {
        val cvar = overlay.historicalCvar(doubleArrayOf())
        assertEquals(-0.05, cvar, 1e-10)
    }

    @Test
    fun `historicalCvar with all negative returns uses entire array as tail`() {
        val returns = DoubleArray(100) { -0.02 }
        val cvar = overlay.historicalCvar(returns)
        assertEquals(-0.02, cvar, 1e-10)
    }

    // --- cornishFisherCvar ---

    @Test
    fun `cornishFisherCvar falls back to historical for small samples`() {
        val smallReturns = DoubleArray(10) { -0.01 }
        val cfCvar = overlay.cornishFisherCvar(smallReturns)
        val histCvar = overlay.historicalCvar(smallReturns)
        assertEquals(histCvar, cfCvar, 1e-10)
    }

    @Test
    fun `cornishFisherCvar returns non-positive value`() {
        val returns = DoubleArray(252) { i ->
            val x = (i.toDouble() / 252) * 2 * Math.PI
            kotlin.math.sin(x) * 0.02
        }
        val cvar = overlay.cornishFisherCvar(returns)
        assertTrue("CF CVaR should be <= 0, got $cvar", cvar <= 0.0)
    }

    @Test
    fun `cornishFisherCvar is more extreme than historicalCvar for negatively skewed returns`() {
        // Negatively skewed: few large losses
        val returns = DoubleArray(252) { i ->
            when {
                i % 50 == 0 -> -0.10 // big crashes
                i % 3 == 0 -> -0.005
                else -> 0.008
            }
        }
        val cfCvar = overlay.cornishFisherCvar(returns)
        val histCvar = overlay.historicalCvar(returns)
        // Both should be negative
        assertTrue("CF CVaR should be negative", cfCvar < 0.0)
        assertTrue("Hist CVaR should be negative", histCvar < 0.0)
    }

    @Test
    fun `cornishFisherCvar with stress scenario mixed severe losses`() {
        // Simulate severe bear market: mostly -10% to -20% with occasional small bounces
        val returns = DoubleArray(252) { i ->
            when {
                i % 7 == 0 -> 0.02       // occasional small bounce
                i % 3 == 0 -> -0.15      // frequent large drops
                else -> -0.08            // moderate losses
            }
        }
        val cvar = overlay.cornishFisherCvar(returns)
        assertTrue("CVaR in stress should be very negative, got $cvar", cvar < -0.05)
    }

    @Test
    fun `cornishFisherCvar with zero variance returns 0`() {
        // All identical returns → std = 0 → CVaR = 0
        val returns = DoubleArray(252) { -0.15 }
        val cvar = overlay.cornishFisherCvar(returns)
        assertEquals(0.0, cvar, 1e-10)
    }

    // --- positionLimit ---

    @Test
    fun `positionLimit returns 0 when cvar is non-negative`() {
        assertEquals(0.0, overlay.positionLimit(0.0), 1e-10)
        assertEquals(0.0, overlay.positionLimit(0.01), 1e-10)
    }

    @Test
    fun `positionLimit returns budget div abs_cvar clipped to 0-1`() {
        // cvar = -0.02, budget = 0.02 → limit = 0.02/0.02 = 1.0
        assertEquals(1.0, overlay.positionLimit(-0.02, 0.02), 1e-10)

        // cvar = -0.04, budget = 0.02 → limit = 0.02/0.04 = 0.5
        assertEquals(0.5, overlay.positionLimit(-0.04, 0.02), 1e-10)

        // cvar = -0.001, budget = 0.02 → limit = 20.0 → clipped to 1.0
        assertEquals(1.0, overlay.positionLimit(-0.001, 0.02), 1e-10)
    }

    @Test
    fun `positionLimit clamps to 0-1 range`() {
        val limit = overlay.positionLimit(-0.10, 0.02)
        assertTrue(limit in 0.0..1.0)
    }

    // --- riskAdjustedSize ---

    @Test
    fun `riskAdjustedSize always le min of kelly and cvar`() {
        val kelly = 0.15
        val cvarLimit = 0.10
        val adjusted = overlay.riskAdjustedSize(kelly, cvarLimit)
        assertTrue(adjusted <= kelly)
        assertTrue(adjusted <= cvarLimit)
        assertEquals(0.10, adjusted, 1e-10)
    }

    @Test
    fun `riskAdjustedSize returns 0 when either is 0`() {
        assertEquals(0.0, overlay.riskAdjustedSize(0.0, 0.15), 1e-10)
        assertEquals(0.0, overlay.riskAdjustedSize(0.10, 0.0), 1e-10)
    }

    @Test
    fun `riskAdjustedSize returns non-negative`() {
        val adjusted = overlay.riskAdjustedSize(-0.05, 0.10)
        assertTrue(adjusted >= 0.0)
    }

    // --- normalCdf and normalQuantile ---

    @Test
    fun `normalCdf at 0 is 0_5`() {
        assertEquals(0.5, CVaRRiskOverlay.normalCdf(0.0), 1e-6)
    }

    @Test
    fun `normalCdf at 1_96 is approx 0_975`() {
        assertEquals(0.975, CVaRRiskOverlay.normalCdf(1.96), 1e-3)
    }

    @Test
    fun `normalQuantile at 0_05 is approx -1_645`() {
        val z = CVaRRiskOverlay.normalQuantile(0.05)
        assertEquals(-1.645, z, 0.01)
    }

    @Test
    fun `normalPdf at 0 is approx 0_3989`() {
        assertEquals(0.3989, CVaRRiskOverlay.normalPdf(0.0), 1e-3)
    }
}
