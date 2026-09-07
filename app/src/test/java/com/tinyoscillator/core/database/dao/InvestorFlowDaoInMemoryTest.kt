package com.tinyoscillator.core.database.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tinyoscillator.core.database.AppDatabase
import com.tinyoscillator.core.database.entity.InvestorFlowEntity
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
 * [InvestorFlowDao] Room in-memory 테스트. 명세: docs/TASK_investor_volume_profile.md §9.3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class InvestorFlowDaoInMemoryTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: InvestorFlowDao

    private fun entity(
        ticker: String = "005930",
        date: String,
        close: Long = 250_500L,
        fetchedAt: Long = 0L
    ): InvestorFlowEntity = InvestorFlowEntity(
        ticker = ticker,
        date = date,
        high = 255_500L,
        low = 249_500L,
        close = close,
        volume = 15_176_841L,
        prsnBuyVol = 4_662_430L,
        prsnBuyAmt = 1_175_428_000_000L,
        prsnSellVol = 4_000_000L,
        prsnSellAmt = 1_000_000_000_000L,
        frgnBuyVol = 3_000_000L,
        frgnBuyAmt = 760_000_000_000L,
        frgnSellVol = 2_900_000L,
        frgnSellAmt = 730_000_000_000L,
        orgnBuyVol = 2_000_000L,
        orgnBuyAmt = 500_000_000_000L,
        orgnSellVol = 1_900_000L,
        orgnSellAmt = 480_000_000_000L,
        fetchedAt = fetchedAt
    )

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.investorFlowDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `latestDate와 earliestDate는 행이 없으면 null을 반환한다`() = runTest {
        assertNull(dao.latestDate("005930"))
        assertNull(dao.earliestDate("005930"))
        assertNull(dao.latestFetchedAt("005930"))
    }

    @Test
    fun `동일 ticker와 date로 upsert하면 최신 값으로 덮어쓴다`() = runTest {
        dao.upsertAll(listOf(entity(date = "20260902", close = 250_500L, fetchedAt = 1L)))
        dao.upsertAll(listOf(entity(date = "20260902", close = 999_999L, fetchedAt = 2L)))

        val range = dao.getRange("005930", "20260101", "20261231")
        assertEquals(1, range.size)
        assertEquals(999_999L, range.first().close)
        assertEquals(2L, range.first().fetchedAt)
    }

    @Test
    fun `getRange는 date 오름차순으로 반환하고 경계를 포함한다`() = runTest {
        dao.upsertAll(
            listOf(
                entity(date = "20260903"),
                entity(date = "20260901"),
                entity(date = "20260902"),
                entity(date = "20260831") // 구간 밖
            )
        )

        val range = dao.getRange("005930", "20260901", "20260903")

        assertEquals(listOf("20260901", "20260902", "20260903"), range.map { it.date })
    }

    @Test
    fun `getRange는 ticker로 격리된다`() = runTest {
        dao.upsertAll(listOf(entity(ticker = "005930", date = "20260902")))
        dao.upsertAll(listOf(entity(ticker = "000660", date = "20260902")))

        val range = dao.getRange("005930", "20260101", "20261231")

        assertEquals(1, range.size)
        assertEquals("005930", range.first().ticker)
    }

    @Test
    fun `latestDate와 earliestDate는 각각 최신 최고 일자를 반환한다`() = runTest {
        dao.upsertAll(listOf(entity(date = "20260902"), entity(date = "20260831"), entity(date = "20260901")))

        assertEquals("20260902", dao.latestDate("005930"))
        assertEquals("20260831", dao.earliestDate("005930"))
    }

    @Test
    fun `latestFetchedAt은 최대 fetchedAt을 반환한다`() = runTest {
        dao.upsertAll(listOf(entity(date = "20260901", fetchedAt = 100L), entity(date = "20260902", fetchedAt = 300L)))

        assertEquals(300L, dao.latestFetchedAt("005930"))
    }

    @Test
    fun `deleteOlderThan은 cutoff 미만 행만 삭제한다`() = runTest {
        dao.upsertAll(
            listOf(
                entity(date = "20230101"),
                entity(date = "20230601"),
                entity(date = "20260902")
            )
        )

        dao.deleteOlderThan("005930", "20230601")

        val remaining = dao.getRange("005930", "19000101", "99991231")
        assertEquals(listOf("20230601", "20260902"), remaining.map { it.date })
    }
}
