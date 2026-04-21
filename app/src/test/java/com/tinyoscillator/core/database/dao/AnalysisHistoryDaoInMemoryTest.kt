package com.tinyoscillator.core.database.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tinyoscillator.core.database.AppDatabase
import com.tinyoscillator.core.database.entity.AnalysisHistoryEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AnalysisHistoryDao — `saveWithFifo` 트랜잭션 검증.
 *
 * 기존 이력이 같은 티커면 제거 후 재삽입, 총 개수가 maxHistory 초과 시
 * 가장 오래된 항목부터 삭제되어야 함.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class AnalysisHistoryDaoInMemoryTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AnalysisHistoryDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.analysisHistoryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `saveWithFifo는_신규_종목을_삽입한다`() = runTest {
        dao.saveWithFifo(ticker = "005930", name = "삼성전자", maxHistory = 10)

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("005930", all[0].ticker)
        assertEquals("삼성전자", all[0].name)
    }

    @Test
    fun `saveWithFifo는_같은_티커_재호출_시_최신값으로_대체한다`() = runTest {
        dao.insert(AnalysisHistoryEntity(ticker = "005930", name = "이전이름", lastAnalyzedAt = 1_000L))

        dao.saveWithFifo(ticker = "005930", name = "삼성전자", maxHistory = 10)

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("삼성전자", all[0].name)
        // 새로 삽입된 row의 lastAnalyzedAt은 System.currentTimeMillis() 이므로 이전값보다 큼
        assert(all[0].lastAnalyzedAt > 1_000L)
    }

    @Test
    fun `saveWithFifo는_maxHistory_초과_시_가장_오래된_항목부터_삭제한다`() = runTest {
        // 과거 타임스탬프 5건 (오래된 순)
        repeat(5) { idx ->
            dao.insert(
                AnalysisHistoryEntity(
                    ticker = String.format("%06d", idx),
                    name = "종목$idx",
                    lastAnalyzedAt = 1_000L + idx
                )
            )
        }
        assertEquals(5, dao.getCount())

        // maxHistory = 3 — 신규 추가 후 (총 6건) → 3건만 남아야 함
        dao.saveWithFifo(ticker = "999999", name = "신규", maxHistory = 3)

        val remaining = dao.getAll()
        assertEquals(3, remaining.size)
        // 가장 최근(신규)이 목록에 있어야 하고, 가장 오래된 2건(000000, 000001)은 삭제됨
        assert(remaining.any { it.ticker == "999999" })
        assert(remaining.none { it.ticker == "000000" })
        assert(remaining.none { it.ticker == "000001" })
    }

    @Test
    fun `deleteByTicker는_지정된_티커만_삭제한다`() = runTest {
        dao.insert(AnalysisHistoryEntity(ticker = "005930", name = "삼성전자", lastAnalyzedAt = 1L))
        dao.insert(AnalysisHistoryEntity(ticker = "000660", name = "SK하이닉스", lastAnalyzedAt = 2L))

        dao.deleteByTicker("005930")

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("000660", all[0].ticker)
    }
}
