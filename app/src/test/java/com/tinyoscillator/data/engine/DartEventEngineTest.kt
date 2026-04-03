package com.tinyoscillator.data.engine

import com.tinyoscillator.core.api.DartApiClient
import com.tinyoscillator.core.database.dao.DartDao
import com.tinyoscillator.core.database.dao.RegimeDao
import com.tinyoscillator.core.database.entity.DartCorpCodeEntity
import com.tinyoscillator.core.database.entity.KospiIndexEntity
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.DartDisclosure
import com.tinyoscillator.domain.model.DartEventType
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class DartEventEngineTest {

    private lateinit var engine: DartEventEngine
    private lateinit var dartApiClient: DartApiClient
    private lateinit var dartDao: DartDao
    private lateinit var regimeDao: RegimeDao

    @Before
    fun setup() {
        dartApiClient = mockk()
        dartDao = mockk()
        regimeDao = mockk()
        engine = DartEventEngine(dartApiClient, dartDao, regimeDao)
    }

    // ─── classify_disclosure ───

    @Test
    fun `classify detects RIGHTS_OFFERING from Korean title`() {
        assertEquals(DartEventType.RIGHTS_OFFERING, DartEventType.classify("유상증자 결정"))
        assertEquals(DartEventType.RIGHTS_OFFERING, DartEventType.classify("[기재정정]신주발행 공고"))
        assertEquals(DartEventType.RIGHTS_OFFERING, DartEventType.classify("주주배정 유상증자 결정 공시"))
    }

    @Test
    fun `classify detects BUYBACK from Korean title`() {
        assertEquals(DartEventType.BUYBACK, DartEventType.classify("자기주식취득 결정"))
        assertEquals(DartEventType.BUYBACK, DartEventType.classify("자사주 매입 신탁 체결"))
        assertEquals(DartEventType.BUYBACK, DartEventType.classify("자사주소각 결정"))
    }

    @Test
    fun `classify detects OWNERSHIP_CHANGE from Korean title`() {
        assertEquals(DartEventType.OWNERSHIP_CHANGE, DartEventType.classify("주식등의대량보유상황보고서"))
        assertEquals(DartEventType.OWNERSHIP_CHANGE, DartEventType.classify("임원ㆍ주요주주특정증권등소유상황보고서"))
        assertEquals(DartEventType.OWNERSHIP_CHANGE, DartEventType.classify("최대주주변경 공시"))
    }

    @Test
    fun `classify detects MGMT_CHANGE from Korean title`() {
        assertEquals(DartEventType.MGMT_CHANGE, DartEventType.classify("대표이사변경 공시"))
        assertEquals(DartEventType.MGMT_CHANGE, DartEventType.classify("사외이사 선임"))
    }

    @Test
    fun `classify detects EARNINGS_SURPRISE from Korean title`() {
        assertEquals(DartEventType.EARNINGS_SURPRISE, DartEventType.classify("사업보고서"))
        assertEquals(DartEventType.EARNINGS_SURPRISE, DartEventType.classify("분기보고서 (2026.03)"))
        assertEquals(DartEventType.EARNINGS_SURPRISE, DartEventType.classify("매출액 또는 손익구조 변동"))
    }

    @Test
    fun `classify detects DIVIDEND_CHANGE from Korean title`() {
        assertEquals(DartEventType.DIVIDEND_CHANGE, DartEventType.classify("현금배당 결정"))
        assertEquals(DartEventType.DIVIDEND_CHANGE, DartEventType.classify("중간배당 공시"))
    }

    @Test
    fun `classify returns OTHER for unknown title`() {
        assertEquals(DartEventType.OTHER, DartEventType.classify("회사 합병 결정"))
        assertEquals(DartEventType.OTHER, DartEventType.classify("전환사채 발행"))
    }

    // ─── compute_car ───

    @Test
    fun `computeCar returns near-zero CAR for zero abnormal returns`() {
        // Stock returns == beta * market returns → AR ≈ 0 → CAR ≈ 0
        val dates = generateTradingDates(200)
        val marketReturns = dates.drop(1).associateWith { 0.01 }
        val stockReturns = dates.drop(1).associateWith { 0.01 }  // beta=1, AR=0

        val eventDate = dates[170]  // enough pre/post days
        val result = engine.computeCar(stockReturns, marketReturns, eventDate)

        assertNotNull(result)
        assertTrue("CAR should be near zero: ${result!!.carFinal}",
            abs(result.carFinal) < 0.01)
    }

    @Test
    fun `computeCar returns positive CAR when stock outperforms`() {
        val dates = generateTradingDates(200)
        // Market flat, stock has positive returns in event window
        val marketReturns = dates.drop(1).associateWith { 0.001 }
        val stockReturns = dates.drop(1).mapIndexed { idx, date ->
            // In event window (around day 170), stock has extra positive returns
            val eventIdx = 169
            val isEventWindow = idx in (eventIdx - 5)..(eventIdx + 20)
            date to if (isEventWindow) 0.02 else 0.001
        }.toMap()

        val eventDate = dates[170]
        val result = engine.computeCar(stockReturns, marketReturns, eventDate)

        assertNotNull(result)
        assertTrue("CAR should be positive: ${result!!.carFinal}",
            result.carFinal > 0)
    }

    @Test
    fun `computeCar returns null with insufficient data`() {
        val dates = generateTradingDates(12)  // too few for event window
        val returns = dates.drop(1).associateWith { 0.01 }

        // event at dates[10] → window needs dates[5]..dates[30], but only 12 dates exist
        // abnormal returns < MIN_POST_DAYS (10)
        val result = engine.computeCar(returns, returns, dates[10])
        assertNull(result)
    }

    @Test
    fun `estimateBeta returns 1 with insufficient observations`() {
        val dates = generateTradingDates(30)
        val returns = dates.drop(1).associateWith { 0.01 }

        val beta = engine.estimateBeta(returns, returns, dates.last())
        assertEquals(1.0, beta, 0.01)
    }

    @Test
    fun `estimateBeta computes correct beta for correlated returns`() {
        val dates = generateTradingDates(200)
        val marketReturns = mutableMapOf<String, Double>()
        val stockReturns = mutableMapOf<String, Double>()

        // Stock = 1.5 * market + noise → beta ≈ 1.5
        val rng = java.util.Random(42)
        for (date in dates.drop(1)) {
            val mr = 0.01 * (rng.nextGaussian())
            marketReturns[date] = mr
            stockReturns[date] = 1.5 * mr + 0.001 * rng.nextGaussian()
        }

        val beta = engine.estimateBeta(stockReturns, marketReturns, dates[180])
        assertTrue("Beta should be near 1.5: $beta", abs(beta - 1.5) < 0.3)
    }

    // ─── analyze (integration) ───

    @Test
    fun `analyze returns empty result when API key is null`() = runTest {
        val prices = makePrices(60)
        val result = engine.analyze(null, "005930", prices)

        assertEquals(0.5, result.signalScore, 0.01)
        assertEquals(0, result.nEvents)
        assertNotNull(result.unavailableReason)
    }

    @Test
    fun `analyze returns empty result when API key is blank`() = runTest {
        val prices = makePrices(60)
        val result = engine.analyze("", "005930", prices)

        assertEquals(0.5, result.signalScore, 0.01)
        assertEquals(0, result.nEvents)
    }

    @Test
    fun `analyze returns empty result when corp_code not found`() = runTest {
        coEvery { dartDao.getCorpCode("999999") } returns null
        coEvery { dartDao.lastUpdatedAt() } returns System.currentTimeMillis()
        coEvery { dartDao.count() } returns 100

        val prices = makePrices(60)
        val result = engine.analyze("test-key", "999999", prices)

        assertEquals(0, result.nEvents)
        assertEquals(0.5, result.signalScore, 0.01)
    }

    @Test
    fun `analyze with no disclosures returns nEvents 0`() = runTest {
        coEvery { dartDao.getCorpCode("005930") } returns DartCorpCodeEntity(
            ticker = "005930", corpCode = "00126380", corpName = "삼성전자"
        )
        coEvery { dartApiClient.fetchRecentDisclosures(any(), "00126380", any()) } returns emptyList()
        coEvery { regimeDao.getAllKospiIndex() } returns emptyList()

        val prices = makePrices(60)
        val result = engine.analyze("test-key", "005930", prices)

        assertEquals(0, result.nEvents)
    }

    @Test
    fun `analyze signal score is bounded 0 to 1`() = runTest {
        val today = LocalDate.now()
        val eventDate = today.minusDays(25)
        val eventDateStr = eventDate.format(DateTimeFormatter.BASIC_ISO_DATE)

        coEvery { dartDao.getCorpCode("005930") } returns DartCorpCodeEntity(
            ticker = "005930", corpCode = "00126380", corpName = "삼성전자"
        )
        coEvery { dartApiClient.fetchRecentDisclosures(any(), "00126380", any()) } returns listOf(
            DartDisclosure(
                rceptNo = "20260101000001",
                rceptDt = eventDateStr,
                reportNm = "자기주식취득 결정",
                corpName = "삼성전자",
                corpCode = "00126380",
                eventType = DartEventType.BUYBACK
            )
        )

        val kospiData = generateKospiData(300)
        coEvery { regimeDao.getAllKospiIndex() } returns kospiData

        val prices = makePrices(300)
        val result = engine.analyze("test-key", "005930", prices)

        assertTrue("Signal score should be >= 0: ${result.signalScore}",
            result.signalScore >= 0.0)
        assertTrue("Signal score should be <= 1: ${result.signalScore}",
            result.signalScore <= 1.0)
    }

    @Test
    fun `computeLogReturns produces correct values`() {
        val data = listOf(
            "20260101" to 100.0,
            "20260102" to 110.0,
            "20260103" to 105.0
        )

        val returns = engine.computeLogReturns(data)

        assertEquals(2, returns.size)
        assertTrue(returns.containsKey("20260102"))
        assertTrue(returns.containsKey("20260103"))
        // ln(110/100) ≈ 0.0953
        assertEquals(kotlin.math.ln(110.0 / 100.0), returns["20260102"]!!, 1e-6)
        // ln(105/110) ≈ -0.0465
        assertEquals(kotlin.math.ln(105.0 / 110.0), returns["20260103"]!!, 1e-6)
    }

    @Test
    fun `all 7 event types have Korean labels`() {
        for (type in DartEventType.ALL_TYPES) {
            val korean = DartEventType.toKorean(type)
            assertTrue("Type $type should have non-empty Korean label", korean.isNotEmpty())
            assertNotEquals(type, korean)  // Korean label should differ from constant name
        }
    }

    @Test
    fun `corp_code cache is checked before download`() = runTest {
        coEvery { dartDao.getCorpCode("005930") } returns DartCorpCodeEntity(
            ticker = "005930", corpCode = "00126380", corpName = "삼성전자",
            updatedAt = System.currentTimeMillis()  // fresh cache
        )
        coEvery { dartApiClient.fetchRecentDisclosures(any(), "00126380", any()) } returns emptyList()
        coEvery { regimeDao.getAllKospiIndex() } returns emptyList()

        engine.analyze("test-key", "005930", makePrices(60))

        // Should NOT call downloadCorpCodeMaster because cache is fresh
        coVerify(exactly = 0) { dartApiClient.downloadCorpCodeMaster(any()) }
    }

    // ─── helpers ───

    private fun makePrices(
        days: Int,
        startPrice: Int = 70000,
        foreignGen: (Int) -> Long = { 1_000_000L * (it % 5 - 2) },
        instGen: (Int) -> Long = { 500_000L * (it % 3 - 1) }
    ): List<DailyTrading> {
        val startDate = LocalDate.now().minusDays(days.toLong())
        return (0 until days).map { i ->
            val date = startDate.plusDays(i.toLong())
            DailyTrading(
                date = date.format(DateTimeFormatter.BASIC_ISO_DATE),
                marketCap = 300_000_000_000L,
                foreignNetBuy = foreignGen(i),
                instNetBuy = instGen(i),
                closePrice = startPrice + i * 50 + (i % 7 - 3) * 200
            )
        }
    }

    private fun generateTradingDates(count: Int): List<String> {
        val start = LocalDate.of(2025, 1, 2)
        val fmt = DateTimeFormatter.BASIC_ISO_DATE
        return (0 until count).map { start.plusDays(it.toLong()).format(fmt) }
    }

    private fun generateKospiData(days: Int): List<KospiIndexEntity> {
        val start = LocalDate.now().minusDays(days.toLong())
        val fmt = DateTimeFormatter.BASIC_ISO_DATE
        return (0 until days).map { i ->
            KospiIndexEntity(
                date = start.plusDays(i.toLong()).format(fmt),
                closeValue = 2500.0 + i * 0.5 + (i % 7 - 3) * 5.0
            )
        }
    }
}
