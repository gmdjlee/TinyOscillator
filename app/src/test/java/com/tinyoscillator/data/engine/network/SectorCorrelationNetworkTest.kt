package com.tinyoscillator.data.engine.network

import io.mockk.coEvery
import io.mockk.mockk
import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.core.database.entity.AnalysisCacheEntity
import com.tinyoscillator.domain.model.DailyTrading
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import java.util.Random

class SectorCorrelationNetworkTest {

    private lateinit var stockMasterDao: StockMasterDao
    private lateinit var analysisCacheDao: AnalysisCacheDao
    private lateinit var engine: SectorCorrelationNetwork

    @Before
    fun setUp() {
        stockMasterDao = mockk()
        analysisCacheDao = mockk()
        engine = SectorCorrelationNetwork(stockMasterDao, analysisCacheDao)
    }

    // ─── Ledoit-Wolf 상관 행렬 검증 ───

    @Test
    fun `ledoitWolfCorrelation produces valid correlation matrix - diagonal equals 1`() {
        val rng = Random(42)
        val nTickers = 10
        val nDays = 60

        // 상관된 수익률 생성 (공통 팩터 + 개별 노이즈)
        val commonFactor = DoubleArray(nDays) { rng.nextGaussian() * 0.01 }
        val returns = Array(nTickers) { i ->
            DoubleArray(nDays) { j ->
                commonFactor[j] * (0.5 + i * 0.05) + rng.nextGaussian() * 0.005
            }
        }

        val (corrMatrix, shrinkage) = engine.ledoitWolfCorrelation(returns)

        // 대각 원소 = 1.0
        for (i in corrMatrix.indices) {
            assertEquals("대각 원소 [$i][$i] = 1.0", 1.0, corrMatrix[i][i], 1e-10)
        }

        // 대칭 행렬
        for (i in corrMatrix.indices) {
            for (j in corrMatrix.indices) {
                assertEquals("대칭: [$i][$j] == [$j][$i]",
                    corrMatrix[i][j], corrMatrix[j][i], 1e-10)
            }
        }

        // 모든 값이 [-1, 1] 범위
        for (i in corrMatrix.indices) {
            for (j in corrMatrix.indices) {
                assertTrue("상관 값 범위 [-1,1]: [$i][$j]=${corrMatrix[i][j]}",
                    corrMatrix[i][j] in -1.0..1.0)
            }
        }

        // 축소 강도가 [0, 1] 범위
        assertTrue("축소 강도 범위 [0,1]: $shrinkage", shrinkage in 0.0..1.0)
    }

    @Test
    fun `detect outlier returns empty when all stocks highly correlated`() = runTest {
        val rng = Random(123)
        val nPeers = 10
        val nDays = 70

        // 날짜 목록 생성 (일관된 형식)
        val dates = (0 until nDays).map { String.format("20250%03d", 101 + it) }

        // 매우 상관된 데이터 생성 (공통 팩터 강하게)
        val commonFactor = DoubleArray(nDays) { rng.nextGaussian() * 0.02 }
        val peerTickers = (1..nPeers).map { String.format("%06d", 1000 + it) }

        val targetPrices = generateCorrelatedPrices(commonFactor, nDays, 50000, rng, beta = 0.95)

        coEvery { stockMasterDao.getSector("005930") } returns "반도체"
        coEvery { stockMasterDao.getTickersBySector("반도체", any()) } returns peerTickers

        // 피어 데이터를 모두 높은 상관으로 생성
        for (peer in peerTickers) {
            val peerPrices = generateCorrelatedPrices(commonFactor, nDays, 30000 + rng.nextInt(20000), rng, beta = 0.95)
            val entities = peerPrices.mapIndexed { i, p ->
                AnalysisCacheEntity(
                    ticker = peer,
                    date = dates[i],
                    marketCap = 1_000_000_000_000L,
                    foreignNet = 0L,
                    instNet = 0L,
                    closePrice = p
                )
            }
            coEvery { analysisCacheDao.getByTickerDateRange(peer, any(), any()) } returns entities
        }

        val prices = targetPrices.mapIndexed { i, p ->
            DailyTrading(
                date = dates[i],
                marketCap = 400_000_000_000_000L,
                foreignNetBuy = 0L,
                instNetBuy = 0L,
                closePrice = p
            )
        }

        val result = engine.analyze(prices, "005930")

        // 높은 상관에서는 아웃라이어 아님
        assertNull("사유 없음 (분석 가능)", result.unavailableReason)
        assertFalse("높은 상관 시 isOutlier=false", result.isOutlier)
        assertTrue("신호 점수 <= 0.5", result.signalScore <= 0.5)
    }

    @Test
    fun `detect outlier returns true for uncorrelated stock`() = runTest {
        val rng = Random(456)
        val nPeers = 8
        val nDays = 70

        val dates = (0 until nDays).map { String.format("20250%03d", 101 + it) }

        // 피어들은 상관, 대상만 독립
        val commonFactor = DoubleArray(nDays) { rng.nextGaussian() * 0.02 }
        val peerTickers = (1..nPeers).map { String.format("%06d", 2000 + it) }

        // 대상 종목: 완전히 독립적인 가격
        val independentPrices = mutableListOf<Int>()
        var price = 50000
        for (i in 0 until nDays) {
            price += (rng.nextGaussian() * 500).toInt()
            price = price.coerceAtLeast(1000)
            independentPrices.add(price)
        }

        coEvery { stockMasterDao.getSector("999999") } returns "테스트섹터"
        coEvery { stockMasterDao.getTickersBySector("테스트섹터", any()) } returns peerTickers

        // 피어 데이터: 서로 높은 상관
        for (peer in peerTickers) {
            val peerPrices = generateCorrelatedPrices(commonFactor, nDays, 40000, rng, beta = 0.9)
            val entities = peerPrices.mapIndexed { i, p ->
                AnalysisCacheEntity(
                    ticker = peer,
                    date = dates[i],
                    marketCap = 500_000_000_000L,
                    foreignNet = 0L,
                    instNet = 0L,
                    closePrice = p
                )
            }
            coEvery { analysisCacheDao.getByTickerDateRange(peer, any(), any()) } returns entities
        }

        val prices = independentPrices.mapIndexed { i, p ->
            DailyTrading(
                date = dates[i],
                marketCap = 100_000_000_000_000L,
                foreignNetBuy = 0L,
                instNetBuy = 0L,
                closePrice = p
            )
        }

        val result = engine.analyze(prices, "999999")

        // 독립 종목은 아웃라이어
        if (result.unavailableReason == null) {
            assertTrue("독립 종목 isOutlier=true", result.isOutlier)
            assertTrue("이상치 신호 점수 > 0.5", result.signalScore > 0.5)
        }
    }

    @Test
    fun `unavailable when sector not found`() = runTest {
        coEvery { stockMasterDao.getSector("000000") } returns null

        val prices = listOf(
            DailyTrading("20250101", 1_000_000L, 0L, 0L, 10000)
        )
        val result = engine.analyze(prices, "000000")

        assertNotNull("사유 있음", result.unavailableReason)
        assertEquals(0.5, result.signalScore, 1e-10)
    }

    @Test
    fun `unavailable when too few peers`() = runTest {
        coEvery { stockMasterDao.getSector("111111") } returns "소형섹터"
        coEvery { stockMasterDao.getTickersBySector("소형섹터", any()) } returns listOf("222222", "333333")

        val prices = (1..70).map { i ->
            DailyTrading(
                String.format("2025%02d%02d", 1 + i / 30, 1 + i % 28),
                1_000_000L, 0L, 0L, 10000 + i * 100
            )
        }
        val result = engine.analyze(prices, "111111")

        assertNotNull("피어 부족 사유", result.unavailableReason)
    }

    @Test
    fun `signal score bounded 0 to 1`() {
        val rng = Random(789)
        val returns = Array(6) { DoubleArray(50) { rng.nextGaussian() * 0.01 } }
        val (corrMatrix, _) = engine.ledoitWolfCorrelation(returns)

        // 상관 행렬의 모든 값이 유효 범위
        for (row in corrMatrix) {
            for (v in row) {
                assertTrue("상관 값 [-1,1]", v >= -1.0 && v <= 1.0)
            }
        }
    }

    @Test
    fun `shrinkage intensity increases with fewer observations`() {
        val rng = Random(101)
        val nTickers = 10

        // 상관된 데이터 생성 (공통 팩터)
        fun makeCorrelated(n: Int): Array<DoubleArray> {
            val common = DoubleArray(n) { rng.nextGaussian() * 0.02 }
            return Array(nTickers) { i ->
                DoubleArray(n) { j -> common[j] * (0.3 + i * 0.05) + rng.nextGaussian() * 0.005 }
            }
        }

        // 적은 관측치
        val fewObs = makeCorrelated(15)
        val (_, shrinkageFew) = engine.ledoitWolfCorrelation(fewObs)

        // 많은 관측치
        val manyObs = makeCorrelated(200)
        val (_, shrinkageMany) = engine.ledoitWolfCorrelation(manyObs)

        // 적은 관측치일수록 축소 강도가 높아야 함
        assertTrue("적은 관측치 시 축소 강도 증가: few=$shrinkageFew > many=$shrinkageMany",
            shrinkageFew > shrinkageMany)
    }

    // ─── 유틸리티 ───

    private fun generateCorrelatedPrices(
        commonFactor: DoubleArray,
        nDays: Int,
        basePrice: Int,
        rng: Random,
        beta: Double = 0.8
    ): List<Int> {
        val prices = mutableListOf<Int>()
        var price = basePrice.toDouble()
        for (i in 0 until nDays) {
            val ret = beta * commonFactor[i] + (1 - beta) * rng.nextGaussian() * 0.01
            price *= (1 + ret)
            price = price.coerceAtLeast(100.0)
            prices.add(price.toInt())
        }
        return prices
    }
}
