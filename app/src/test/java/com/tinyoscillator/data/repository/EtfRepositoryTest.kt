package com.tinyoscillator.data.repository

import com.krxkt.model.EtfInfo
import com.krxkt.model.EtfPortfolio
import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.core.database.dao.EtfDao
import com.tinyoscillator.core.database.entity.EtfEntity
import com.tinyoscillator.core.database.entity.EtfHoldingEntity
import com.tinyoscillator.domain.model.AmountRankingRow
import com.tinyoscillator.domain.model.ChangeType
import com.tinyoscillator.domain.model.EtfDataProgress
import com.tinyoscillator.presentation.settings.EtfKeywordFilter
import com.tinyoscillator.presentation.settings.KrxCredentials
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EtfRepositoryTest {

    private lateinit var etfDao: EtfDao
    private lateinit var krxApiClient: KrxApiClient
    private lateinit var repository: EtfRepository

    private val testDispatcher = StandardTestDispatcher()

    private val testCreds = KrxCredentials(id = "testId", password = "testPw")
    private val emptyKeywords = EtfKeywordFilter(includeKeywords = emptyList(), excludeKeywords = emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        etfDao = mockk(relaxed = true)
        krxApiClient = mockk(relaxed = true)
        repository = EtfRepository(etfDao, krxApiClient)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -- Helpers --

    private fun createHolding(
        etfTicker: String,
        stockTicker: String,
        date: String,
        stockName: String = "종목$stockTicker",
        weight: Double? = 5.0,
        shares: Long = 100,
        amount: Long = 1_000_000
    ) = EtfHoldingEntity(
        etfTicker = etfTicker,
        stockTicker = stockTicker,
        date = date,
        stockName = stockName,
        weight = weight,
        shares = shares,
        amount = amount
    )

    private fun createEtfEntity(
        ticker: String,
        name: String,
        isinCode: String = "KR7${ticker}007"
    ) = EtfEntity(
        ticker = ticker,
        name = name,
        isinCode = isinCode
    )

    // ==========================================================
    // computeStockChanges 테스트
    // ==========================================================

    @Test
    fun `computeStockChanges - 신규 종목은 NEW로 분류된다`() = runTest {
        val date1 = "20260301"
        val date2 = "20260302"

        coEvery { etfDao.getAllHoldingsForDate(date1) } returns emptyList()
        coEvery { etfDao.getAllHoldingsForDate(date2) } returns listOf(
            createHolding("ETF001", "005930", date2, "삼성전자", 10.0, 100, 5_000_000)
        )
        coEvery { etfDao.getEtf("ETF001") } returns createEtfEntity("ETF001", "테스트ETF")

        val changes = repository.computeStockChanges(date1, date2)

        assertEquals(1, changes.size)
        assertEquals(ChangeType.NEW, changes[0].changeType)
        assertEquals("005930", changes[0].stockTicker)
        assertNull(changes[0].previousWeight)
        assertEquals(10.0, changes[0].currentWeight!!, 0.001)
        assertEquals(0L, changes[0].previousAmount)
        assertEquals(5_000_000L, changes[0].currentAmount)
    }

    @Test
    fun `computeStockChanges - 제거된 종목은 REMOVED로 분류된다`() = runTest {
        val date1 = "20260301"
        val date2 = "20260302"

        coEvery { etfDao.getAllHoldingsForDate(date1) } returns listOf(
            createHolding("ETF001", "005930", date1, "삼성전자", 8.0, 200, 3_000_000)
        )
        coEvery { etfDao.getAllHoldingsForDate(date2) } returns emptyList()
        coEvery { etfDao.getEtf("ETF001") } returns createEtfEntity("ETF001", "테스트ETF")

        val changes = repository.computeStockChanges(date1, date2)

        assertEquals(1, changes.size)
        assertEquals(ChangeType.REMOVED, changes[0].changeType)
        assertEquals(8.0, changes[0].previousWeight!!, 0.001)
        assertNull(changes[0].currentWeight)
        assertEquals(3_000_000L, changes[0].previousAmount)
        assertEquals(0L, changes[0].currentAmount)
    }

    @Test
    fun `computeStockChanges - 비중 증가 종목은 INCREASED로 분류된다`() = runTest {
        val date1 = "20260301"
        val date2 = "20260302"

        coEvery { etfDao.getAllHoldingsForDate(date1) } returns listOf(
            createHolding("ETF001", "005930", date1, "삼성전자", 5.0, 100, 1_000_000)
        )
        coEvery { etfDao.getAllHoldingsForDate(date2) } returns listOf(
            createHolding("ETF001", "005930", date2, "삼성전자", 8.0, 150, 1_500_000)
        )
        coEvery { etfDao.getEtf("ETF001") } returns createEtfEntity("ETF001", "테스트ETF")

        val changes = repository.computeStockChanges(date1, date2)

        assertEquals(1, changes.size)
        assertEquals(ChangeType.INCREASED, changes[0].changeType)
        assertEquals(5.0, changes[0].previousWeight!!, 0.001)
        assertEquals(8.0, changes[0].currentWeight!!, 0.001)
    }

    @Test
    fun `computeStockChanges - 비중 감소 종목은 DECREASED로 분류된다`() = runTest {
        val date1 = "20260301"
        val date2 = "20260302"

        coEvery { etfDao.getAllHoldingsForDate(date1) } returns listOf(
            createHolding("ETF001", "005930", date1, "삼성전자", 8.0, 200, 2_000_000)
        )
        coEvery { etfDao.getAllHoldingsForDate(date2) } returns listOf(
            createHolding("ETF001", "005930", date2, "삼성전자", 4.0, 100, 1_000_000)
        )
        coEvery { etfDao.getEtf("ETF001") } returns createEtfEntity("ETF001", "테스트ETF")

        val changes = repository.computeStockChanges(date1, date2)

        assertEquals(1, changes.size)
        assertEquals(ChangeType.DECREASED, changes[0].changeType)
        assertEquals(8.0, changes[0].previousWeight!!, 0.001)
        assertEquals(4.0, changes[0].currentWeight!!, 0.001)
    }

    @Test
    fun `computeStockChanges - 비중 변화가 0_001 이내이면 UNCHANGED로 제외된다`() = runTest {
        val date1 = "20260301"
        val date2 = "20260302"

        coEvery { etfDao.getAllHoldingsForDate(date1) } returns listOf(
            createHolding("ETF001", "005930", date1, "삼성전자", 5.0, 100, 1_000_000)
        )
        coEvery { etfDao.getAllHoldingsForDate(date2) } returns listOf(
            createHolding("ETF001", "005930", date2, "삼성전자", 5.0005, 100, 1_000_000)
        )
        coEvery { etfDao.getEtf("ETF001") } returns createEtfEntity("ETF001", "테스트ETF")

        val changes = repository.computeStockChanges(date1, date2)

        assertTrue("비중 변화가 0.001 이내이면 결과에 포함되지 않아야 한다", changes.isEmpty())
    }

    @Test
    fun `computeStockChanges - excludedTickers가 있으면 제외 쿼리를 사용한다`() = runTest {
        val date1 = "20260301"
        val date2 = "20260302"
        val excluded = listOf("EXCLUDED01")

        coEvery { etfDao.getAllHoldingsForDateExcluding(date1, excluded) } returns emptyList()
        coEvery { etfDao.getAllHoldingsForDateExcluding(date2, excluded) } returns emptyList()

        repository.computeStockChanges(date1, date2, excluded)

        coVerify { etfDao.getAllHoldingsForDateExcluding(date1, excluded) }
        coVerify { etfDao.getAllHoldingsForDateExcluding(date2, excluded) }
        coVerify(exactly = 0) { etfDao.getAllHoldingsForDate(any()) }
    }

    @Test
    fun `computeStockChanges - 여러 ETF에 같은 종목이 있을 때 각각 독립적으로 변화를 추적한다`() = runTest {
        val date1 = "20260301"
        val date2 = "20260302"

        coEvery { etfDao.getAllHoldingsForDate(date1) } returns listOf(
            createHolding("ETF001", "005930", date1, "삼성전자", 5.0),
            createHolding("ETF002", "005930", date1, "삼성전자", 3.0)
        )
        coEvery { etfDao.getAllHoldingsForDate(date2) } returns listOf(
            createHolding("ETF001", "005930", date2, "삼성전자", 8.0),  // INCREASED
            // ETF002에서 005930 제거 → REMOVED
        )
        coEvery { etfDao.getEtf("ETF001") } returns createEtfEntity("ETF001", "테스트ETF1")
        coEvery { etfDao.getEtf("ETF002") } returns createEtfEntity("ETF002", "테스트ETF2")

        val changes = repository.computeStockChanges(date1, date2)

        assertEquals(2, changes.size)
        val etf1Change = changes.find { it.etfTicker == "ETF001" }
        val etf2Change = changes.find { it.etfTicker == "ETF002" }
        assertNotNull(etf1Change)
        assertNotNull(etf2Change)
        assertEquals(ChangeType.INCREASED, etf1Change!!.changeType)
        assertEquals(ChangeType.REMOVED, etf2Change!!.changeType)
    }

    @Test
    fun `computeStockChanges - null weight는 0_0으로 처리된다`() = runTest {
        val date1 = "20260301"
        val date2 = "20260302"

        coEvery { etfDao.getAllHoldingsForDate(date1) } returns listOf(
            createHolding("ETF001", "005930", date1, "삼성전자", weight = null)
        )
        coEvery { etfDao.getAllHoldingsForDate(date2) } returns listOf(
            createHolding("ETF001", "005930", date2, "삼성전자", weight = 3.0)
        )
        coEvery { etfDao.getEtf("ETF001") } returns createEtfEntity("ETF001", "테스트ETF")

        val changes = repository.computeStockChanges(date1, date2)

        assertEquals(1, changes.size)
        assertEquals(ChangeType.INCREASED, changes[0].changeType)
    }

    // ==========================================================
    // getExcludedTickers 테스트
    // ==========================================================

    @Test
    fun `getExcludedTickers - 제외 키워드가 비어있으면 빈 리스트를 반환한다`() = runTest {
        val result = repository.getExcludedTickers(emptyList())

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { etfDao.getAllEtfsList() }
    }

    @Test
    fun `getExcludedTickers - 키워드에 매칭되는 ETF 티커만 반환한다`() = runTest {
        coEvery { etfDao.getAllEtfsList() } returns listOf(
            createEtfEntity("ETF001", "KODEX 200 액티브"),
            createEtfEntity("ETF002", "TIGER 반도체"),
            createEtfEntity("ETF003", "KODEX 채권 액티브"),
            createEtfEntity("ETF004", "ARIRANG 미국나스닥100")
        )

        val result = repository.getExcludedTickers(listOf("채권", "미국"))

        assertEquals(2, result.size)
        assertTrue(result.contains("ETF003"))
        assertTrue(result.contains("ETF004"))
        assertFalse(result.contains("ETF001"))
    }

    @Test
    fun `getExcludedTickers - 매칭되는 ETF가 없으면 빈 리스트를 반환한다`() = runTest {
        coEvery { etfDao.getAllEtfsList() } returns listOf(
            createEtfEntity("ETF001", "KODEX 200 액티브"),
            createEtfEntity("ETF002", "TIGER 반도체")
        )

        val result = repository.getExcludedTickers(listOf("해외", "원자재"))

        assertTrue(result.isEmpty())
    }

    // ==========================================================
    // getEnrichedAmountRanking 테스트
    // ==========================================================

    @Test
    fun `getEnrichedAmountRanking - 랭킹과 변화 데이터를 병합하여 반환한다`() = runTest {
        val date = "20260305"
        val compDate = "20260304"

        coEvery { etfDao.getAmountRanking(date) } returns listOf(
            AmountRankingRow("005930", "삼성전자", 500_000_000_000L, 10),
            AmountRankingRow("000660", "SK하이닉스", 300_000_000_000L, 7)
        )

        // 삼성전자: ETF001에서 NEW, ETF002에서 INCREASED
        coEvery { etfDao.getAllHoldingsForDate(compDate) } returns listOf(
            createHolding("ETF002", "005930", compDate, "삼성전자", 3.0),
            createHolding("ETF001", "000660", compDate, "SK하이닉스", 5.0)
        )
        coEvery { etfDao.getAllHoldingsForDate(date) } returns listOf(
            createHolding("ETF001", "005930", date, "삼성전자", 7.0),
            createHolding("ETF002", "005930", date, "삼성전자", 6.0),
            createHolding("ETF001", "000660", date, "SK하이닉스", 5.0005)  // unchanged
        )
        coEvery { etfDao.getEtf("ETF001") } returns createEtfEntity("ETF001", "테스트ETF1")
        coEvery { etfDao.getEtf("ETF002") } returns createEtfEntity("ETF002", "테스트ETF2")

        val result = repository.getEnrichedAmountRanking(date, compDate)

        assertEquals(2, result.size)

        // 삼성전자: rank=1
        val samsung = result[0]
        assertEquals(1, samsung.rank)
        assertEquals("005930", samsung.stockTicker)
        assertEquals("삼성전자", samsung.stockName)
        assertEquals(500_000_000_000L / 100_000_000.0, samsung.totalAmountBillion, 0.001)
        assertEquals(10, samsung.etfCount)
        assertEquals(1, samsung.newCount)       // ETF001에서 NEW
        assertEquals(1, samsung.increasedCount) // ETF002에서 INCREASED

        // SK하이닉스: rank=2, 변화 없음
        val sk = result[1]
        assertEquals(2, sk.rank)
        assertEquals(0, sk.newCount)
        assertEquals(0, sk.increasedCount)
        assertEquals(0, sk.decreasedCount)
        assertEquals(0, sk.removedCount)
    }

    @Test
    fun `getEnrichedAmountRanking - comparisonDate가 null이면 변화 카운트가 모두 0이다`() = runTest {
        val date = "20260305"

        coEvery { etfDao.getAmountRanking(date) } returns listOf(
            AmountRankingRow("005930", "삼성전자", 500_000_000_000L, 10)
        )

        val result = repository.getEnrichedAmountRanking(date, null)

        assertEquals(1, result.size)
        assertEquals(0, result[0].newCount)
        assertEquals(0, result[0].increasedCount)
        assertEquals(0, result[0].decreasedCount)
        assertEquals(0, result[0].removedCount)
    }

    @Test
    fun `getEnrichedAmountRanking - totalAmount를 억 단위로 변환한다`() = runTest {
        val date = "20260305"

        coEvery { etfDao.getAmountRanking(date) } returns listOf(
            AmountRankingRow("005930", "삼성전자", 100_000_000L, 1)  // 정확히 1억
        )

        val result = repository.getEnrichedAmountRanking(date, null)

        assertEquals(1.0, result[0].totalAmountBillion, 0.001)
    }

    // ==========================================================
    // updateData 테스트
    // ==========================================================

    @Test
    fun `updateData - 로그인 실패 시 Error를 emit한다`() = runTest {
        coEvery { krxApiClient.login(any(), any()) } returns false

        val emissions = repository.updateData(testCreds, emptyKeywords).toList()

        assertTrue(emissions.any { it is EtfDataProgress.Loading })
        val error = emissions.last() as EtfDataProgress.Error
        assertTrue(error.message.contains("로그인 실패"))
    }

    @Test
    fun `updateData - 정상 흐름은 Loading에서 Success로 진행한다`() = runTest {
        coEvery { krxApiClient.login(any(), any()) } returns true
        coEvery { krxApiClient.getEtfTickerList(any()) } returns listOf(
            EtfInfo(
                ticker = "ETF001",
                name = "KODEX 200 액티브",
                isinCode = "KR7ETF001007",
                indexName = "코스피200",
                targetIndexName = null,
                indexProvider = null,
                cu = null,
                totalFee = 0.15
            )
        )
        coEvery { etfDao.getAllEtfsList() } returns emptyList()
        coEvery { etfDao.getLatestDate() } returns null
        coEvery { krxApiClient.getPortfolio(any(), any()) } returns listOf(
            EtfPortfolio(
                ticker = "005930",
                name = "삼성전자",
                shares = 100,
                valuationAmount = 5_000_000,
                amount = 5_000_000,
                weight = 10.0
            )
        )

        val emissions = repository.updateData(testCreds, emptyKeywords, daysBack = 7).toList()

        assertTrue(emissions.first() is EtfDataProgress.Loading)
        val success = emissions.last()
        assertTrue("마지막 emission은 Success여야 한다: $success", success is EtfDataProgress.Success)

        coVerify { etfDao.insertEtfs(any()) }
        coVerify { etfDao.insertHoldings(any()) }
        coVerify { krxApiClient.close() }
    }

    @Test
    fun `updateData - 예외 발생 시 Error를 emit하고 close를 호출한다`() = runTest {
        coEvery { krxApiClient.login(any(), any()) } returns true
        coEvery { krxApiClient.getEtfTickerList(any()) } throws RuntimeException("네트워크 오류")

        val emissions = repository.updateData(testCreds, emptyKeywords).toList()

        val error = emissions.last() as EtfDataProgress.Error
        assertTrue(error.message.contains("네트워크 오류"))
        coVerify { krxApiClient.close() }
    }

    @Test
    fun `updateData - 새 수집 대상이 없으면 즉시 Success를 반환한다`() = runTest {
        coEvery { krxApiClient.login(any(), any()) } returns true
        coEvery { krxApiClient.getEtfTickerList(any()) } returns listOf(
            EtfInfo("ETF001", "KODEX 200 액티브", "KR7ETF001007", null, null, null, null, null)
        )
        // 이미 존재하고, latestDate가 모든 영업일을 커버
        coEvery { etfDao.getAllEtfsList() } returns listOf(
            createEtfEntity("ETF001", "KODEX 200 액티브")
        )
        // latestDate를 미래로 설정하여 workItems가 비게 함
        coEvery { etfDao.getLatestDate() } returns "20990101"

        val emissions = repository.updateData(testCreds, emptyKeywords, daysBack = 7).toList()

        val success = emissions.last()
        assertTrue("workItems가 비면 Success를 반환해야 한다", success is EtfDataProgress.Success)
        assertEquals(0, (success as EtfDataProgress.Success).holdingCount)
    }

    @Test
    fun `updateData - 키워드 필터가 ETF를 올바르게 필터링한다`() = runTest {
        coEvery { krxApiClient.login(any(), any()) } returns true
        coEvery { krxApiClient.getEtfTickerList(any()) } returns listOf(
            EtfInfo("ETF001", "KODEX 반도체 액티브", "KR7001", null, null, null, null, null),
            EtfInfo("ETF002", "TIGER 채권 액티브", "KR7002", null, null, null, null, null),
            EtfInfo("ETF003", "KODEX AI 액티브", "KR7003", null, null, null, null, null)
        )
        coEvery { etfDao.getAllEtfsList() } returns emptyList()
        coEvery { etfDao.getLatestDate() } returns null
        coEvery { krxApiClient.getPortfolio(any(), any()) } returns emptyList()

        val keywords = EtfKeywordFilter(
            includeKeywords = listOf("반도체"),
            excludeKeywords = listOf("채권")
        )

        val emissions = repository.updateData(testCreds, keywords, daysBack = 7).toList()

        // ETF001만 통과 (액티브 + 채권 아님 + 반도체 포함)
        val insertSlot = slot<List<EtfEntity>>()
        coVerify { etfDao.insertEtfs(capture(insertSlot)) }
        assertEquals(1, insertSlot.captured.size)
        assertEquals("ETF001", insertSlot.captured[0].ticker)
    }

    // ==========================================================
    // 엣지 케이스 테스트
    // ==========================================================

    @Test
    fun `computeStockChanges - 양쪽 날짜 모두 데이터가 비어있으면 빈 리스트를 반환한다`() = runTest {
        coEvery { etfDao.getAllHoldingsForDate(any()) } returns emptyList()

        val changes = repository.computeStockChanges("20260301", "20260302")

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `getEnrichedAmountRanking - 랭킹이 비어있으면 빈 리스트를 반환한다`() = runTest {
        coEvery { etfDao.getAmountRanking(any()) } returns emptyList()

        val result = repository.getEnrichedAmountRanking("20260305", "20260304")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `computeStockChanges - ETF 이름이 DB에 없으면 티커를 이름으로 사용한다`() = runTest {
        val date1 = "20260301"
        val date2 = "20260302"

        coEvery { etfDao.getAllHoldingsForDate(date1) } returns emptyList()
        coEvery { etfDao.getAllHoldingsForDate(date2) } returns listOf(
            createHolding("UNKNOWN_ETF", "005930", date2, "삼성전자", 5.0)
        )
        coEvery { etfDao.getEtf("UNKNOWN_ETF") } returns null

        val changes = repository.computeStockChanges(date1, date2)

        assertEquals(1, changes.size)
        assertEquals("UNKNOWN_ETF", changes[0].etfName)
    }

    @Test
    fun `getAmountRanking - excludedTickers가 비어있으면 일반 쿼리를 사용한다`() = runTest {
        coEvery { etfDao.getAmountRanking("20260305") } returns listOf(
            AmountRankingRow("005930", "삼성전자", 100L, 1)
        )

        repository.getAmountRanking("20260305")

        coVerify { etfDao.getAmountRanking("20260305") }
        coVerify(exactly = 0) { etfDao.getAmountRankingExcluding(any(), any()) }
    }

    @Test
    fun `getAmountRanking - excludedTickers가 있으면 제외 쿼리를 사용한다`() = runTest {
        val excluded = listOf("ETF001")
        coEvery { etfDao.getAmountRankingExcluding("20260305", excluded) } returns emptyList()

        repository.getAmountRanking("20260305", excluded)

        coVerify { etfDao.getAmountRankingExcluding("20260305", excluded) }
        coVerify(exactly = 0) { etfDao.getAmountRanking(any()) }
    }

    @Test
    fun `getAllEtfs는 DAO의 Flow를 그대로 반환한다`() = runTest {
        val etfs = listOf(createEtfEntity("ETF001", "테스트ETF"))
        coEvery { etfDao.getAllEtfs() } returns flowOf(etfs)

        val flow = repository.getAllEtfs()
        val result = flow.toList()

        assertEquals(1, result.size)
        assertEquals("ETF001", result[0][0].ticker)
    }

    @Test
    fun `getHoldings는 DAO에 위임한다`() = runTest {
        val holdings = listOf(createHolding("ETF001", "005930", "20260305"))
        coEvery { etfDao.getHoldings("ETF001", "20260305") } returns holdings

        val result = repository.getHoldings("ETF001", "20260305")

        assertEquals(1, result.size)
        assertEquals("005930", result[0].stockTicker)
    }

    @Test
    fun `updateData - 액티브가 아닌 ETF는 필터링된다`() = runTest {
        coEvery { krxApiClient.login(any(), any()) } returns true
        coEvery { krxApiClient.getEtfTickerList(any()) } returns listOf(
            EtfInfo("ETF001", "KODEX 200 액티브", "KR7001", null, null, null, null, null),
            EtfInfo("ETF002", "KODEX 200", "KR7002", null, null, null, null, null) // 액티브 아님
        )
        coEvery { etfDao.getAllEtfsList() } returns emptyList()
        coEvery { etfDao.getLatestDate() } returns null
        coEvery { krxApiClient.getPortfolio(any(), any()) } returns emptyList()

        repository.updateData(testCreds, emptyKeywords, daysBack = 7).toList()

        val insertSlot = slot<List<EtfEntity>>()
        coVerify { etfDao.insertEtfs(capture(insertSlot)) }
        assertEquals(1, insertSlot.captured.size)
        assertEquals("ETF001", insertSlot.captured[0].ticker)
    }

    @Test
    fun `updateData - 개별 포트폴리오 수집 실패는 전체 프로세스를 중단하지 않는다`() = runTest {
        coEvery { krxApiClient.login(any(), any()) } returns true
        coEvery { krxApiClient.getEtfTickerList(any()) } returns listOf(
            EtfInfo("ETF001", "KODEX 200 액티브", "KR7001", null, null, null, null, null)
        )
        coEvery { etfDao.getAllEtfsList() } returns emptyList()
        coEvery { etfDao.getLatestDate() } returns null
        coEvery { krxApiClient.getPortfolio(any(), any()) } throws RuntimeException("API 오류")

        val emissions = repository.updateData(testCreds, emptyKeywords, daysBack = 7).toList()

        // 개별 실패에도 불구하고 Success로 완료
        val last = emissions.last()
        assertTrue("개별 포트폴리오 오류는 전체를 중단하지 않아야 한다", last is EtfDataProgress.Success)
        assertEquals(0, (last as EtfDataProgress.Success).holdingCount)
    }
}
