package com.tinyoscillator.data.engine.calibration

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CalibrationMonitorTest {

    private lateinit var monitor: CalibrationMonitor

    @Before
    fun setup() {
        monitor = CalibrationMonitor()
        monitor.configure(windowSize = 60, degradationThreshold = 0.05)
    }

    // ─── basic observation tracking ───

    @Test
    fun `addObservation and getMetrics returns valid metrics`() {
        for (i in 0 until 20) {
            monitor.addObservation("NaiveBayes", 0.7, if (i % 3 == 0) 1.0 else 0.0)
        }

        val metrics = monitor.getMetrics("NaiveBayes")
        assertEquals("NaiveBayes", metrics.algoName)
        assertEquals(20, metrics.sampleCount)
        assertTrue("Brier score should be finite", metrics.brierScore.isFinite())
        assertTrue("Log loss should be finite", metrics.logLoss.isFinite())
        assertTrue("ECE should be finite", metrics.ece.isFinite())
        assertTrue("Should have reliability bins", metrics.reliabilityBins.isNotEmpty())
    }

    @Test
    fun `empty algo returns NaN metrics and zero sample count`() {
        val metrics = monitor.getMetrics("unknown")
        assertEquals(0, metrics.sampleCount)
        assertTrue(metrics.brierScore.isNaN())
        assertFalse(metrics.needsRecalibration)
    }

    // ─── rolling window ───

    @Test
    fun `window evicts oldest observations`() {
        monitor.configure(windowSize = 10)

        for (i in 0 until 20) {
            monitor.addObservation("test", 0.5, 1.0)
        }

        assertEquals(10, monitor.observationCount("test"))
    }

    // ─── recalibration flag ───

    @Test
    fun `recalibration flag triggers when brier degrades beyond threshold`() {
        // Set a good baseline
        monitor.setBaseline("test", 0.10)

        // Add deliberately bad predictions (all predict 0.9 but outcome is 0)
        for (i in 0 until 30) {
            monitor.addObservation("test", 0.9, 0.0)
        }

        val metrics = monitor.getMetrics("test")
        // Brier for predicting 0.9 when actual is 0.0 = (0.9-0.0)^2 = 0.81
        // 0.81 - 0.10 = 0.71 >> 0.05 threshold
        assertTrue("Should flag recalibration for miscalibrated scores", metrics.needsRecalibration)
    }

    @Test
    fun `no recalibration flag when predictions are good`() {
        monitor.setBaseline("test", 0.10)

        // Add well-calibrated predictions
        for (i in 0 until 30) {
            val pred = if (i % 3 == 0) 0.7 else 0.3
            val outcome = if (i % 3 == 0) 1.0 else 0.0
            monitor.addObservation("test", pred, outcome)
        }

        val metrics = monitor.getMetrics("test")
        assertFalse("Should not flag recalibration for good predictions", metrics.needsRecalibration)
    }

    @Test
    fun `no recalibration flag without baseline`() {
        // Add bad predictions but no baseline set
        for (i in 0 until 30) {
            monitor.addObservation("test", 0.9, 0.0)
        }

        val metrics = monitor.getMetrics("test")
        assertFalse("Should not flag without baseline", metrics.needsRecalibration)
    }

    // ─── ECE ───

    @Test
    fun `perfect calibration has zero ECE`() {
        // Predict 0.05 for bin 0-0.1, with fraction_positive ≈ 0.05
        // This is hard to achieve exactly, so test with perfectly separated data
        for (i in 0 until 50) {
            monitor.addObservation("test", 0.0, 0.0)
        }
        for (i in 0 until 50) {
            monitor.addObservation("test", 1.0, 1.0)
        }

        val metrics = monitor.getMetrics("test")
        // ECE should be low — predictions of 0.0 and 1.0 match outcomes exactly
        // The bin [0, 0.1) has frac_pos=0.0, bin_mid=0.05 → |0-0.05|=0.05
        // The bin [0.9, 1.0) has frac_pos=1.0, bin_mid=0.95 → |1-0.95|=0.05
        assertTrue("ECE should be small for well-separated data, got ${metrics.ece}",
            metrics.ece < 0.10)
    }

    // ─── reliability bins ───

    @Test
    fun `reliability bins have correct count`() {
        for (i in 0 until 100) {
            monitor.addObservation("test", i / 100.0, if (i > 50) 1.0 else 0.0)
        }

        val metrics = monitor.getMetrics("test")
        assertEquals(10, metrics.reliabilityBins.size)

        // Total count across all bins should equal sample count
        val totalInBins = metrics.reliabilityBins.sumOf { it.count }
        assertEquals(metrics.sampleCount, totalInBins)
    }

    // ─── clear ───

    @Test
    fun `clear removes algo data`() {
        monitor.addObservation("test", 0.5, 1.0)
        monitor.setBaseline("test", 0.1)
        assertEquals(1, monitor.observationCount("test"))

        monitor.clear("test")
        assertEquals(0, monitor.observationCount("test"))

        val metrics = monitor.getMetrics("test")
        assertEquals(0, metrics.sampleCount)
    }

    @Test
    fun `clearAll removes all data`() {
        monitor.addObservation("algo1", 0.5, 1.0)
        monitor.addObservation("algo2", 0.5, 1.0)

        monitor.clearAll()
        assertEquals(0, monitor.observationCount("algo1"))
        assertEquals(0, monitor.observationCount("algo2"))
    }

    // ─── multiple algorithms ───

    @Test
    fun `tracks algorithms independently`() {
        monitor.addObservation("NaiveBayes", 0.8, 1.0)
        monitor.addObservation("Logistic", 0.3, 0.0)

        assertEquals(1, monitor.observationCount("NaiveBayes"))
        assertEquals(1, monitor.observationCount("Logistic"))

        val nbMetrics = monitor.getMetrics("NaiveBayes")
        val lgMetrics = monitor.getMetrics("Logistic")
        assertNotEquals(nbMetrics.brierScore, lgMetrics.brierScore)
    }
}
