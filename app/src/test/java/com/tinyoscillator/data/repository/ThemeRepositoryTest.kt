package com.tinyoscillator.data.repository

import app.cash.turbine.test
import com.tinyoscillator.core.api.KiwoomApiClient
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.core.api.PageHeaders
import com.tinyoscillator.core.database.dao.ThemeGroupDao
import com.tinyoscillator.core.database.dao.ThemeStockDao
import com.tinyoscillator.core.database.entity.ThemeGroupEntity
import com.tinyoscillator.core.database.entity.ThemeStockEntity
import com.tinyoscillator.data.dto.KiwoomThemeGroupItem
import com.tinyoscillator.data.dto.KiwoomThemeListResponse
import com.tinyoscillator.data.dto.KiwoomThemeStockItem
import com.tinyoscillator.data.dto.KiwoomThemeStockResponse
import com.tinyoscillator.domain.model.ThemeDataProgress
import com.tinyoscillator.domain.model.ThemeExchange
import com.tinyoscillator.domain.model.ThemeSortMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.coVerifySequence
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * [ThemeRepository] 단위 테스트.
 *
 * - `KiwoomApiClient.callWithHeaders`를 MockK로 시퀀싱해 ka90001/ka90002 페이지네이션 흐름과
 *   부분 실패 격리, 진행률 emit 순서를 검증한다.
 * - 실제 HTTP/JSON 파싱은 별도 [com.tinyoscillator.data.dto.KiwoomThemeModelsSerializationTest]가 책임진다.
 *
 * MockK가 parser 람다를 호출하지 않으므로, 본 테스트는 응답 구조 자체보다는
 * "헤더 piping + DAO 호출 순서 + 진행 상태 emit"을 다룬다.
 */
class ThemeRepositoryTest {

    private lateinit var themeGroupDao: ThemeGroupDao
    private lateinit var themeStockDao: ThemeStockDao
    private lateinit var kiwoomApiClient: KiwoomApiClient
    private lateinit var repository: ThemeRepository

    private val json = KiwoomApiClient.createDefaultJson()
    private val validConfig = KiwoomApiKeyConfig(appKey = "AK", secretKey = "SK")

    @Before
    fun setup() {
        themeGroupDao = mockk(relaxed = true)
        themeStockDao = mockk(relaxed = true)
        kiwoomApiClient = mockk(relaxed = true)
        repository = ThemeRepository(themeGroupDao, themeStockDao, kiwoomApiClient, json)
    }

    // ============================================================================
    // updateAll - 키 미설정
    // ============================================================================

    @Test
    fun `updateAll - 키 미설정이면 Error 단일 emit`() = runTest {
        val invalid = KiwoomApiKeyConfig(appKey = "", secretKey = "")
        val emissions = repository.updateAll(invalid, ThemeExchange.KRX).toList()
        assertEquals(1, emissions.size)
        assertTrue(emissions[0] is ThemeDataProgress.Error)
        assertTrue(
            (emissions[0] as ThemeDataProgress.Error).message.contains("API 키")
        )
        coVerify(exactly = 0) { kiwoomApiClient.callWithHeaders<Any>(any(), any(), any(), any(), any(), any()) }
    }

    // ============================================================================
    // updateAll - 정상 흐름
    // ============================================================================

    @Test
    fun `updateAll - ka90001 단일 페이지 + 1개 테마 정상 종료`() = runTest {
        val themeItem = KiwoomThemeGroupItem(
            themeCode = "T001",
            themeName = "AI",
            stockCount = "10",
            fluctuationRate = "+1.5",
            periodReturnRate = "+12.3",
            risingStockCount = "7",
            fallingStockCount = "2",
            mainStocks = "삼성전자,네이버",
        )
        val stockItem = KiwoomThemeStockItem(
            stockCode = "005930",
            stockName = "삼성전자",
            currentPrice = "75000",
            priorDiff = "+500",
            fluctuationRate = "+0.67",
            accumulatedVolume = "1234567",
            periodReturnRate = "+5.5",
        )

        coEvery {
            kiwoomApiClient.callWithHeaders<Pair<KiwoomThemeListResponse, PageHeaders>>(
                apiId = "ka90001",
                url = any(),
                body = any(),
                config = any(),
                extraHeaders = any(),
                parser = any(),
            )
        } returns Result.success(
            KiwoomThemeListResponse(
                returnCode = 0,
                themeGroups = listOf(themeItem),
            ) to PageHeaders(contYn = "N", nextKey = "", apiId = "ka90001")
        )

        coEvery {
            kiwoomApiClient.callWithHeaders<Pair<KiwoomThemeStockResponse, PageHeaders>>(
                apiId = "ka90002",
                url = any(),
                body = any(),
                config = any(),
                extraHeaders = any(),
                parser = any(),
            )
        } returns Result.success(
            KiwoomThemeStockResponse(
                returnCode = 0,
                themeStocks = listOf(stockItem),
            ) to PageHeaders(contYn = "N", nextKey = "", apiId = "ka90002")
        )

        val emissions = repository.updateAll(validConfig, ThemeExchange.KRX).toList()

        // 마지막은 Success
        val last = emissions.last()
        assertTrue("Success 최종 emit 기대 — actual=$last", last is ThemeDataProgress.Success)
        last as ThemeDataProgress.Success
        assertEquals(1, last.themeCount)
        assertEquals(1, last.stockCount)

        // 진행률은 단조 증가, 0~1 범위
        val progressed = emissions.filterIsInstance<ThemeDataProgress.Loading>().map { it.progress }
        assertTrue(progressed.first() <= progressed.last())
        progressed.forEach {
            assertTrue("progress out of range: $it", it in 0f..1f)
        }

        // DAO에 정확히 1건 그룹 + 1건 종목 저장 확인
        val groupSlot = slot<List<ThemeGroupEntity>>()
        coVerify { themeGroupDao.replaceAll(capture(groupSlot)) }
        assertEquals(1, groupSlot.captured.size)
        assertEquals("T001", groupSlot.captured[0].themeCode)
        assertEquals(12.3, groupSlot.captured[0].periodReturnRate, 1e-6)

        val stockSlot = slot<List<ThemeStockEntity>>()
        coVerify { themeStockDao.replaceForTheme("T001", capture(stockSlot)) }
        assertEquals(1, stockSlot.captured.size)
        assertEquals("005930", stockSlot.captured[0].stockCode)
        assertEquals(75000.0, stockSlot.captured[0].currentPrice, 1e-6)
        assertEquals(1234567L, stockSlot.captured[0].volume)
    }

    // ============================================================================
    // updateAll - ka90001 페이지네이션 (cont-yn=Y → N)
    // ============================================================================

    @Test
    fun `updateAll - ka90001 cont-yn Y 후 N이면 두 페이지 누적`() = runTest {
        val page1Item = KiwoomThemeGroupItem(themeCode = "T001", themeName = "A")
        val page2Item = KiwoomThemeGroupItem(themeCode = "T002", themeName = "B")

        coEvery {
            kiwoomApiClient.callWithHeaders<Pair<KiwoomThemeListResponse, PageHeaders>>(
                apiId = "ka90001",
                url = any(),
                body = any(),
                config = any(),
                extraHeaders = any(),
                parser = any(),
            )
        } returnsMany listOf(
            Result.success(
                KiwoomThemeListResponse(themeGroups = listOf(page1Item)) to
                    PageHeaders(contYn = "Y", nextKey = "page2-key", apiId = "ka90001")
            ),
            Result.success(
                KiwoomThemeListResponse(themeGroups = listOf(page2Item)) to
                    PageHeaders(contYn = "N", nextKey = "", apiId = "ka90001")
            ),
        )

        coEvery {
            kiwoomApiClient.callWithHeaders<Pair<KiwoomThemeStockResponse, PageHeaders>>(
                apiId = "ka90002",
                url = any(),
                body = any(),
                config = any(),
                extraHeaders = any(),
                parser = any(),
            )
        } returns Result.success(
            KiwoomThemeStockResponse(themeStocks = emptyList()) to
                PageHeaders(contYn = "N", nextKey = "", apiId = "ka90002")
        )

        val emissions = repository.updateAll(validConfig, ThemeExchange.KRX).toList()

        val last = emissions.last()
        assertTrue(last is ThemeDataProgress.Success)
        assertEquals(2, (last as ThemeDataProgress.Success).themeCount)

        // ka90001은 2회 호출 (페이지 2장)
        coVerify(exactly = 2) {
            kiwoomApiClient.callWithHeaders<Pair<KiwoomThemeListResponse, PageHeaders>>(
                apiId = "ka90001",
                url = any(), body = any(), config = any(),
                extraHeaders = any(), parser = any(),
            )
        }

        // 첫 페이지는 extraHeaders 비어 있고, 두 번째 페이지는 cont-yn=Y + next-key echo
        coVerifyOrder {
            kiwoomApiClient.callWithHeaders<Pair<KiwoomThemeListResponse, PageHeaders>>(
                apiId = "ka90001", url = any(), body = any(), config = any(),
                extraHeaders = match { it.isEmpty() }, parser = any(),
            )
            kiwoomApiClient.callWithHeaders<Pair<KiwoomThemeListResponse, PageHeaders>>(
                apiId = "ka90001", url = any(), body = any(), config = any(),
                extraHeaders = match { it["cont-yn"] == "Y" && it["next-key"] == "page2-key" },
                parser = any(),
            )
        }
    }

    // ============================================================================
    // updateAll - 부분 실패 격리
    // ============================================================================

    @Test
    fun `updateAll - 한 테마의 ka90002 실패는 다른 테마를 막지 않는다`() = runTest {
        val themes = listOf(
            KiwoomThemeGroupItem(themeCode = "TBAD", themeName = "FAIL"),
            KiwoomThemeGroupItem(themeCode = "TGOOD", themeName = "OK"),
        )

        coEvery {
            kiwoomApiClient.callWithHeaders<Pair<KiwoomThemeListResponse, PageHeaders>>(
                apiId = "ka90001",
                url = any(), body = any(), config = any(),
                extraHeaders = any(), parser = any(),
            )
        } returns Result.success(
            KiwoomThemeListResponse(themeGroups = themes) to
                PageHeaders(contYn = "N", nextKey = "", apiId = "ka90001")
        )

        // TBAD는 실패, TGOOD은 성공
        coEvery {
            kiwoomApiClient.callWithHeaders<Pair<KiwoomThemeStockResponse, PageHeaders>>(
                apiId = "ka90002",
                url = any(),
                body = match { it["thema_grp_cd"] == "TBAD" },
                config = any(), extraHeaders = any(), parser = any(),
            )
        } returns Result.failure(RuntimeException("forced failure"))

        coEvery {
            kiwoomApiClient.callWithHeaders<Pair<KiwoomThemeStockResponse, PageHeaders>>(
                apiId = "ka90002",
                url = any(),
                body = match { it["thema_grp_cd"] == "TGOOD" },
                config = any(), extraHeaders = any(), parser = any(),
            )
        } returns Result.success(
            KiwoomThemeStockResponse(
                themeStocks = listOf(
                    KiwoomThemeStockItem(stockCode = "005930", stockName = "삼전")
                )
            ) to PageHeaders(contYn = "N", nextKey = "", apiId = "ka90002")
        )

        val emissions = repository.updateAll(validConfig, ThemeExchange.KRX).toList()

        // 부분 실패임에도 최종은 Success
        val last = emissions.last()
        assertTrue("부분 실패 시에도 Success로 마감 — actual=$last", last is ThemeDataProgress.Success)
        last as ThemeDataProgress.Success
        assertEquals(2, last.themeCount)
        assertEquals(1, last.stockCount) // TGOOD만 1건

        // TBAD는 replaceForTheme 호출 안 됨
        coVerify(exactly = 0) { themeStockDao.replaceForTheme("TBAD", any()) }
        // TGOOD는 정상 호출
        coVerify(exactly = 1) { themeStockDao.replaceForTheme("TGOOD", any()) }
    }

    // ============================================================================
    // updateAll - ka90001 전체 실패
    // ============================================================================

    @Test
    fun `updateAll - ka90001 실패는 Error emit + replaceAll 미호출`() = runTest {
        coEvery {
            kiwoomApiClient.callWithHeaders<Pair<KiwoomThemeListResponse, PageHeaders>>(
                apiId = "ka90001",
                url = any(), body = any(), config = any(),
                extraHeaders = any(), parser = any(),
            )
        } returns Result.failure(RuntimeException("network down"))

        val emissions = repository.updateAll(validConfig, ThemeExchange.KRX).toList()
        val last = emissions.last()
        assertTrue(last is ThemeDataProgress.Error)
        assertTrue((last as ThemeDataProgress.Error).message.contains("network down"))

        coVerify(exactly = 0) { themeGroupDao.replaceAll(any()) }
        coVerify(exactly = 0) { themeStockDao.replaceForTheme(any(), any()) }
    }

    // ============================================================================
    // updateAll - 빈 응답
    // ============================================================================

    @Test
    fun `updateAll - ka90001이 빈 리스트면 Error로 마감`() = runTest {
        coEvery {
            kiwoomApiClient.callWithHeaders<Pair<KiwoomThemeListResponse, PageHeaders>>(
                apiId = "ka90001",
                url = any(), body = any(), config = any(),
                extraHeaders = any(), parser = any(),
            )
        } returns Result.success(
            KiwoomThemeListResponse(themeGroups = emptyList()) to
                PageHeaders(contYn = "N", nextKey = "", apiId = "ka90001")
        )

        val emissions = repository.updateAll(validConfig, ThemeExchange.KRX).toList()
        assertTrue(emissions.last() is ThemeDataProgress.Error)
        coVerify(exactly = 0) { themeGroupDao.replaceAll(any()) }
    }

    // ============================================================================
    // observeThemes (DAO 매핑)
    // ============================================================================

    @Test
    fun `observeThemes는 DAO Flow를 도메인 모델로 매핑한다`() = runTest {
        val entity = ThemeGroupEntity(
            themeCode = "T001",
            themeName = "AI",
            stockCount = 10,
            fluRate = 1.5,
            periodReturnRate = 12.3,
            riseCount = 7,
            fallCount = 2,
            mainStocks = "삼성전자,네이버",
            lastUpdated = 1000L,
        )
        coEvery { themeGroupDao.observeAll() } returns flowOf(listOf(entity))

        repository.observeThemes().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("T001", items[0].themeCode)
            assertEquals("AI", items[0].themeName)
            assertEquals(12.3, items[0].periodReturnRate, 1e-6)
            awaitComplete()
        }
    }

    @Test
    fun `observeThemes(query, sort) - TOP_RETURN 모드는 periodReturnRate 내림차순`() = runTest {
        val a = entity("T001", "A", periodReturnRate = 10.0)
        val b = entity("T002", "B", periodReturnRate = 30.0)
        val c = entity("T003", "C", periodReturnRate = 20.0)
        coEvery { themeGroupDao.observeAll() } returns flowOf(listOf(a, b, c))

        repository.observeThemes(query = "", sort = ThemeSortMode.TOP_RETURN).test {
            val items = awaitItem()
            assertEquals(listOf("T002", "T003", "T001"), items.map { it.themeCode })
            awaitComplete()
        }
    }

    @Test
    fun `observeThemes(query, sort) - NAME 모드는 themeName 오름차순`() = runTest {
        val a = entity("T001", "Charlie")
        val b = entity("T002", "Alpha")
        val c = entity("T003", "Bravo")
        coEvery { themeGroupDao.observeAll() } returns flowOf(listOf(a, b, c))

        repository.observeThemes(query = "", sort = ThemeSortMode.NAME).test {
            val items = awaitItem()
            assertEquals(listOf("Alpha", "Bravo", "Charlie"), items.map { it.themeName })
            awaitComplete()
        }
    }

    @Test
    fun `observeThemes(query, sort) - STOCK_COUNT 모드는 stockCount 내림차순`() = runTest {
        val a = entity("T001", "A", stockCount = 5)
        val b = entity("T002", "B", stockCount = 20)
        val c = entity("T003", "C", stockCount = 10)
        coEvery { themeGroupDao.observeAll() } returns flowOf(listOf(a, b, c))

        repository.observeThemes(query = "", sort = ThemeSortMode.STOCK_COUNT).test {
            val items = awaitItem()
            assertEquals(listOf(20, 10, 5), items.map { it.stockCount })
            awaitComplete()
        }
    }

    @Test
    fun `observeThemes(query, sort) - 검색어가 있으면 searchByName DAO 호출`() = runTest {
        coEvery { themeGroupDao.searchByName("AI") } returns flowOf(
            listOf(entity("T001", "AI"))
        )

        repository.observeThemes(query = " AI ", sort = ThemeSortMode.NAME).test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("AI", items[0].themeName)
            awaitComplete()
        }
        coVerify { themeGroupDao.searchByName("AI") }
    }

    // ============================================================================
    // observeThemeStocks
    // ============================================================================

    @Test
    fun `observeThemeStocks는 DAO Flow를 도메인 모델로 매핑한다`() = runTest {
        val stock = ThemeStockEntity(
            themeCode = "T001",
            stockCode = "005930",
            stockName = "삼성전자",
            currentPrice = 75000.0,
            priorDiff = 500.0,
            fluRate = 0.67,
            volume = 1234567L,
            periodReturnRate = 5.5,
            lastUpdated = 1000L,
        )
        coEvery { themeStockDao.observeByTheme("T001") } returns flowOf(listOf(stock))

        repository.observeThemeStocks("T001").test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("005930", items[0].stockCode)
            assertEquals(75000.0, items[0].currentPrice, 1e-6)
            awaitComplete()
        }
    }

    // ============================================================================
    // 위임 메서드
    // ============================================================================

    @Test
    fun `lastUpdatedAt - DAO에 위임`() = runTest {
        coEvery { themeGroupDao.lastUpdatedAt() } returns 999L
        assertEquals(999L, repository.lastUpdatedAt())
    }

    @Test
    fun `themeCount - DAO에 위임`() = runTest {
        coEvery { themeGroupDao.count() } returns 42
        assertEquals(42, repository.themeCount())
    }

    // ============================================================================
    // 정상 흐름 — DAO replace 순서
    // ============================================================================

    @Test
    fun `updateAll - replaceAll이 replaceForTheme보다 먼저 호출된다`() = runTest {
        coEvery {
            kiwoomApiClient.callWithHeaders<Pair<KiwoomThemeListResponse, PageHeaders>>(
                apiId = "ka90001",
                url = any(), body = any(), config = any(),
                extraHeaders = any(), parser = any(),
            )
        } returns Result.success(
            KiwoomThemeListResponse(
                themeGroups = listOf(KiwoomThemeGroupItem(themeCode = "T001", themeName = "A"))
            ) to PageHeaders("N", "", "ka90001")
        )

        coEvery {
            kiwoomApiClient.callWithHeaders<Pair<KiwoomThemeStockResponse, PageHeaders>>(
                apiId = "ka90002",
                url = any(), body = any(), config = any(),
                extraHeaders = any(), parser = any(),
            )
        } returns Result.success(
            KiwoomThemeStockResponse(themeStocks = emptyList()) to
                PageHeaders("N", "", "ka90002")
        )

        repository.updateAll(validConfig, ThemeExchange.KRX).toList()

        coVerifyOrder {
            themeGroupDao.replaceAll(any())
            themeStockDao.replaceForTheme("T001", any())
        }
    }

    @Test
    fun `updateAll - 거래소 코드가 stex_tp 파라미터로 전달된다`() = runTest {
        val bodySlot = slot<Map<String, String>>()

        coEvery {
            kiwoomApiClient.callWithHeaders<Pair<KiwoomThemeListResponse, PageHeaders>>(
                apiId = "ka90001",
                url = any(),
                body = capture(bodySlot),
                config = any(),
                extraHeaders = any(),
                parser = any(),
            )
        } returns Result.success(
            KiwoomThemeListResponse(themeGroups = emptyList()) to
                PageHeaders("N", "", "ka90001")
        )

        repository.updateAll(validConfig, ThemeExchange.NXT).toList()

        assertEquals("2", bodySlot.captured["stex_tp"])
    }

    // ============================================================================
    // helpers
    // ============================================================================

    private fun entity(
        code: String,
        name: String,
        stockCount: Int = 0,
        fluRate: Double = 0.0,
        periodReturnRate: Double = 0.0,
    ) = ThemeGroupEntity(
        themeCode = code,
        themeName = name,
        stockCount = stockCount,
        fluRate = fluRate,
        periodReturnRate = periodReturnRate,
        riseCount = 0,
        fallCount = 0,
        mainStocks = "",
        lastUpdated = 0L,
    )
}
