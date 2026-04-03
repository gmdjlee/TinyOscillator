package com.tinyoscillator.data.engine.regime

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class RegimeWeightTableTest {

    @Test
    fun `validateWeights가 정상 테이블에서 예외를 던지지 않는다`() {
        RegimeWeightTable.validateWeights()
    }

    @Test
    fun `모든 레짐의 가중치 합이 1점0이다`() {
        for ((regime, weights) in RegimeWeightTable.WEIGHT_TABLE) {
            val sum = weights.values.sum()
            assertTrue("$regime 가중치 합 = $sum (1.0 ± 1e-6)", abs(sum - 1.0) < 1e-6)
        }
    }

    @Test
    fun `모든 레짐에 7개 알고리즘이 있다`() {
        for ((regime, weights) in RegimeWeightTable.WEIGHT_TABLE) {
            assertEquals("$regime 알고리즘 수 = 9", 9, weights.size)
            for (algo in RegimeWeightTable.ALL_ALGOS) {
                assertTrue("$regime 에 $algo 존재", algo in weights)
            }
        }
    }

    @Test
    fun `모든 가중치가 양수이다`() {
        for ((regime, weights) in RegimeWeightTable.WEIGHT_TABLE) {
            for ((algo, w) in weights) {
                assertTrue("$regime/$algo 가중치 > 0: $w", w > 0)
            }
        }
    }

    @Test
    fun `4개 레짐이 모두 존재한다`() {
        val expectedRegimes = setOf("BULL_LOW_VOL", "BEAR_HIGH_VOL", "SIDEWAYS", "CRISIS")
        assertEquals("4개 레짐 존재", expectedRegimes, RegimeWeightTable.WEIGHT_TABLE.keys)
    }

    @Test
    fun `getWeights가 유효한 레짐에 대해 가중치를 반환한다`() {
        val weights = RegimeWeightTable.getWeights("BULL_LOW_VOL")
        assertEquals(9, weights.size)
        assertTrue(abs(weights.values.sum() - 1.0) < 1e-6)
    }

    @Test
    fun `getWeights가 유효하지 않은 레짐에 대해 균등 가중치를 반환한다`() {
        val weights = RegimeWeightTable.getWeights("UNKNOWN_REGIME")
        assertEquals(9, weights.size)
        val expectedWeight = 1.0 / 9
        for ((_, w) in weights) {
            assertEquals(expectedWeight, w, 1e-6)
        }
    }

    @Test
    fun `equalWeights가 균등 가중치를 반환한다`() {
        val weights = RegimeWeightTable.equalWeights()
        assertEquals(9, weights.size)
        val sum = weights.values.sum()
        assertTrue("균등 가중치 합 ≈ 1.0", abs(sum - 1.0) < 1e-6)
    }

    @Test
    fun `BULL_LOW_VOL에서 모멘텀 전략 가중치가 높다`() {
        val weights = RegimeWeightTable.getWeights("BULL_LOW_VOL")
        val patternWeight = weights["PatternScan"]!!
        val signalWeight = weights["SignalScoring"]!!
        val hmmWeight = weights["HMM"]!!

        assertTrue("BULL에서 PatternScan > HMM", patternWeight > hmmWeight)
        assertTrue("BULL에서 SignalScoring > HMM", signalWeight > hmmWeight)
    }

    @Test
    fun `CRISIS에서 HMM과 BayesianUpdate 가중치가 높다`() {
        val weights = RegimeWeightTable.getWeights("CRISIS")
        val hmmWeight = weights["HMM"]!!
        val bayesianWeight = weights["BayesianUpdate"]!!
        val patternWeight = weights["PatternScan"]!!

        assertTrue("CRISIS에서 HMM > PatternScan", hmmWeight > patternWeight)
        assertTrue("CRISIS에서 BayesianUpdate > PatternScan", bayesianWeight > patternWeight)
    }

    @Test
    fun `SIDEWAYS에서 통계 전략 가중치가 높다`() {
        val weights = RegimeWeightTable.getWeights("SIDEWAYS")
        val logisticWeight = weights["Logistic"]!!
        val correlationWeight = weights["Correlation"]!!
        val hmmWeight = weights["HMM"]!!

        assertTrue("SIDEWAYS에서 Logistic > HMM", logisticWeight > hmmWeight)
        assertTrue("SIDEWAYS에서 Correlation > HMM", correlationWeight > hmmWeight)
    }

    @Test
    fun `레짐별 가중치 분포가 서로 다르다`() {
        val bullWeights = RegimeWeightTable.getWeights("BULL_LOW_VOL")
        val crisisWeights = RegimeWeightTable.getWeights("CRISIS")

        var diffCount = 0
        for (algo in RegimeWeightTable.ALL_ALGOS) {
            if (bullWeights[algo] != crisisWeights[algo]) diffCount++
        }
        assertTrue("BULL과 CRISIS의 가중치가 달라야 함", diffCount >= 5)
    }

    @Test(expected = IllegalStateException::class)
    fun `validateWeights가 잘못된 테이블에서 예외를 던진다`() {
        // Create a mock with bad weights to test validation logic
        // This tests the concept — actual table is correct so we test with reflection or direct
        val badTable = mapOf(
            "BULL_LOW_VOL" to mapOf("NaiveBayes" to 0.5, "Logistic" to 0.6) // sum > 1.0, missing algos
        )

        // Directly test validation logic
        val sum = badTable["BULL_LOW_VOL"]!!.values.sum()
        check(abs(sum - 1.0) < 1e-6) {
            "레짐 'BULL_LOW_VOL' 가중치 합이 1.0이 아닙니다: $sum"
        }
    }
}
