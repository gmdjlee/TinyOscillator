package com.tinyoscillator.data.engine.regime

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class GaussianHmmTest {

    private lateinit var hmm: GaussianHmm

    @Before
    fun setup() {
        hmm = GaussianHmm(nStates = 4, nFeatures = 2, nIter = 50, randomState = 42)
    }

    @Test
    fun `fit 학습 후 predict가 유효한 상태를 반환한다`() {
        val observations = generateSyntheticData(200)
        hmm.fit(observations)

        val path = hmm.predict(observations)
        assertEquals(observations.size, path.size)
        for (state in path) {
            assertTrue("상태 $state 는 0~3 범위", state in 0 until 4)
        }
    }

    @Test
    fun `predictProba 확률합이 1점0이다`() {
        val observations = generateSyntheticData(200)
        hmm.fit(observations)

        val proba = hmm.predictProba(observations)
        assertEquals(observations.size, proba.size)

        for ((t, row) in proba.withIndex()) {
            val sum = row.sum()
            assertEquals("시점 $t 에서 확률 합이 1.0이어야 함", 1.0, sum, 0.01)
            for (p in row) {
                assertTrue("확률 >= 0", p >= 0.0)
                assertTrue("확률 <= 1", p <= 1.0)
            }
        }
    }

    @Test
    fun `score가 유한한 로그 우도를 반환한다`() {
        val observations = generateSyntheticData(200)
        hmm.fit(observations)

        val score = hmm.score(observations)
        assertTrue("로그 우도가 유한해야 함", score.isFinite())
    }

    @Test
    fun `save-load 라운드트립이 예측을 보존한다`() {
        val observations = generateSyntheticData(200)
        hmm.fit(observations)

        val pathBefore = hmm.predict(observations)
        val probaBefore = hmm.predictProba(observations)

        // Serialize
        val state = hmm.toStateMap()

        // Deserialize
        val restored = GaussianHmm.fromStateMap(state)
        val pathAfter = restored.predict(observations)
        val probaAfter = restored.predictProba(observations)

        // Paths should be identical
        assertArrayEquals("Viterbi 경로가 보존되어야 함", pathBefore, pathAfter)

        // Probabilities should be within tolerance
        for (t in probaBefore.indices) {
            for (j in probaBefore[t].indices) {
                assertEquals("시점 $t 상태 $j 확률 보존",
                    probaBefore[t][j], probaAfter[t][j], 1e-6)
            }
        }
    }

    @Test
    fun `transition matrix 행합이 1점0이다`() {
        val observations = generateSyntheticData(200)
        hmm.fit(observations)

        for (i in 0 until hmm.nStates) {
            val rowSum = hmm.transmat[i].sum()
            assertEquals("전환 행렬 행 $i 합이 1.0이어야 함", 1.0, rowSum, 0.01)
        }
    }

    @Test
    fun `startProb 합이 1점0이다`() {
        val observations = generateSyntheticData(200)
        hmm.fit(observations)

        val sum = hmm.startProb.sum()
        assertEquals("초기 확률 합이 1.0이어야 함", 1.0, sum, 0.01)
    }

    @Test
    fun `covars가 모두 양수이다`() {
        val observations = generateSyntheticData(200)
        hmm.fit(observations)

        for (s in 0 until hmm.nStates) {
            for (d in 0 until hmm.nFeatures) {
                assertTrue("분산 양수: state=$s feature=$d", hmm.covars[s][d] > 0)
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `관측값 부족 시 예외 발생`() {
        val observations = arrayOf(doubleArrayOf(1.0, 2.0))
        hmm.fit(observations)
    }

    @Test
    fun `emission이 양수를 반환한다`() {
        val observations = generateSyntheticData(100)
        hmm.fit(observations)

        val prob = hmm.emission(0, doubleArrayOf(0.0, 0.0))
        assertTrue("emission > 0", prob > 0)
    }

    @Test
    fun `deterministic with same random seed`() {
        val obs = generateSyntheticData(200)

        val hmm1 = GaussianHmm(nStates = 4, nFeatures = 2, nIter = 50, randomState = 42)
        hmm1.fit(obs)
        val path1 = hmm1.predict(obs)

        val hmm2 = GaussianHmm(nStates = 4, nFeatures = 2, nIter = 50, randomState = 42)
        hmm2.fit(obs)
        val path2 = hmm2.predict(obs)

        assertArrayEquals("같은 시드로 동일한 결과", path1, path2)
    }

    // ─── Helper ───

    private fun generateSyntheticData(n: Int): Array<DoubleArray> {
        val rng = kotlin.random.Random(42)
        // Generate 4 clusters to simulate regimes
        return Array(n) { i ->
            val cluster = i % 4
            val baseX = when (cluster) {
                0 -> 1.0   // bull
                1 -> -1.0  // bear
                2 -> 0.0   // sideways
                else -> -2.0 // crisis
            }
            val baseY = when (cluster) {
                0 -> -0.5  // low vol
                1 -> 1.0   // high vol
                2 -> 0.0   // mid vol
                else -> 2.0 // extreme vol
            }
            doubleArrayOf(
                baseX + rng.nextDouble(-0.5, 0.5),
                baseY + rng.nextDouble(-0.3, 0.3)
            )
        }
    }
}
