package com.tinyoscillator.feature.bearsignal.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tinyoscillator.core.database.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [BearSnapshotDao] Room in-memory 테스트 (TASK_bear_signal_console.md §6.1 Phase 3.5-1
 * 하드 게이트 — upsert 덮어쓰기·observeRange·latest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class BearSnapshotDaoInMemoryTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: BearSnapshotDao

    private fun entity(
        day: String,
        phase: String = "AMBER",
        gate: Int = 1,
        createdAt: Long = 0L
    ): BearSnapshotEntity = BearSnapshotEntity(
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
        createdAt = createdAt
    )

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.bearSnapshotDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `latest는 이력이 없으면 null을 반환한다`() = runTest {
        assertNull(dao.latest())
    }

    @Test
    fun `동일한 day로 upsert하면 최신 값으로 덮어쓴다`() = runTest {
        dao.upsert(entity("2026-07-11", gate = 1, createdAt = 1L))
        dao.upsert(entity("2026-07-11", gate = 2, createdAt = 2L))

        val latest = dao.latest()
        assertEquals(2, latest?.gate)
        assertEquals(2L, latest?.createdAt)

        // 같은 day로 두 번 upsert했지만 행은 하나만 존재해야 한다.
        val range = dao.observeRange("2026-07-01", "2026-07-31").first()
        assertEquals(1, range.size)
    }

    @Test
    fun `observeRange는 구간 내 스냅샷을 day 오름차순으로 반환한다`() = runTest {
        dao.upsert(entity("2026-07-12"))
        dao.upsert(entity("2026-07-10"))
        dao.upsert(entity("2026-07-11"))
        dao.upsert(entity("2026-06-30")) // 구간 밖

        val range = dao.observeRange("2026-07-01", "2026-07-31").first()

        assertEquals(listOf("2026-07-10", "2026-07-11", "2026-07-12"), range.map { it.day })
    }

    @Test
    fun `observeRange는 양끝 경계 day를 포함한다`() = runTest {
        dao.upsert(entity("2026-07-01"))
        dao.upsert(entity("2026-07-31"))
        dao.upsert(entity("2026-08-01")) // 구간 밖

        val range = dao.observeRange("2026-07-01", "2026-07-31").first()

        assertEquals(listOf("2026-07-01", "2026-07-31"), range.map { it.day })
    }

    @Test
    fun `latest는 day 기준 가장 최근 스냅샷을 반환한다`() = runTest {
        dao.upsert(entity("2026-07-05"))
        dao.upsert(entity("2026-07-11"))
        dao.upsert(entity("2026-07-08"))

        assertEquals("2026-07-11", dao.latest()?.day)
    }

    @Test
    fun `observeLatest는 Flow로 최신 스냅샷을 방출한다`() = runTest {
        dao.upsert(entity("2026-07-05"))
        dao.upsert(entity("2026-07-11"))

        assertEquals("2026-07-11", dao.observeLatest().first()?.day)
    }
}
