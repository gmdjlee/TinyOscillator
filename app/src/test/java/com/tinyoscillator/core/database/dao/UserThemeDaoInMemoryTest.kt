package com.tinyoscillator.core.database.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tinyoscillator.core.database.AppDatabase
import com.tinyoscillator.core.database.entity.UserThemeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UserThemeDao Room in-memory 통합 테스트 (Robolectric).
 *
 * 기존 mock 기반 DAO 테스트와 달리, 실제 Room SQLite 엔진을 JVM에서 실행해
 * 쿼리/트랜잭션이 의도대로 동작하는지 검증한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class UserThemeDaoInMemoryTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: UserThemeDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.userThemeDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert_후_getById가_동일_엔티티를_반환한다`() = runTest {
        val id = dao.insert(UserThemeEntity(name = "반도체", tickers = "[\"005930\",\"000660\"]"))
        val loaded = dao.getById(id)

        assertNotNull(loaded)
        assertEquals("반도체", loaded?.name)
        assertEquals("[\"005930\",\"000660\"]", loaded?.tickers)
    }

    @Test
    fun `count는_삽입_개수를_반환한다`() = runTest {
        dao.insert(UserThemeEntity(name = "A", tickers = "[]"))
        dao.insert(UserThemeEntity(name = "B", tickers = "[]"))
        dao.insert(UserThemeEntity(name = "C", tickers = "[]"))

        assertEquals(3, dao.count())
    }

    @Test
    fun `observeAll은_sort_order_오름차순으로_emit한다`() = runTest {
        dao.insert(UserThemeEntity(name = "세번째", tickers = "[]", sortOrder = 3))
        dao.insert(UserThemeEntity(name = "첫번째", tickers = "[]", sortOrder = 1))
        dao.insert(UserThemeEntity(name = "두번째", tickers = "[]", sortOrder = 2))

        val emitted = dao.observeAll().first()

        assertEquals(listOf("첫번째", "두번째", "세번째"), emitted.map { it.name })
    }

    @Test
    fun `getMaxSortOrder는_빈_테이블에서_minus1을_반환한다`() = runTest {
        assertEquals(-1, dao.getMaxSortOrder())
    }

    @Test
    fun `getMaxSortOrder는_삽입_후_최대값을_반환한다`() = runTest {
        dao.insert(UserThemeEntity(name = "A", tickers = "[]", sortOrder = 5))
        dao.insert(UserThemeEntity(name = "B", tickers = "[]", sortOrder = 12))
        dao.insert(UserThemeEntity(name = "C", tickers = "[]", sortOrder = 3))

        assertEquals(12, dao.getMaxSortOrder())
    }

    @Test
    fun `delete_후_getById는_null을_반환한다`() = runTest {
        val id = dao.insert(UserThemeEntity(name = "삭제될 테마", tickers = "[]"))
        val entity = dao.getById(id)!!

        dao.delete(entity)

        assertNull(dao.getById(id))
        assertEquals(0, dao.count())
    }
}
