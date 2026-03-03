package com.tinyoscillator.domain.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Edge case tests for FinancialModels: formatting utilities, FinancialPeriod,
 * toSummary transformation, and FinancialSummary trimming.
 */
class FinancialModelsEdgeCaseTest {

    // ========== formatNumber edge cases ==========

    @Test
    fun `formatNumber - 0은 0억으로 표시된다`() {
        assertEquals("0억", formatNumber(0L))
    }

    @Test
    fun `formatNumber - 양수 소규모는 억 단위로 표시된다`() {
        assertEquals("999억", formatNumber(999L))
    }

    @Test
    fun `formatNumber - 1000은 천억 단위로 표시된다`() {
        assertEquals("1.0천억", formatNumber(1_000L))
    }

    @Test
    fun `formatNumber - 1500은 1점5천억으로 표시된다`() {
        assertEquals("1.5천억", formatNumber(1_500L))
    }

    @Test
    fun `formatNumber - 10000은 조 단위로 표시된다`() {
        assertEquals("1.0조", formatNumber(10_000L))
    }

    @Test
    fun `formatNumber - 음수 조 단위`() {
        assertEquals("-1.0조", formatNumber(-10_000L))
    }

    @Test
    fun `formatNumber - 음수 천억 단위`() {
        assertEquals("-2.3천억", formatNumber(-2_300L))
    }

    @Test
    fun `formatNumber - 음수 억 단위`() {
        assertEquals("-50억", formatNumber(-50L))
    }

    @Test
    fun `formatNumber - 큰 조 단위`() {
        assertEquals("100.5조", formatNumber(1_005_000L))
    }

    // ========== formatPercent edge cases ==========

    @Test
    fun `formatPercent - 0은 0점0%이다`() {
        assertEquals("0.0%", formatPercent(0.0))
    }

    @Test
    fun `formatPercent - 양수 소수점`() {
        assertEquals("12.3%", formatPercent(12.345))
    }

    @Test
    fun `formatPercent - 음수`() {
        assertEquals("-5.7%", formatPercent(-5.678))
    }

    @Test
    fun `formatPercent - 100%`() {
        assertEquals("100.0%", formatPercent(100.0))
    }

    // ========== FinancialPeriod edge cases ==========

    @Test
    fun `FinancialPeriod - 정상 분기 파싱`() {
        val fp = FinancialPeriod.fromYearMonth("202303")
        assertEquals(2023, fp.year)
        assertEquals(1, fp.quarter)
        assertEquals("202303", fp.yearMonth)
    }

    @Test
    fun `FinancialPeriod - 4분기 파싱`() {
        val fp = FinancialPeriod.fromYearMonth("202312")
        assertEquals(2023, fp.year)
        assertEquals(4, fp.quarter)
    }

    @Test
    fun `FinancialPeriod - 비정규 월은 quarter=0`() {
        val fp = FinancialPeriod.fromYearMonth("202301")
        assertEquals(2023, fp.year)
        assertEquals(0, fp.quarter)
    }

    @Test
    fun `FinancialPeriod - 짧은 문자열은 year=0, quarter=0`() {
        val fp = FinancialPeriod.fromYearMonth("2023")
        assertEquals(0, fp.year)
        assertEquals(0, fp.quarter)
    }

    @Test
    fun `FinancialPeriod - toDisplayString short`() {
        val fp = FinancialPeriod.fromYearMonth("202303")
        assertEquals("23.03", fp.toDisplayString(short = true))
    }

    @Test
    fun `FinancialPeriod - toDisplayString full`() {
        val fp = FinancialPeriod.fromYearMonth("202303")
        assertEquals("2023.03", fp.toDisplayString(short = false))
    }

    @Test
    fun `FinancialPeriod - 짧은 문자열은 toDisplayString 원본 반환`() {
        val fp = FinancialPeriod.fromYearMonth("2023")
        assertEquals("2023", fp.toDisplayString())
    }

    // ========== FinancialTab enum ==========

    @Test
    fun `FinancialTab - PROFITABILITY 레이블 확인`() {
        assertEquals("수익성", FinancialTab.PROFITABILITY.label)
    }

    @Test
    fun `FinancialTab - STABILITY 레이블 확인`() {
        assertEquals("안정성", FinancialTab.STABILITY.label)
    }

    @Test
    fun `FinancialTab - 2개의 값이 있다`() {
        assertEquals(2, FinancialTab.entries.size)
    }

    // ========== FinancialData.toSummary edge cases ==========

    @Test
    fun `toSummary - 빈 periods는 빈 summary를 반환한다`() {
        val data = FinancialData(
            ticker = "005930", name = "삼성전자",
            periods = emptyList(),
            balanceSheets = emptyMap(),
            incomeStatements = emptyMap(),
            profitabilityRatios = emptyMap(),
            stabilityRatios = emptyMap(),
            growthRatios = emptyMap()
        )
        val summary = data.toSummary()
        assertTrue(summary.periods.isEmpty())
        assertTrue(summary.revenues.isEmpty())
    }

    @Test
    fun `toSummary - 단일 분기 데이터`() {
        val data = FinancialData(
            ticker = "005930", name = "삼성전자",
            periods = listOf("202303"),
            balanceSheets = emptyMap(),
            incomeStatements = mapOf(
                "202303" to IncomeStatement(
                    period = FinancialPeriod.fromYearMonth("202303"),
                    revenue = 5000L, costOfSales = 3000L, grossProfit = 2000L,
                    operatingProfit = 1000L, ordinaryProfit = 900L, netIncome = 800L
                )
            ),
            profitabilityRatios = emptyMap(),
            stabilityRatios = emptyMap(),
            growthRatios = emptyMap()
        )
        val summary = data.toSummary()
        assertEquals(1, summary.periods.size)
        assertEquals(5000L, summary.revenues[0])
    }

    @Test
    fun `toSummary - periods는 정렬된다`() {
        val data = FinancialData(
            ticker = "005930", name = "삼성전자",
            periods = listOf("202309", "202303", "202306"),
            balanceSheets = emptyMap(),
            incomeStatements = emptyMap(),
            profitabilityRatios = emptyMap(),
            stabilityRatios = emptyMap(),
            growthRatios = emptyMap()
        )
        val summary = data.toSummary()
        assertEquals(listOf("202303", "202306", "202309"), summary.periods)
    }

    // ========== FinancialSummary trimToLast ==========

    @Test
    fun `trimToLast - 전체보다 큰 count는 전체를 반환한다`() {
        val summary = createTestSummary(5)
        val trimmed = summary.trimToLast(10)
        assertEquals(5, trimmed.periods.size)
    }

    @Test
    fun `trimToLast - 0은 MIN_DISPLAY_QUARTERS로 coerce된다`() {
        val summary = createTestSummary(5)
        val trimmed = summary.trimToLast(0)
        assertEquals(FinancialSummary.MIN_DISPLAY_QUARTERS, trimmed.periods.size)
    }

    @Test
    fun `trimToLast - 음수는 MIN_DISPLAY_QUARTERS로 coerce된다`() {
        val summary = createTestSummary(5)
        val trimmed = summary.trimToLast(-1)
        assertEquals(FinancialSummary.MIN_DISPLAY_QUARTERS, trimmed.periods.size)
    }

    @Test
    fun `trimToLast - MIN_DISPLAY_QUARTERS 미만은 최소 분기수로 반환한다`() {
        val summary = createTestSummary(8)
        val trimmed = summary.trimToLast(3)
        // coerceAtLeast(4) → 4
        assertEquals(FinancialSummary.MIN_DISPLAY_QUARTERS, trimmed.periods.size)
        assertEquals(FinancialSummary.MIN_DISPLAY_QUARTERS, trimmed.revenues.size)
    }

    // ========== FinancialState enum ==========

    @Test
    fun `FinancialState NoStock은 초기 상태이다`() {
        val state: FinancialState = FinancialState.NoStock
        assertEquals(FinancialState.NoStock, state)
    }

    @Test
    fun `FinancialState Loading`() {
        val state = FinancialState.Loading
        assertEquals(FinancialState.Loading, state)
    }

    @Test
    fun `FinancialState Error는 메시지를 포함한다`() {
        val state = FinancialState.Error("test error")
        assertEquals("test error", state.message)
    }

    @Test
    fun `FinancialState NoApiKey`() {
        assertEquals(FinancialState.NoApiKey, FinancialState.NoApiKey)
    }

    // ========== Helpers ==========

    private fun createTestSummary(periodCount: Int): FinancialSummary {
        val periods = (1..periodCount).map { "2023${String.format("%02d", it * 3)}" }
        val displayPeriods = periods.map { FinancialPeriod.fromYearMonth(it).toDisplayString(short = true) }
        return FinancialSummary(
            ticker = "005930",
            name = "삼성전자",
            periods = periods,
            displayPeriods = displayPeriods,
            revenues = List(periodCount) { it * 1000L },
            operatingProfits = List(periodCount) { it * 500L },
            netIncomes = List(periodCount) { it * 300L },
            revenueGrowthRates = List(periodCount) { it * 5.0 },
            operatingProfitGrowthRates = List(periodCount) { it * 3.0 },
            netIncomeGrowthRates = List(periodCount) { it * 2.0 },
            equityGrowthRates = List(periodCount) { it * 1.5 },
            totalAssetsGrowthRates = List(periodCount) { it * 1.0 },
            debtRatios = List(periodCount) { 100.0 + it },
            currentRatios = List(periodCount) { 200.0 - it },
            borrowingDependencies = List(periodCount) { 30.0 + it }
        )
    }
}
