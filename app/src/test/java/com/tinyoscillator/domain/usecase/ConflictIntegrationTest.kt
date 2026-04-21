package com.tinyoscillator.domain.usecase

import com.tinyoscillator.core.testing.fixture.SyntheticSignalData
import com.tinyoscillator.domain.model.AlgoResult
import org.junit.Assert.*
import org.junit.Test

/**
 * 충돌 감지 + 신호 파이프라인 통합 테스트.
 *
 * SignalTransparencyViewModel이 없으므로 (AiProbabilityAnalysisViewModel에 통합됨),
 * RationaleBuilder → ConflictDetector 파이프라인을 직접 검증한다.
 */
class ConflictIntegrationTest {

    @Test
    fun `conflictResult is NONE for agreeing algo results`() {
        val results = SyntheticSignalData.ALGO_NAMES
            .associateWith { AlgoResult(score = 0.72f) }
        val conflict = SignalConflictDetector.detect(results)
        assertEquals(SignalConflictDetector.ConflictLevel.NONE, conflict.conflictLevel)
    }

    @Test
    fun `conflicting signals produce HIGH or CRITICAL conflict`() {
        val results = SyntheticSignalData.conflictingResults()
        val conflict = SignalConflictDetector.detect(results)
        assertTrue(
            "Expected HIGH or CRITICAL, got ${conflict.conflictLevel}",
            conflict.conflictLevel == SignalConflictDetector.ConflictLevel.HIGH ||
                conflict.conflictLevel == SignalConflictDetector.ConflictLevel.CRITICAL
        )
    }

    @Test
    fun `conflict multiplier reduces position recommendation`() {
        val results = mapOf(
            "a" to AlgoResult(score = 0.90f),
            "b" to AlgoResult(score = 0.90f),
            "c" to AlgoResult(score = 0.10f),
            "d" to AlgoResult(score = 0.10f),
        )
        val conflict = SignalConflictDetector.detect(results)
        // CRITICAL → 0.25 multiplier
        val originalPct = 0.15 // 15% recommended by Kelly
        val adjustedPct = originalPct * conflict.positionMultiplier
        assertTrue(
            "Expected adjusted < original, got $adjustedPct >= $originalPct",
            adjustedPct < originalPct
        )
        assertEquals(0.0375, adjustedPct, 0.001)
    }

    @Test
    fun `no conflict multiplier leaves position unchanged`() {
        val results = SyntheticSignalData.ALGO_NAMES
            .associateWith { AlgoResult(score = 0.72f) }
        val conflict = SignalConflictDetector.detect(results)
        val originalPct = 0.15
        val adjustedPct = originalPct * conflict.positionMultiplier
        assertEquals(originalPct, adjustedPct, 0.001)
    }
}
