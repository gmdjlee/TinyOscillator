package com.tinyoscillator.data.repository

import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.EtfDao
import com.tinyoscillator.core.database.dao.FundamentalCacheDao
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.core.database.entity.AnalysisCacheEntity
import com.tinyoscillator.core.database.entity.FundamentalCacheEntity
import com.tinyoscillator.domain.model.DemarkPeriodType
import com.tinyoscillator.domain.model.DemarkTDRow
import com.tinyoscillator.domain.model.OscillatorRow
import com.tinyoscillator.domain.model.StockAggregatedTimePoint
import com.tinyoscillator.domain.usecase.CalcDemarkTDUseCase
import com.tinyoscillator.domain.usecase.CalcOscillatorUseCase
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StatisticalRepositoryImplTest {

    private lateinit var analysisCacheDao: AnalysisCacheDao
    private lateinit var stockMasterDao: StockMasterDao
    private lateinit var fundamentalCacheDao: FundamentalCacheDao
    private lateinit var etfDao: EtfDao
    private lateinit var calcOscillatorUseCase: CalcOscillatorUseCase
    private lateinit var calcDemarkTDUseCase: CalcDemarkTDUseCase
    private lateinit var repository: StatisticalRepositoryImpl

    @Before
    fun setup() {
        analysisCacheDao = mockk()
        stockMasterDao = mockk()
        fundamentalCacheDao = mockk()
        etfDao = mockk()
        calcOscillatorUseCase = mockk()
        calcDemarkTDUseCase = mockk()

        repository = StatisticalRepositoryImpl(
            analysisCacheDao = analysisCacheDao,
            stockMasterDao = stockMasterDao,
            fundamentalCacheDao = fundamentalCacheDao,
            etfDao = etfDao,
            calcOscillatorUseCase = calcOscillatorUseCase,
            calcDemarkTDUseCase = calcDemarkTDUseCase
        )
    }

    // ─── getOscillatorData ───

    @Test
    fun `getOscillatorData - 빈 데이터 반환 시 emptyList`() = runTest {
        coEvery { analysisCacheDao.getRecentByTicker("005930", 200) } returns emptyList()

        val result = repository.getOscillatorData("005930", 200)

        assertTrue("빈 데이터에서 emptyList", result.isEmpty())
        coVerify(exactly = 0) { calcOscillatorUseCase.execute(any()) }
    }

    @Test
    fun `getOscillatorData - UseCase 예외 시 emptyList 반환`() = runTest {
        val entities = listOf(
            AnalysisCacheEntity("005930", "20250101", 50000000000L, 1000L, -500L, 50000)
        )
        coEvery { analysisCacheDao.getRecentByTicker("005930", 200) } returns entities
        coEvery { calcOscillatorUseCase.execute(any()) } throws RuntimeException("계산 오류")

        val result = repository.getOscillatorData("005930", 200)

        assertTrue("예외 시 emptyList", result.isEmpty())
    }

    @Test
    fun `getOscillatorData - 정상 데이터 반환`() = runTest {
        val entities = listOf(
            AnalysisCacheEntity("005930", "20250101", 50000000000L, 1000L, -500L, 50000),
            AnalysisCacheEntity("005930", "20250102", 51000000000L, 2000L, -300L, 51000)
        )
        val expectedOscillators = listOf(
            OscillatorRow("20250101", 50000000000L, 50.0, 1000L, -500L, 0.001, 0.001, 0.001, 0.0, 0.0, 0.0),
            OscillatorRow("20250102", 51000000000L, 51.0, 2000L, -300L, 0.001, 0.001, 0.001, 0.0, 0.0, 0.0)
        )
        coEvery { analysisCacheDao.getRecentByTicker("005930", 200) } returns entities
        coEvery { calcOscillatorUseCase.execute(any()) } returns expectedOscillators

        val result = repository.getOscillatorData("005930", 200)

        assertEquals(2, result.size)
    }

    // ─── getDemarkData ───

    @Test
    fun `getDemarkData - 5개 미만 가격 시 emptyList`() = runTest {
        val entities = listOf(
            AnalysisCacheEntity("005930", "20250101", 50000000000L, 1000L, -500L, 50000),
            AnalysisCacheEntity("005930", "20250102", 51000000000L, 2000L, -300L, 51000),
            AnalysisCacheEntity("005930", "20250103", 52000000000L, 3000L, -200L, 52000)
        )
        coEvery { analysisCacheDao.getRecentByTicker("005930", 200) } returns entities

        val result = repository.getDemarkData("005930", 200)

        assertTrue("5개 미만 데이터에서 emptyList", result.isEmpty())
        coVerify(exactly = 0) { calcDemarkTDUseCase.execute(any(), any()) }
    }

    @Test
    fun `getDemarkData - 빈 가격 데이터 시 emptyList`() = runTest {
        coEvery { analysisCacheDao.getRecentByTicker("005930", 200) } returns emptyList()

        val result = repository.getDemarkData("005930", 200)

        assertTrue("빈 데이터에서 emptyList", result.isEmpty())
    }

    @Test
    fun `getDemarkData - UseCase 예외 시 emptyList 반환`() = runTest {
        val entities = (1..10).map { i ->
            AnalysisCacheEntity("005930", "202501%02d".format(i), 50000000000L, 1000L, -500L, 50000)
        }
        coEvery { analysisCacheDao.getRecentByTicker("005930", 200) } returns entities
        coEvery { calcDemarkTDUseCase.execute(any(), any()) } throws RuntimeException("DeMark 오류")

        val result = repository.getDemarkData("005930", 200)

        assertTrue("예외 시 emptyList", result.isEmpty())
    }

    @Test
    fun `getDemarkData - 정상 5개 이상 데이터 시 UseCase 호출`() = runTest {
        val entities = (1..10).map { i ->
            AnalysisCacheEntity("005930", "202501%02d".format(i), 50000000000L, 1000L, -500L, 50000 + i * 100)
        }
        val expectedDemarks = listOf(
            DemarkTDRow("20250101", 50100, 50.0, 3, 0)
        )
        coEvery { analysisCacheDao.getRecentByTicker("005930", 200) } returns entities
        coEvery { calcDemarkTDUseCase.execute(any(), DemarkPeriodType.DAILY) } returns expectedDemarks

        val result = repository.getDemarkData("005930", 200)

        assertEquals(1, result.size)
        coVerify { calcDemarkTDUseCase.execute(any(), DemarkPeriodType.DAILY) }
    }

    // ─── getDailyPrices ───

    @Test
    fun `getDailyPrices - 엔티티를 DailyTrading으로 정확히 매핑`() = runTest {
        val entities = listOf(
            AnalysisCacheEntity("005930", "20250102", 51000000000L, 2000L, -300L, 51000),
            AnalysisCacheEntity("005930", "20250101", 50000000000L, 1000L, -500L, 50000)
        )
        coEvery { analysisCacheDao.getRecentByTicker("005930", 200) } returns entities

        val result = repository.getDailyPrices("005930", 200)

        // 날짜 오름차순 정렬 확인
        assertEquals("20250101", result[0].date)
        assertEquals("20250102", result[1].date)

        // 필드 매핑 확인
        assertEquals(50000000000L, result[0].marketCap)
        assertEquals(1000L, result[0].foreignNetBuy)
        assertEquals(-500L, result[0].instNetBuy)
        assertEquals(50000, result[0].closePrice)
    }

    @Test
    fun `getDailyPrices - 빈 엔티티 시 emptyList`() = runTest {
        coEvery { analysisCacheDao.getRecentByTicker("005930", 100) } returns emptyList()

        val result = repository.getDailyPrices("005930", 100)

        assertTrue("빈 엔티티에서 emptyList", result.isEmpty())
    }

    // ─── getFundamentalData ───

    @Test
    fun `getFundamentalData - 엔티티를 FundamentalSnapshot으로 정확히 매핑`() = runTest {
        val entities = listOf(
            FundamentalCacheEntity("005930", "20250101", 50000L, 5000L, 10.0, 62500L, 0.8, 1500L, 2.0)
        )
        coEvery { fundamentalCacheDao.getRecentByTicker("005930", 200) } returns entities

        val result = repository.getFundamentalData("005930", 200)

        assertEquals(1, result.size)
        assertEquals("20250101", result[0].date)
        assertEquals(50000L, result[0].close)
        assertEquals(10.0, result[0].per, 0.001)
        assertEquals(0.8, result[0].pbr, 0.001)
        assertEquals(5000L, result[0].eps)
        assertEquals(62500L, result[0].bps)
        assertEquals(2.0, result[0].dividendYield, 0.001)
    }

    @Test
    fun `getFundamentalData - 빈 데이터 시 emptyList`() = runTest {
        coEvery { fundamentalCacheDao.getRecentByTicker("005930", 200) } returns emptyList()

        val result = repository.getFundamentalData("005930", 200)

        assertTrue("빈 펀더멘털 데이터", result.isEmpty())
    }

    @Test
    fun `getFundamentalData - 날짜 오름차순 정렬 확인`() = runTest {
        val entities = listOf(
            FundamentalCacheEntity("005930", "20250103", 52000L, 5200L, 10.0, 65000L, 0.8, 1500L, 2.0),
            FundamentalCacheEntity("005930", "20250101", 50000L, 5000L, 10.0, 62500L, 0.8, 1500L, 2.0)
        )
        coEvery { fundamentalCacheDao.getRecentByTicker("005930", 200) } returns entities

        val result = repository.getFundamentalData("005930", 200)

        assertEquals("20250101", result[0].date)
        assertEquals("20250103", result[1].date)
    }

    // ─── getSectorEtfReturns ───

    @Test
    fun `getSectorEtfReturns - 항상 emptyList 반환`() = runTest {
        val result = repository.getSectorEtfReturns("005930", 200)

        assertTrue("미구현 기능은 emptyList", result.isEmpty())
    }

    // ─── getEtfHoldingCount ───

    @Test
    fun `getEtfHoldingCount - DAO 값 그대로 반환`() = runTest {
        coEvery { etfDao.getEtfCountForStock("005930") } returns 15

        val result = repository.getEtfHoldingCount("005930")

        assertEquals(15, result)
    }

    @Test
    fun `getEtfHoldingCount - ETF 없는 종목은 0`() = runTest {
        coEvery { etfDao.getEtfCountForStock("000000") } returns 0

        val result = repository.getEtfHoldingCount("000000")

        assertEquals(0, result)
    }

    // ─── getEtfAmountTrend ───

    @Test
    fun `getEtfAmountTrend - 빈 트렌드 시 emptyList`() = runTest {
        coEvery { etfDao.getStockAggregatedTrend("005930") } returns emptyList()

        val result = repository.getEtfAmountTrend("005930")

        assertTrue("빈 트렌드", result.isEmpty())
    }

    @Test
    fun `getEtfAmountTrend - 정상 매핑 확인`() = runTest {
        val trend = listOf(
            StockAggregatedTimePoint("20250101", 1000000L, 5, 0.05, 0.03),
            StockAggregatedTimePoint("20250102", 1200000L, 6, 0.06, 0.04)
        )
        coEvery { etfDao.getStockAggregatedTrend("005930") } returns trend

        val result = repository.getEtfAmountTrend("005930")

        assertEquals(2, result.size)
        assertEquals("20250101", result[0].date)
        assertEquals(1000000L, result[0].totalAmount)
        assertEquals(5, result[0].etfCount)
    }

    // ─── getStockName ───

    @Test
    fun `getStockName - 존재하는 종목명 반환`() = runTest {
        coEvery { stockMasterDao.getStockName("005930") } returns "삼성전자"

        val result = repository.getStockName("005930")

        assertEquals("삼성전자", result)
    }

    @Test
    fun `getStockName - 미등록 종목은 null`() = runTest {
        coEvery { stockMasterDao.getStockName("999999") } returns null

        val result = repository.getStockName("999999")

        assertNull(result)
    }
}
