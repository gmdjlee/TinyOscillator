package com.tinyoscillator.core.database.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tinyoscillator.core.database.AppDatabase
import com.tinyoscillator.core.database.entity.StockMasterEntity
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
 * StockMasterDao Room in-memory 테스트 — `replaceAll` 원자성 + 검색 동작 검증.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class StockMasterDaoInMemoryTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: StockMasterDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.stockMasterDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun stock(ticker: String, name: String, market: String = "KOSPI", sector: String = "") =
        StockMasterEntity(
            ticker = ticker,
            name = name,
            market = market,
            sector = sector,
            initialConsonants = "",
            lastUpdated = 0L,
        )

    @Test
    fun `replaceAll은_기존_데이터를_전량_교체한다`() = runTest {
        dao.insertAll(listOf(stock("111111", "기존1"), stock("222222", "기존2")))
        assertEquals(2, dao.getCount())

        dao.replaceAll(listOf(stock("005930", "삼성전자"), stock("000660", "SK하이닉스"), stock("035420", "NAVER")))

        assertEquals(3, dao.getCount())
        assertNull(dao.getByTicker("111111"))
        assertEquals("삼성전자", dao.getByTicker("005930")?.name)
    }

    @Test
    fun `searchByText는_이름_부분일치를_찾는다`() = runTest {
        dao.insertAll(
            listOf(
                stock("005930", "삼성전자"),
                stock("028260", "삼성물산"),
                stock("000660", "SK하이닉스"),
            )
        )

        val results = dao.searchByText("삼성")

        assertEquals(2, results.size)
        assert(results.any { it.ticker == "005930" })
        assert(results.any { it.ticker == "028260" })
    }

    @Test
    fun `getTickersBySector는_지정된_섹터의_티커만_반환하며_공백_섹터는_제외한다`() = runTest {
        dao.insertAll(
            listOf(
                stock("005930", "삼성전자", sector = "전기전자"),
                stock("000660", "SK하이닉스", sector = "전기전자"),
                stock("035420", "NAVER", sector = "서비스업"),
                stock("999999", "섹터없음", sector = ""),
            )
        )

        val tickers = dao.getTickersBySector("전기전자", limit = 10)

        assertEquals(setOf("005930", "000660"), tickers.toSet())
    }

    @Test
    fun `getFilteredCandidates는_market과_sector_null_필터를_지원한다`() = runTest {
        dao.insertAll(
            listOf(
                stock("005930", "삼성전자", market = "KOSPI", sector = "전기전자"),
                stock("000660", "SK하이닉스", market = "KOSPI", sector = "전기전자"),
                stock("091990", "셀트리온헬스케어", market = "KOSDAQ", sector = "제약"),
            )
        )

        // market만 필터
        val kospi = dao.getFilteredCandidates(marketType = "KOSPI", sectorCode = null, candidateLimit = 10)
        assertEquals(2, kospi.size)

        // market + sector 필터
        val kospiElec = dao.getFilteredCandidates(marketType = "KOSPI", sectorCode = "전기전자", candidateLimit = 10)
        assertEquals(2, kospiElec.size)

        // 둘 다 null — 전체 반환
        val all = dao.getFilteredCandidates(marketType = null, sectorCode = null, candidateLimit = 10)
        assertEquals(3, all.size)
    }
}
