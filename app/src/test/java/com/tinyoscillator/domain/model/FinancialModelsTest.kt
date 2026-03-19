package com.tinyoscillator.domain.model

import org.junit.Assert.*
import org.junit.Test

/**
 * FinancialModels 단위 테스트
 *
 * formatNumber, formatPercent, FinancialPeriod, convertYtdToQuarterly (via toSummary),
 * calculateYoYGrowthRates (via toSummary), toCache/toData 라운드트립, trimToLast,
 * FinancialSummary computed properties 검증
 */
class FinancialModelsTest {

    private val TOLERANCE = 1e-6

    // ========== formatNumber 테스트 ==========

    @Test
    fun `formatNumber - 조 단위 표시`() {
        // 10,000억 = 1조
        assertEquals("1.0조", formatNumber(10_000L))
    }

    @Test
    fun `formatNumber - 조 단위 나머지 포함`() {
        // 15,300억 = 1.5조 (나머지 5300 / 1000 = 5)
        assertEquals("1.5조", formatNumber(15_300L))
    }

    @Test
    fun `formatNumber - 대규모 조 단위`() {
        assertEquals("10.0조", formatNumber(100_000L))
    }

    @Test
    fun `formatNumber - 천억 단위 표시`() {
        // 1000억 = 1.0천억
        assertEquals("1.0천억", formatNumber(1_000L))
    }

    @Test
    fun `formatNumber - 천억 단위 나머지 포함`() {
        // 5600억 = 5.6천억
        assertEquals("5.6천억", formatNumber(5_600L))
    }

    @Test
    fun `formatNumber - 억 단위 표시`() {
        assertEquals("999억", formatNumber(999L))
    }

    @Test
    fun `formatNumber - 영`() {
        assertEquals("0억", formatNumber(0L))
    }

    @Test
    fun `formatNumber - 음수 조 단위`() {
        assertEquals("-1.5조", formatNumber(-15_300L))
    }

    @Test
    fun `formatNumber - 음수 천억 단위`() {
        assertEquals("-3.2천억", formatNumber(-3_200L))
    }

    @Test
    fun `formatNumber - 음수 억 단위`() {
        assertEquals("-50억", formatNumber(-50L))
    }

    @Test
    fun `formatNumber - 경계값 10000 (조 시작)`() {
        assertEquals("1.0조", formatNumber(10_000L))
    }

    @Test
    fun `formatNumber - 경계값 9999 (천억 최대)`() {
        assertEquals("9.9천억", formatNumber(9_999L))
    }

    @Test
    fun `formatNumber - 경계값 1000 (천억 시작)`() {
        assertEquals("1.0천억", formatNumber(1_000L))
    }

    @Test
    fun `formatNumber - 경계값 999 (억 최대)`() {
        assertEquals("999억", formatNumber(999L))
    }

    // ========== formatPercent 테스트 ==========

    @Test
    fun `formatPercent - 양수`() {
        assertEquals("12.3%", formatPercent(12.3))
    }

    @Test
    fun `formatPercent - 음수`() {
        assertEquals("-5.7%", formatPercent(-5.7))
    }

    @Test
    fun `formatPercent - 영`() {
        assertEquals("0.0%", formatPercent(0.0))
    }

    @Test
    fun `formatPercent - 소수점 반올림`() {
        assertEquals("33.3%", formatPercent(33.333))
    }

    // ========== FinancialPeriod.fromYearMonth 테스트 ==========

    @Test
    fun `fromYearMonth - Q1 (3월)`() {
        val fp = FinancialPeriod.fromYearMonth("202403")
        assertEquals(2024, fp.year)
        assertEquals(3, fp.yearMonth.substring(4, 6).toInt())
        assertEquals(1, fp.quarter)
    }

    @Test
    fun `fromYearMonth - Q2 (6월)`() {
        val fp = FinancialPeriod.fromYearMonth("202406")
        assertEquals(2024, fp.year)
        assertEquals(2, fp.quarter)
    }

    @Test
    fun `fromYearMonth - Q3 (9월)`() {
        val fp = FinancialPeriod.fromYearMonth("202309")
        assertEquals(2023, fp.year)
        assertEquals(3, fp.quarter)
    }

    @Test
    fun `fromYearMonth - Q4 (12월)`() {
        val fp = FinancialPeriod.fromYearMonth("202312")
        assertEquals(2023, fp.year)
        assertEquals(4, fp.quarter)
    }

    @Test
    fun `fromYearMonth - 비분기 월은 quarter 0`() {
        val fp = FinancialPeriod.fromYearMonth("202401")
        assertEquals(2024, fp.year)
        assertEquals(0, fp.quarter)
    }

    // ========== FinancialPeriod.toDisplayString 테스트 ==========

    @Test
    fun `toDisplayString - short 형식`() {
        val fp = FinancialPeriod.fromYearMonth("202403")
        assertEquals("24.03", fp.toDisplayString(short = true))
    }

    @Test
    fun `toDisplayString - full 형식`() {
        val fp = FinancialPeriod.fromYearMonth("202312")
        assertEquals("2023.12", fp.toDisplayString(short = false))
    }

    @Test
    fun `toDisplayString - 기본값은 full 형식`() {
        val fp = FinancialPeriod.fromYearMonth("202406")
        assertEquals("2024.06", fp.toDisplayString())
    }

    // ========== convertYtdToQuarterly (toSummary 경유) 테스트 ==========

    @Test
    fun `toSummary - YTD를 분기별로 변환 (연속 분기)`() {
        // Q1=100, Q2=300(YTD) -> Q2 standalone=200, Q3=600(YTD) -> Q3 standalone=300
        val data = createFinancialDataWithIncomeStatements(
            mapOf(
                "202403" to 100L,
                "202406" to 300L,
                "202409" to 600L
            )
        )
        val summary = data.toSummary()

        assertEquals(3, summary.revenues.size)
        assertEquals(100L, summary.revenues[0]) // Q1: 그대로
        assertEquals(200L, summary.revenues[1]) // Q2: 300 - 100
        assertEquals(300L, summary.revenues[2]) // Q3: 600 - 300
    }

    @Test
    fun `toSummary - Q1은 YTD 값 그대로 사용`() {
        val data = createFinancialDataWithIncomeStatements(
            mapOf("202403" to 500L)
        )
        val summary = data.toSummary()
        assertEquals(500L, summary.revenues[0])
    }

    @Test
    fun `toSummary - 빈 기간 리스트`() {
        val data = FinancialData(
            ticker = "005930", name = "삼성전자",
            periods = emptyList(),
            balanceSheets = emptyMap(), incomeStatements = emptyMap(),
            profitabilityRatios = emptyMap(), stabilityRatios = emptyMap(),
            growthRatios = emptyMap()
        )
        val summary = data.toSummary()
        assertTrue(summary.revenues.isEmpty())
    }

    @Test
    fun `toSummary - 이전 분기 누락 시 YTD 값 그대로 사용`() {
        // Q2만 있고 Q1이 없는 경우 -> YTD 값 그대로
        val data = createFinancialDataWithIncomeStatements(
            mapOf("202406" to 300L)
        )
        val summary = data.toSummary()
        assertEquals(300L, summary.revenues[0])
    }

    // ========== calculateYoYGrowthRates (toSummary 경유) 테스트 ==========

    @Test
    fun `toSummary - YoY 성장률 계산 (전년 동기 존재)`() {
        // netIncome = revenue / 20이므로
        // 2023 Q1 netIncome = 200/20 = 10, 2024 Q1 netIncome = 300/20 = 15
        // 성장률 = (15 - 10) / |10| * 100 = 50%
        val incomes = mapOf(
            "202303" to 200L,
            "202403" to 300L
        )
        val data = createFinancialDataWithIncomeStatements(incomes)
        val summary = data.toSummary()

        // API 성장률이 모두 0.0이고 netIncome 데이터가 있으면 자체 계산
        assertEquals(2, summary.netIncomeGrowthRates.size)
        assertEquals(0.0, summary.netIncomeGrowthRates[0], TOLERANCE) // 전년 데이터 없음
        assertEquals(50.0, summary.netIncomeGrowthRates[1], TOLERANCE) // (15-10)/10 * 100
    }

    @Test
    fun `toSummary - YoY 성장률 전년 데이터 없으면 0`() {
        val data = createFinancialDataWithIncomeStatements(
            mapOf("202403" to 200L)
        )
        val summary = data.toSummary()
        assertEquals(0.0, summary.netIncomeGrowthRates[0], TOLERANCE)
    }

    @Test
    fun `toSummary - YoY 성장률 전년 값이 0이면 0 반환 (0 나누기 방지)`() {
        val incomes = mapOf(
            "202303" to 0L,
            "202403" to 100L
        )
        val data = createFinancialDataWithIncomeStatements(incomes)
        val summary = data.toSummary()
        assertEquals(0.0, summary.netIncomeGrowthRates[1], TOLERANCE) // div by zero 방지
    }

    // ========== toCache / toData 라운드트립 테스트 ==========

    @Test
    fun `toCache와 toData 라운드트립 - 데이터 보존 검증`() {
        val original = createFullFinancialData()
        val cache = original.toCache()
        val restored = cache.toData()

        assertEquals(original.ticker, restored.ticker)
        assertEquals(original.name, restored.name)
        assertEquals(original.periods, restored.periods)
        assertEquals(original.periods.size, restored.periods.size)

        // BalanceSheet 검증
        for (period in original.periods) {
            val origBs = original.balanceSheets[period]!!
            val resBs = restored.balanceSheets[period]!!
            assertEquals(origBs.totalAssets, resBs.totalAssets)
            assertEquals(origBs.totalLiabilities, resBs.totalLiabilities)
            assertEquals(origBs.totalEquity, resBs.totalEquity)
        }

        // IncomeStatement 검증
        for (period in original.periods) {
            val origIs = original.incomeStatements[period]!!
            val resIs = restored.incomeStatements[period]!!
            assertEquals(origIs.revenue, resIs.revenue)
            assertEquals(origIs.operatingProfit, resIs.operatingProfit)
            assertEquals(origIs.netIncome, resIs.netIncome)
        }
    }

    @Test
    fun `toCache와 toData 라운드트립 - null 값 보존`() {
        val period = FinancialPeriod.fromYearMonth("202403")
        val original = FinancialData(
            ticker = "005930", name = "삼성전자",
            periods = listOf("202403"),
            balanceSheets = mapOf("202403" to BalanceSheet(
                period = period,
                currentAssets = null, fixedAssets = null, totalAssets = 1000L,
                currentLiabilities = null, fixedLiabilities = null, totalLiabilities = 500L,
                capital = null, capitalSurplus = null,
                retainedEarnings = null, totalEquity = 500L
            )),
            incomeStatements = mapOf("202403" to IncomeStatement(
                period = period,
                revenue = 100L, costOfSales = null, grossProfit = null,
                operatingProfit = null, ordinaryProfit = null, netIncome = 50L
            )),
            profitabilityRatios = emptyMap(),
            stabilityRatios = emptyMap(),
            growthRatios = emptyMap()
        )
        val restored = original.toCache().toData()
        val bs = restored.balanceSheets["202403"]!!
        assertNull(bs.currentAssets)
        assertNull(bs.fixedAssets)
        assertEquals(1000L, bs.totalAssets)
    }

    // ========== trimToLast 테스트 ==========

    @Test
    fun `trimToLast - n이 size보다 작을 때 마지막 n개 반환`() {
        val summary = createSummaryWithPeriods(8)
        val trimmed = summary.trimToLast(5)
        assertEquals(5, trimmed.periods.size)
        assertEquals(5, trimmed.revenues.size)
        assertEquals(5, trimmed.debtRatios.size)
    }

    @Test
    fun `trimToLast - n이 size보다 크거나 같으면 원본 반환`() {
        val summary = createSummaryWithPeriods(4)
        val trimmed = summary.trimToLast(10)
        assertSame(summary, trimmed) // 동일 객체 반환
    }

    @Test
    fun `trimToLast - n이 MIN_DISPLAY_QUARTERS보다 작으면 최소값 적용`() {
        val summary = createSummaryWithPeriods(8)
        val trimmed = summary.trimToLast(2) // MIN_DISPLAY_QUARTERS = 4로 강제
        assertEquals(4, trimmed.periods.size)
    }

    @Test
    fun `trimToLast - 모든 리스트가 동일하게 잘림`() {
        val summary = createSummaryWithPeriods(6)
        val trimmed = summary.trimToLast(4)
        assertEquals(4, trimmed.periods.size)
        assertEquals(4, trimmed.displayPeriods.size)
        assertEquals(4, trimmed.revenues.size)
        assertEquals(4, trimmed.operatingProfits.size)
        assertEquals(4, trimmed.netIncomes.size)
        assertEquals(4, trimmed.revenueGrowthRates.size)
        assertEquals(4, trimmed.operatingProfitGrowthRates.size)
        assertEquals(4, trimmed.netIncomeGrowthRates.size)
        assertEquals(4, trimmed.equityGrowthRates.size)
        assertEquals(4, trimmed.totalAssetsGrowthRates.size)
        assertEquals(4, trimmed.debtRatios.size)
        assertEquals(4, trimmed.currentRatios.size)
        assertEquals(4, trimmed.borrowingDependencies.size)
    }

    // ========== FinancialSummary computed properties 테스트 ==========

    @Test
    fun `hasProfitabilityData - 매출이 있으면 true`() {
        val summary = createEmptySummary().copy(revenues = listOf(100L, 0L))
        assertTrue(summary.hasProfitabilityData)
    }

    @Test
    fun `hasProfitabilityData - 모두 0이면 false`() {
        val summary = createEmptySummary().copy(
            revenues = listOf(0L), operatingProfits = listOf(0L), netIncomes = listOf(0L)
        )
        assertFalse(summary.hasProfitabilityData)
    }

    @Test
    fun `hasGrowthData - 성장률이 있으면 true`() {
        val summary = createEmptySummary().copy(revenueGrowthRates = listOf(5.0))
        assertTrue(summary.hasGrowthData)
    }

    @Test
    fun `hasGrowthData - 모두 0이면 false`() {
        val summary = createEmptySummary().copy(
            revenueGrowthRates = listOf(0.0),
            operatingProfitGrowthRates = listOf(0.0),
            netIncomeGrowthRates = listOf(0.0)
        )
        assertFalse(summary.hasGrowthData)
    }

    @Test
    fun `hasAssetGrowthData - 자산 성장률이 있으면 true`() {
        val summary = createEmptySummary().copy(equityGrowthRates = listOf(3.0))
        assertTrue(summary.hasAssetGrowthData)
    }

    @Test
    fun `hasStabilityData - 부채비율이 있으면 true`() {
        val summary = createEmptySummary().copy(debtRatios = listOf(120.0))
        assertTrue(summary.hasStabilityData)
    }

    @Test
    fun `hasStabilityData - 모두 0이면 false`() {
        val summary = createEmptySummary().copy(
            debtRatios = listOf(0.0),
            currentRatios = listOf(0.0),
            borrowingDependencies = listOf(0.0)
        )
        assertFalse(summary.hasStabilityData)
    }

    @Test
    fun `latestRevenue - 마지막 값 반환`() {
        val summary = createEmptySummary().copy(revenues = listOf(100L, 200L, 300L))
        assertEquals(300L, summary.latestRevenue)
    }

    @Test
    fun `latestRevenue - 빈 리스트면 null`() {
        val summary = createEmptySummary().copy(revenues = emptyList())
        assertNull(summary.latestRevenue)
    }

    @Test
    fun `latestOperatingProfit - 마지막 값 반환`() {
        val summary = createEmptySummary().copy(operatingProfits = listOf(10L, 20L, 30L))
        assertEquals(30L, summary.latestOperatingProfit)
    }

    @Test
    fun `latestNetIncome - 빈 리스트면 null`() {
        val summary = createEmptySummary().copy(netIncomes = emptyList())
        assertNull(summary.latestNetIncome)
    }

    @Test
    fun `latestDebtRatio - 마지막 값 반환`() {
        val summary = createEmptySummary().copy(debtRatios = listOf(100.0, 120.0))
        assertEquals(120.0, summary.latestDebtRatio!!, TOLERANCE)
    }

    @Test
    fun `latestCurrentRatio - 빈 리스트면 null`() {
        val summary = createEmptySummary().copy(currentRatios = emptyList())
        assertNull(summary.latestCurrentRatio)
    }

    // ========== 비연속 분기 edge case 테스트 ==========

    @Test
    fun `toSummary - 비연속 분기 (Q1 → Q3 건너뛰기)`() {
        // Q1=100, Q3=400(YTD) → Q3 standalone = 400-100 = 300 (비연속 경고)
        val data = createFinancialDataWithIncomeStatements(
            mapOf("202403" to 100L, "202409" to 400L)
        )
        val summary = data.toSummary()
        assertEquals(2, summary.revenues.size)
        assertEquals(100L, summary.revenues[0]) // Q1
        assertEquals(300L, summary.revenues[1]) // Q3: 400-100 (비연속이지만 계산됨)
    }

    @Test
    fun `toSummary - 다른 연도 분기 혼합 시 올바른 YTD 계산`() {
        val data = createFinancialDataWithIncomeStatements(
            mapOf(
                "202303" to 100L,
                "202306" to 250L,
                "202403" to 200L,
                "202406" to 500L
            )
        )
        val summary = data.toSummary()
        assertEquals(4, summary.revenues.size)
        assertEquals(100L, summary.revenues[0]) // 2023 Q1
        assertEquals(150L, summary.revenues[1]) // 2023 Q2: 250-100
        assertEquals(200L, summary.revenues[2]) // 2024 Q1
        assertEquals(300L, summary.revenues[3]) // 2024 Q2: 500-200
    }

    // ========== YoY 성장률 edge case 테스트 ==========

    @Test
    fun `toSummary - YoY 성장률 음수에서 양수 전환`() {
        val incomes = mapOf(
            "202303" to -200L, // 전년 Q1 적자
            "202403" to 100L   // 올해 Q1 흑자
        )
        val data = createFinancialDataWithIncomeStatements(incomes)
        val summary = data.toSummary()

        // netIncome: 전년=-10, 올해=5 → 성장률 = (5 - (-10)) / |(-10)| * 100 = 150%
        assertEquals(2, summary.netIncomeGrowthRates.size)
        assertEquals(150.0, summary.netIncomeGrowthRates[1], TOLERANCE)
    }

    // ========== formatNumber edge case 테스트 ==========

    @Test
    fun `formatNumber - 1억`() {
        assertEquals("1억", formatNumber(1L))
    }

    @Test
    fun `formatNumber - 매우 큰 조 단위`() {
        // 100조
        assertEquals("100.0조", formatNumber(1_000_000L))
    }

    // ========== Helper Functions ==========

    private fun createFinancialDataWithIncomeStatements(
        revenueByPeriod: Map<String, Long>
    ): FinancialData {
        val periods = revenueByPeriod.keys.toList().sorted()
        val incomeStatements = revenueByPeriod.mapValues { (ym, revenue) ->
            IncomeStatement(
                period = FinancialPeriod.fromYearMonth(ym),
                revenue = revenue,
                costOfSales = null,
                grossProfit = null,
                operatingProfit = revenue / 10,
                ordinaryProfit = null,
                netIncome = revenue / 20
            )
        }
        return FinancialData(
            ticker = "005930", name = "삼성전자",
            periods = periods,
            balanceSheets = emptyMap(),
            incomeStatements = incomeStatements,
            profitabilityRatios = emptyMap(),
            stabilityRatios = emptyMap(),
            growthRatios = emptyMap()
        )
    }

    private fun createFullFinancialData(): FinancialData {
        val periods = listOf("202303", "202306", "202309", "202312")
        val balanceSheets = periods.associate { ym ->
            ym to BalanceSheet(
                period = FinancialPeriod.fromYearMonth(ym),
                currentAssets = 1000L, fixedAssets = 2000L, totalAssets = 3000L,
                currentLiabilities = 500L, fixedLiabilities = 800L, totalLiabilities = 1300L,
                capital = 100L, capitalSurplus = 200L,
                retainedEarnings = 1400L, totalEquity = 1700L
            )
        }
        val incomeStatements = periods.associate { ym ->
            ym to IncomeStatement(
                period = FinancialPeriod.fromYearMonth(ym),
                revenue = 500L, costOfSales = 300L, grossProfit = 200L,
                operatingProfit = 100L, ordinaryProfit = 90L, netIncome = 80L
            )
        }
        val profitabilityRatios = periods.associate { ym ->
            ym to ProfitabilityRatios(
                period = FinancialPeriod.fromYearMonth(ym),
                operatingMargin = 20.0, netMargin = 16.0, roe = 15.0, roa = 10.0
            )
        }
        val stabilityRatios = periods.associate { ym ->
            ym to StabilityRatios(
                period = FinancialPeriod.fromYearMonth(ym),
                debtRatio = 76.5, currentRatio = 200.0, quickRatio = 150.0,
                borrowingDependency = 10.0, interestCoverageRatio = 5.0
            )
        }
        val growthRatios = periods.associate { ym ->
            ym to GrowthRatios(
                period = FinancialPeriod.fromYearMonth(ym),
                revenueGrowth = 5.0, operatingProfitGrowth = 8.0,
                netIncomeGrowth = 10.0, equityGrowth = 3.0, totalAssetsGrowth = 4.0
            )
        }
        return FinancialData(
            ticker = "005930", name = "삼성전자",
            periods = periods,
            balanceSheets = balanceSheets,
            incomeStatements = incomeStatements,
            profitabilityRatios = profitabilityRatios,
            stabilityRatios = stabilityRatios,
            growthRatios = growthRatios
        )
    }

    private fun createSummaryWithPeriods(count: Int): FinancialSummary {
        val periods = (1..count).map { String.format("2023%02d", (it * 3).coerceAtMost(12)) }
        return FinancialSummary(
            ticker = "005930", name = "삼성전자",
            periods = periods,
            displayPeriods = periods.map { "23.${it.substring(4)}" },
            revenues = List(count) { (it + 1) * 100L },
            operatingProfits = List(count) { (it + 1) * 10L },
            netIncomes = List(count) { (it + 1) * 5L },
            revenueGrowthRates = List(count) { it.toDouble() },
            operatingProfitGrowthRates = List(count) { it * 2.0 },
            netIncomeGrowthRates = List(count) { it * 1.5 },
            equityGrowthRates = List(count) { it * 0.5 },
            totalAssetsGrowthRates = List(count) { it * 0.3 },
            debtRatios = List(count) { 100.0 + it },
            currentRatios = List(count) { 200.0 - it },
            borrowingDependencies = List(count) { 10.0 + it }
        )
    }

    private fun createEmptySummary(): FinancialSummary {
        return FinancialSummary(
            ticker = "005930", name = "삼성전자",
            periods = emptyList(), displayPeriods = emptyList(),
            revenues = emptyList(), operatingProfits = emptyList(),
            netIncomes = emptyList(), revenueGrowthRates = emptyList(),
            operatingProfitGrowthRates = emptyList(),
            netIncomeGrowthRates = emptyList(),
            equityGrowthRates = emptyList(),
            totalAssetsGrowthRates = emptyList(),
            debtRatios = emptyList(), currentRatios = emptyList(),
            borrowingDependencies = emptyList()
        )
    }

    // ========== DuPont 분석 테스트 ==========

    @Test
    fun `toSummary - 듀퐁 순이익률이 올바르게 계산된다`() {
        // createFullFinancialData: Q1 revenue=500, netIncome=80 (YTD=quarterly for Q1)
        val data = createFullFinancialData()
        val summary = data.toSummary()

        // Q1: netIncome=80, revenue=500 → 순이익률 = 80/500*100 = 16.0%
        assertEquals(16.0, summary.netProfitMargins.first(), TOLERANCE)
    }

    @Test
    fun `toSummary - 듀퐁 총자산회전율이 올바르게 계산된다`() {
        val data = createFullFinancialData()
        val summary = data.toSummary()

        // Q1: quarterly revenue=500, totalAssets=3000 → 500/3000 = 0.1667
        val expected = 500.0 / 3000.0
        assertEquals(expected, summary.assetTurnovers.first(), TOLERANCE)
    }

    @Test
    fun `toSummary - 듀퐁 재무레버리지가 올바르게 계산된다`() {
        val data = createFullFinancialData()
        val summary = data.toSummary()

        // totalAssets=3000, totalEquity=1700 → 3000/1700 = 1.7647
        val expected = 3000.0 / 1700.0
        assertEquals(expected, summary.equityMultipliers.first(), TOLERANCE)
    }

    @Test
    fun `toSummary - 듀퐁 ROE가 API 값으로 설정된다`() {
        val data = createFullFinancialData()
        val summary = data.toSummary()

        // profitabilityRatios.roe = 15.0
        assertEquals(15.0, summary.roes.first(), TOLERANCE)
    }

    @Test
    fun `toSummary - 매출액 0일 때 순이익률은 0이다`() {
        val periods = listOf("202303")
        val data = FinancialData(
            ticker = "TEST", name = "테스트",
            periods = periods,
            balanceSheets = periods.associate {
                it to BalanceSheet(FinancialPeriod.fromYearMonth(it),
                    null, null, 1000L, null, null, null, null, null, null, 500L)
            },
            incomeStatements = periods.associate {
                it to IncomeStatement(FinancialPeriod.fromYearMonth(it),
                    0L, null, null, null, null, 100L)
            },
            profitabilityRatios = emptyMap(),
            stabilityRatios = emptyMap(),
            growthRatios = emptyMap()
        )
        val summary = data.toSummary()
        assertEquals(0.0, summary.netProfitMargins.first(), TOLERANCE)
    }

    @Test
    fun `toSummary - 총자산 0일 때 총자산회전율은 0이다`() {
        val periods = listOf("202303")
        val data = FinancialData(
            ticker = "TEST", name = "테스트",
            periods = periods,
            balanceSheets = periods.associate {
                it to BalanceSheet(FinancialPeriod.fromYearMonth(it),
                    null, null, 0L, null, null, null, null, null, null, 500L)
            },
            incomeStatements = periods.associate {
                it to IncomeStatement(FinancialPeriod.fromYearMonth(it),
                    1000L, null, null, null, null, 100L)
            },
            profitabilityRatios = emptyMap(),
            stabilityRatios = emptyMap(),
            growthRatios = emptyMap()
        )
        val summary = data.toSummary()
        assertEquals(0.0, summary.assetTurnovers.first(), TOLERANCE)
    }

    @Test
    fun `toSummary - 총자본 0일 때 재무레버리지는 0이다`() {
        val periods = listOf("202303")
        val data = FinancialData(
            ticker = "TEST", name = "테스트",
            periods = periods,
            balanceSheets = periods.associate {
                it to BalanceSheet(FinancialPeriod.fromYearMonth(it),
                    null, null, 1000L, null, null, null, null, null, null, 0L)
            },
            incomeStatements = periods.associate {
                it to IncomeStatement(FinancialPeriod.fromYearMonth(it),
                    1000L, null, null, null, null, 100L)
            },
            profitabilityRatios = emptyMap(),
            stabilityRatios = emptyMap(),
            growthRatios = emptyMap()
        )
        val summary = data.toSummary()
        assertEquals(0.0, summary.equityMultipliers.first(), TOLERANCE)
    }

    @Test
    fun `hasDuPontData - 모든 값이 0이면 false`() {
        val summary = createEmptySummary()
        assertFalse(summary.hasDuPontData)
    }

    @Test
    fun `hasDuPontData - ROE가 존재하면 true`() {
        val summary = createEmptySummary().copy(roes = listOf(10.0))
        assertTrue(summary.hasDuPontData)
    }

    @Test
    fun `hasDuPontData - assetTurnovers만 존재해도 true`() {
        val summary = createEmptySummary().copy(assetTurnovers = listOf(0.5))
        assertTrue(summary.hasDuPontData)
    }

    @Test
    fun `trimToLast - 듀퐁 필드도 함께 잘린다`() {
        val summary = createSummaryWithPeriods(8).copy(
            netProfitMargins = List(8) { it * 1.0 },
            assetTurnovers = List(8) { it * 0.1 },
            equityMultipliers = List(8) { 1.0 + it * 0.1 },
            roes = List(8) { it * 2.0 }
        )
        val trimmed = summary.trimToLast(4)
        assertEquals(4, trimmed.netProfitMargins.size)
        assertEquals(4, trimmed.assetTurnovers.size)
        assertEquals(4, trimmed.equityMultipliers.size)
        assertEquals(4, trimmed.roes.size)
        // 마지막 4개가 남아야 함
        assertEquals(7.0 * 2.0, trimmed.roes.last(), TOLERANCE)
    }
}
