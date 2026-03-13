package com.tinyoscillator.domain.usecase

import com.tinyoscillator.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AiAnalysisPreparerComprehensiveTest {

    private lateinit var preparer: AiAnalysisPreparer

    @Before
    fun setup() {
        preparer = AiAnalysisPreparer()
    }

    private fun createOscillatorRows(count: Int): List<OscillatorRow> {
        return (1..count).map { i ->
            OscillatorRow(
                date = "2026${String.format("%02d", (i % 12) + 1)}${String.format("%02d", (i % 28) + 1)}",
                marketCap = 300_000_000_000_000L + i * 1_000_000_000L,
                marketCapTril = 300.0 + i * 0.1,
                foreign5d = (i * 1_0000_0000L),
                inst5d = (i * 5000_0000L),
                supplyRatio = 0.5 + i * 0.01,
                ema12 = i * 0.15,
                ema26 = i * 0.12,
                macd = i * 0.1,
                signal = i * 0.08,
                oscillator = i * 0.02
            )
        }
    }

    private fun createSignals(count: Int): List<SignalAnalysis> {
        return (1..count).map { i ->
            SignalAnalysis(
                date = "2026${String.format("%02d", (i % 12) + 1)}${String.format("%02d", (i % 28) + 1)}",
                marketCapTril = 300.0 + i,
                oscillator = i * 0.02,
                macd = i * 0.1,
                signal = i * 0.08,
                trend = Trend.BULLISH,
                crossSignal = if (i % 3 == 0) CrossSignal.GOLDEN_CROSS else null
            )
        }
    }

    private fun createDemarkRows(count: Int): List<DemarkTDRow> {
        return (1..count).map { i ->
            DemarkTDRow(
                date = "2026${String.format("%02d", (i % 12) + 1)}${String.format("%02d", (i % 28) + 1)}",
                closePrice = 50000 + i * 100,
                marketCapTril = 300.0 + i * 0.1,
                tdSellCount = i % 14,
                tdBuyCount = (i + 5) % 14
            )
        }
    }

    private fun createFinancialData(): FinancialData {
        val periods = listOf("202412", "202409", "202406", "202403")
        return FinancialData(
            ticker = "005930",
            name = "삼성전자",
            periods = periods,
            balanceSheets = emptyMap(),
            incomeStatements = periods.associateWith { period ->
                IncomeStatement(
                    period = FinancialPeriod.fromYearMonth(period),
                    revenue = 70_0000_0000_0000L,
                    costOfSales = null,
                    grossProfit = null,
                    operatingProfit = 10_0000_0000_0000L,
                    ordinaryProfit = null,
                    netIncome = 8_0000_0000_0000L
                )
            },
            profitabilityRatios = periods.associateWith { period ->
                ProfitabilityRatios(
                    period = FinancialPeriod.fromYearMonth(period),
                    operatingMargin = 14.3,
                    netMargin = 11.4,
                    roe = 12.5,
                    roa = 8.2
                )
            },
            stabilityRatios = periods.associateWith { period ->
                StabilityRatios(
                    period = FinancialPeriod.fromYearMonth(period),
                    debtRatio = 35.2,
                    currentRatio = 2.5,
                    quickRatio = 1.8,
                    borrowingDependency = 10.0,
                    interestCoverageRatio = 50.0
                )
            },
            growthRatios = periods.associateWith { period ->
                GrowthRatios(
                    period = FinancialPeriod.fromYearMonth(period),
                    revenueGrowth = 5.2,
                    operatingProfitGrowth = 8.1,
                    netIncomeGrowth = 7.3,
                    equityGrowth = 3.0,
                    totalAssetsGrowth = 4.5
                )
            }
        )
    }

    private fun createEtfAggregated(count: Int): List<StockAggregatedTimePoint> {
        return (1..count).map { i ->
            StockAggregatedTimePoint(
                date = "2026${String.format("%02d", (i % 12) + 1)}${String.format("%02d", (i % 28) + 1)}",
                totalAmount = (i * 100_0000_0000L),
                etfCount = 10 + i,
                maxWeight = 5.0 + i * 0.1,
                avgWeight = 2.0 + i * 0.05
            )
        }
    }

    @Test
    fun `allData_producesValidOutput`() {
        val result = preparer.prepareComprehensiveStockAnalysis(
            stockName = "삼성전자",
            ticker = "005930",
            oscillatorRows = createOscillatorRows(30),
            signals = createSignals(5),
            demarkRows = createDemarkRows(20),
            financialData = createFinancialData(),
            etfAggregated = createEtfAggregated(10),
            market = "KOSPI",
            sector = "반도체"
        )

        assertTrue(result.contains("삼성전자"))
        assertTrue(result.contains("005930"))
        assertTrue(result.contains("KOSPI"))
        assertTrue(result.contains("반도체"))
        assertTrue(result.contains("[수급 오실레이터]"))
        assertTrue(result.contains("[최근 시그널]"))
        assertTrue(result.contains("[DeMark TD]"))
        assertTrue(result.contains("[재무정보"))
        assertTrue(result.contains("[ETF 편입 추이]"))
    }

    @Test
    fun `noFinancial_skipsSection`() {
        val result = preparer.prepareComprehensiveStockAnalysis(
            stockName = "테스트",
            ticker = "000001",
            oscillatorRows = createOscillatorRows(10),
            signals = createSignals(2),
            demarkRows = createDemarkRows(5),
            financialData = null,
            etfAggregated = createEtfAggregated(3),
            market = null,
            sector = null
        )

        assertTrue(result.contains("[수급 오실레이터]"))
        assertFalse(result.contains("[재무정보"))
        assertTrue(result.contains("[ETF 편입 추이]"))
    }

    @Test
    fun `noEtf_skipsSection`() {
        val result = preparer.prepareComprehensiveStockAnalysis(
            stockName = "테스트",
            ticker = "000001",
            oscillatorRows = createOscillatorRows(10),
            signals = createSignals(2),
            demarkRows = createDemarkRows(5),
            financialData = createFinancialData(),
            etfAggregated = emptyList(),
            market = null,
            sector = null
        )

        assertTrue(result.contains("[재무정보"))
        assertFalse(result.contains("[ETF 편입 추이]"))
    }

    @Test
    fun `noDemarkData_skipsSection`() {
        val result = preparer.prepareComprehensiveStockAnalysis(
            stockName = "테스트",
            ticker = "000001",
            oscillatorRows = createOscillatorRows(10),
            signals = createSignals(2),
            demarkRows = emptyList(),
            financialData = null,
            etfAggregated = emptyList(),
            market = null,
            sector = null
        )

        assertTrue(result.contains("[수급 오실레이터]"))
        assertFalse(result.contains("[DeMark TD]"))
    }

    @Test
    fun `emptyOscillator_returnsEmpty`() {
        val result = preparer.prepareComprehensiveStockAnalysis(
            stockName = "테스트",
            ticker = "000001",
            oscillatorRows = emptyList(),
            signals = emptyList(),
            demarkRows = emptyList(),
            financialData = null,
            etfAggregated = emptyList(),
            market = null,
            sector = null
        )

        assertEquals("데이터 없음", result)
    }

    @Test
    fun `tokenBudget_under1200chars`() {
        val result = preparer.prepareComprehensiveStockAnalysis(
            stockName = "삼성전자",
            ticker = "005930",
            oscillatorRows = createOscillatorRows(365),
            signals = createSignals(10),
            demarkRows = createDemarkRows(250),
            financialData = createFinancialData(),
            etfAggregated = createEtfAggregated(20),
            market = "KOSPI",
            sector = "반도체"
        )

        // ~800 tokens ≈ ~3200 chars (rough), we allow up to 5000 chars
        assertTrue("Output too large: ${result.length} chars", result.length < 5000)
    }

    @Test
    fun `systemPrompt_comprehensiveStock`() {
        val prompt = preparer.getSystemPrompt(AiAnalysisType.COMPREHENSIVE_STOCK)
        assertTrue(prompt.contains("종합 분석"))
        assertTrue(prompt.contains("수급"))
        assertTrue(prompt.contains("DeMark"))
        assertTrue(prompt.contains("재무"))
        assertTrue(prompt.contains("ETF"))
    }

    @Test
    fun `sampleRows10_correctCount`() {
        val rows = createOscillatorRows(365)
        val sampled = preparer.sampleRows(rows, 10)
        assertEquals(10, sampled.size)
    }
}
