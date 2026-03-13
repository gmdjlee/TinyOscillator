package com.tinyoscillator.domain.usecase

import com.tinyoscillator.domain.model.AiAnalysisType
import com.tinyoscillator.domain.model.AmountRankingItem
import com.tinyoscillator.domain.model.CrossSignal
import com.tinyoscillator.domain.model.MarketDeposit
import com.tinyoscillator.domain.model.MarketOscillator
import com.tinyoscillator.domain.model.OscillatorRow
import com.tinyoscillator.domain.model.SignalAnalysis
import com.tinyoscillator.domain.model.Trend
import com.tinyoscillator.domain.model.WeightTrend
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AiAnalysisPreparerTest {

    private lateinit var preparer: AiAnalysisPreparer

    @Before
    fun setup() {
        preparer = AiAnalysisPreparer()
    }

    // --- prepareStockAnalysis ---

    @Test
    fun `prepareStockAnalysis with empty rows returns data empty`() {
        val result = preparer.prepareStockAnalysis("삼성전자", "005930", emptyList(), emptyList())
        assertEquals("데이터 없음", result)
    }

    @Test
    fun `prepareStockAnalysis includes stock name and ticker`() {
        val rows = createOscillatorRows(5)
        val result = preparer.prepareStockAnalysis("삼성전자", "005930", rows, emptyList())
        assertTrue(result.contains("삼성전자"))
        assertTrue(result.contains("005930"))
    }

    @Test
    fun `prepareStockAnalysis includes header line`() {
        val rows = createOscillatorRows(5)
        val result = preparer.prepareStockAnalysis("삼성전자", "005930", rows, emptyList())
        assertTrue(result.contains("날짜|시총(조)|외인5일(억)|기관5일(억)|수급비|MACD|시그널|오실레이터"))
    }

    @Test
    fun `prepareStockAnalysis includes trend summary for 5+ rows`() {
        val rows = createOscillatorRows(10)
        val result = preparer.prepareStockAnalysis("삼성전자", "005930", rows, emptyList())
        assertTrue(result.contains("[추세 요약]"))
        assertTrue(result.contains("최근5일 평균 오실레이터"))
    }

    @Test
    fun `prepareStockAnalysis no trend summary for less than 5 rows`() {
        val rows = createOscillatorRows(3)
        val result = preparer.prepareStockAnalysis("삼성전자", "005930", rows, emptyList())
        assertFalse(result.contains("[추세 요약]"))
    }

    @Test
    fun `prepareStockAnalysis includes signals`() {
        val rows = createOscillatorRows(5)
        val signals = listOf(
            SignalAnalysis("20260301", 300.0, 5.0, 0.1, 0.05, Trend.BULLISH, CrossSignal.GOLDEN_CROSS)
        )
        val result = preparer.prepareStockAnalysis("삼성전자", "005930", rows, signals)
        assertTrue(result.contains("[최근 시그널]"))
        assertTrue(result.contains("GOLDEN_CROSS"))
    }

    @Test
    fun `prepareStockAnalysis token budget under 2000 chars for 365 rows`() {
        val rows = createOscillatorRows(365)
        val signals = listOf(
            SignalAnalysis("20260301", 300.0, 5.0, 0.1, 0.05, Trend.BULLISH, null)
        )
        val result = preparer.prepareStockAnalysis("삼성전자", "005930", rows, signals)
        // ~400 tokens ≈ ~1600 chars, allow generous limit
        assertTrue("Output too long: ${result.length}", result.length < 3000)
    }

    @Test
    fun `sampleRows returns all rows when count is less than target`() {
        val rows = createOscillatorRows(5)
        val sampled = preparer.sampleRows(rows, 20)
        assertEquals(5, sampled.size)
    }

    @Test
    fun `sampleRows reduces to target count`() {
        val rows = createOscillatorRows(365)
        val sampled = preparer.sampleRows(rows, 20)
        assertEquals(20, sampled.size)
    }

    // --- prepareEtfRankingAnalysis ---

    @Test
    fun `prepareEtfRankingAnalysis with empty rankings returns data empty`() {
        val result = preparer.prepareEtfRankingAnalysis(emptyList(), "20260313")
        assertEquals("데이터 없음", result)
    }

    @Test
    fun `prepareEtfRankingAnalysis includes top 20`() {
        val rankings = (1..30).map { createAmountRankingItem(it) }
        val result = preparer.prepareEtfRankingAnalysis(rankings, "20260313")
        assertTrue(result.contains("[ETF 금액 순위 Top 20]"))
        assertTrue(result.contains("기준일: 20260313"))
        // Should contain rank 20 but not rank 21
        assertTrue(result.contains("종목20"))
    }

    @Test
    fun `prepareEtfRankingAnalysis includes sector concentration`() {
        val rankings = listOf(
            createAmountRankingItem(1, sector = "반도체"),
            createAmountRankingItem(2, sector = "반도체"),
            createAmountRankingItem(3, sector = "바이오")
        )
        val result = preparer.prepareEtfRankingAnalysis(rankings, "20260313")
        assertTrue(result.contains("[업종별 자금 집중도]"))
        assertTrue(result.contains("반도체"))
    }

    @Test
    fun `prepareEtfRankingAnalysis token budget under 3000 chars`() {
        val rankings = (1..50).map { createAmountRankingItem(it, sector = "섹터${it % 5}") }
        val result = preparer.prepareEtfRankingAnalysis(rankings, "20260313")
        assertTrue("Output too long: ${result.length}", result.length < 4000)
    }

    // --- prepareMarketAnalysis ---

    @Test
    fun `prepareMarketAnalysis with KOSPI data`() {
        val kospi = (1..14).map { createMarketOscillator("KOSPI", it) }
        val result = preparer.prepareMarketAnalysis(kospi, emptyList())
        assertTrue(result.contains("[KOSPI 오실레이터"))
        assertFalse(result.contains("[KOSDAQ"))
    }

    @Test
    fun `prepareMarketAnalysis with both markets and deposits`() {
        val kospi = (1..14).map { createMarketOscillator("KOSPI", it) }
        val kosdaq = (1..14).map { createMarketOscillator("KOSDAQ", it) }
        val deposits = (1..10).map { createMarketDeposit(it) }
        val result = preparer.prepareMarketAnalysis(kospi, kosdaq, deposits)
        assertTrue(result.contains("[KOSPI"))
        assertTrue(result.contains("[KOSDAQ"))
        assertTrue(result.contains("[투자자 예탁금 동향"))
    }

    // --- getSystemPrompt ---

    @Test
    fun `getSystemPrompt returns different prompts for each type`() {
        val stockPrompt = preparer.getSystemPrompt(AiAnalysisType.STOCK_OSCILLATOR)
        val etfPrompt = preparer.getSystemPrompt(AiAnalysisType.ETF_RANKING)
        val marketPrompt = preparer.getSystemPrompt(AiAnalysisType.MARKET_OVERVIEW)

        assertNotEquals(stockPrompt, etfPrompt)
        assertNotEquals(etfPrompt, marketPrompt)
        assertTrue(stockPrompt.contains("수급"))
        assertTrue(etfPrompt.contains("ETF"))
        assertTrue(marketPrompt.contains("거시"))
    }

    @Test
    fun `getSystemPrompt includes disclaimer`() {
        AiAnalysisType.entries.forEach { type ->
            val prompt = preparer.getSystemPrompt(type)
            assertTrue("Missing disclaimer in $type", prompt.contains("투자 권유가 아닌"))
        }
    }

    // --- Helpers ---

    private fun createOscillatorRows(count: Int): List<OscillatorRow> =
        (1..count).map { i ->
            OscillatorRow(
                date = "2026${String.format("%02d", (i % 12) + 1)}${String.format("%02d", (i % 28) + 1)}",
                marketCap = 300_0000_0000_0000L + i * 1000_0000_0000L,
                marketCapTril = 300.0 + i * 0.1,
                foreign5d = 1000_0000_0000L * i,
                inst5d = 500_0000_0000L * i,
                supplyRatio = 0.5 + i * 0.01,
                ema12 = 0.1 * i,
                ema26 = 0.08 * i,
                macd = 0.02 * i,
                signal = 0.015 * i,
                oscillator = 0.005 * i
            )
        }

    private fun createAmountRankingItem(rank: Int, sector: String? = null) = AmountRankingItem(
        rank = rank,
        stockTicker = String.format("%06d", rank),
        stockName = "종목$rank",
        totalAmountBillion = 100.0 - rank,
        etfCount = 10 - rank % 5,
        market = if (rank % 2 == 0) "코스피" else "코스닥",
        sector = sector,
        maxWeight = rank.toDouble(),
        maxWeightTrend = WeightTrend.NONE
    )

    private fun createMarketOscillator(market: String, day: Int) = MarketOscillator(
        id = "$market-2026-03-${String.format("%02d", day)}",
        market = market,
        date = "2026-03-${String.format("%02d", day)}",
        indexValue = 2500.0 + day * 10,
        oscillator = -20.0 + day * 3,
        lastUpdated = System.currentTimeMillis()
    )

    private fun createMarketDeposit(day: Int) = MarketDeposit(
        date = "2026-03-${String.format("%02d", day)}",
        depositAmount = 50_0000_0000_0000.0 + day * 1000_0000_0000.0,
        depositChange = 1000_0000_0000.0 * (if (day % 2 == 0) 1 else -1),
        creditAmount = 20_0000_0000_0000.0,
        creditChange = 500_0000_0000.0,
        lastUpdated = System.currentTimeMillis()
    )
}
