package com.tinyoscillator.data.repository

import com.tinyoscillator.core.api.InvestmentMode
import com.tinyoscillator.core.api.KiwoomApiClient
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.core.database.entity.StockMasterEntity
import com.tinyoscillator.data.dto.StockListItem
import com.tinyoscillator.data.dto.StockListResponse
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StockMasterRepositoryTest {

    private lateinit var stockMasterDao: StockMasterDao
    private lateinit var apiClient: KiwoomApiClient
    private lateinit var json: Json
    private lateinit var repository: StockMasterRepository

    private val validConfig = KiwoomApiKeyConfig(
        appKey = "testKey",
        secretKey = "testSecret",
        investmentMode = InvestmentMode.MOCK
    )

    private val invalidConfig = KiwoomApiKeyConfig(appKey = "", secretKey = "")

    @Before
    fun setup() {
        stockMasterDao = mockk(relaxed = true)
        apiClient = mockk(relaxed = true)
        json = Json { ignoreUnknownKeys = true }
        repository = StockMasterRepository(stockMasterDao, apiClient, json)
    }

    // ==========================================================
    // populateIfEmpty 테스트
    // ==========================================================

    @Test
    fun `populateIfEmpty - DB에 데이터가 있으면 API를 호출하지 않는다`() = runTest {
        coEvery { stockMasterDao.getCount() } returns 100

        repository.populateIfEmpty(validConfig)

        coVerify(exactly = 0) { apiClient.call<Any>(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `populateIfEmpty - API 키가 유효하지 않으면 API를 호출하지 않는다`() = runTest {
        coEvery { stockMasterDao.getCount() } returns 0

        repository.populateIfEmpty(invalidConfig)

        coVerify(exactly = 0) { apiClient.call<Any>(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `populateIfEmpty - DB가 비어있고 API 키가 유효하면 KOSPI와 KOSDAQ API를 호출한다`() = runTest {
        coEvery { stockMasterDao.getCount() } returns 0
        coEvery { apiClient.call<StockListResponse>(any(), any(), any(), any(), any()) } returns
                Result.success(StockListResponse(stkList = listOf(
                    StockListItem(stkCd = "005930", stkNm = "삼성전자", mrktNm = "KOSPI")
                )))

        repository.populateIfEmpty(validConfig)

        coVerify(exactly = 2) { apiClient.call<StockListResponse>(any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { stockMasterDao.insertAll(any()) }
    }

    @Test
    fun `populateIfEmpty - API 응답에서 빈 ticker를 필터링한다`() = runTest {
        coEvery { stockMasterDao.getCount() } returns 0
        coEvery { apiClient.call<StockListResponse>(any(), any(), match { it["mrkt_tp"] == "0" }, any(), any()) } returns
                Result.success(StockListResponse(stkList = listOf(
                    StockListItem(stkCd = "", stkNm = "빈종목코드", mrktNm = "KOSPI"),
                    StockListItem(stkCd = null, stkNm = "null종목코드", mrktNm = "KOSPI"),
                    StockListItem(stkCd = "005930", stkNm = "삼성전자", mrktNm = "KOSPI")
                )))
        coEvery { apiClient.call<StockListResponse>(any(), any(), match { it["mrkt_tp"] == "10" }, any(), any()) } returns
                Result.success(StockListResponse(stkList = emptyList()))

        repository.populateIfEmpty(validConfig)

        val entitiesSlot = slot<List<StockMasterEntity>>()
        coVerify { stockMasterDao.insertAll(capture(entitiesSlot)) }
        assertEquals(1, entitiesSlot.captured.size)
        assertEquals("005930", entitiesSlot.captured[0].ticker)
    }

    @Test
    fun `populateIfEmpty - API 응답에서 빈 이름을 필터링한다`() = runTest {
        coEvery { stockMasterDao.getCount() } returns 0
        coEvery { apiClient.call<StockListResponse>(any(), any(), match { it["mrkt_tp"] == "0" }, any(), any()) } returns
                Result.success(StockListResponse(stkList = listOf(
                    StockListItem(stkCd = "000001", stkNm = "", mrktNm = "KOSPI"),
                    StockListItem(stkCd = "000002", stkNm = null, mrktNm = "KOSPI"),
                    StockListItem(stkCd = "005930", stkNm = "삼성전자", mrktNm = "KOSPI")
                )))
        coEvery { apiClient.call<StockListResponse>(any(), any(), match { it["mrkt_tp"] == "10" }, any(), any()) } returns
                Result.success(StockListResponse(stkList = emptyList()))

        repository.populateIfEmpty(validConfig)

        val entitiesSlot = slot<List<StockMasterEntity>>()
        coVerify { stockMasterDao.insertAll(capture(entitiesSlot)) }
        assertEquals(1, entitiesSlot.captured.size)
        assertEquals("삼성전자", entitiesSlot.captured[0].name)
    }

    @Test
    fun `populateIfEmpty - 양쪽 시장 모두 API 실패 시 예외를 전파하지 않는다`() = runTest {
        coEvery { stockMasterDao.getCount() } returns 0
        coEvery { apiClient.call<StockListResponse>(any(), any(), any(), any(), any()) } returns
                Result.failure(RuntimeException("API error"))

        // Should not throw
        repository.populateIfEmpty(validConfig)

        coVerify(exactly = 0) { stockMasterDao.insertAll(any()) }
    }

    @Test
    fun `populateIfEmpty - 양쪽 시장 모두 빈 stkList일 때 insertAll을 호출하지 않는다`() = runTest {
        coEvery { stockMasterDao.getCount() } returns 0
        coEvery { apiClient.call<StockListResponse>(any(), any(), any(), any(), any()) } returns
                Result.success(StockListResponse(stkList = emptyList()))

        repository.populateIfEmpty(validConfig)

        coVerify(exactly = 0) { stockMasterDao.insertAll(any()) }
    }

    @Test
    fun `populateIfEmpty - 양쪽 시장 모두 null stkList일 때 insertAll을 호출하지 않는다`() = runTest {
        coEvery { stockMasterDao.getCount() } returns 0
        coEvery { apiClient.call<StockListResponse>(any(), any(), any(), any(), any()) } returns
                Result.success(StockListResponse(stkList = null))

        repository.populateIfEmpty(validConfig)

        coVerify(exactly = 0) { stockMasterDao.insertAll(any()) }
    }

    @Test
    fun `populateIfEmpty - KOSPI와 KOSDAQ 종목이 합쳐져서 저장된다`() = runTest {
        coEvery { stockMasterDao.getCount() } returns 0
        coEvery { apiClient.call<StockListResponse>(any(), any(), match { it["mrkt_tp"] == "0" }, any(), any()) } returns
                Result.success(StockListResponse(stkList = listOf(
                    StockListItem(stkCd = "005930", stkNm = "삼성전자", mrktNm = "KOSPI")
                )))
        coEvery { apiClient.call<StockListResponse>(any(), any(), match { it["mrkt_tp"] == "10" }, any(), any()) } returns
                Result.success(StockListResponse(stkList = listOf(
                    StockListItem(stkCd = "035720", stkNm = "카카오", mrktNm = "KOSDAQ")
                )))

        repository.populateIfEmpty(validConfig)

        val entitiesSlot = slot<List<StockMasterEntity>>()
        coVerify { stockMasterDao.insertAll(capture(entitiesSlot)) }
        assertEquals(2, entitiesSlot.captured.size)
        val tickers = entitiesSlot.captured.map { it.ticker }.toSet()
        assertTrue(tickers.contains("005930"))
        assertTrue(tickers.contains("035720"))
    }

    @Test
    fun `populateIfEmpty - 한쪽 시장 실패 시 다른 시장 데이터는 저장된다`() = runTest {
        coEvery { stockMasterDao.getCount() } returns 0
        coEvery { apiClient.call<StockListResponse>(any(), any(), match { it["mrkt_tp"] == "0" }, any(), any()) } returns
                Result.success(StockListResponse(stkList = listOf(
                    StockListItem(stkCd = "005930", stkNm = "삼성전자", mrktNm = "KOSPI")
                )))
        coEvery { apiClient.call<StockListResponse>(any(), any(), match { it["mrkt_tp"] == "10" }, any(), any()) } returns
                Result.failure(RuntimeException("KOSDAQ API error"))

        repository.populateIfEmpty(validConfig)

        val entitiesSlot = slot<List<StockMasterEntity>>()
        coVerify { stockMasterDao.insertAll(capture(entitiesSlot)) }
        assertEquals(1, entitiesSlot.captured.size)
        assertEquals("005930", entitiesSlot.captured[0].ticker)
    }

    @Test
    fun `populateIfEmpty - 엔티티에 올바른 market과 lastUpdated가 설정된다`() = runTest {
        coEvery { stockMasterDao.getCount() } returns 0
        coEvery { apiClient.call<StockListResponse>(any(), any(), any(), any(), any()) } returns
                Result.success(StockListResponse(stkList = listOf(
                    StockListItem(stkCd = "005930", stkNm = "삼성전자", mrktNm = "KOSPI")
                )))

        repository.populateIfEmpty(validConfig)

        val entitiesSlot = slot<List<StockMasterEntity>>()
        coVerify { stockMasterDao.insertAll(capture(entitiesSlot)) }
        val entity = entitiesSlot.captured[0]
        assertEquals("KOSPI", entity.market)
        assertTrue(entity.lastUpdated > 0)
    }

    @Test
    fun `populateIfEmpty - null market은 빈 문자열로 대체된다`() = runTest {
        coEvery { stockMasterDao.getCount() } returns 0
        coEvery { apiClient.call<StockListResponse>(any(), any(), any(), any(), any()) } returns
                Result.success(StockListResponse(stkList = listOf(
                    StockListItem(stkCd = "005930", stkNm = "삼성전자", mrktNm = null)
                )))

        repository.populateIfEmpty(validConfig)

        val entitiesSlot = slot<List<StockMasterEntity>>()
        coVerify { stockMasterDao.insertAll(capture(entitiesSlot)) }
        assertEquals("", entitiesSlot.captured[0].market)
    }

    // ==========================================================
    // forceRefresh 테스트
    // ==========================================================

    @Test
    fun `forceRefresh - deleteAll 후 populateIfEmpty를 호출한다`() = runTest {
        coEvery { stockMasterDao.getCount() } returns 0
        coEvery { apiClient.call<StockListResponse>(any(), any(), any(), any(), any()) } returns
                Result.success(StockListResponse(stkList = listOf(
                    StockListItem(stkCd = "005930", stkNm = "삼성전자", mrktNm = "KOSPI")
                )))

        repository.forceRefresh(validConfig)

        coVerifyOrder {
            stockMasterDao.deleteAll()
            stockMasterDao.getCount() // populateIfEmpty 내부에서 호출
        }
    }

    @Test
    fun `forceRefresh - 삭제 후 새 데이터가 저장된다`() = runTest {
        coEvery { stockMasterDao.getCount() } returns 0
        coEvery { apiClient.call<StockListResponse>(any(), any(), any(), any(), any()) } returns
                Result.success(StockListResponse(stkList = listOf(
                    StockListItem(stkCd = "005930", stkNm = "삼성전자", mrktNm = "KOSPI")
                )))

        repository.forceRefresh(validConfig)

        coVerify(exactly = 1) { stockMasterDao.deleteAll() }
        coVerify(exactly = 1) { stockMasterDao.insertAll(any()) }
    }

    @Test
    fun `forceRefresh - API 키가 유효하지 않으면 삭제만 되고 데이터는 저장되지 않는다`() = runTest {
        coEvery { stockMasterDao.getCount() } returns 0

        repository.forceRefresh(invalidConfig)

        coVerify(exactly = 1) { stockMasterDao.deleteAll() }
        coVerify(exactly = 0) { stockMasterDao.insertAll(any()) }
    }

    // ==========================================================
    // searchStocks 테스트
    // ==========================================================

    @Test
    fun `searchStocks는 DAO에 위임한다`() = runTest {
        val entities = listOf(
            StockMasterEntity("005930", "삼성전자", "KOSPI", 0L)
        )
        every { stockMasterDao.searchStocks("삼성") } returns flowOf(entities)

        val flow = repository.searchStocks("삼성")
        flow.collect { result ->
            assertEquals(1, result.size)
            assertEquals("005930", result[0].ticker)
        }
    }

    // ==========================================================
    // getCount 테스트
    // ==========================================================

    @Test
    fun `getCount는 DAO에 위임한다`() = runTest {
        coEvery { stockMasterDao.getCount() } returns 2500

        val count = repository.getCount()
        assertEquals(2500, count)
    }
}
