package com.tinyoscillator.data.engine.calibration

import com.tinyoscillator.domain.model.CalibratorState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class SignalCalibratorTest {

    private lateinit var calibrator: SignalCalibrator

    @Before
    fun setup() {
        calibrator = SignalCalibrator()
    }

    // ─── fit/transform roundtrip ───

    @Test
    fun `isotonic fit-transform produces values in 0-1 range`() {
        val scores = doubleArrayOf(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9)
        val labels = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 1.0)

        calibrator.fit("NaiveBayes", scores, labels, "isotonic")

        for (s in scores) {
            val calibrated = calibrator.transform("NaiveBayes", s)
            assertTrue("Calibrated score $calibrated should be in [0,1]", calibrated in 0.0..1.0)
        }
    }

    @Test
    fun `sigmoid fit-transform produces values in 0-1 range`() {
        val scores = doubleArrayOf(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9)
        val labels = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 1.0)

        calibrator.fit("Logistic", scores, labels, "sigmoid")

        for (s in scores) {
            val calibrated = calibrator.transform("Logistic", s)
            assertTrue("Calibrated score $calibrated should be in [0,1]", calibrated in 0.0..1.0)
        }
    }

    @Test
    fun `isotonic produces monotonically non-decreasing output`() {
        val n = 100
        val scores = DoubleArray(n) { it.toDouble() / n }
        // Noisy but generally increasing labels
        val labels = DoubleArray(n) { if (it.toDouble() / n + (Math.random() - 0.5) * 0.3 > 0.5) 1.0 else 0.0 }

        calibrator.fit("test", scores, labels, "isotonic")

        var prev = -1.0
        for (i in 0 until n) {
            val calibrated = calibrator.transform("test", scores[i])
            assertTrue("Isotonic output should be non-decreasing: prev=$prev, current=$calibrated",
                calibrated >= prev - 1e-10)
            prev = calibrated
        }
    }

    @Test
    fun `transform without fitting returns clamped raw score`() {
        assertEquals(0.5, calibrator.transform("unfitted", 0.5), 1e-10)
        assertEquals(0.0, calibrator.transform("unfitted", -0.5), 1e-10)
        assertEquals(1.0, calibrator.transform("unfitted", 1.5), 1e-10)
    }

    @Test
    fun `isFitted returns correct state`() {
        assertFalse(calibrator.isFitted("NaiveBayes"))

        calibrator.fit("NaiveBayes",
            doubleArrayOf(0.1, 0.5, 0.9),
            doubleArrayOf(0.0, 1.0, 1.0))

        assertTrue(calibrator.isFitted("NaiveBayes"))
        assertFalse(calibrator.isFitted("Logistic"))
    }

    // ─── save/load state ───

    @Test
    fun `save and load isotonic state preserves calibration`() {
        val scores = doubleArrayOf(0.1, 0.3, 0.5, 0.7, 0.9)
        val labels = doubleArrayOf(0.0, 0.0, 1.0, 1.0, 1.0)

        calibrator.fit("NaiveBayes", scores, labels, "isotonic")
        val original = calibrator.transform("NaiveBayes", 0.4)

        val state = calibrator.saveState()

        val newCalibrator = SignalCalibrator()
        newCalibrator.loadState(state)

        val restored = newCalibrator.transform("NaiveBayes", 0.4)
        assertEquals(original, restored, 1e-10)
    }

    @Test
    fun `save and load sigmoid state preserves calibration`() {
        val scores = doubleArrayOf(0.1, 0.3, 0.5, 0.7, 0.9)
        val labels = doubleArrayOf(0.0, 0.0, 1.0, 1.0, 1.0)

        calibrator.fit("Logistic", scores, labels, "sigmoid")
        val original = calibrator.transform("Logistic", 0.6)

        val state = calibrator.saveState()

        val newCalibrator = SignalCalibrator()
        newCalibrator.loadState(state)

        val restored = newCalibrator.transform("Logistic", 0.6)
        assertEquals(original, restored, 1e-10)
    }

    @Test
    fun `save state is JSON-serializable (no pickle)`() {
        calibrator.fit("test",
            doubleArrayOf(0.2, 0.4, 0.6, 0.8),
            doubleArrayOf(0.0, 0.0, 1.0, 1.0),
            "isotonic")

        val states = calibrator.saveState()
        // Verify all fields are primitives or lists of primitives
        for (state in states) {
            assertNotNull(state.algoName)
            assertNotNull(state.method)
            assertNotNull(state.params)
            // isotonicXs/Ys should be List<Double>
            if (state.method == "isotonic") {
                assertNotNull(state.isotonicXs)
                assertNotNull(state.isotonicYs)
            }
        }
    }

    // ─── all 7 algo names ───

    @Test
    fun `can fit and transform all supported algorithm names`() {
        val scores = doubleArrayOf(0.2, 0.4, 0.6, 0.8)
        val labels = doubleArrayOf(0.0, 0.0, 1.0, 1.0)

        for (name in SignalCalibrator.ALGO_NAMES) {
            calibrator.fit(name, scores, labels)
            val result = calibrator.transform(name, 0.5)
            assertTrue("$name should produce value in [0,1], got $result", result in 0.0..1.0)
        }
    }

    // ─── reliability stats ───

    @Test
    fun `reliability stats returns correct structure`() {
        val scores = doubleArrayOf(0.1, 0.3, 0.5, 0.7, 0.9)
        val labels = doubleArrayOf(0.0, 0.0, 1.0, 1.0, 1.0)

        calibrator.fit("test", scores, labels, "isotonic")

        val stats = calibrator.reliabilityStats("test", scores, labels, 5)
        assertTrue(stats.containsKey("brier_score"))
        assertTrue(stats.containsKey("log_loss"))
        assertTrue(stats.containsKey("reliability_bins"))
        assertTrue(stats.containsKey("sample_count"))

        val brier = stats["brier_score"] as Double
        assertTrue("Brier score should be in [0,1], got $brier", brier in 0.0..1.0)
        assertEquals(5, stats["sample_count"])
    }

    @Test
    fun `perfectly calibrated predictions have low brier score`() {
        val scores = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
        val labels = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)

        // No calibration needed — test raw stats
        val stats = calibrator.reliabilityStats("raw", scores, labels)
        val brier = stats["brier_score"] as Double
        assertEquals(0.0, brier, 1e-10)
    }

    // ─── edge cases ───

    @Test(expected = IllegalArgumentException::class)
    fun `fit with mismatched lengths throws`() {
        calibrator.fit("test", doubleArrayOf(0.1, 0.2), doubleArrayOf(0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fit with single sample throws`() {
        calibrator.fit("test", doubleArrayOf(0.5), doubleArrayOf(1.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fit with unknown method throws`() {
        calibrator.fit("test", doubleArrayOf(0.1, 0.2), doubleArrayOf(0.0, 1.0), "unknown")
    }

    @Test
    fun `transform extrapolates beyond training range`() {
        calibrator.fit("test",
            doubleArrayOf(0.3, 0.5, 0.7),
            doubleArrayOf(0.0, 0.5, 1.0),
            "isotonic")

        val below = calibrator.transform("test", 0.0)
        val above = calibrator.transform("test", 1.0)
        assertTrue(below in 0.0..1.0)
        assertTrue(above in 0.0..1.0)
    }
}
