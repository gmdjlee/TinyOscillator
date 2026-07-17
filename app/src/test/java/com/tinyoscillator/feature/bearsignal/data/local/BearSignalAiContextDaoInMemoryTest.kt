package com.tinyoscillator.feature.bearsignal.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tinyoscillator.core.database.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [BearSignalAiContextDao] Room in-memory 테스트 (TASK_bear_signal_console.md §4.7 Phase 7-1
 * 하드 게이트 — upsert 대체·섹션별 단건 조회·전체 조회/삭제).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class BearSignalAiContextDaoInMemoryTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: BearSignalAiContextDao

    private fun entity(
        sectionKey: String = "type0_monitor",
        contentJson: String = "[]",
        asOf: String = "2026-07-17",
        provider: String = "claude",
        approvedAt: Long = 0L
    ): BearSignalAiContextEntity = BearSignalAiContextEntity(
        sectionKey = sectionKey,
        contentJson = contentJson,
        asOf = asOf,
        provider = provider,
        approvedAt = approvedAt
    )

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.bearSignalAiContextDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `getBySectionKey는 없으면 null을 반환한다`() = runTest {
        assertNull(dao.getBySectionKey("type0_monitor"))
    }

    @Test
    fun `upsert 후 getBySectionKey로 조회된다`() = runTest {
        dao.upsert(entity(sectionKey = "type0_monitor", contentJson = "[{\"text\":\"a\"}]"))

        val result = dao.getBySectionKey("type0_monitor")
        assertEquals("type0_monitor", result?.sectionKey)
        assertEquals("[{\"text\":\"a\"}]", result?.contentJson)
    }

    @Test
    fun `동일 section_key로 upsert하면 최신 값으로 대체된다`() = runTest {
        dao.upsert(entity(sectionKey = "type0_monitor", asOf = "2026-07-01", approvedAt = 1L))
        dao.upsert(entity(sectionKey = "type0_monitor", asOf = "2026-07-17", approvedAt = 2L))

        val result = dao.getBySectionKey("type0_monitor")
        assertEquals("2026-07-17", result?.asOf)
        assertEquals(2L, result?.approvedAt)

        // 같은 section_key로 두 번 upsert했지만 행은 하나만 존재해야 한다.
        assertEquals(1, dao.getAll().size)
    }

    @Test
    fun `getAll은 여러 섹션의 승인 콘텐츠를 모두 반환한다`() = runTest {
        dao.upsert(entity(sectionKey = "type0_monitor"))
        dao.upsert(entity(sectionKey = "type1_cases"))
        dao.upsert(entity(sectionKey = "history_current"))

        val all = dao.getAll()
        assertEquals(3, all.size)
        assertTrue(all.map { it.sectionKey }.containsAll(listOf("type0_monitor", "type1_cases", "history_current")))
    }

    @Test
    fun `clearAll은 모든 승인 콘텐츠를 삭제한다`() = runTest {
        dao.upsert(entity(sectionKey = "type0_monitor"))
        dao.upsert(entity(sectionKey = "type1_cases"))

        dao.clearAll()

        assertEquals(0, dao.getAll().size)
    }

    @Test
    fun `provider와 approvedAt이 정확히 저장된다`() = runTest {
        dao.upsert(entity(sectionKey = "history_current", provider = "gemini", approvedAt = 12345L))

        val result = dao.getBySectionKey("history_current")
        assertEquals("gemini", result?.provider)
        assertEquals(12345L, result?.approvedAt)
    }
}
