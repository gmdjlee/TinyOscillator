package com.tinyoscillator.feature.bearsignal.data.repository

import com.tinyoscillator.feature.bearsignal.data.local.BearSnapshotDao
import com.tinyoscillator.feature.bearsignal.data.local.BearSnapshotEntity
import com.tinyoscillator.feature.bearsignal.domain.model.BearPhase
import com.tinyoscillator.feature.bearsignal.domain.model.BearSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [SnapshotRepositoryImpl] — [BearSnapshotDao] 위임·매핑 검증. */
class SnapshotRepositoryImplTest {

    private val dao = mockk<BearSnapshotDao>()
    private val repository = SnapshotRepositoryImpl(dao)

    private fun snapshot(day: String) = BearSnapshot(
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
        createdAt = 1L
    )

    private fun entity(day: String, phase: String = "AMBER") = BearSnapshotEntity(
        day = day,
        phase = phase,
        lead = 3,
        gate = 1,
        s1 = 1,
        s2 = 1,
        s3 = 1,
        amp = 1.30,
        configBasis = "신영 2026.6.30",
        inputsJson = "{}",
        fieldMetaJson = "{}",
        createdAt = 1L
    )

    @Test
    fun `upsertToday는 도메인을 entity로 매핑해 dao에 위임한다`() = runTest {
        coEvery { dao.upsert(any()) } returns Unit

        repository.upsertToday(snapshot("2026-07-11"))

        coVerify(exactly = 1) {
            dao.upsert(withArg {
                assertEquals("2026-07-11", it.day)
                assertEquals("AMBER", it.phase)
            })
        }
    }

    @Test
    fun `observeLatest는 entity를 domain으로 매핑한다`() = runTest {
        every { dao.observeLatest() } returns flowOf(entity("2026-07-11"))

        val result = repository.observeLatest()
        var last: BearSnapshot? = null
        result.collect { last = it }

        assertEquals("2026-07-11", last?.day)
        assertEquals(BearPhase.AMBER, last?.phase)
    }

    @Test
    fun `observeLatest는 null을 그대로 전달한다`() = runTest {
        every { dao.observeLatest() } returns flowOf(null)

        var last: BearSnapshot? = snapshot("불변값 확인용")
        repository.observeLatest().collect { last = it }

        assertNull(last)
    }

    @Test
    fun `observeRange는 리스트 전체를 domain으로 매핑한다`() = runTest {
        every { dao.observeRange("2026-07-01", "2026-07-31") } returns
            flowOf(listOf(entity("2026-07-10"), entity("2026-07-11")))

        var last: List<BearSnapshot>? = null
        repository.observeRange("2026-07-01", "2026-07-31").collect { last = it }

        assertEquals(listOf("2026-07-10", "2026-07-11"), last?.map { it.day })
    }

    @Test
    fun `latestOrNull은 dao latest 결과를 domain으로 매핑한다`() = runTest {
        coEvery { dao.latest() } returns entity("2026-07-11")

        val result = repository.latestOrNull()

        assertEquals("2026-07-11", result?.day)
    }

    @Test
    fun `latestOrNull은 이력이 없으면 null을 반환한다`() = runTest {
        coEvery { dao.latest() } returns null

        assertNull(repository.latestOrNull())
    }
}
