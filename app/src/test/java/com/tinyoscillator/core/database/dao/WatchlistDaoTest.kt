package com.tinyoscillator.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tinyoscillator.core.database.AppDatabase
import com.tinyoscillator.core.database.entity.WatchlistItemEntity
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WatchlistDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: WatchlistDao

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.watchlistDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `insert and observe item`() = runTest {
        dao.insert(WatchlistItemEntity(ticker = "005930", name = "삼성전자"))
        assertEquals(1, dao.observeAll().first().size)
    }

    @Test
    fun `updateSortOrder updates correctly`() = runTest {
        val id = dao.insert(WatchlistItemEntity(ticker = "005930", name = "삼성전자", sortOrder = 0))
        dao.updateSortOrder(id, 5)
        val item = dao.getById(id)
        assertEquals(5, item?.sortOrder)
    }

    @Test
    fun `updateCache sets price and signal`() = runTest {
        dao.insert(WatchlistItemEntity(ticker = "005930", name = "삼성전자"))
        dao.updateCache("005930", 73500L, 0.02f, 0.74f)
        val item = dao.getByTicker("005930")
        assertEquals(73500L, item?.cachedPrice)
        assertEquals(0.74f, item?.cachedSignal!!, 0.001f)
    }

    @Test
    fun `delete removes item`() = runTest {
        val entity = WatchlistItemEntity(ticker = "005930", name = "삼성전자")
        val id = dao.insert(entity)
        dao.delete(entity.copy(id = id))
        assertEquals(0, dao.observeAll().first().size)
    }

    @Test
    fun `moveToGroup updates groupId`() = runTest {
        val id = dao.insert(WatchlistItemEntity(ticker = "005930", name = "삼성전자", groupId = 0))
        dao.moveToGroup(id, 2L)
        val item = dao.getById(id)
        assertEquals(2L, item?.groupId)
    }

    @Test
    fun `observeBySignal sorts by cachedSignal descending`() = runTest {
        dao.insert(WatchlistItemEntity(ticker = "A", name = "A", cachedSignal = 0.6f))
        dao.insert(WatchlistItemEntity(ticker = "B", name = "B", cachedSignal = 0.9f))
        dao.insert(WatchlistItemEntity(ticker = "C", name = "C", cachedSignal = 0.4f))
        val items = dao.observeBySignal().first()
        assertEquals("B", items[0].ticker)
        assertEquals("C", items[2].ticker)
    }
}
