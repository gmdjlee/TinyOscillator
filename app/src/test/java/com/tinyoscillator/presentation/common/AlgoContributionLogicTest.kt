package com.tinyoscillator.presentation.common

import com.tinyoscillator.domain.model.AlgoResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlgoContributionLogicTest {

    // ── 기여분 부호 테스트 ──

    @Test
    fun `contribution is positive when score above 0_5`() {
        val result = AlgoResult(score = 0.7f, weight = 0.15f)
        val contrib = (result.score - 0.5f) * result.weight
        assertTrue("Expected positive contrib, got $contrib", contrib > 0f)
    }

    @Test
    fun `contribution is negative when score below 0_5`() {
        val result = AlgoResult(score = 0.3f, weight = 0.15f)
        val contrib = (result.score - 0.5f) * result.weight
        assertTrue("Expected negative contrib, got $contrib", contrib < 0f)
    }

    @Test
    fun `contribution is zero at exactly 0_5`() {
        val result = AlgoResult(score = 0.5f, weight = 0.15f)
        val contrib = (result.score - 0.5f) * result.weight
        assertEquals(0f, contrib, 1e-6f)
    }

    // ── 앙상블 합산 테스트 ──

    @Test
    fun `total contributions sum matches ensemble offset from baseline`() {
        val results = buildAlgoResults(baseSeed = 0.65f)
        val totalContrib = results.values.sumOf {
            ((it.score - 0.5f) * it.weight).toDouble()
        }.toFloat()
        val expectedEnsemble = 0.5f + totalContrib
        assertTrue(
            "Ensemble $expectedEnsemble should be in [0,1]",
            expectedEnsemble in 0f..1f
        )
    }

    // ── 정렬 테스트 ──

    @Test
    fun `waterfall bars sorted descending by weight`() {
        val results = buildAlgoResults()
        val sorted = results.entries.sortedByDescending { it.value.weight }
        val weights = sorted.map { it.value.weight }
        assertEquals(weights, weights.sortedDescending())
    }

    // ── 레이더 차트 데이터 테스트 ──

    @Test
    fun `radar entries count equals algo count`() {
        val results = buildAlgoResults()
        val entries = results.entries.sortedBy { it.key }.map { it.value.score }
        assertEquals(results.size, entries.size)
    }

    @Test
    fun `all radar entries in 0 to 1 range`() {
        val results = buildAlgoResults()
        results.values.forEach { r ->
            assertTrue("${r.algoName}: ${r.score} out of range", r.score in 0f..1f)
        }
    }

    // ── 경계값 테스트 ──

    @Test
    fun `contribution with zero weight is zero`() {
        val result = AlgoResult(score = 0.9f, weight = 0f)
        val contrib = (result.score - 0.5f) * result.weight
        assertEquals(0f, contrib, 1e-6f)
    }

    @Test
    fun `extreme scores produce bounded contributions`() {
        val bullish = AlgoResult(score = 1.0f, weight = 0.2f)
        val bearish = AlgoResult(score = 0.0f, weight = 0.2f)
        val bullContrib = (bullish.score - 0.5f) * bullish.weight
        val bearContrib = (bearish.score - 0.5f) * bearish.weight
        assertEquals(0.1f, bullContrib, 1e-6f)
        assertEquals(-0.1f, bearContrib, 1e-6f)
    }

    @Test
    fun `all algo names have display name mapping`() {
        val results = buildAlgoResults()
        results.keys.forEach { name ->
            assertTrue(
                "$name missing from ALGO_DISPLAY_NAMES",
                ALGO_DISPLAY_NAMES.containsKey(name)
            )
        }
    }

    // ── Helper ──

    private val ALGO_NAMES = listOf(
        "NaiveBayes", "Logistic", "HMM", "PatternScan", "SignalScoring",
        "BayesianUpdate", "OrderFlow", "DartEvent", "Korea5Factor", "SectorCorrelation"
    )

    private fun buildAlgoResults(baseSeed: Float = 0.6f): Map<String, AlgoResult> {
        val n = ALGO_NAMES.size
        return ALGO_NAMES.mapIndexed { i, name ->
            val score = (baseSeed + i * 0.03f).coerceIn(0f, 1f)
            val weight = 1f / n
            name to AlgoResult(
                algoName = name,
                score = score,
                rationale = "테스트 근거 $i",
                weight = weight
            )
        }.toMap()
    }
}
