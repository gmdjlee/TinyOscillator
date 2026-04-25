package com.tinyoscillator.core.database.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tinyoscillator.core.database.AppDatabase
import com.tinyoscillator.core.database.entity.ThemeGroupEntity
import com.tinyoscillator.core.database.entity.ThemeStockEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ThemeGroupDao / ThemeStockDao — `replaceAll` / `replaceForTheme` 트랜잭션 검증
 * + Flow 구독 + 인덱스 기반 역조회.
 *
 * `@Config(application = Application::class)`로 `@HiltAndroidApp TinyOscillatorApp`의
 * AndroidKeyStore 초기화 회피 (Robolectric 환경에서 미지원).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class ThemeDaoInMemoryTest {

    private lateinit var db: AppDatabase
    private lateinit var groupDao: ThemeGroupDao
    private lateinit var stockDao: ThemeStockDao

    private val now = 1_700_000_000_000L

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        groupDao = db.themeGroupDao()
        stockDao = db.themeStockDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun group(code: String, name: String, returnRate: Double = 0.0, ts: Long = now) =
        ThemeGroupEntity(
            themeCode = code,
            themeName = name,
            stockCount = 0,
            fluRate = 0.0,
            periodReturnRate = returnRate,
            riseCount = 0,
            fallCount = 0,
            mainStocks = "",
            lastUpdated = ts,
        )

    private fun stock(themeCode: String, stockCode: String, stockName: String, returnRate: Double = 0.0) =
        ThemeStockEntity(
            themeCode = themeCode,
            stockCode = stockCode,
            stockName = stockName,
            currentPrice = 0.0,
            priorDiff = 0.0,
            fluRate = 0.0,
            volume = 0L,
            periodReturnRate = returnRate,
            lastUpdated = now,
        )

    // ========== ThemeGroupDao ==========

    @Test
    fun `ThemeGroupDao replaceAll - 빈 DB에 신규 삽입`() = runTest {
        val items = listOf(
            group("319", "2차전지", returnRate = 15.5),
            group("100", "AI", returnRate = 22.3),
        )
        groupDao.replaceAll(items)

        val all = groupDao.observeAll().first()
        assertEquals(2, all.size)
        // observeAll은 theme_name ASC 정렬
        assertEquals("2차전지", all[0].themeName)
        assertEquals("AI", all[1].themeName)
    }

    @Test
    fun `ThemeGroupDao replaceAll - 기존 데이터를 완전히 대체`() = runTest {
        groupDao.insertAll(listOf(group("319", "2차전지", returnRate = 1.0)))
        assertEquals(1, groupDao.count())

        val newItems = listOf(
            group("100", "AI", returnRate = 22.3),
            group("200", "반도체", returnRate = 11.1),
        )
        groupDao.replaceAll(newItems)

        val all = groupDao.observeAll().first()
        assertEquals(2, all.size)
        // 기존 319 코드는 제거되어야 함
        assertNull(groupDao.getByCode("319"))
        assertNotNull(groupDao.getByCode("100"))
    }

    @Test
    fun `ThemeGroupDao replaceAll - 빈 리스트 호출 시 모두 삭제`() = runTest {
        groupDao.insertAll(listOf(group("319", "2차전지")))
        assertEquals(1, groupDao.count())

        groupDao.replaceAll(emptyList())

        assertEquals(0, groupDao.count())
    }

    @Test
    fun `ThemeGroupDao searchByName - 부분 일치 LIKE`() = runTest {
        groupDao.replaceAll(
            listOf(
                group("100", "AI 반도체"),
                group("200", "반도체 장비"),
                group("300", "2차전지"),
            )
        )

        val matches = groupDao.searchByName("반도체").first()
        assertEquals(2, matches.size)
        assertTrue(matches.all { it.themeName.contains("반도체") })

        val noMatch = groupDao.searchByName("바이오").first()
        assertTrue(noMatch.isEmpty())
    }

    @Test
    fun `ThemeGroupDao lastUpdatedAt - MAX last_updated`() = runTest {
        groupDao.replaceAll(
            listOf(
                group("100", "A", ts = 1_000L),
                group("200", "B", ts = 2_000L),
                group("300", "C", ts = 1_500L),
            )
        )

        assertEquals(2_000L, groupDao.lastUpdatedAt())
    }

    @Test
    fun `ThemeGroupDao lastUpdatedAt - 빈 테이블이면 null`() = runTest {
        assertNull(groupDao.lastUpdatedAt())
    }

    @Test
    fun `ThemeGroupDao getByCode - 존재하지 않으면 null`() = runTest {
        groupDao.replaceAll(listOf(group("319", "2차전지")))
        assertNull(groupDao.getByCode("999"))
    }

    // ========== ThemeStockDao ==========

    @Test
    fun `ThemeStockDao replaceForTheme - 다른 테마의 종목은 영향 없음`() = runTest {
        stockDao.insertAll(
            listOf(
                stock("319", "005930", "삼성전자"),
                stock("319", "000660", "SK하이닉스"),
                stock("100", "035420", "NAVER"),
            )
        )
        assertEquals(3, stockDao.count())

        // 319 테마만 부분 교체
        stockDao.replaceForTheme(
            "319",
            listOf(stock("319", "005380", "현대차")),
        )

        // 319: 1건 (현대차만), 100: 1건 (NAVER 그대로)
        val theme319 = stockDao.observeByTheme("319").first()
        assertEquals(1, theme319.size)
        assertEquals("현대차", theme319[0].stockName)

        val theme100 = stockDao.observeByTheme("100").first()
        assertEquals(1, theme100.size)
        assertEquals("NAVER", theme100[0].stockName)
    }

    @Test
    fun `ThemeStockDao replaceForTheme - 빈 리스트 호출 시 해당 테마만 삭제`() = runTest {
        stockDao.insertAll(
            listOf(
                stock("319", "005930", "삼성전자"),
                stock("100", "035420", "NAVER"),
            )
        )
        stockDao.replaceForTheme("319", emptyList())

        assertEquals(0, stockDao.countForTheme("319"))
        assertEquals(1, stockDao.countForTheme("100"))
    }

    @Test
    fun `ThemeStockDao observeByTheme - period_return_rate DESC 정렬`() = runTest {
        stockDao.replaceForTheme(
            "319",
            listOf(
                stock("319", "A", "낮은수익", returnRate = 1.0),
                stock("319", "B", "높은수익", returnRate = 99.9),
                stock("319", "C", "중간수익", returnRate = 50.0),
            ),
        )

        val items = stockDao.observeByTheme("319").first()
        assertEquals(3, items.size)
        // periodReturnRate DESC
        assertEquals("높은수익", items[0].stockName)
        assertEquals("중간수익", items[1].stockName)
        assertEquals("낮은수익", items[2].stockName)
    }

    @Test
    fun `ThemeStockDao getByStockCode - 인덱스 역조회`() = runTest {
        // 같은 종목(005930)이 3개 테마에 동시에 속함
        stockDao.insertAll(
            listOf(
                stock("100", "005930", "삼성전자"),
                stock("200", "005930", "삼성전자"),
                stock("300", "005930", "삼성전자"),
                stock("319", "000660", "SK하이닉스"),
            )
        )

        val sameStock = stockDao.getByStockCode("005930")
        assertEquals(3, sameStock.size)
        assertTrue(sameStock.all { it.stockCode == "005930" })

        val unique = stockDao.getByStockCode("000660")
        assertEquals(1, unique.size)
        assertEquals("319", unique[0].themeCode)
    }

    @Test
    fun `ThemeStockDao 복합 PK - 동일 (theme_code, stock_code) 재삽입은 REPLACE`() = runTest {
        stockDao.insertAll(listOf(stock("319", "005930", "삼성전자(이전)")))
        stockDao.insertAll(listOf(stock("319", "005930", "삼성전자(최신)")))

        val items = stockDao.observeByTheme("319").first()
        assertEquals(1, items.size)
        assertEquals("삼성전자(최신)", items[0].stockName)
    }

    // ========== Cross-DAO ==========

    @Test
    fun `Theme DAO들은 독립 — group replaceAll이 stock 캐시를 건드리지 않음`() = runTest {
        // 시나리오: group 캐시는 일일 갱신으로 교체되지만, stock 캐시는 테마 단위로 부분 갱신.
        // group의 replaceAll이 stock 행을 cascading 삭제하면 안 됨 (외래키 제약 없음).
        groupDao.replaceAll(listOf(group("319", "2차전지")))
        stockDao.insertAll(listOf(stock("319", "005930", "삼성전자")))

        // 그룹 전체 교체
        groupDao.replaceAll(listOf(group("999", "신규테마")))

        // stock 캐시는 그대로 유지 (orphan row가 되지만 다음 stock 갱신 사이클에서 정리)
        assertEquals(1, stockDao.countForTheme("319"))
    }
}
