package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.BearPhase
import com.tinyoscillator.feature.bearsignal.domain.model.BearSnapshot
import com.tinyoscillator.feature.bearsignal.domain.model.GateAdvance
import com.tinyoscillator.feature.bearsignal.domain.model.PhaseChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DetectTransitionsUseCase] 결정적 테스트 (TASK_bear_signal_console.md §6.1 의사코드 1:1).
 */
class DetectTransitionsUseCaseTest {

    private val useCase = DetectTransitionsUseCase()

    private fun snapshot(
        day: String,
        phase: BearPhase = BearPhase.AMBER,
        gate: Int = 1
    ): BearSnapshot = BearSnapshot(
        day = day,
        phase = phase,
        lead = 3,
        gate = gate,
        s1 = 1,
        s2 = 1,
        s3 = 1,
        amp = 1.30,
        configBasis = "신영 2026.6.30",
        inputsJson = "{}",
        fieldMetaJson = "{}",
        createdAt = 0L
    )

    @Test
    fun `빈 리스트는 빈 결과를 반환한다`() {
        assertEquals(emptyList<Any>(), useCase(emptyList()))
    }

    @Test
    fun `스냅샷이 1건이면 비교 대상이 없어 빈 결과를 반환한다`() {
        assertEquals(emptyList<Any>(), useCase(listOf(snapshot("2026-07-10"))))
    }

    @Test
    fun `국면 변화가 없으면 빈 결과를 반환한다`() {
        val series = listOf(
            snapshot("2026-07-10", phase = BearPhase.AMBER, gate = 1),
            snapshot("2026-07-11", phase = BearPhase.AMBER, gate = 1)
        )
        assertEquals(emptyList<Any>(), useCase(series))
    }

    @Test
    fun `국면 변화가 있으면 PhaseChange를 기록한다`() {
        val series = listOf(
            snapshot("2026-07-10", phase = BearPhase.GREEN, gate = 0),
            snapshot("2026-07-11", phase = BearPhase.AMBER, gate = 0)
        )
        val transitions = useCase(series)

        assertEquals(1, transitions.size)
        assertEquals("2026-07-11", transitions[0].asOf)
        assertEquals(PhaseChange(BearPhase.GREEN, BearPhase.AMBER), transitions[0].kind)
    }

    @Test
    fun `국면이 개선되는 방향(약세에서 안전으로)도 PhaseChange로 기록한다`() {
        val series = listOf(
            snapshot("2026-07-10", phase = BearPhase.RED, gate = 3),
            snapshot("2026-07-11", phase = BearPhase.GREEN, gate = 3)
        )
        val transitions = useCase(series)

        assertEquals(1, transitions.size)
        assertEquals(PhaseChange(BearPhase.RED, BearPhase.GREEN), transitions[0].kind)
    }

    @Test
    fun `gate가 상승하면 GateAdvance를 기록한다`() {
        val series = listOf(
            snapshot("2026-07-10", phase = BearPhase.AMBER, gate = 1),
            snapshot("2026-07-11", phase = BearPhase.AMBER, gate = 2)
        )
        val transitions = useCase(series)

        assertEquals(1, transitions.size)
        assertEquals("2026-07-11", transitions[0].asOf)
        assertEquals(GateAdvance(2), transitions[0].kind)
    }

    @Test
    fun `gate가 하락하면 전이로 기록하지 않는다`() {
        val series = listOf(
            snapshot("2026-07-10", phase = BearPhase.AMBER, gate = 2),
            snapshot("2026-07-11", phase = BearPhase.AMBER, gate = 1)
        )
        assertEquals(emptyList<Any>(), useCase(series))
    }

    @Test
    fun `gate가 동일하면 GateAdvance를 기록하지 않는다`() {
        val series = listOf(
            snapshot("2026-07-10", phase = BearPhase.AMBER, gate = 1),
            snapshot("2026-07-11", phase = BearPhase.AMBER, gate = 1)
        )
        assertEquals(emptyList<Any>(), useCase(series))
    }

    @Test
    fun `국면 변화와 gate 상승이 동시에 일어나면 같은 asOf로 두 건 모두 기록한다`() {
        val series = listOf(
            snapshot("2026-07-10", phase = BearPhase.AMBER, gate = 1),
            snapshot("2026-07-11", phase = BearPhase.RED, gate = 3)
        )
        val transitions = useCase(series)

        assertEquals(2, transitions.size)
        assertTrue(transitions.all { it.asOf == "2026-07-11" })
        assertTrue(transitions.any { it.kind == PhaseChange(BearPhase.AMBER, BearPhase.RED) })
        assertTrue(transitions.any { it.kind == GateAdvance(3) })
    }

    @Test
    fun `여러 스냅샷에서 각 인접 쌍마다 독립적으로 전이를 산출한다`() {
        val series = listOf(
            snapshot("2026-07-08", phase = BearPhase.GREEN, gate = 0),
            snapshot("2026-07-09", phase = BearPhase.GREEN, gate = 0), // 무변화
            snapshot("2026-07-10", phase = BearPhase.AMBER, gate = 1), // phase 변화 + gate 상승
            snapshot("2026-07-11", phase = BearPhase.AMBER, gate = 1) // 무변화
        )
        val transitions = useCase(series)

        assertEquals(2, transitions.size)
        assertTrue(transitions.all { it.asOf == "2026-07-10" })
    }
}
