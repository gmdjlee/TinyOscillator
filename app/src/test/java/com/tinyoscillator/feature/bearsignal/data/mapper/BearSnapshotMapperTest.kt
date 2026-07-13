package com.tinyoscillator.feature.bearsignal.data.mapper

import com.tinyoscillator.feature.bearsignal.data.local.BearSnapshotEntity
import com.tinyoscillator.feature.bearsignal.domain.model.BearPhase
import com.tinyoscillator.feature.bearsignal.domain.model.BearSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

/** [BearSnapshotMapper] 왕복 변환 테스트. */
class BearSnapshotMapperTest {

    private val domain = BearSnapshot(
        day = "2026-07-11",
        phase = BearPhase.AMBER,
        lead = 3,
        gate = 1,
        s1 = 1,
        s2 = 1,
        s3 = 1,
        amp = 1.30,
        configBasis = "신영 2026.6.30",
        inputsJson = """{"s2_up":14}""",
        fieldMetaJson = """{"s2_up":{"source":"AUTO","as_of":"2026-07-05","origin":"kotlin_krx:KS11"}}""",
        createdAt = 123456L
    )

    @Test
    fun `domain을 entity로 변환하면 phase는 name 문자열로 인코딩된다`() {
        val entity = BearSnapshotMapper.toEntity(domain)

        assertEquals("2026-07-11", entity.day)
        assertEquals("AMBER", entity.phase)
        assertEquals(3, entity.lead)
        assertEquals(1, entity.gate)
        assertEquals(1, entity.s1)
        assertEquals(1, entity.s2)
        assertEquals(1, entity.s3)
        assertEquals(1.30, entity.amp, 1e-9)
        assertEquals("신영 2026.6.30", entity.configBasis)
        assertEquals(domain.inputsJson, entity.inputsJson)
        assertEquals(domain.fieldMetaJson, entity.fieldMetaJson)
        assertEquals(123456L, entity.createdAt)
    }

    @Test
    fun `entity를 domain으로 변환하면 phase 문자열이 BearPhase로 디코딩된다`() {
        val entity = BearSnapshotEntity(
            day = "2026-07-11",
            phase = "RED",
            lead = 8,
            gate = 3,
            s1 = 3,
            s2 = 3,
            s3 = 2,
            amp = 1.6,
            configBasis = "신영 2026.6.30",
            inputsJson = "{}",
            fieldMetaJson = "{}",
            createdAt = 999L
        )

        val result = BearSnapshotMapper.toDomain(entity)

        assertEquals(BearPhase.RED, result.phase)
        assertEquals(8, result.lead)
        assertEquals(3, result.gate)
        assertEquals(1.6, result.amp, 1e-9)
    }

    @Test
    fun `domain을 entity로_다시_domain으로_변환해도_값이_보존된다`() {
        val roundTripped = BearSnapshotMapper.toDomain(BearSnapshotMapper.toEntity(domain))
        assertEquals(domain, roundTripped)
    }
}
