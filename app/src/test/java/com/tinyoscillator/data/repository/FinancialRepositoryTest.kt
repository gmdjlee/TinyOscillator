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

class FinancialRepositoryTest {

    private lateinit var financialCacheDao: FinancialCacheDao
    private lateinit var kisApiClient: KisApiClient
    private lateinit var json: Json
    private lateinit var repository: FinancialRepository

    private val validConfig = KisApiKeyConfig(
        appKey = "test-app-key",
        appSecret = "test-app-secret"
    )

    private val invalidConfig = KisApiKeyConfig(appKey = "", appSecret = "")

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

    // -- Helper --

    private fun createFinancialData(
        ticker: String = testTicker,
        name: String = testName,
        periods: List<String> = listOf("202303", "202306"),
        balanceSheets: Map<String, BalanceSheet> = emptyMap(),
        incomeStatements: Map<String, IncomeStatement> = emptyMap(),
        profitabilityRatios: Map<String, ProfitabilityRatios> = emptyMap(),
        stabilityRatios: Map<String, StabilityRatios> = emptyMap(),
        growthRatios: Map<String, GrowthRatios> = emptyMap()
    ) = FinancialData(
        ticker = ticker,
        name = name,
        periods = periods,
        balanceSheets = balanceSheets,
        incomeStatements = incomeStatements,
        profitabilityRatios = profitabilityRatios,
        stabilityRatios = stabilityRatios,
        growthRatios = growthRatios
    )

    private fun createCacheEntity(
        ticker: String = testTicker,
        data: FinancialData? = null,
        cachedAt: Long = System.currentTimeMillis()
    ): FinancialCacheEntity {
        val financialData = data ?: createFinancialData()
        val cacheData = financialData.toCache()
        val jsonStr = json.encodeToString(FinancialDataCache.serializer(), cacheData)
        return FinancialCacheEntity(
            ticker = ticker,
            name = testName,
            data = jsonStr,
            cachedAt = cachedAt
        )
    }

    // ==========================================================
    // Tests
    // ==========================================================

    @Test
    fun `유효하지 않은 KIS config는 즉시 실패를 반환한다`() = runTest {
        val result = repository.getFinancialData(testTicker, testName, invalidConfig)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("KIS API key not configured"))

        // API 호출 없음
        coVerify(exactly = 0) { kisApiClient.get<Any>(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `TTL 유효한 캐시가 있으면 캐시 데이터를 반환하고 API를 호출하지 않는다`() = runTest {
        val freshCachedAt = System.currentTimeMillis() - 1000 // 1초 전 (TTL 내)
        val cacheEntity = createCacheEntity(cachedAt = freshCachedAt)

        coEvery { financialCacheDao.get(testTicker) } returns cacheEntity

        val result = repository.getFinancialData(testTicker, testName, validConfig)

        assertTrue(result.isSuccess)
        assertEquals(testTicker, result.getOrThrow().ticker)

        // API 호출이 없어야 함
        coVerify(exactly = 0) { kisApiClient.get<Any>(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `캐시가 만료되면 API를 호출한다`() = runTest {
        val expiredCachedAt = System.currentTimeMillis() - 25 * 60 * 60 * 1000L // 25시간 전
        val expiredCache = createCacheEntity(cachedAt = expiredCachedAt)

        coEvery { financialCacheDao.get(testTicker) } returns expiredCache

        // API mock: Result.failure → fetchList returns emptyList → fetchFromApi returns empty data
        coEvery {
            kisApiClient.get<Any>(any(), any(), any(), any(), any())
        } returns Result.failure(Exception("API error"))

        val result = repository.getFinancialData(testTicker, testName, validConfig)

        // fetchFromApi returns non-null empty data, merges with existing cache → success
        assertTrue(result.isSuccess)
        assertEquals(testTicker, result.getOrThrow().ticker)
    }

    @Test
    fun `캐시가 없으면 API 경로를 통해 데이터를 반환한다`() = runTest {
        coEvery { financialCacheDao.get(testTicker) } returns null

        // API가 빈 결과를 반환 → fetchFromApi는 빈 FinancialData 반환 (not null)
        coEvery {
            kisApiClient.get<Any>(any(), any(), any(), any(), any())
        } returns Result.failure(Exception("Mocked: no data"))

        val result = repository.getFinancialData(testTicker, testName, validConfig)

        // fetchFromApi returns empty but non-null data → Result.success
        assertTrue(result.isSuccess)
        // periods는 비어 있음 (API 실패)
        assertTrue(result.getOrThrow().periods.isEmpty())
    }

    @Test
    fun `useCache가 false이면 TTL 체크를 건너뛴다`() = runTest {
        val freshCachedAt = System.currentTimeMillis() - 1000
        val cacheEntity = createCacheEntity(cachedAt = freshCachedAt)

        coEvery { financialCacheDao.get(testTicker) } returns cacheEntity

        coEvery {
            kisApiClient.get<Any>(any(), any(), any(), any(), any())
        } returns Result.failure(Exception("API mocked fail"))

        val result = repository.getFinancialData(testTicker, testName, validConfig, useCache = false)

        // useCache=false이므로 TTL 체크를 건너뛰고 API 호출
        // fetchFromApi는 빈 데이터 반환 → loadCachedData에서 캐시 로드 → merge
        assertTrue(result.isSuccess)
    }

    @Test
    fun `API 실패 시 stale 캐시가 있으면 캐시를 반환한다`() = runTest {
        val expiredCachedAt = System.currentTimeMillis() - 25 * 60 * 60 * 1000L
        val cacheEntity = createCacheEntity(cachedAt = expiredCachedAt)

        coEvery { financialCacheDao.get(testTicker) } returns cacheEntity

        // API 호출 시 exception throw
        coEvery {
            kisApiClient.get<Any>(any(), any(), any(), any(), any())
        } throws RuntimeException("Network failure")

        val result = repository.getFinancialData(testTicker, testName, validConfig)

        // fetchList catches exception → emptyList, fetchFromApi returns empty data
        // merges with existing cache → success
        assertTrue(result.isSuccess)
        assertEquals(testTicker, result.getOrThrow().ticker)
    }

    @Test
    fun `API 실패 시 캐시도 없으면 빈 데이터를 반환한다`() = runTest {
        coEvery { financialCacheDao.get(testTicker) } returns null

        coEvery {
            kisApiClient.get<Any>(any(), any(), any(), any(), any())
        } throws RuntimeException("Network failure")

        val result = repository.getFinancialData(testTicker, testName, validConfig)

        // fetchList catches exception → emptyList
        // fetchFromApi returns empty but non-null FinancialData
        // existingCache=null, freshData=non-null → mergedData=freshData
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().periods.isEmpty())
    }

    @Test
    fun `deleteExpired가 호출되어 주기적 정리가 수행된다`() = runTest {
        val freshCachedAt = System.currentTimeMillis() - 1000
        val cacheEntity = createCacheEntity(cachedAt = freshCachedAt)

        coEvery { financialCacheDao.get(testTicker) } returns cacheEntity

        repository.getFinancialData(testTicker, testName, validConfig)

        coVerify(exactly = 1) { financialCacheDao.deleteExpired(any()) }
    }

    @Test
    fun `캐시 역직렬화 실패 시 해당 캐시를 삭제하고 API를 호출한다`() = runTest {
        val corruptEntity = FinancialCacheEntity(
            ticker = testTicker,
            name = testName,
            data = "{ invalid json data !!!",
            cachedAt = System.currentTimeMillis() - 1000 // TTL 유효
        )

        // 첫 get(): TTL 체크용 (corrupt), 이후 get(): loadCachedData용 (null)
        coEvery { financialCacheDao.get(testTicker) } returnsMany listOf(corruptEntity, null)

        coEvery {
            kisApiClient.get<Any>(any(), any(), any(), any(), any())
        } returns Result.failure(Exception("API mocked"))

        val result = repository.getFinancialData(testTicker, testName, validConfig)

        // corrupt 캐시 삭제 확인
        coVerify(exactly = 1) { financialCacheDao.delete(testTicker) }

        // fetchFromApi returns empty data → Result.success
        assertTrue(result.isSuccess)
    }

    @Test
    fun `refreshFinancialData는 유효하지 않은 config일 때 failure를 반환한다`() = runTest {
        val result = repository.refreshFinancialData(testTicker, testName, invalidConfig)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `refreshFinancialData는 항상 API를 호출한다`() = runTest {
        val cacheEntity = createCacheEntity(cachedAt = System.currentTimeMillis() - 1000)
        coEvery { financialCacheDao.get(testTicker) } returns cacheEntity

        // API가 빈 결과를 반환 → fetchFromApi는 빈 데이터 반환 (not null)
        coEvery {
            kisApiClient.get<Any>(any(), any(), any(), any(), any())
        } returns Result.failure(Exception("API error"))

        val result = repository.refreshFinancialData(testTicker, testName, validConfig)

        // fetchFromApi returns non-null empty data → merges with cache → success
        assertTrue(result.isSuccess)

        // API가 호출되었는지 확인
        coVerify(atLeast = 1) { kisApiClient.get<Any>(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `refreshFinancialData 성공 시 DB에 저장한다`() = runTest {
        coEvery { financialCacheDao.get(testTicker) } returns null
        coEvery {
            kisApiClient.get<Any>(any(), any(), any(), any(), any())
        } returns Result.failure(Exception("API error"))

        val result = repository.refreshFinancialData(testTicker, testName, validConfig)

        // fetchFromApi returns non-null empty data → saveToCacheDb called
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { financialCacheDao.insert(any()) }
    }

    @Test
    fun `getFinancialData에서 캐시 히트 시 insert가 호출되지 않는다`() = runTest {
        val cacheEntity = createCacheEntity(cachedAt = System.currentTimeMillis() - 1000)

        coEvery { financialCacheDao.get(testTicker) } returns cacheEntity

        repository.getFinancialData(testTicker, testName, validConfig)

        // TTL 유효한 캐시 히트이므로 insert 호출이 없어야 함
        coVerify(exactly = 0) { financialCacheDao.insert(any()) }
    }

    @Test
    fun `mergeData는 기존 캐시와 새 데이터를 합친다`() = runTest {
        // 캐시에 balanceSheet 데이터가 있는 FinancialData 생성
        val bs = BalanceSheet(
            period = FinancialPeriod.fromYearMonth("202303"),
            currentAssets = 30L,
            fixedAssets = 70L,
            totalAssets = 100L,
            currentLiabilities = 20L,
            fixedLiabilities = 30L,
            totalLiabilities = 50L,
            capital = 5L,
            capitalSurplus = 35L,
            retainedEarnings = 10L,
            totalEquity = 50L
        )
        val existingData = createFinancialData(
            periods = listOf("202303"),
            balanceSheets = mapOf("202303" to bs)
        )
        val expiredCache = createCacheEntity(
            data = existingData,
            cachedAt = System.currentTimeMillis() - 25 * 60 * 60 * 1000L
        )

        coEvery { financialCacheDao.get(testTicker) } returns expiredCache

        // API returns empty data → merge with existing cache
        coEvery {
            kisApiClient.get<Any>(any(), any(), any(), any(), any())
        } returns Result.failure(Exception("Empty"))

        val result = repository.getFinancialData(testTicker, testName, validConfig)

        assertTrue(result.isSuccess)
        // mergeData rebuilds periods from map keys
        assertTrue(result.getOrThrow().periods.contains("202303"))
        assertNotNull(result.getOrThrow().balanceSheets["202303"])
    }

    // ==========================================================
    // 입력 검증 테스트
    // ==========================================================

    @Test
    fun `getFinancialData - 빈 ticker는 실패를 반환한다`() = runTest {
        val result = repository.getFinancialData("", testName, validConfig)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `getFinancialData - 공백 ticker는 실패를 반환한다`() = runTest {
        val result = repository.getFinancialData("   ", testName, validConfig)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `refreshFinancialData - 빈 ticker는 실패를 반환한다`() = runTest {
        val result = repository.refreshFinancialData("", testName, validConfig)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `refreshFinancialData - 공백 ticker는 실패를 반환한다`() = runTest {
        val result = repository.refreshFinancialData("   ", testName, validConfig)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    // ==========================================================
    // 쿨다운 및 정리 간격 테스트
    // ==========================================================

    @Test
    fun `연속 호출에서 deleteExpired는 쿨다운으로 인해 한 번만 호출된다`() = runTest {
        val freshCachedAt = System.currentTimeMillis() - 1000
        val cacheEntity = createCacheEntity(cachedAt = freshCachedAt)

        coEvery { financialCacheDao.get(testTicker) } returns cacheEntity

        // 첫 호출
        repository.getFinancialData(testTicker, testName, validConfig)
        // 두 번째 호출
        repository.getFinancialData(testTicker, testName, validConfig)

        // deleteExpired는 쿨다운(1시간)으로 인해 첫 호출에서만 실행
        coVerify(exactly = 1) { financialCacheDao.deleteExpired(any()) }
    }

    // ==========================================================
    // CancellationException 전파 테스트
    // ==========================================================

    @Test
    fun `getFinancialData에서 CancellationException은 전파된다`() = runTest {
        coEvery { financialCacheDao.get(testTicker) } returns null
        coEvery {
            kisApiClient.get<Any>(any(), any(), any(), any(), any())
        } throws kotlin.coroutines.cancellation.CancellationException("Job cancelled")

        try {
            repository.getFinancialData(testTicker, testName, validConfig)
            fail("CancellationException should be rethrown")
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            // Expected
        }
    }

    @Test
    fun `refreshFinancialData에서 CancellationException은 전파된다`() = runTest {
        coEvery { financialCacheDao.get(testTicker) } returns null
        coEvery {
            kisApiClient.get<Any>(any(), any(), any(), any(), any())
        } throws kotlin.coroutines.cancellation.CancellationException("Job cancelled")

        try {
            repository.refreshFinancialData(testTicker, testName, validConfig)
            fail("CancellationException should be rethrown")
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            // Expected
        }
    }

    // ==========================================================
    // AtomicLong 정리 쿨다운 테스트
    // ==========================================================

    @Test
    fun `서로 다른 ticker 연속 호출에서도 deleteExpired 쿨다운이 적용된다`() = runTest {
        val freshCachedAt = System.currentTimeMillis() - 1000
        val cacheEntity1 = createCacheEntity(ticker = testTicker, cachedAt = freshCachedAt)
        val cacheEntity2 = createCacheEntity(ticker = "000660", cachedAt = freshCachedAt)

        coEvery { financialCacheDao.get(testTicker) } returns cacheEntity1
        coEvery { financialCacheDao.get("000660") } returns cacheEntity2

        repository.getFinancialData(testTicker, testName, validConfig)
        repository.getFinancialData("000660", "SK하이닉스", validConfig)

        // 쿨다운으로 인해 한 번만 실행
        coVerify(exactly = 1) { financialCacheDao.deleteExpired(any()) }
    }

    // ==========================================================
    // 추가 edge case 테스트
    // ==========================================================

    @Test
    fun `캐시 역직렬화 실패 후 재조회 시 새 캐시를 DB에 저장한다`() = runTest {
        val corruptEntity = FinancialCacheEntity(
            ticker = testTicker,
            name = testName,
            data = "not json at all",
            cachedAt = System.currentTimeMillis() - 1000
        )

        // 첫 get: corrupt → delete, 둘째 get (loadCachedData): null
        coEvery { financialCacheDao.get(testTicker) } returnsMany listOf(corruptEntity, null)

        coEvery {
            kisApiClient.get<Any>(any(), any(), any(), any(), any())
        } returns Result.failure(Exception("API mocked"))

        val result = repository.getFinancialData(testTicker, testName, validConfig)
        assertTrue(result.isSuccess)

        // fetchFromApi returns empty FinancialData → saved to DB
        coVerify(exactly = 1) { financialCacheDao.insert(any()) }
    }

    @Test
    fun `refreshFinancialData는 빈 ticker일 때 실패를 반환한다`() = runTest {
        val result = repository.refreshFinancialData("   ", testName, validConfig)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
