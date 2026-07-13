package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.BearPhase
import com.tinyoscillator.feature.bearsignal.domain.model.BearSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * [EvaluateSnapshotFreshnessUseCase] 테스트 — "최신 as_of가 로컬보다 오래되면 갱신 제안만
 * 표면화(자동 반영 금지)"(§6.1)를 순수 비교 함수로 검증한다.
 */
class EvaluateSnapshotFreshnessUseCaseTest {

    private val useCase = EvaluateSnapshotFreshnessUseCase()

    private fun snapshot(day: String): BearSnapshot = BearSnapshot(
        day = day,
        phase = BearPhase.AMBER,
        lead = 3,
        gate = 1,
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
    fun `이력이 없으면 제안하지 않는다`() {
        assertNull(useCase(null, LocalDate.parse("2026-07-13")))
    }

    @Test
    fun `최신 스냅샷이 오늘과 같으면 제안하지 않는다`() {
        val latest = snapshot("2026-07-13")
        assertNull(useCase(latest, LocalDate.parse("2026-07-13")))
    }

    @Test
    fun `최신 스냅샷이 오늘보다 오래되면 제안을 반환한다`() {
        val latest = snapshot("2026-07-11")
        val suggestion = useCase(latest, LocalDate.parse("2026-07-13"))

        assertEquals("2026-07-11", suggestion?.latestAsOf)
        assertEquals("2026-07-13", suggestion?.today)
    }

    @Test
    fun `최신 스냅샷이 오늘보다 하루라도 오래되면 경계값으로 제안한다`() {
        val latest = snapshot("2026-07-12")
        val suggestion = useCase(latest, LocalDate.parse("2026-07-13"))
        assertEquals("2026-07-12", suggestion?.latestAsOf)
    }

    @Test
    fun `최신 스냅샷이 오늘보다 미래(방어적 케이스)면 제안하지 않는다`() {
        val latest = snapshot("2026-07-14")
        assertNull(useCase(latest, LocalDate.parse("2026-07-13")))
    }
}
