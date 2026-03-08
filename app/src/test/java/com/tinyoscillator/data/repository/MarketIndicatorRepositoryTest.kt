package com.tinyoscillator.data.repository

import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.core.database.dao.MarketDepositDao
import com.tinyoscillator.core.database.dao.MarketOscillatorDao
import com.tinyoscillator.core.database.entity.MarketDepositEntity
import com.tinyoscillator.core.database.entity.MarketOscillatorEntity
import com.tinyoscillator.core.scraper.NaverFinanceScraper
import com.tinyoscillator.domain.model.MarketDepositChartData
import com.tinyoscillator.domain.usecase.MarketOscillatorCalculator
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class MarketIndicatorRepositoryTest {

    private lateinit var oscillatorDao: MarketOscillatorDao
    private lateinit var depositDao: MarketDepositDao
    private lateinit var calculator: MarketOscillatorCalculator
    private lateinit var scraper: NaverFinanceScraper
    private lateinit var krxApiClient: KrxApiClient
    private lateinit var repository: MarketIndicatorRepository

    private val testMarket = "KOSPI"
    private val testKrxId = "test-id"
    private val testKrxPassword = "test-pw"

    @Before
    fun setup() {
        oscillatorDao = mockk(relaxed = true)
        depositDao = mockk(relaxed = true)
        calculator = mockk(relaxed = true)
        scraper = mockk(relaxed = true)
        krxApiClient = mockk(relaxed = true)
        repository = MarketIndicatorRepository(
            oscillatorDao = oscillatorDao,
            depositDao = depositDao,
            calculator = calculator,
            scraper = scraper,
            krxApiClient = krxApiClient
        )
    }

    // -- Helpers --

    private fun createOscillatorResult(
        market: String = testMarket,
        dates: List<String> = listOf("20260301", "20260302"),
        indexValues: List<Double> = listOf(2500.0, 2510.0),
        oscillator: List<Double> = listOf(65.0, -55.0)
    ) = MarketOscillatorCalculator.OscillatorResult(
        market = market,
        dates = dates,
        indexValues = indexValues,
        oscillator = oscillator,
        stats = MarketOscillatorCalculator.OscillatorStats(
            mean = oscillator.average(),
            max = oscillator.maxOrNull() ?: 0.0,
            min = oscillator.minOrNull() ?: 0.0,
            latest = oscillator.lastOrNull() ?: 0.0
        )
    )

    private fun createDepositChartData(
        dates: List<String> = listOf("2026-03-01", "2026-03-02"),
        depositAmounts: List<Double> = listOf(50000.0, 51000.0),
        depositChanges: List<Double> = listOf(0.0, 1000.0),
        creditAmounts: List<Double> = listOf(20000.0, 20500.0),
        creditChanges: List<Double> = listOf(0.0, 500.0)
    ) = MarketDepositChartData(
        dates = dates,
        depositAmounts = depositAmounts,
        depositChanges = depositChanges,
        creditAmounts = creditAmounts,
        creditChanges = creditChanges
    )

    private fun createDepositEntity(
        date: String = "2026-03-01",
        depositAmount: Double = 50000.0,
        depositChange: Double = 0.0,
        creditAmount: Double = 20000.0,
        creditChange: Double = 0.0,
        lastUpdated: Long = System.currentTimeMillis()
    ) = MarketDepositEntity(
        date = date,
        depositAmount = depositAmount,
        depositChange = depositChange,
        creditAmount = creditAmount,
        creditChange = creditChange,
        lastUpdated = lastUpdated
    )

    private fun createOscillatorEntity(
        market: String = testMarket,
        date: String = "2026-03-01",
        indexValue: Double = 2500.0,
        oscillator: Double = 65.0,
        lastUpdated: Long = System.currentTimeMillis()
    ) = MarketOscillatorEntity(
        id = "$market-$date",
        market = market,
        date = date,
        indexValue = indexValue,
        oscillator = oscillator,
        lastUpdated = lastUpdated
    )

    // ==========================================================
    // initializeMarketData 테스트
    // ==========================================================

    @Test
    fun `initializeMarketData - 성공 시 데이터 건수를 반환한다`() = runTest {
        val oscillatorResult = createOscillatorResult()
        coEvery { krxApiClient.login(testKrxId, testKrxPassword) } returns true
        coEvery { calculator.analyze(testMarket, any(), any()) } returns oscillatorResult

        val result = repository.initializeMarketData(testMarket, 30, testKrxId, testKrxPassword)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow())
        coVerify(exactly = 1) { oscillatorDao.insertAll(any()) }
    }

    @Test
    fun `initializeMarketData - onProgress 콜백이 호출된다`() = runTest {
        val oscillatorResult = createOscillatorResult()
        coEvery { krxApiClient.login(testKrxId, testKrxPassword) } returns true
        coEvery { calculator.analyze(testMarket, any(), any()) } returns oscillatorResult

        val progressMessages = mutableListOf<Pair<String, Int>>()
        val onProgress: (String, Int) -> Unit = { msg, pct -> progressMessages.add(msg to pct) }

        repository.initializeMarketData(testMarket, 30, testKrxId, testKrxPassword, onProgress)

        assertTrue(progressMessages.isNotEmpty())
        assertEquals(0, progressMessages.first().second)
        assertEquals(100, progressMessages.last().second)
    }

    @Test
    fun `initializeMarketData - KRX 로그인 실패 시 failure를 반환한다`() = runTest {
        coEvery { krxApiClient.login(testKrxId, testKrxPassword) } returns false

        val result = repository.initializeMarketData(testMarket, 30, testKrxId, testKrxPassword)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("KRX 로그인 실패"))
        coVerify(exactly = 0) { calculator.analyze(any(), any(), any()) }
    }

    @Test
    fun `initializeMarketData - calculator가 null을 반환하면 failure를 반환한다`() = runTest {
        coEvery { krxApiClient.login(testKrxId, testKrxPassword) } returns true
        coEvery { calculator.analyze(testMarket, any(), any()) } returns null

        val result = repository.initializeMarketData(testMarket, 30, testKrxId, testKrxPassword)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("데이터를 가져오지 못했습니다"))
    }

    @Test
    fun `initializeMarketData - 빈 dates 목록이면 failure를 반환한다`() = runTest {
        val emptyResult = createOscillatorResult(dates = emptyList(), indexValues = emptyList(), oscillator = emptyList())
        coEvery { krxApiClient.login(testKrxId, testKrxPassword) } returns true
        coEvery { calculator.analyze(testMarket, any(), any()) } returns emptyResult

        val result = repository.initializeMarketData(testMarket, 30, testKrxId, testKrxPassword)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("데이터를 가져오지 못했습니다"))
        coVerify(exactly = 0) { oscillatorDao.insertAll(any()) }
    }

    // ==========================================================
    // updateMarketData 테스트
    // ==========================================================

    @Test
    fun `updateMarketData - 성공 시 데이터를 저장하고 오래된 데이터를 삭제한다`() = runTest {
        val oscillatorResult = createOscillatorResult()
        coEvery { krxApiClient.login(testKrxId, testKrxPassword) } returns true
        coEvery { calculator.analyze(testMarket, any(), any()) } returns oscillatorResult

        val result = repository.updateMarketData(testMarket, testKrxId, testKrxPassword)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow())
        coVerify(exactly = 1) { oscillatorDao.insertAndCleanup(any(), testMarket, 90) }
    }

    @Test
    fun `updateMarketData - KRX 로그인 실패 시 failure를 반환한다`() = runTest {
        coEvery { krxApiClient.login(testKrxId, testKrxPassword) } returns false

        val result = repository.updateMarketData(testMarket, testKrxId, testKrxPassword)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("KRX 로그인 실패"))
    }

    @Test
    fun `updateMarketData - calculator가 null을 반환하면 failure를 반환한다`() = runTest {
        coEvery { krxApiClient.login(testKrxId, testKrxPassword) } returns true
        coEvery { calculator.analyze(testMarket, any(), any()) } returns null

        val result = repository.updateMarketData(testMarket, testKrxId, testKrxPassword)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("데이터를 가져오지 못했습니다"))
    }

    @Test
    fun `updateMarketData - 빈 dates 목록이면 failure를 반환한다`() = runTest {
        val emptyResult = createOscillatorResult(dates = emptyList(), indexValues = emptyList(), oscillator = emptyList())
        coEvery { krxApiClient.login(testKrxId, testKrxPassword) } returns true
        coEvery { calculator.analyze(testMarket, any(), any()) } returns emptyResult

        val result = repository.updateMarketData(testMarket, testKrxId, testKrxPassword)

        assertTrue(result.isFailure)
    }

    @Test
    fun `updateMarketData - 일반 예외 발생 시 failure를 반환한다`() = runTest {
        coEvery { krxApiClient.login(testKrxId, testKrxPassword) } returns true
        coEvery { calculator.analyze(testMarket, any(), any()) } throws RuntimeException("Network error")

        val result = repository.updateMarketData(testMarket, testKrxId, testKrxPassword)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Network error"))
    }

    // ==========================================================
    // initializeDeposits 테스트
    // ==========================================================

    @Test
    fun `initializeDeposits - 성공 시 데이터를 저장하고 건수를 반환한다`() = runTest {
        val chartData = createDepositChartData()
        coEvery { scraper.scrapeDepositData(5) } returns chartData

        val result = repository.initializeDeposits(numPages = 5)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow())
        coVerify(exactly = 1) { depositDao.insertAndCleanup(any(), any()) }
    }

    @Test
    fun `initializeDeposits - onProgress 콜백이 호출된다`() = runTest {
        val chartData = createDepositChartData()
        coEvery { scraper.scrapeDepositData(5) } returns chartData

        val progressMessages = mutableListOf<Pair<String, Int>>()
        val onProgress: (String, Int) -> Unit = { msg, pct -> progressMessages.add(msg to pct) }

        repository.initializeDeposits(numPages = 5, onProgress = onProgress)

        assertTrue(progressMessages.isNotEmpty())
        assertEquals(100, progressMessages.last().second)
    }

    @Test
    fun `initializeDeposits - scraper가 null을 반환하면 failure를 반환한다`() = runTest {
        coEvery { scraper.scrapeDepositData(5) } returns null

        val result = repository.initializeDeposits(numPages = 5)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("스크래핑 실패"))
        coVerify(exactly = 0) { depositDao.insertAll(any()) }
    }

    @Test
    fun `initializeDeposits - 빈 데이터이면 failure를 반환한다`() = runTest {
        val emptyData = MarketDepositChartData.empty()
        coEvery { scraper.scrapeDepositData(5) } returns emptyData

        val result = repository.initializeDeposits(numPages = 5)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("데이터가 비어있습니다"))
    }

    @Test
    fun `initializeDeposits - scraper 예외 발생 시 failure를 반환한다`() = runTest {
        coEvery { scraper.scrapeDepositData(5) } throws RuntimeException("Connection refused")

        val result = repository.initializeDeposits(numPages = 5)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Connection refused"))
    }

    // ==========================================================
    // getOrUpdateMarketData 테스트
    // ==========================================================

    @Test
    fun `getOrUpdateMarketData - 캐시가 유효하면 스크래핑 없이 캐시를 반환한다`() = runTest {
        val recentTimestamp = System.currentTimeMillis() - 1000 // 1초 전
        val today = java.time.LocalDate.now().toString()
        val entities = listOf(
            createDepositEntity(date = today, lastUpdated = recentTimestamp)
        )
        coEvery { depositDao.getAllDeposits() } returns flowOf(entities)

        val result = repository.getOrUpdateMarketData()

        assertNotNull(result)
        assertEquals(1, result!!.dates.size)
        assertEquals(today, result.dates.first())
        coVerify(exactly = 0) { scraper.scrapeDepositData(any()) }
    }

    @Test
    fun `getOrUpdateMarketData - 캐시가 비어있으면 스크래핑을 수행한다`() = runTest {
        val freshData = createDepositChartData()
        val newEntities = listOf(
            createDepositEntity(date = "2026-03-01"),
            createDepositEntity(date = "2026-03-02", depositAmount = 51000.0)
        )

        // 처음엔 빈 목록, insertAll 후 재조회 시 데이터 있음
        coEvery { depositDao.getAllDeposits() } returnsMany listOf(
            flowOf(emptyList()),
            flowOf(newEntities)
        )
        coEvery { scraper.scrapeDepositData(any()) } returns freshData

        val result = repository.getOrUpdateMarketData()

        assertNotNull(result)
        coVerify(exactly = 1) { scraper.scrapeDepositData(any()) }
        coVerify(exactly = 1) { depositDao.insertAndCleanup(any(), any()) }
    }

    @Test
    fun `getOrUpdateMarketData - 캐시가 만료되었으면 스크래핑을 수행한다`() = runTest {
        val expiredTimestamp = System.currentTimeMillis() - 13 * 60 * 60 * 1000 // 13시간 전
        val oldEntities = listOf(
            createDepositEntity(date = "2026-03-01", lastUpdated = expiredTimestamp)
        )
        val updatedEntities = listOf(
            createDepositEntity(date = "2026-03-01", lastUpdated = System.currentTimeMillis()),
            createDepositEntity(date = "2026-03-02", lastUpdated = System.currentTimeMillis())
        )
        val freshData = createDepositChartData()

        coEvery { depositDao.getAllDeposits() } returnsMany listOf(
            flowOf(oldEntities),
            flowOf(updatedEntities)
        )
        coEvery { scraper.scrapeDepositData(any()) } returns freshData

        val result = repository.getOrUpdateMarketData()

        assertNotNull(result)
        coVerify(exactly = 1) { scraper.scrapeDepositData(any()) }
    }

    @Test
    fun `getOrUpdateMarketData - 스크래핑 실패 시 캐시가 있으면 캐시를 반환한다`() = runTest {
        val expiredTimestamp = System.currentTimeMillis() - 13 * 60 * 60 * 1000
        val existingEntities = listOf(
            createDepositEntity(date = "2026-03-01", lastUpdated = expiredTimestamp)
        )

        coEvery { depositDao.getAllDeposits() } returns flowOf(existingEntities)
        coEvery { scraper.scrapeDepositData(any()) } throws RuntimeException("Scraping failed")

        val result = repository.getOrUpdateMarketData()

        assertNotNull(result)
        assertEquals(1, result!!.dates.size)
        assertEquals("2026-03-01", result.dates.first())
    }

    @Test
    fun `getOrUpdateMarketData - 스크래핑 실패 시 캐시도 없으면 null을 반환한다`() = runTest {
        coEvery { depositDao.getAllDeposits() } returns flowOf(emptyList())
        coEvery { scraper.scrapeDepositData(any()) } throws RuntimeException("Scraping failed")

        val result = repository.getOrUpdateMarketData()

        assertNull(result)
    }

    @Test
    fun `getOrUpdateMarketData - scraper가 null 반환 시 캐시가 있으면 캐시를 반환한다`() = runTest {
        val expiredTimestamp = System.currentTimeMillis() - 13 * 60 * 60 * 1000
        val existingEntities = listOf(
            createDepositEntity(date = "2026-03-01", lastUpdated = expiredTimestamp)
        )

        coEvery { depositDao.getAllDeposits() } returns flowOf(existingEntities)
        coEvery { scraper.scrapeDepositData(any()) } returns null

        val result = repository.getOrUpdateMarketData()

        assertNotNull(result)
        assertEquals("2026-03-01", result!!.dates.first())
    }

    @Test
    fun `getOrUpdateMarketData - scraper가 null 반환하고 캐시도 없으면 null을 반환한다`() = runTest {
        coEvery { depositDao.getAllDeposits() } returns flowOf(emptyList())
        coEvery { scraper.scrapeDepositData(any()) } returns null

        val result = repository.getOrUpdateMarketData()

        assertNull(result)
    }

    @Test
    fun `getOrUpdateMarketData - TTL 이내이면 날짜와 무관하게 스크래핑하지 않는다`() = runTest {
        val recentTimestamp = System.currentTimeMillis() - 1000 // 1초 전 (TTL 이내)
        val yesterday = java.time.LocalDate.now().minusDays(1).toString()
        val existingEntities = listOf(
            createDepositEntity(date = yesterday, lastUpdated = recentTimestamp)
        )
        coEvery { depositDao.getAllDeposits() } returns flowOf(existingEntities)

        val result = repository.getOrUpdateMarketData()

        assertNotNull(result)
        assertEquals(1, result!!.dates.size)
        coVerify(exactly = 0) { scraper.scrapeDepositData(any()) }
    }

    // ==========================================================
    // CancellationException 전파 테스트
    // ==========================================================

    @Test
    fun `initializeMarketData에서 CancellationException은 전파된다`() = runTest {
        coEvery { krxApiClient.login(testKrxId, testKrxPassword) } returns true
        coEvery { calculator.analyze(testMarket, any(), any()) } throws CancellationException("Job cancelled")

        try {
            repository.initializeMarketData(testMarket, 30, testKrxId, testKrxPassword)
            fail("CancellationException이 전파되어야 한다")
        } catch (_: CancellationException) {
            // Expected
        }
    }

    @Test
    fun `updateMarketData에서 CancellationException은 전파된다`() = runTest {
        coEvery { krxApiClient.login(testKrxId, testKrxPassword) } returns true
        coEvery { calculator.analyze(testMarket, any(), any()) } throws CancellationException("Job cancelled")

        try {
            repository.updateMarketData(testMarket, testKrxId, testKrxPassword)
            fail("CancellationException이 전파되어야 한다")
        } catch (_: CancellationException) {
            // Expected
        }
    }

    @Test
    fun `initializeDeposits에서 CancellationException은 전파된다`() = runTest {
        coEvery { scraper.scrapeDepositData(5) } throws CancellationException("Job cancelled")

        try {
            repository.initializeDeposits(numPages = 5)
            fail("CancellationException이 전파되어야 한다")
        } catch (_: CancellationException) {
            // Expected
        }
    }

    @Test
    fun `getOrUpdateMarketData에서 CancellationException은 전파된다`() = runTest {
        // depositDao.getAllDeposits()가 Flow를 반환하지만 first()에서 CancellationException 발생
        coEvery { depositDao.getAllDeposits() } returns flow {
            throw CancellationException("Job cancelled")
        }

        try {
            repository.getOrUpdateMarketData()
            fail("CancellationException이 전파되어야 한다")
        } catch (_: CancellationException) {
            // Expected
        }
    }

    // ==========================================================
    // Entity → Domain 매핑 테스트
    // ==========================================================

    @Test
    fun `getMarketData - OscillatorEntity가 MarketOscillator 도메인으로 매핑된다`() = runTest {
        val entity = createOscillatorEntity(
            market = "KOSPI",
            date = "2026-03-01",
            indexValue = 2500.0,
            oscillator = 65.0,
            lastUpdated = 1000L
        )
        coEvery { oscillatorDao.getMarketData("KOSPI") } returns flowOf(listOf(entity))

        val flow = repository.getMarketData("KOSPI")
        val result = flow.first()

        assertEquals(1, result.size)
        val domain = result.first()
        assertEquals("KOSPI-2026-03-01", domain.id)
        assertEquals("KOSPI", domain.market)
        assertEquals("2026-03-01", domain.date)
        assertEquals(2500.0, domain.indexValue, 0.001)
        assertEquals(65.0, domain.oscillator, 0.001)
        assertEquals(1000L, domain.lastUpdated)
    }

    @Test
    fun `getAllDeposits - DepositEntity가 MarketDeposit 도메인으로 매핑된다`() = runTest {
        val entity = createDepositEntity(
            date = "2026-03-01",
            depositAmount = 50000.0,
            depositChange = 1000.0,
            creditAmount = 20000.0,
            creditChange = 500.0,
            lastUpdated = 2000L
        )
        coEvery { depositDao.getAllDeposits() } returns flowOf(listOf(entity))

        val flow = repository.getAllDeposits()
        val result = flow.first()

        assertEquals(1, result.size)
        val domain = result.first()
        assertEquals("2026-03-01", domain.date)
        assertEquals(50000.0, domain.depositAmount, 0.001)
        assertEquals(1000.0, domain.depositChange, 0.001)
        assertEquals(20000.0, domain.creditAmount, 0.001)
        assertEquals(500.0, domain.creditChange, 0.001)
        assertEquals(2000L, domain.lastUpdated)
    }

    @Test
    fun `getLatestData - entity가 null이면 null을 반환한다`() = runTest {
        coEvery { oscillatorDao.getLatestData("KOSPI") } returns null

        val result = repository.getLatestData("KOSPI")

        assertNull(result)
    }

    @Test
    fun `getLatestData - entity가 있으면 도메인으로 매핑하여 반환한다`() = runTest {
        val entity = createOscillatorEntity()
        coEvery { oscillatorDao.getLatestData("KOSPI") } returns entity

        val result = repository.getLatestData("KOSPI")

        assertNotNull(result)
        assertEquals(entity.id, result!!.id)
        assertEquals(entity.market, result.market)
        assertEquals(entity.date, result.date)
    }

    // ==========================================================
    // toEntities 매핑 (initializeMarketData를 통한 간접 검증)
    // ==========================================================

    @Test
    fun `initializeMarketData - KRX 날짜 형식이 ISO 형식으로 변환된다`() = runTest {
        val oscillatorResult = createOscillatorResult(
            dates = listOf("20260301"),
            indexValues = listOf(2500.0),
            oscillator = listOf(65.0)
        )
        coEvery { krxApiClient.login(testKrxId, testKrxPassword) } returns true
        coEvery { calculator.analyze(testMarket, any(), any()) } returns oscillatorResult

        val entitySlot = slot<List<MarketOscillatorEntity>>()
        coEvery { oscillatorDao.insertAll(capture(entitySlot)) } just Runs

        repository.initializeMarketData(testMarket, 30, testKrxId, testKrxPassword)

        val captured = entitySlot.captured
        assertEquals(1, captured.size)
        assertEquals("KOSPI-2026-03-01", captured[0].id)
        assertEquals("2026-03-01", captured[0].date)
        assertEquals("KOSPI", captured[0].market)
        assertEquals(2500.0, captured[0].indexValue, 0.001)
        assertEquals(65.0, captured[0].oscillator, 0.001)
    }

    // ==========================================================
    // getDataCount, getDataByDateRange 위임 테스트
    // ==========================================================

    @Test
    fun `getDataCount - DAO에 위임한다`() = runTest {
        coEvery { oscillatorDao.getDataCount("KOSPI") } returns 42

        val count = repository.getDataCount("KOSPI")

        assertEquals(42, count)
    }

    @Test
    fun `getDataByDateRange - DAO에 위임하고 도메인으로 매핑한다`() = runTest {
        val entity = createOscillatorEntity(date = "2026-03-01")
        coEvery {
            oscillatorDao.getDataByDateRange("KOSPI", "2026-03-01", "2026-03-07")
        } returns flowOf(listOf(entity))

        val flow = repository.getDataByDateRange("KOSPI", "2026-03-01", "2026-03-07")
        val result = flow.first()

        assertEquals(1, result.size)
        assertEquals("2026-03-01", result.first().date)
    }

    @Test
    fun `getDepositsByDateRange - DAO에 위임하고 도메인으로 매핑한다`() = runTest {
        val entity = createDepositEntity(date = "2026-03-01")
        coEvery {
            depositDao.getByDateRange("2026-03-01", "2026-03-07")
        } returns flowOf(listOf(entity))

        val flow = repository.getDepositsByDateRange("2026-03-01", "2026-03-07")
        val result = flow.first()

        assertEquals(1, result.size)
        assertEquals("2026-03-01", result.first().date)
    }

    // ==========================================================
    // getOrUpdateMarketData - convertToChartData 정렬 검증
    // ==========================================================

    @Test
    fun `getOrUpdateMarketData - 반환된 차트 데이터는 날짜 오름차순으로 정렬된다`() = runTest {
        val recentTimestamp = System.currentTimeMillis() - 1000
        val today = java.time.LocalDate.now().toString()
        val yesterday = java.time.LocalDate.now().minusDays(1).toString()
        // 의도적으로 역순으로 배치
        val entities = listOf(
            createDepositEntity(date = today, depositAmount = 51000.0, lastUpdated = recentTimestamp),
            createDepositEntity(date = yesterday, depositAmount = 50000.0, lastUpdated = recentTimestamp)
        )
        coEvery { depositDao.getAllDeposits() } returns flowOf(entities)

        val result = repository.getOrUpdateMarketData()

        assertNotNull(result)
        assertEquals(yesterday, result!!.dates[0])
        assertEquals(today, result.dates[1])
        assertEquals(50000.0, result.depositAmounts[0], 0.001)
        assertEquals(51000.0, result.depositAmounts[1], 0.001)
    }
}
