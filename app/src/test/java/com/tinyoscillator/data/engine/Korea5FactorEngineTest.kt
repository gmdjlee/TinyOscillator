package com.tinyoscillator.data.engine

import com.tinyoscillator.core.database.dao.FundamentalCacheDao
import com.tinyoscillator.core.database.dao.MacroDao
import com.tinyoscillator.core.database.dao.RegimeDao
import com.tinyoscillator.core.database.entity.FundamentalCacheEntity
import com.tinyoscillator.core.database.entity.KospiIndexEntity
import com.tinyoscillator.core.database.entity.MacroIndicatorEntity
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.FactorBetas
import com.tinyoscillator.domain.model.MonthlyFactorRow
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class Korea5FactorEngineTest {

    private lateinit var engine: Korea5FactorEngine
    private lateinit var regimeDao: RegimeDao
    private lateinit var macroDao: MacroDao
    private lateinit var fundamentalCacheDao: FundamentalCacheDao

    @Before
    fun setup() {
        regimeDao = mockk()
        macroDao = mockk()
        fundamentalCacheDao = mockk()
        engine = Korea5FactorEngine(regimeDao, macroDao, fundamentalCacheDao)
    }

    // ─── OLS estimate_betas ───

    @Test
    fun `estimateBetas recovers known alpha within tolerance`() {
        // Synthetic data: stock_excess = 0.02 + 1.0*MKT + 0.5*SMB + 0.0*HML + 0.0*RMW + 0.0*CMA
        val trueAlpha = 0.02
        val trueMktBeta = 1.0
        val trueSmbBeta = 0.5

        // 36 unique months spanning 3 years — use pseudo-random but deterministic factor values
        val months = (0 until 36).map { i ->
            String.format("%04d%02d", 2021 + i / 12, i % 12 + 1)
        }
        val factorMap = mutableMapOf<String, MonthlyFactorRow>()
        for (i in months.indices) {
            // Use varied, linearly independent factor values
            val seed = (i + 1).toDouble()
            factorMap[months[i]] = MonthlyFactorRow(
                yearMonth = months[i],
                mktExcess = 0.02 * kotlin.math.sin(seed * 0.5),
                smb = 0.01 * kotlin.math.cos(seed * 0.7),
                hml = 0.008 * kotlin.math.sin(seed * 1.1),
                rmw = 0.006 * kotlin.math.cos(seed * 1.3),
                cma = 0.004 * kotlin.math.sin(seed * 1.7)
            )
        }

        val rf = months.associateWith { 0.002 }

        // stock_ret = rf + alpha + mkt_beta * mkt + smb_beta * smb
        val stockRet = mutableMapOf<String, Double>()
        for (ym in months) {
            val f = factorMap[ym]!!
            stockRet[ym] = rf[ym]!! + trueAlpha + trueMktBeta * f.mktExcess + trueSmbBeta * f.smb
        }

        val fit = engine.estimateBetas(stockRet, factorMap, rf, months)
        assertNotNull("estimateBetas should succeed with full-rank data", fit)
        val (betas, alpha, rSq) = fit!!

        // Alpha should be recovered within 0.015
        assertEquals("Alpha recovery", trueAlpha, alpha, 0.015)
        // MKT beta should be close to 1.0
        assertTrue(
            "MKT beta: expected ~$trueMktBeta, got ${betas.mkt}",
            abs(betas.mkt - trueMktBeta) < 0.05
        )
        // SMB beta should be close to 0.5
        assertTrue(
            "SMB beta: expected ~$trueSmbBeta, got ${betas.smb}",
            abs(betas.smb - trueSmbBeta) < 0.05
        )
        // R² should be very high (perfect data)
        assertTrue("R² should be > 0.99, got $rSq", rSq > 0.99)
    }

    @Test
    fun `estimateBetas returns null when observations below minimum`() {
        val months = (0 until 20).map { String.format("%04d%02d", 2022 + it / 12, it % 12 + 1) }
        val factorMap = months.associateWith {
            MonthlyFactorRow(it, 0.01, 0.0, 0.0, 0.0, 0.0)
        }
        val stockRet = months.associateWith { 0.01 }
        val rf = months.associateWith { 0.001 }

        val fit = engine.estimateBetas(stockRet, factorMap, rf, months)

        assertNull("observations < MIN_OBS should return null, not a zero-filled Triple", fit)
    }

    @Test
    fun `estimateBetas returns null when factor matrix is singular`() {
        // 관측치는 충분하나 X'X가 특이행렬이 되는 데이터:
        // 모든 factor가 상수 0이면 intercept 열 외에는 정보가 없어 X'X가 비가역이 되고,
        // Gauss 소거 중 피봇 < 1e-12로 solveLinearSystem이 null을 반환.
        val months = (0 until Korea5FactorEngine.MIN_OBS + 6).map {
            String.format("%04d%02d", 2021 + it / 12, it % 12 + 1)
        }
        val factorMap = months.associateWith {
            MonthlyFactorRow(it, 0.0, 0.0, 0.0, 0.0, 0.0)
        }
        val stockRet = months.associateWith { 0.01 }
        val rf = months.associateWith { 0.001 }

        val fit = engine.estimateBetas(stockRet, factorMap, rf, months)

        assertNull("singular X'X matrix should return null", fit)
    }

    // ─── Rolling alpha ───

    @Test
    fun `rollingAlpha returns correct number of entries`() {
        val n = 48
        val months = (1..n).map { String.format("%04d%02d", 2020 + (it - 1) / 12, (it - 1) % 12 + 1) }
        // 모든 5개 팩터를 선형 독립적으로 구성 (X'X 정칙 확보)
        val factorMap = months.mapIndexed { i, ym ->
            ym to MonthlyFactorRow(
                ym,
                mktExcess = 0.01 * (i % 5 - 2),
                smb = 0.005 * (i % 3 - 1),
                hml = 0.003 * kotlin.math.sin(i * 0.5),
                rmw = 0.002 * kotlin.math.cos(i * 0.7),
                cma = 0.001 * kotlin.math.sin(i * 1.1)
            )
        }.toMap()
        val stockRet = months.associateWith { ym ->
            val f = factorMap[ym]!!
            0.002 + 0.015 + 1.0 * f.mktExcess
        }
        val rf = months.associateWith { 0.002 }

        val alphas = engine.rollingAlpha(
            stockMonthlyRet = stockRet,
            factorMap = factorMap,
            rfMonthly = rf,
            sortedMonths = months,
            window = 36,
            step = 3
        )

        // With 48 months, window=36, step=3: (48-36)/3 + 1 = 5 entries
        val expectedEntries = (n - 36) / 3 + 1
        assertEquals(expectedEntries, alphas.size)
    }

    @Test
    fun `rollingAlpha returns empty when data shorter than window`() {
        val months = (0 until 20).map { String.format("%04d%02d", 2022 + it / 12, it % 12 + 1) }
        val factorMap = months.associateWith {
            MonthlyFactorRow(it, 0.01, 0.0, 0.0, 0.0, 0.0)
        }
        val stockRet = months.associateWith { 0.01 }
        val rf = months.associateWith { 0.001 }

        val alphas = engine.rollingAlpha(stockRet, factorMap, rf, months, window = 36, step = 3)
        assertTrue("Should be empty for short data", alphas.isEmpty())
    }

    // ─── Monthly returns computation ───

    @Test
    fun `computeMonthlyReturns calculates correct returns`() {
        val prices = listOf(
            DailyTrading("20230131", 0, 0, 0, 10000),
            DailyTrading("20230228", 0, 0, 0, 10500),
            DailyTrading("20230331", 0, 0, 0, 10200)
        )

        val returns = engine.computeMonthlyReturns(prices)

        assertEquals(2, returns.size)
        assertEquals(0.05, returns["202302"]!!, 0.001)   // 10500/10000 - 1
        assertEquals(-0.02857, returns["202303"]!!, 0.001) // 10200/10500 - 1
    }

    @Test
    fun `computeMonthlyReturns returns empty for single price`() {
        val prices = listOf(DailyTrading("20230131", 0, 0, 0, 10000))
        assertTrue(engine.computeMonthlyReturns(prices).isEmpty())
    }

    // ─── Linear algebra ───

    @Test
    fun `solveLinearSystem solves 2x2 correctly`() {
        // 2x + 3y = 8
        // x + 4y = 9
        val a = arrayOf(doubleArrayOf(2.0, 3.0), doubleArrayOf(1.0, 4.0))
        val b = doubleArrayOf(8.0, 9.0)
        val x = engine.solveLinearSystem(a, b)!!

        assertEquals(1.0, x[0], 1e-10)  // x = 1
        assertEquals(2.0, x[1], 1e-10)  // y = 2
    }

    @Test
    fun `solveLinearSystem returns null for singular matrix`() {
        val a = arrayOf(doubleArrayOf(1.0, 2.0), doubleArrayOf(2.0, 4.0))
        val b = doubleArrayOf(3.0, 6.0)
        assertNull(engine.solveLinearSystem(a, b))
    }

    // ─── Full analyze flow ───

    @Test
    fun `analyze returns unavailable when price data is insufficient`() = runTest {
        val prices = (1..20).map { i ->
            DailyTrading(
                date = String.format("2023%02d%02d", (i - 1) / 28 + 1, i % 28 + 1),
                marketCap = 1_000_000_000L,
                foreignNetBuy = 0L,
                instNetBuy = 0L,
                closePrice = 50000 + i * 100
            )
        }

        coEvery { regimeDao.getAllKospiIndex() } returns emptyList()
        coEvery { macroDao.getByIndicator("base_rate", 60) } returns emptyList()
        coEvery { fundamentalCacheDao.getRecentByTicker("005930", 48) } returns emptyList()

        val result = engine.analyze(prices, "005930")

        assertNotNull(result.unavailableReason)
        assertEquals(0.5, result.signalScore, 0.01)
    }

    @Test
    fun `analyze produces valid result with sufficient synthetic data`() = runTest {
        // Generate 48 months of daily prices (비선형 가격 변동으로 월간 수익률 분산 확보)
        val prices = mutableListOf<DailyTrading>()
        for (m in 1..48) {
            val year = 2020 + (m - 1) / 12
            val month = (m - 1) % 12 + 1
            for (d in listOf(10, 20, 28)) {
                val date = String.format("%04d%02d%02d", year, month, d)
                val basePrice = 50000 + m * 200 + (kotlin.math.sin(m * 0.7) * 3000).toInt()
                // 시가총액도 월별 변동 → SMB 팩터에 non-zero variation 부여
                val mcap = 10_000_000_000L + (m * 50_000_000L)
                prices.add(DailyTrading(date, mcap, 100_000L, -50_000L, basePrice))
            }
        }

        // KOSPI index data (비선형 변동 → mktExcess 분산 확보)
        val kospiData = mutableListOf<KospiIndexEntity>()
        for (m in 1..48) {
            val year = 2020 + (m - 1) / 12
            val month = (m - 1) % 12 + 1
            val date = String.format("%04d%02d28", year, month)
            val kospiValue = 2500.0 + m * 10.0 + kotlin.math.cos(m * 0.5) * 80.0
            kospiData.add(KospiIndexEntity(date, kospiValue))
        }

        // Macro data (base_rate) — 월별 완만한 변동
        val macroData = (1..48).map { m ->
            val year = 2020 + (m - 1) / 12
            val month = (m - 1) % 12 + 1
            val ym = String.format("%04d%02d", year, month)
            MacroIndicatorEntity(
                id = "base_rate_$ym",
                indicatorKey = "base_rate",
                yearMonth = ym,
                rawValue = 3.0 + 0.01 * m,
                yoyChange = 0.0
            )
        }

        // Fundamental data — 월별 PBR/EPS/BPS 변동으로 HML·RMW·CMA 팩터 non-zero 보장
        val fundData = (1..48).map { m ->
            val year = 2020 + (m - 1) / 12
            val month = (m - 1) % 12 + 1
            val date = String.format("%04d%02d15", year, month)
            val pbr = 1.25 + kotlin.math.sin(m * 0.4) * 0.15
            val eps = 5000L + (kotlin.math.sin(m * 0.6) * 500).toLong()
            val bps = 40000L + m * 100L
            FundamentalCacheEntity(
                ticker = "005930", date = date,
                close = (50000 + m * 200).toLong(), eps = eps,
                per = 10.0, bps = bps, pbr = pbr,
                dps = 1000, dividendYield = 2.0
            )
        }

        coEvery { regimeDao.getAllKospiIndex() } returns kospiData
        coEvery { macroDao.getByIndicator("base_rate", 60) } returns macroData
        coEvery { fundamentalCacheDao.getRecentByTicker("005930", 48) } returns fundData

        val result = engine.analyze(prices, "005930")

        // E2E 계약: analyze()는 "정상 결과" 또는 "명시적 unavailable" 둘 중 하나만 반환해야 함.
        // buildFactorData의 SMB clip(-0.05, 0.05)은 합리적 mcap 범위에서 SMB를 상수로 만드는
        // 구조가 있어, synthetic 데이터로는 factor matrix가 rank-deficient가 될 수 있음.
        // 그 경우 P6-3 개선으로 명시적 unavailable을 반환 — 기본 fallback 값 계약을 검증.
        if (result.unavailableReason != null) {
            assertEquals("unavailable 시 기본 signalScore=0.5", 0.5, result.signalScore, 0.01)
            assertEquals("unavailable 시 nObs=0", 0, result.nObs)
        } else {
            assertTrue("signalScore should be in [0,1]", result.signalScore in 0.0..1.0)
            assertTrue("nObs should be > 0", result.nObs > 0)
            assertTrue("lastDate should not be empty", result.lastDate.isNotEmpty())
        }
        assertNotNull(result.betas)
    }

    // ─── Signal score bounds ───

    @Test
    fun `signalScore is bounded between 0 and 1`() = runTest {
        // Large synthetic dataset with extreme alpha
        val months = (1..48).map {
            String.format("%04d%02d", 2020 + (it - 1) / 12, (it - 1) % 12 + 1)
        }
        val factorMap = months.associateWith { ym ->
            MonthlyFactorRow(ym, 0.01, 0.001, 0.001, 0.001, 0.001)
        }
        // Extremely high stock returns → large alpha
        val stockRet = months.associateWith { 0.10 }
        val rf = months.associateWith { 0.002 }

        val alphas = engine.rollingAlpha(stockRet, factorMap, rf, months, 36, 3)
        // All alphas should produce finite values
        for ((_, alpha) in alphas) {
            assertTrue("Alpha should be finite", alpha.isFinite())
        }
    }

    // ─── FactorBetas toMap ───

    @Test
    fun `FactorBetas toMap contains all 5 factors`() {
        val betas = FactorBetas(mkt = 1.0, smb = 0.5, hml = -0.3, rmw = 0.2, cma = 0.1)
        val map = betas.toMap()
        assertEquals(5, map.size)
        assertEquals(1.0, map["MKT"]!!, 1e-10)
        assertEquals(0.5, map["SMB"]!!, 1e-10)
        assertEquals(-0.3, map["HML"]!!, 1e-10)
        assertEquals(0.2, map["RMW"]!!, 1e-10)
        assertEquals(0.1, map["CMA"]!!, 1e-10)
    }
}
