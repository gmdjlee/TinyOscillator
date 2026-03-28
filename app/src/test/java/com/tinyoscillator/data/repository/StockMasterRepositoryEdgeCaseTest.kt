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

/**
 * StockMasterRepository edge case tests.
 *
 * Tests for error handling, boundary conditions, and error propagation.
 */
class StockMasterRepositoryEdgeCaseTest {

    private lateinit var stockMasterDao: StockMasterDao
    private lateinit var apiClient: KiwoomApiClient
    private lateinit var json: Json
    private lateinit var repository: StockMasterRepository

    private val validConfig = KiwoomApiKeyConfig(
        appKey = "testKey",
        secretKey = "testSecret",
        investmentMode = InvestmentMode.MOCK
    )

    @Before
    fun setup() {
        stockMasterDao = mockk(relaxed = true)
        apiClient = mockk(relaxed = true)
        json = Json { ignoreUnknownKeys = true }
        repository = StockMasterRepository(stockMasterDao, apiClient, json)
    }

    @Test
    fun `populateIfEmpty - insertAll 예외 발생 시 전파된다`() = runTest {
        coEvery { stockMasterDao.getCount() } returns 0
        coEvery { apiClient.call<StockListResponse>(any(), any(), any(), any(), any()) } returns
                Result.success(StockListResponse(stkList = listOf(
                    StockListItem(stkCd = "005930", stkNm = "삼성전자", mrktNm = "KOSPI")
                )))
        coEvery { stockMasterDao.insertAll(any()) } throws RuntimeException("DB write error")

        try {
            repository.populateIfEmpty(validConfig)
            fail("Should throw exception")
        } catch (e: RuntimeException) {
            assertEquals("DB write error", e.message)
        }

        coVerify(exactly = 1) { stockMasterDao.insertAll(any()) }
    }

    @Test
    fun `populateIfEmpty - 모든 항목이 유효하지 않으면 insertAll을 호출하지 않는다`() = runTest {
        coEvery { stockMasterDao.getCount() } returns 0
        coEvery { apiClient.call<StockListResponse>(any(), any(), any(), any(), any()) } returns
                Result.success(StockListResponse(stkList = listOf(
                    StockListItem(stkCd = "", stkNm = "빈코드", mrktNm = "KOSPI"),
                    StockListItem(stkCd = "000001", stkNm = "", mrktNm = "KOSPI"),
                    StockListItem(stkCd = null, stkNm = null, mrktNm = "KOSPI"),
                    StockListItem(stkCd = "  ", stkNm = "공백코드", mrktNm = "KOSPI")
                )))

        val count = repository.populateIfEmpty(validConfig)

        assertEquals(0, count)
        coVerify(exactly = 0) { stockMasterDao.insertAll(any()) }
    }

    @Test
    fun `populateIfEmpty - 대량 데이터 처리`() = runTest {
        coEvery { stockMasterDao.getCount() } returns 0
        val largeList = (1..5000).map { i ->
            StockListItem(
                stkCd = String.format("%06d", i),
                stkNm = "종목$i",
                mrktNm = if (i % 2 == 0) "KOSPI" else "KOSDAQ"
            )
        }
        coEvery { apiClient.call<StockListResponse>(any(), any(), any(), any(), any()) } returns
                Result.success(StockListResponse(stkList = largeList))

        repository.populateIfEmpty(validConfig)

        coVerify(exactly = 1) { stockMasterDao.insertAll(any()) }
    }

    @Test
    fun `populateIfEmpty - getCount 예외 시 전파된다`() = runTest {
        coEvery { stockMasterDao.getCount() } throws RuntimeException("DB read error")

        try {
            repository.populateIfEmpty(validConfig)
            fail("Should throw exception")
        } catch (e: RuntimeException) {
            assertEquals("DB read error", e.message)
        }
    }

    @Test
    fun `searchStocks - 빈 문자열로 검색 시 DAO에 위임한다`() = runTest {
        val entities = listOf(
            StockMasterEntity("005930", "삼성전자", "KOSPI", lastUpdated = 0L),
            StockMasterEntity("005935", "삼성전자우", "KOSPI", lastUpdated = 0L)
        )
        every { stockMasterDao.searchStocks("") } returns flowOf(entities)

        val flow = repository.searchStocks("")
        flow.collect { result ->
            assertEquals(2, result.size)
        }
    }

    @Test
    fun `getCount - 0 반환 시 빈 DB를 의미한다`() = runTest {
        coEvery { stockMasterDao.getCount() } returns 0
        assertEquals(0, repository.getCount())
    }

    @Test
    fun `populateIfEmpty - 혼합된 유효 무효 항목에서 유효 항목만 저장한다`() = runTest {
        coEvery { stockMasterDao.getCount() } returns 0
        coEvery { apiClient.call<StockListResponse>(any(), any(), match { it["mrkt_tp"] == "0" }, any(), any()) } returns
                Result.success(StockListResponse(stkList = listOf(
                    StockListItem(stkCd = "005930", stkNm = "삼성전자", mrktNm = "KOSPI"),
                    StockListItem(stkCd = "", stkNm = "빈코드", mrktNm = "KOSPI"),
                    StockListItem(stkCd = "000660", stkNm = "SK하이닉스", mrktNm = "KOSPI"),
                    StockListItem(stkCd = "999999", stkNm = null, mrktNm = "KOSDAQ")
                )))
        coEvery { apiClient.call<StockListResponse>(any(), any(), match { it["mrkt_tp"] == "10" }, any(), any()) } returns
                Result.success(StockListResponse(stkList = emptyList()))

        repository.populateIfEmpty(validConfig)

        val entitiesSlot = slot<List<StockMasterEntity>>()
        coVerify { stockMasterDao.insertAll(capture(entitiesSlot)) }
        assertEquals(2, entitiesSlot.captured.size)
        assertEquals("005930", entitiesSlot.captured[0].ticker)
        assertEquals("000660", entitiesSlot.captured[1].ticker)
    }

    @Test
    fun `forceRefresh - API 실패 시 기존 데이터를 보존한다`() = runTest {
        coEvery { apiClient.call<StockListResponse>(any(), any(), any(), any(), any()) } returns
                Result.failure(RuntimeException("Network error"))

        try {
            repository.forceRefresh(validConfig)
            fail("Should throw")
        } catch (e: RuntimeException) {
            assertTrue(e.message!!.contains("종목 조회 실패"))
        }

        // deleteAll이 호출되지 않아 기존 데이터 보존
        coVerify(exactly = 0) { stockMasterDao.deleteAll() }
    }

    @Test
    fun `forceRefresh - API 성공 후 deleteAll과 insertAll이 순서대로 호출된다`() = runTest {
        coEvery { apiClient.call<StockListResponse>(any(), any(), any(), any(), any()) } returns
                Result.success(StockListResponse(stkList = listOf(
                    StockListItem(stkCd = "005930", stkNm = "삼성전자", mrktNm = "KOSPI")
                )))

        repository.forceRefresh(validConfig)

        coVerifyOrder {
            stockMasterDao.deleteAll()
            stockMasterDao.insertAll(any())
        }
    }
}
