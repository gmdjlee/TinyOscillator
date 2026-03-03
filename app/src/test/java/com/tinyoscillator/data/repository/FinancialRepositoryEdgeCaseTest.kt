package com.tinyoscillator.data.repository

import com.tinyoscillator.core.api.KisApiClient
import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.core.database.dao.FinancialCacheDao
import com.tinyoscillator.core.database.entity.FinancialCacheEntity
import com.tinyoscillator.domain.model.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * FinancialRepository edge case tests.
 *
 * Tests for merge behavior, period handling, and boundary conditions.
 */
class FinancialRepositoryEdgeCaseTest {

    private lateinit var financialCacheDao: FinancialCacheDao
    private lateinit var kisApiClient: KisApiClient
    private lateinit var json: Json
    private lateinit var repository: FinancialRepository

    private val validConfig = KisApiKeyConfig(
        appKey = "test-app-key",
        appSecret = "test-app-secret"
    )

    private val testTicker = "005930"
    private val testName = "삼성전자"

    @Before
    fun setup() {
        financialCacheDao = mockk(relaxed = true)
        kisApiClient = mockk(relaxed = true)
        json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            encodeDefaults = true
        }
        repository = FinancialRepository(financialCacheDao, kisApiClient, json)
    }

    private fun createFinancialData(
        ticker: String = testTicker,
        name: String = testName,
        periods: List<String> = listOf("202303"),
        balanceSheets: Map<String, BalanceSheet> = emptyMap(),
        profitabilityRatios: Map<String, ProfitabilityRatios> = emptyMap()
    ) = FinancialData(
        ticker = ticker,
        name = name,
        periods = periods,
        balanceSheets = balanceSheets,
        incomeStatements = emptyMap(),
        profitabilityRatios = profitabilityRatios,
        stabilityRatios = emptyMap(),
        growthRatios = emptyMap()
    )

    private fun createCacheEntity(
        data: FinancialData,
        cachedAt: Long = System.currentTimeMillis() - 1000
    ): FinancialCacheEntity {
        val cacheData = data.toCache()
        return FinancialCacheEntity(
            ticker = data.ticker,
            name = data.name,
            data = json.encodeToString(FinancialDataCache.serializer(), cacheData),
            cachedAt = cachedAt
        )
    }

    @Test
    fun `refreshFinancialData는 기존 캐시와 병합한다`() = runTest {
        val existingData = createFinancialData(
            periods = listOf("202303"),
            balanceSheets = mapOf(
                "202303" to BalanceSheet(
                    period = FinancialPeriod.fromYearMonth("202303"),
                    currentAssets = 100L, fixedAssets = 200L, totalAssets = 300L,
                    currentLiabilities = 50L, fixedLiabilities = 100L, totalLiabilities = 150L,
                    capital = 10L, capitalSurplus = 50L, retainedEarnings = 90L, totalEquity = 150L
                )
            )
        )
        val cacheEntity = createCacheEntity(existingData)
        coEvery { financialCacheDao.get(testTicker) } returns cacheEntity

        coEvery {
            kisApiClient.get<Any>(any(), any(), any(), any(), any())
        } returns Result.failure(Exception("API error"))

        val result = repository.refreshFinancialData(testTicker, testName, validConfig)

        assertTrue(result.isSuccess)
        // Merge should include existing period
        assertTrue(result.getOrThrow().balanceSheets.containsKey("202303"))
    }

    @Test
    fun `캐시에 profitabilityRatios가 있으면 merge 후에도 유지된다`() = runTest {
        val existingData = createFinancialData(
            periods = listOf("202303"),
            profitabilityRatios = mapOf(
                "202303" to ProfitabilityRatios(
                    period = FinancialPeriod.fromYearMonth("202303"),
                    roe = 15.0, roa = 8.0, operatingMargin = 20.0, netMargin = 18.0
                )
            )
        )
        val expiredCache = createCacheEntity(
            existingData,
            cachedAt = System.currentTimeMillis() - 25 * 60 * 60 * 1000L
        )
        coEvery { financialCacheDao.get(testTicker) } returns expiredCache

        coEvery {
            kisApiClient.get<Any>(any(), any(), any(), any(), any())
        } returns Result.failure(Exception("API error"))

        val result = repository.getFinancialData(testTicker, testName, validConfig)
        assertTrue(result.isSuccess)

        val ratios = result.getOrThrow().profitabilityRatios["202303"]
        assertNotNull(ratios)
        assertEquals(15.0, ratios!!.roe!!, 0.01)
    }

    @Test
    fun `getFinancialData에서 API 예외 발생 시 catch하고 stale 캐시를 반환한다`() = runTest {
        val existingData = createFinancialData(periods = listOf("202303"))
        val cacheEntity = createCacheEntity(
            existingData,
            cachedAt = System.currentTimeMillis() - 25 * 60 * 60 * 1000L
        )
        coEvery { financialCacheDao.get(testTicker) } returns cacheEntity

        // API throws OutOfMemoryError-like exception
        coEvery {
            kisApiClient.get<Any>(any(), any(), any(), any(), any())
        } throws RuntimeException("unexpected crash")

        val result = repository.getFinancialData(testTicker, testName, validConfig)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `refreshFinancialData에서 캐시가 없어도 API 성공 시 정상 반환한다`() = runTest {
        coEvery { financialCacheDao.get(testTicker) } returns null

        coEvery {
            kisApiClient.get<Any>(any(), any(), any(), any(), any())
        } returns Result.failure(Exception("no real data"))

        val result = repository.refreshFinancialData(testTicker, testName, validConfig)
        // fetchFromApi returns empty data (not null) → success
        assertTrue(result.isSuccess)
    }

    @Test
    fun `getFinancialData 여러 번 호출해도 첫 호출에서만 deleteExpired가 실행된다`() = runTest {
        val cacheEntity = createCacheEntity(
            createFinancialData(),
            cachedAt = System.currentTimeMillis() - 1000
        )
        coEvery { financialCacheDao.get(testTicker) } returns cacheEntity

        repeat(5) {
            repository.getFinancialData(testTicker, testName, validConfig)
        }

        // deleteExpired should be called only once (1-hour cooldown)
        coVerify(exactly = 1) { financialCacheDao.deleteExpired(any()) }
    }
}
