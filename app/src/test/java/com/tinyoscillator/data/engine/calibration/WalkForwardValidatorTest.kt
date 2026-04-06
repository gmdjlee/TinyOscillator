package com.tinyoscillator.data.engine.calibration

import org.junit.Assert.*
import org.junit.Test

class WalkForwardValidatorTest {

    // ─── split indices ───

    @Test
    fun `split generates non-overlapping train-test splits`() {
        val validator = WalkForwardValidator(nTrain = 10, nTest = 3, step = 3)
        val splits = validator.split(20)

        assertTrue("Should have at least one split", splits.isNotEmpty())

        for ((train, test) in splits) {
            // No overlap between train and test
            val overlap = train.intersect(test.toSet())
            assertTrue("Train and test should not overlap: $overlap", overlap.isEmpty())
        }
    }

    @Test
    fun `split prevents future data leakage - train max less than test min`() {
        val validator = WalkForwardValidator(nTrain = 50, nTest = 10, step = 10)
        val splits = validator.split(200)

        for ((train, test) in splits) {
            val trainMax = train.max()
            val testMin = test.min()
            assertTrue(
                "Train max index ($trainMax) must be < test min index ($testMin)",
                trainMax < testMin
            )
        }
    }

    @Test
    fun `split produces correct number of folds`() {
        val validator = WalkForwardValidator(nTrain = 100, nTest = 20, step = 20)
        val splits = validator.split(200)

        // (200 - 100 - 20) / 20 + 1 = 5 folds
        assertEquals(5, splits.size)
    }

    @Test
    fun `split with step=1 produces maximum folds`() {
        val validator = WalkForwardValidator(nTrain = 5, nTest = 2, step = 1)
        val splits = validator.split(10)

        // start can go from 0 to 10-5-2=3, so 4 folds
        assertEquals(4, splits.size)
    }

    @Test
    fun `split with exact fit produces one fold`() {
        val validator = WalkForwardValidator(nTrain = 10, nTest = 5, step = 5)
        val splits = validator.split(15)

        assertEquals(1, splits.size)
        assertEquals((0 until 10).toList(), splits[0].first)
        assertEquals((10 until 15).toList(), splits[0].second)
    }

    @Test
    fun `train indices are contiguous`() {
        val validator = WalkForwardValidator(nTrain = 20, nTest = 5, step = 5)
        val splits = validator.split(50)

        for ((train, _) in splits) {
            for (i in 1 until train.size) {
                assertEquals("Train indices should be contiguous",
                    train[i - 1] + 1, train[i])
            }
        }
    }

    @Test
    fun `test indices are contiguous`() {
        val validator = WalkForwardValidator(nTrain = 20, nTest = 5, step = 5)
        val splits = validator.split(50)

        for ((_, test) in splits) {
            for (i in 1 until test.size) {
                assertEquals("Test indices should be contiguous",
                    test[i - 1] + 1, test[i])
            }
        }
    }

    @Test
    fun `train and test have correct sizes`() {
        val nTrain = 30
        val nTest = 7
        val validator = WalkForwardValidator(nTrain = nTrain, nTest = nTest, step = 7)
        val splits = validator.split(100)

        for ((train, test) in splits) {
            assertEquals(nTrain, train.size)
            assertEquals(nTest, test.size)
        }
    }

    // ─── evaluate ───

    @Test
    fun `evaluate with perfect predictions returns zero brier score`() {
        val validator = WalkForwardValidator(nTrain = 5, nTest = 2, step = 2)
        val n = 15
        val scores = DoubleArray(n) { if (it >= n / 2) 1.0 else 0.0 }
        val labels = scores.copyOf()

        val result = validator.evaluate(
            predictFn = { _, _, xTest -> xTest }, // identity — perfect calibration
            scores = scores,
            labels = labels
        )

        assertTrue("Should have folds", result.nFolds > 0)
        assertEquals(0.0, result.meanBrierScore, 0.01)
    }

    @Test
    fun `evaluate returns valid metrics structure`() {
        val validator = WalkForwardValidator(nTrain = 10, nTest = 3, step = 3)
        val n = 25
        val scores = DoubleArray(n) { it.toDouble() / n }
        val labels = DoubleArray(n) { if (it > n / 2) 1.0 else 0.0 }

        val result = validator.evaluate(
            predictFn = { _, _, xTest -> xTest },
            scores = scores,
            labels = labels
        )

        assertTrue("nFolds should be positive", result.nFolds > 0)
        assertTrue("meanBrierScore should be finite", result.meanBrierScore.isFinite())
        assertTrue("meanLogLoss should be finite", result.meanLogLoss.isFinite())
        assertEquals(result.nFolds, result.perFoldBrier.size)
        assertEquals(result.nFolds, result.perFoldLogLoss.size)
    }

    @Test
    fun `evaluate predictFn receives only train data for fitting`() {
        val validator = WalkForwardValidator(nTrain = 5, nTest = 2, step = 2)
        val n = 15
        val scores = DoubleArray(n) { it.toDouble() }
        val labels = DoubleArray(n) { 0.0 }

        val trainMaxIndices = mutableListOf<Double>()

        validator.evaluate(
            predictFn = { xTrain, _, xTest ->
                trainMaxIndices.add(xTrain.max())
                // Verify train data comes before test data
                assertTrue("Train max (${xTrain.max()}) must be < test min (${xTest.min()})",
                    xTrain.max() < xTest.min())
                DoubleArray(xTest.size) { 0.5 }
            },
            scores = scores,
            labels = labels
        )

        assertTrue("predictFn should have been called", trainMaxIndices.isNotEmpty())
    }

    // ─── edge cases ───

    @Test(expected = IllegalArgumentException::class)
    fun `split with insufficient data throws`() {
        val validator = WalkForwardValidator(nTrain = 100, nTest = 50, step = 50)
        validator.split(50) // too few samples
    }

    @Test(expected = IllegalArgumentException::class)
    fun `evaluate with mismatched lengths throws`() {
        val validator = WalkForwardValidator(nTrain = 5, nTest = 2, step = 2)
        validator.evaluate(
            predictFn = { _, _, xTest -> xTest },
            scores = doubleArrayOf(0.1, 0.2),
            labels = doubleArrayOf(0.0)
        )
    }

    @Test
    fun `constructor rejects non-positive parameters`() {
        try {
            WalkForwardValidator(nTrain = 0, nTest = 5, step = 5)
            fail("Should reject nTrain=0")
        } catch (e: IllegalArgumentException) { /* expected */ }

        try {
            WalkForwardValidator(nTrain = 10, nTest = -1, step = 5)
            fail("Should reject negative nTest")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    // ─── static metrics ───

    @Test
    fun `brierScore of perfect predictions is zero`() {
        assertEquals(0.0, WalkForwardValidator.brierScore(
            doubleArrayOf(0.0, 1.0, 0.0, 1.0),
            doubleArrayOf(0.0, 1.0, 0.0, 1.0)
        ), 1e-10)
    }

    @Test
    fun `brierScore of worst predictions is one`() {
        assertEquals(1.0, WalkForwardValidator.brierScore(
            doubleArrayOf(1.0, 0.0),
            doubleArrayOf(0.0, 1.0)
        ), 1e-10)
    }

    @Test
    fun `logLoss of perfect predictions is near zero`() {
        val ll = WalkForwardValidator.logLoss(
            doubleArrayOf(0.999, 0.001, 0.999),
            doubleArrayOf(1.0, 0.0, 1.0)
        )
        assertTrue("Log loss should be near zero for good predictions, got $ll", ll < 0.01)
    }
}
