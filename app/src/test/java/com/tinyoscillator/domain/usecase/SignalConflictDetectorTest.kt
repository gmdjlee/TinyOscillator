package com.tinyoscillator.domain.usecase

import com.tinyoscillator.core.testing.fixture.SyntheticSignalData
import com.tinyoscillator.domain.model.AlgoResult
import org.junit.Assert.*
import org.junit.Test

class SignalConflictDetectorTest {

    private val ALGO_NAMES = SyntheticSignalData.ALGO_NAMES

    // ── 표준편차 계산 ──────────────────────────────────────────────

    @Test
    fun `stdDev is 0 when all scores are identical`() {
        val results = ALGO_NAMES.associateWith { AlgoResult(score = 0.7f) }
        val conflict = SignalConflictDetector.detect(results)
        assertEquals(0f, conflict.stdDev, 0.001f)
    }

    @Test
    fun `stdDev is positive when scores differ`() {
        val results = SyntheticSignalData.conflictingResults()
        val conflict = SignalConflictDetector.detect(results)
        assertTrue(conflict.stdDev > 0f)
    }

    @Test
    fun `stdDev calculation matches manual calculation`() {
        val scores = listOf(0.8f, 0.2f, 0.8f, 0.2f)
        val mean = scores.average().toFloat()
        val expected = kotlin.math.sqrt(
            scores.sumOf { ((it - mean) * (it - mean)).toDouble() }.toFloat()
                / scores.size
        )
        val results = scores.mapIndexed { i, s ->
            "algo_$i" to AlgoResult(score = s)
        }.toMap()
        val conflict = SignalConflictDetector.detect(results)
        assertEquals(expected, conflict.stdDev, 0.001f)
    }

    // ── 충돌 수준 분류 ─────────────────────────────────────────────

    @Test
    fun `NONE level when all algorithms agree strongly`() {
        val results = ALGO_NAMES.associateWith { AlgoResult(score = 0.72f) }
        val conflict = SignalConflictDetector.detect(results)
        assertEquals(SignalConflictDetector.ConflictLevel.NONE, conflict.conflictLevel)
    }

    @Test
    fun `HIGH level for conflicting results fixture`() {
        val conflict = SignalConflictDetector.detect(
            SyntheticSignalData.conflictingResults()
        )
        assertTrue(
            "Expected HIGH or CRITICAL, got ${conflict.conflictLevel}",
            conflict.conflictLevel == SignalConflictDetector.ConflictLevel.HIGH ||
                conflict.conflictLevel == SignalConflictDetector.ConflictLevel.CRITICAL,
        )
    }

    @Test
    fun `CRITICAL level when half strongly bull half strongly bear`() {
        val results = mapOf(
            "a" to AlgoResult(score = 0.90f),
            "b" to AlgoResult(score = 0.90f),
            "c" to AlgoResult(score = 0.90f),
            "d" to AlgoResult(score = 0.10f),
            "e" to AlgoResult(score = 0.10f),
            "f" to AlgoResult(score = 0.10f),
        )
        val conflict = SignalConflictDetector.detect(results)
        assertEquals(SignalConflictDetector.ConflictLevel.CRITICAL, conflict.conflictLevel)
    }

    // ── 포지션 배수 ────────────────────────────────────────────────

    @Test
    fun `NONE level gives full position multiplier 1_0`() {
        val results = ALGO_NAMES.associateWith { AlgoResult(score = 0.7f) }
        val conflict = SignalConflictDetector.detect(results)
        assertEquals(1.0f, conflict.positionMultiplier, 0.001f)
    }

    @Test
    fun `CRITICAL level gives 25pct position multiplier`() {
        val results = mapOf(
            "a" to AlgoResult(score = 0.95f),
            "b" to AlgoResult(score = 0.95f),
            "c" to AlgoResult(score = 0.05f),
            "d" to AlgoResult(score = 0.05f),
        )
        val conflict = SignalConflictDetector.detect(results)
        assertEquals(0.25f, conflict.positionMultiplier, 0.001f)
    }

    @Test
    fun `position multiplier always in 0 to 1`() {
        listOf(
            SyntheticSignalData.algoResults(seed = 0.3f),
            SyntheticSignalData.algoResults(seed = 0.7f),
            SyntheticSignalData.conflictingResults(),
        ).forEach { results ->
            val conflict = SignalConflictDetector.detect(results)
            assertTrue(
                "Expected multiplier in [0,1], got ${conflict.positionMultiplier}",
                conflict.positionMultiplier in 0f..1f
            )
        }
    }

    // ── 알고리즘 카운트 ────────────────────────────────────────────

    @Test
    fun `bullCount counts algos with score above 0_6`() {
        val results = mapOf(
            "a" to AlgoResult(score = 0.80f),
            "b" to AlgoResult(score = 0.70f),
            "c" to AlgoResult(score = 0.50f),
            "d" to AlgoResult(score = 0.30f),
        )
        val conflict = SignalConflictDetector.detect(results)
        assertEquals(2, conflict.bullCount)
    }

    @Test
    fun `bearCount counts algos with score below 0_4`() {
        val results = mapOf(
            "a" to AlgoResult(score = 0.80f),
            "b" to AlgoResult(score = 0.35f),
            "c" to AlgoResult(score = 0.20f),
        )
        val conflict = SignalConflictDetector.detect(results)
        assertEquals(2, conflict.bearCount)
    }

    @Test
    fun `bull plus neutral plus bear equals total algo count`() {
        val results = SyntheticSignalData.algoResults()
        val conflict = SignalConflictDetector.detect(results)
        val total = conflict.bullCount + conflict.neutralCount + conflict.bearCount
        assertEquals(results.size, total)
    }

    // ── 경고 메시지 ────────────────────────────────────────────────

    @Test
    fun `NONE level has empty warning message`() {
        val results = ALGO_NAMES.associateWith { AlgoResult(score = 0.65f) }
        val conflict = SignalConflictDetector.detect(results)
        assertTrue(conflict.warningMessage.isEmpty())
    }

    @Test
    fun `HIGH level message contains sigma value`() {
        val results = SyntheticSignalData.conflictingResults()
        val conflict = SignalConflictDetector.detect(results)
        if (conflict.conflictLevel == SignalConflictDetector.ConflictLevel.HIGH ||
            conflict.conflictLevel == SignalConflictDetector.ConflictLevel.CRITICAL
        ) {
            assertTrue(
                "Expected message to contain σ=, got: ${conflict.warningMessage}",
                conflict.warningMessage.contains("σ=")
            )
        }
    }

    @Test
    fun `empty algo results returns NONE conflict`() {
        val conflict = SignalConflictDetector.detect(emptyMap())
        assertEquals(SignalConflictDetector.ConflictLevel.NONE, conflict.conflictLevel)
        assertEquals(1.0f, conflict.positionMultiplier)
    }

    // ── 성능 ──────────────────────────────────────────────────────

    @Test
    fun `detect 11 algorithms completes under 1ms`() {
        val results = (1..11).associate { "algo_$it" to AlgoResult(score = it * 0.08f) }
        val start = System.nanoTime()
        SignalConflictDetector.detect(results)
        val ms = (System.nanoTime() - start) / 1_000_000L
        assertTrue("Expected <1ms, got ${ms}ms", ms < 1L)
    }
}
