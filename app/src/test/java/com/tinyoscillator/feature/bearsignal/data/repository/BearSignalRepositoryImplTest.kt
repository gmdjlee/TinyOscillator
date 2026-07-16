package com.tinyoscillator.feature.bearsignal.data.repository

import com.krxkt.KrxIndex
import com.krxkt.KrxStock
import com.krxkt.model.IndexOhlcv
import com.krxkt.model.Market
import com.krxkt.model.MarketCap
import com.tinyoscillator.core.api.BokEcosApiClient
import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.domain.model.EcosDataPoint
import com.tinyoscillator.domain.model.KrxCredentials
import com.tinyoscillator.feature.bearsignal.data.local.BearSignalAutoCacheEntity
import com.tinyoscillator.feature.bearsignal.data.local.BearSignalDao
import com.tinyoscillator.feature.bearsignal.data.local.BearSignalManualCountryReturnEntity
import com.tinyoscillator.feature.bearsignal.data.local.BearSignalManualInputEntity
import com.tinyoscillator.feature.bearsignal.data.mapper.BearSignalAutoCacheMapper
import com.tinyoscillator.feature.bearsignal.data.mapper.BearSignalCountryReturnMapper
import com.tinyoscillator.feature.bearsignal.data.mapper.BearSignalManualInputMapper
import com.tinyoscillator.feature.bearsignal.data.remote.CustomsTradeApiClient
import com.tinyoscillator.feature.bearsignal.data.remote.FredApiClient
import com.tinyoscillator.feature.bearsignal.data.remote.FredObservation
import com.tinyoscillator.feature.bearsignal.data.remote.IndexDailyBar
import com.tinyoscillator.feature.bearsignal.data.remote.StooqCsvClient
import com.tinyoscillator.feature.bearsignal.data.remote.YahooChartApiClient
import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.AutoIndicator
import com.tinyoscillator.feature.bearsignal.domain.model.AutoMarketReturn
import com.tinyoscillator.feature.bearsignal.domain.model.BearIndicatorKey
import com.tinyoscillator.feature.bearsignal.domain.model.CustomsTradeItem
import com.tinyoscillator.feature.bearsignal.domain.model.GlobalIndexSource
import com.tinyoscillator.feature.bearsignal.domain.model.InputSource
import com.tinyoscillator.feature.bearsignal.domain.model.ManualFieldUpdate
import com.tinyoscillator.feature.bearsignal.domain.model.ManualIndicatorKey
import com.tinyoscillator.feature.bearsignal.domain.model.MarketCoverage
import com.tinyoscillator.feature.bearsignal.domain.model.MarketReturnsSnapshot
import com.tinyoscillator.feature.bearsignal.domain.model.SuggestionField
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [BearSignalRepositoryImpl] 자동 수집·Room 캐시 폴백 테스트 (TASK.md §1.2, §4, Phase 1+2).
 *
 * KRX/관세청/FRED/ECOS/시세소스(Yahoo·Stooq) 실호출 없이 관련 클라이언트를 MockK로 대체한다.
 * 시세 소스는 기본값(Yahoo 우선)으로 조회하고, 실패 시 Stooq로 자동 폴백하는 경로를 함께 검증한다.
 */
class BearSignalRepositoryImplTest {

    private lateinit var dao: BearSignalDao
    private lateinit var krxApiClient: KrxApiClient
    private lateinit var apiConfigProvider: ApiConfigProvider
    private lateinit var customsTradeApiClient: CustomsTradeApiClient
    private lateinit var fredApiClient: FredApiClient
    private lateinit var bokEcosApiClient: BokEcosApiClient
    private lateinit var stooqCsvClient: StooqCsvClient
    private lateinit var yahooChartApiClient: YahooChartApiClient
    private lateinit var repository: BearSignalRepositoryImpl

    private val krxIndex: KrxIndex = mockk()
    private val krxStock: KrxStock = mockk()

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        krxApiClient = mockk(relaxed = true)
        apiConfigProvider = mockk()
        customsTradeApiClient = mockk()
        fredApiClient = mockk()
        bokEcosApiClient = mockk()
        stooqCsvClient = mockk()
        yahooChartApiClient = mockk()
        repository = createRepository()
    }

    private fun createRepository(
        indexSource: GlobalIndexSource = GlobalIndexSource.DEFAULT
    ) = BearSignalRepositoryImpl(
        dao, krxApiClient, apiConfigProvider,
        customsTradeApiClient, fredApiClient, bokEcosApiClient,
        stooqCsvClient, yahooChartApiClient,
        indexSourceProvider = { indexSource }
    )

    private fun createIndexOhlcv(date: String, close: Double) = IndexOhlcv(
        date = date,
        open = close,
        high = close,
        low = close,
        close = close,
        volume = 0L,
        tradingValue = 0L,
        changeType = null,
        change = null
    )

    private fun validCredentials() = KrxCredentials("test-id", "test-pw")

    private fun cachedInputs() = AutoBearSignalInputs(
        up3 = AutoIndicator(14, InputSource.AUTO, 500L),
        down3 = AutoIndicator(12, InputSource.AUTO, 500L),
        up4 = AutoIndicator(3, InputSource.AUTO, 500L),
        down4 = AutoIndicator(2, InputSource.AUTO, 500L),
        kospi2 = AutoIndicator(56.0, InputSource.AUTO, 500L)
    )

    private fun cachedInputsWithExternal() = cachedInputs().copy(
        semi = AutoIndicator(20.0, InputSource.AUTO, 400L),
        buffer = AutoIndicator(true, InputSource.AUTO, 400L),
        rate = AutoIndicator(4.00, InputSource.AUTO, 400L),
        dir = AutoIndicator("hold", InputSource.AUTO, 400L),
        etf = AutoIndicator("flat", InputSource.AUTO, 400L)
    )

    // ── 정상 수집 경로(Phase 1) ─────────────────────────────────

    @Test
    fun `refreshAutoInputs 성공 시 캐시에 upsert하고 결과 반환`() = runTest {
        coEvery { apiConfigProvider.getKrxCredentials() } returns validCredentials()
        coEvery { krxApiClient.login(any(), any()) } returns true
        every { krxApiClient.getKrxIndex() } returns krxIndex
        every { krxApiClient.getKrxStock() } returns krxStock
        coEvery { dao.getAutoCache() } returns emptyList()

        // 26개 종가(25 수익률) — MIN_RETURNS(20) 충족, 급변 없이 완만한 흐름
        val closes = (0..25).map { i -> createIndexOhlcv(String.format("202606%02d", i + 1), 2500.0 + i * 0.1) }
        coEvery { krxIndex.getKospi(any(), any()) } returns closes
        coEvery { krxStock.getMarketCap(any(), Market.KOSPI) } returns listOf(
            MarketCap("005930", "삼성전자", 70_000L, 0.0, 500_000_000_000L, 1L),
            MarketCap("000660", "SK하이닉스", 200_000L, 0.0, 300_000_000_000L, 1L),
            MarketCap("005380", "현대차", 200_000L, 0.0, 200_000_000_000L, 1L)
        )

        val result = repository.refreshAutoInputs()

        assertTrue(result.isSuccess)
        // total=1_000_000_000_000, 삼성+SK=800_000_000_000 → 80%
        assertEquals(80.0, result.getOrNull()!!.kospi2.value, 1e-9)
        coVerify { dao.upsertAll(any()) }
        coVerify { krxApiClient.close() }
    }

    @Test
    fun `refreshAutoInputs 성공 시 기존 Phase2 캐시값을 보존한다`() = runTest {
        coEvery { apiConfigProvider.getKrxCredentials() } returns validCredentials()
        coEvery { krxApiClient.login(any(), any()) } returns true
        every { krxApiClient.getKrxIndex() } returns krxIndex
        every { krxApiClient.getKrxStock() } returns krxStock
        // getCachedAutoInputs()는 refreshAutoInputs 내부에서 두 번 불릴 수 있음(폴백/보존 조회) — 항상 기존 P2값 반환
        coEvery { dao.getAutoCache() } returns BearSignalAutoCacheMapper.toEntities(cachedInputsWithExternal())

        val closes = (0..25).map { i -> createIndexOhlcv(String.format("202606%02d", i + 1), 2500.0 + i * 0.1) }
        coEvery { krxIndex.getKospi(any(), any()) } returns closes
        coEvery { krxStock.getMarketCap(any(), Market.KOSPI) } returns listOf(
            MarketCap("005930", "삼성전자", 70_000L, 0.0, 500_000_000_000L, 1L),
            MarketCap("000660", "SK하이닉스", 200_000L, 0.0, 300_000_000_000L, 1L)
        )

        val result = repository.refreshAutoInputs()

        assertTrue(result.isSuccess)
        assertEquals("hold", result.getOrNull()!!.dir!!.value)
        assertEquals(20.0, result.getOrNull()!!.semi!!.value, 1e-9)
    }

    // ── 폴백 경로(Phase 1) ─────────────────────────────────────

    @Test
    fun `refreshAutoInputs KRX 로그인 실패 시 캐시로 폴백`() = runTest {
        coEvery { apiConfigProvider.getKrxCredentials() } returns validCredentials()
        coEvery { krxApiClient.login(any(), any()) } returns false
        coEvery { dao.getAutoCache() } returns BearSignalAutoCacheMapper.toEntities(cachedInputs())

        val result = repository.refreshAutoInputs()

        assertTrue(result.isSuccess)
        assertEquals(14, result.getOrNull()!!.up3.value)
    }

    @Test
    fun `refreshAutoInputs 계정 미설정 시 로그인 시도 없이 캐시로 폴백`() = runTest {
        coEvery { apiConfigProvider.getKrxCredentials() } returns KrxCredentials("", "")
        coEvery { dao.getAutoCache() } returns BearSignalAutoCacheMapper.toEntities(cachedInputs())

        val result = repository.refreshAutoInputs()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { krxApiClient.login(any(), any()) }
    }

    @Test
    fun `refreshAutoInputs 실패하고 캐시도 없으면 failure`() = runTest {
        coEvery { apiConfigProvider.getKrxCredentials() } returns validCredentials()
        coEvery { krxApiClient.login(any(), any()) } returns false
        coEvery { dao.getAutoCache() } returns emptyList()

        val result = repository.refreshAutoInputs()

        assertTrue(result.isFailure)
    }

    @Test
    fun `refreshAutoInputs 데이터 부족 시 캐시로 폴백`() = runTest {
        coEvery { apiConfigProvider.getKrxCredentials() } returns validCredentials()
        coEvery { krxApiClient.login(any(), any()) } returns true
        every { krxApiClient.getKrxIndex() } returns krxIndex
        every { krxApiClient.getKrxStock() } returns krxStock
        // 5개 종가(4 수익률) — MIN_RETURNS(20) 미달
        coEvery { krxIndex.getKospi(any(), any()) } returns (0..4).map {
            createIndexOhlcv("2026060${it + 1}", 2500.0)
        }
        coEvery { dao.getAutoCache() } returns BearSignalAutoCacheMapper.toEntities(cachedInputs())

        val result = repository.refreshAutoInputs()

        assertTrue(result.isSuccess)
        assertEquals(14, result.getOrNull()!!.up3.value)
    }

    // ── 조회(Phase 1) ────────────────────────────────────────

    @Test
    fun `observeAutoInputs Room Flow를 도메인 모델로 매핑`() = runTest {
        every { dao.observeAutoCache() } returns flowOf(BearSignalAutoCacheMapper.toEntities(cachedInputs()))

        val emitted = repository.observeAutoInputs().first()

        assertEquals(14, emitted?.up3?.value)
        assertEquals(56.0, emitted?.kospi2?.value)
    }

    @Test
    fun `getCachedAutoInputs 캐시 없으면 null`() = runTest {
        coEvery { dao.getAutoCache() } returns emptyList()

        val cached = repository.getCachedAutoInputs()

        assertEquals(null, cached)
    }

    // ── Phase 2: refreshExternalAutoInputs ──────────────────────

    private fun customsFixtureItems(yearMonth: String) = listOf(
        CustomsTradeItem("반도체", "854239", 20_000.0, 0.0, yearMonth),
        CustomsTradeItem("자동차", "870323", 15_000.0, 0.0, yearMonth),
        CustomsTradeItem("일반기계", "845011", 10_000.0, 0.0, yearMonth),
        CustomsTradeItem("석유제품", "271019", 5_000.0, 0.0, yearMonth),
        CustomsTradeItem("선박", "890120", 5_000.0, 0.0, yearMonth)
    )

    @Test
    fun `refreshExternalAutoInputs 성공 시 5개 지표 모두 반영하고 upsert`() = runTest {
        coEvery { dao.getAutoCache() } returns BearSignalAutoCacheMapper.toEntities(cachedInputs())
        coEvery { apiConfigProvider.getCustomsTradeApiKey() } returns "customs-key"
        coEvery { customsTradeApiClient.fetchItemTrade(any(), any(), any()) } coAnswers {
            customsFixtureItems(secondArg())
        }
        coEvery { apiConfigProvider.getFredApiKey() } returns "fred-key"
        coEvery { fredApiClient.fetchLatestObservation(any()) } returns FredObservation("2026-06-30", 4.25)
        coEvery { apiConfigProvider.getEcosApiKey() } returns "ecos-key"
        coEvery { bokEcosApiClient.fetchSeries(any(), "base_rate", any(), any()) } returns listOf(
            EcosDataPoint("202604", 3.25),
            EcosDataPoint("202605", 3.50)
        )
        coEvery { yahooChartApiClient.fetchDailyCloses(any()) } returns listOf(
            IndexDailyBar("2026-06-01", 40.0),
            IndexDailyBar("2026-06-30", 41.0)
        )

        val result = repository.refreshExternalAutoInputs()

        assertTrue(result.isSuccess)
        val inputs = result.getOrNull()!!
        assertEquals(4.25, inputs.rate!!.value, 1e-9)
        assertEquals("hike", inputs.dir!!.value) // 3.50 > 3.25
        assertEquals(InputSource.AUTO, inputs.semi!!.source)
        coVerify { dao.upsertAll(any()) }
    }

    @Test
    fun `refreshExternalAutoInputs 관세청 최신월 빈 응답이면 한 달 전으로 폴백`() = runTest {
        coEvery { dao.getAutoCache() } returns BearSignalAutoCacheMapper.toEntities(cachedInputs())
        coEvery { apiConfigProvider.getCustomsTradeApiKey() } returns "customs-key"
        // 매월 15일경 현행화 전 구간: 최신월(now-1)은 아직 빈 응답, 그 전 달부터 데이터 존재
        val latestYm = java.time.YearMonth.now().minusMonths(1)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"))
        coEvery { customsTradeApiClient.fetchItemTrade(any(), any(), any()) } coAnswers {
            if (secondArg<String>() == latestYm) emptyList() else customsFixtureItems(secondArg())
        }
        coEvery { apiConfigProvider.getFredApiKey() } returns null
        coEvery { apiConfigProvider.getEcosApiKey() } returns null
        coEvery { yahooChartApiClient.fetchDailyCloses(any()) } returns listOf(IndexDailyBar("2026-06-30", 40.0))

        val result = repository.refreshExternalAutoInputs()

        assertTrue(result.isSuccess)
        // 폴백 월 데이터로 semi 산출(반도체 20000/55000)
        assertEquals(InputSource.AUTO, result.getOrNull()!!.semi!!.source)
        assertEquals(20_000.0 / 55_000.0 * 100.0, result.getOrNull()!!.semi!!.value, 1e-9)
        // 호출 3회: 최신월(빈 응답) → 폴백월 → 폴백월 기준 전년동월
        coVerify(exactly = 3) { customsTradeApiClient.fetchItemTrade(any(), any(), any()) }
    }

    @Test
    fun `refreshExternalAutoInputs 관세청 키 미설정 시 semi buffer는 기존 캐시 유지`() = runTest {
        coEvery { dao.getAutoCache() } returns BearSignalAutoCacheMapper.toEntities(cachedInputsWithExternal())
        coEvery { apiConfigProvider.getCustomsTradeApiKey() } returns null
        coEvery { apiConfigProvider.getFredApiKey() } returns "fred-key"
        coEvery { fredApiClient.fetchLatestObservation(any()) } returns FredObservation("2026-06-30", 4.25)
        coEvery { apiConfigProvider.getEcosApiKey() } returns "ecos-key"
        coEvery { bokEcosApiClient.fetchSeries(any(), "base_rate", any(), any()) } returns listOf(
            EcosDataPoint("202604", 3.50),
            EcosDataPoint("202605", 3.50)
        )
        coEvery { yahooChartApiClient.fetchDailyCloses(any()) } returns listOf(IndexDailyBar("2026-06-30", 40.0))

        val result = repository.refreshExternalAutoInputs()

        assertTrue(result.isSuccess)
        // 기존 캐시(cachedInputsWithExternal)의 semi=20.0, buffer=true 그대로 유지
        assertEquals(20.0, result.getOrNull()!!.semi!!.value, 1e-9)
        assertTrue(result.getOrNull()!!.buffer!!.value)
    }

    @Test
    fun `refreshExternalAutoInputs FRED 호출 실패 시 rate만 기존 캐시 유지하고 나머지는 갱신`() = runTest {
        coEvery { dao.getAutoCache() } returns BearSignalAutoCacheMapper.toEntities(cachedInputsWithExternal())
        coEvery { apiConfigProvider.getCustomsTradeApiKey() } returns "customs-key"
        coEvery { customsTradeApiClient.fetchItemTrade(any(), any(), any()) } coAnswers {
            customsFixtureItems(secondArg())
        }
        coEvery { apiConfigProvider.getFredApiKey() } returns "fred-key"
        coEvery { fredApiClient.fetchLatestObservation(any()) } throws RuntimeException("network error")
        coEvery { apiConfigProvider.getEcosApiKey() } returns "ecos-key"
        coEvery { bokEcosApiClient.fetchSeries(any(), "base_rate", any(), any()) } returns listOf(
            EcosDataPoint("202604", 3.25),
            EcosDataPoint("202605", 3.50)
        )
        coEvery { yahooChartApiClient.fetchDailyCloses(any()) } returns listOf(IndexDailyBar("2026-06-30", 40.0))

        val result = repository.refreshExternalAutoInputs()

        assertTrue(result.isSuccess)
        // rate는 실패 → 기존 캐시(4.00) 유지, dir은 정상 갱신(hike)
        assertEquals(4.00, result.getOrNull()!!.rate!!.value, 1e-9)
        assertEquals("hike", result.getOrNull()!!.dir!!.value)
    }

    @Test
    fun `refreshExternalAutoInputs ECOS 키 미설정 시 dir은 기존 캐시 유지`() = runTest {
        coEvery { dao.getAutoCache() } returns BearSignalAutoCacheMapper.toEntities(cachedInputsWithExternal())
        coEvery { apiConfigProvider.getCustomsTradeApiKey() } returns null
        coEvery { apiConfigProvider.getFredApiKey() } returns null
        coEvery { apiConfigProvider.getEcosApiKey() } returns null
        coEvery { yahooChartApiClient.fetchDailyCloses(any()) } returns listOf(IndexDailyBar("2026-06-30", 40.0))

        val result = repository.refreshExternalAutoInputs()

        assertTrue(result.isSuccess)
        assertEquals("hold", result.getOrNull()!!.dir!!.value)
    }

    @Test
    fun `refreshExternalAutoInputs IPO ETF 두 소스 모두 실패 시 etf는 기존 캐시 유지`() = runTest {
        coEvery { dao.getAutoCache() } returns BearSignalAutoCacheMapper.toEntities(cachedInputsWithExternal())
        coEvery { apiConfigProvider.getCustomsTradeApiKey() } returns null
        coEvery { apiConfigProvider.getFredApiKey() } returns null
        coEvery { apiConfigProvider.getEcosApiKey() } returns null
        coEvery { yahooChartApiClient.fetchDailyCloses(any()) } throws RuntimeException("network error")
        coEvery { stooqCsvClient.fetchDailyCloses(any()) } throws RuntimeException("network error")

        val result = repository.refreshExternalAutoInputs()

        assertTrue(result.isSuccess)
        assertEquals("flat", result.getOrNull()!!.etf!!.value)
    }

    @Test
    fun `refreshExternalAutoInputs IPO ETF 기본 소스 실패 시 백업 소스로 폴백한다`() = runTest {
        coEvery { dao.getAutoCache() } returns BearSignalAutoCacheMapper.toEntities(cachedInputs())
        coEvery { apiConfigProvider.getCustomsTradeApiKey() } returns null
        coEvery { apiConfigProvider.getFredApiKey() } returns null
        coEvery { apiConfigProvider.getEcosApiKey() } returns null
        coEvery { yahooChartApiClient.fetchDailyCloses(any()) } returns emptyList()
        // 최신 종가가 최근 고점 근접 → "up"
        coEvery { stooqCsvClient.fetchDailyCloses(any()) } returns listOf(
            IndexDailyBar("2026-06-01", 40.0),
            IndexDailyBar("2026-06-30", 41.0)
        )

        val result = repository.refreshExternalAutoInputs()

        assertTrue(result.isSuccess)
        assertEquals("up", result.getOrNull()!!.etf!!.value)
        coVerify(exactly = 1) { stooqCsvClient.fetchDailyCloses("ipo.us") }
    }

    @Test
    fun `refreshExternalAutoInputs Phase1 기본 캐시가 없으면 failure`() = runTest {
        coEvery { dao.getAutoCache() } returns emptyList()

        val result = repository.refreshExternalAutoInputs()

        assertTrue(result.isFailure)
    }

    // ── Phase 2: refreshMarketReturns ────────────────────────────

    private fun kospiCloses() = (0..25).map { i -> createIndexOhlcv(String.format("202606%02d", i + 1), 2500.0 + i) }

    @Test
    fun `refreshMarketReturns 성공 시 코스피와 커버 지수는 AUTO, 미커버는 MANUAL_REQUIRED`() = runTest {
        coEvery { dao.getCountryReturns() } returns emptyList()
        coEvery { apiConfigProvider.getKrxCredentials() } returns validCredentials()
        coEvery { krxApiClient.login(any(), any()) } returns true
        every { krxApiClient.getKrxIndex() } returns krxIndex
        coEvery { krxIndex.getKospi(any(), any()) } returns kospiCloses()
        coEvery { yahooChartApiClient.fetchDailyCloses(any()) } returns (0..25).map { i ->
            IndexDailyBar(String.format("2026-06-%02d", (i % 28) + 1), 100.0 + i)
        }

        val result = repository.refreshMarketReturns()

        assertTrue(result.isSuccess)
        val snapshot = result.getOrNull()!!
        assertEquals(20, snapshot.markets.size) // 코스피 1 + 해외 19
        val kospi = snapshot.markets.first { it.name == "코스피" }
        assertEquals(MarketCoverage.AUTO, kospi.coverage)
        assertTrue(kospi.lead)
        assertTrue(snapshot.manualRequiredNames.isNotEmpty())
        assertTrue(snapshot.manualRequiredNames.contains("RTS"))
        // 기본 소스(Yahoo) 성공 시 백업 소스(Stooq)는 호출하지 않는다
        coVerify(exactly = 0) { stooqCsvClient.fetchDailyCloses(any()) }
        coVerify { dao.upsertCountryReturns(any()) }
    }

    @Test
    fun `refreshMarketReturns 코스피 조회 실패 시 캐시로 폴백`() = runTest {
        val cachedSnapshot = MarketReturnsSnapshot(
            markets = listOf(
                AutoMarketReturn("코스피", listOf(1.0, 2.0, 3.0, 4.0), lead = true, coverage = MarketCoverage.AUTO, updatedAt = 100L)
            )
        )
        coEvery { dao.getCountryReturns() } returns BearSignalCountryReturnMapper.toEntities(cachedSnapshot)
        coEvery { apiConfigProvider.getKrxCredentials() } returns validCredentials()
        coEvery { krxApiClient.login(any(), any()) } returns false
        coEvery { yahooChartApiClient.fetchDailyCloses(any()) } returns emptyList()
        coEvery { stooqCsvClient.fetchDailyCloses(any()) } returns emptyList()

        val result = repository.refreshMarketReturns()

        assertTrue(result.isSuccess)
        val kospi = result.getOrNull()!!.markets.first { it.name == "코스피" }
        assertEquals(4.0, kospi.r[3]!!, 1e-9)
    }

    @Test
    fun `refreshMarketReturns 코스피 실패하고 캐시도 없으면 failure`() = runTest {
        coEvery { dao.getCountryReturns() } returns emptyList()
        coEvery { apiConfigProvider.getKrxCredentials() } returns validCredentials()
        coEvery { krxApiClient.login(any(), any()) } returns false

        val result = repository.refreshMarketReturns()

        assertTrue(result.isFailure)
    }

    @Test
    fun `refreshMarketReturns 두 소스 모두 실패 시 해당 지수만 캐시 유지`() = runTest {
        val cachedSnapshot = MarketReturnsSnapshot(
            markets = listOf(
                AutoMarketReturn("닛케이", listOf(10.0, 8.0, 5.0, 1.0), lead = false, coverage = MarketCoverage.AUTO, updatedAt = 100L)
            )
        )
        coEvery { dao.getCountryReturns() } returns BearSignalCountryReturnMapper.toEntities(cachedSnapshot)
        coEvery { apiConfigProvider.getKrxCredentials() } returns validCredentials()
        coEvery { krxApiClient.login(any(), any()) } returns true
        every { krxApiClient.getKrxIndex() } returns krxIndex
        coEvery { krxIndex.getKospi(any(), any()) } returns kospiCloses()
        coEvery { yahooChartApiClient.fetchDailyCloses(any()) } throws RuntimeException("network error")
        coEvery { stooqCsvClient.fetchDailyCloses(any()) } throws RuntimeException("network error")

        val result = repository.refreshMarketReturns()

        assertTrue(result.isSuccess)
        val nikkei = result.getOrNull()!!.markets.first { it.name == "닛케이" }
        assertEquals(1.0, nikkei.r[3]!!, 1e-9)
    }

    @Test
    fun `refreshMarketReturns 기본 소스(Yahoo) 실패 시 백업 소스(Stooq)로 폴백한다`() = runTest {
        coEvery { dao.getCountryReturns() } returns emptyList()
        coEvery { apiConfigProvider.getKrxCredentials() } returns validCredentials()
        coEvery { krxApiClient.login(any(), any()) } returns true
        every { krxApiClient.getKrxIndex() } returns krxIndex
        coEvery { krxIndex.getKospi(any(), any()) } returns kospiCloses()
        coEvery { yahooChartApiClient.fetchDailyCloses(any()) } returns emptyList()
        coEvery { stooqCsvClient.fetchDailyCloses(any()) } returns (0..25).map { i ->
            IndexDailyBar(String.format("2026-06-%02d", (i % 28) + 1), 100.0 + i)
        }

        val result = repository.refreshMarketReturns()

        assertTrue(result.isSuccess)
        val nikkei = result.getOrNull()!!.markets.first { it.name == "닛케이" }
        assertEquals(MarketCoverage.AUTO, nikkei.coverage)
        assertFalse(nikkei.r.all { it == null })
        coVerify { stooqCsvClient.fetchDailyCloses("^nkx") }
    }

    @Test
    fun `refreshMarketReturns 선택 소스가 STOOQ면 Stooq를 먼저 조회하고 Yahoo는 호출하지 않는다`() = runTest {
        val stooqFirstRepository = createRepository(indexSource = GlobalIndexSource.STOOQ)
        coEvery { dao.getCountryReturns() } returns emptyList()
        coEvery { apiConfigProvider.getKrxCredentials() } returns validCredentials()
        coEvery { krxApiClient.login(any(), any()) } returns true
        every { krxApiClient.getKrxIndex() } returns krxIndex
        coEvery { krxIndex.getKospi(any(), any()) } returns kospiCloses()
        coEvery { stooqCsvClient.fetchDailyCloses(any()) } returns (0..25).map { i ->
            IndexDailyBar(String.format("2026-06-%02d", (i % 28) + 1), 100.0 + i)
        }

        val result = stooqFirstRepository.refreshMarketReturns()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { yahooChartApiClient.fetchDailyCloses(any()) }
    }

    @Test
    fun `observeMarketReturns Room Flow를 도메인 모델로 매핑`() = runTest {
        val snapshot = MarketReturnsSnapshot(
            markets = listOf(
                AutoMarketReturn("코스피", listOf(1.0, 2.0, 3.0, 4.0), lead = true, coverage = MarketCoverage.AUTO, updatedAt = 100L)
            )
        )
        every { dao.observeCountryReturns() } returns flowOf(BearSignalCountryReturnMapper.toEntities(snapshot))

        val emitted = repository.observeMarketReturns().first()

        assertEquals(1, emitted?.markets?.size)
        assertEquals("코스피", emitted?.markets?.first()?.name)
    }

    @Test
    fun `getCachedMarketReturns 캐시 없으면 null`() = runTest {
        coEvery { dao.getCountryReturns() } returns emptyList()

        val cached = repository.getCachedMarketReturns()

        assertNull(cached)
    }

    // ── Phase 3: 수동 오버라이드([C]/[D] 등급) ────────────────────

    @Test
    fun `updateManualInput Loss는 LOSS 키로 upsert된다`() = runTest {
        val slot = slot<BearSignalManualInputEntity>()
        coEvery { dao.upsertManualInput(capture(slot)) } returns Unit

        repository.updateManualInput(ManualFieldUpdate.Loss(72.0))

        assertEquals(ManualIndicatorKey.LOSS.key, slot.captured.indicatorKey)
        assertEquals(72.0, slot.captured.value, 1e-9)
    }

    @Test
    fun `updateManualInput Big은 인코딩되어 upsert된다`() = runTest {
        val slot = slot<BearSignalManualInputEntity>()
        coEvery { dao.upsertManualInput(capture(slot)) } returns Unit

        repository.updateManualInput(ManualFieldUpdate.Big("failed"))

        assertEquals(ManualIndicatorKey.BIG.key, slot.captured.indicatorKey)
        assertEquals(BearSignalManualInputMapper.encodeBig("failed"), slot.captured.value, 1e-9)
    }

    @Test
    fun `updateManualInput Margin은 boolean 인코딩되어 upsert된다`() = runTest {
        val slot = slot<BearSignalManualInputEntity>()
        coEvery { dao.upsertManualInput(capture(slot)) } returns Unit

        repository.updateManualInput(ManualFieldUpdate.Margin(true))

        assertEquals(ManualIndicatorKey.MARGIN.key, slot.captured.indicatorKey)
        assertEquals(1.0, slot.captured.value, 1e-9)
    }

    @Test
    fun `updateManualInput Dir는 DIR 키로 인코딩되어 upsert된다`() = runTest {
        val slot = slot<BearSignalManualInputEntity>()
        coEvery { dao.upsertManualInput(capture(slot)) } returns Unit

        repository.updateManualInput(ManualFieldUpdate.Dir("hike"))

        assertEquals(ManualIndicatorKey.DIR.key, slot.captured.indicatorKey)
        assertEquals(BearSignalManualInputMapper.encodeDir("hike"), slot.captured.value, 1e-9)
    }

    @Test
    fun `updateManualInput MarketReturn은 country_return 테이블에 upsert된다`() = runTest {
        val slot = slot<BearSignalManualCountryReturnEntity>()
        coEvery { dao.upsertManualCountryReturn(capture(slot)) } returns Unit

        repository.updateManualInput(ManualFieldUpdate.MarketReturn("RTS", listOf(-1.0, -2.0, -3.0, -4.0)))

        assertEquals("RTS", slot.captured.countryName)
        assertEquals(-4.0, slot.captured.r1m!!, 1e-9)
    }

    @Test
    fun `getManualInputs Room 캐시를 도메인 모델로 매핑`() = runTest {
        coEvery { dao.getManualInputs() } returns listOf(
            BearSignalManualInputEntity(ManualIndicatorKey.CREDIT.key, 45.0, 100L)
        )

        val manual = repository.getManualInputs()

        assertEquals(45.0, manual.credit!!.value, 1e-9)
        assertEquals(InputSource.MANUAL, manual.credit!!.source)
    }

    @Test
    fun `observeManualInputs Room Flow를 도메인 모델로 매핑`() = runTest {
        every { dao.observeManualInputs() } returns flowOf(
            listOf(BearSignalManualInputEntity(ManualIndicatorKey.LOSS.key, 60.0, 1L))
        )

        val emitted = repository.observeManualInputs().first()

        assertEquals(60.0, emitted.loss!!.value, 1e-9)
    }

    @Test
    fun `getManualMarketReturns Room 캐시를 도메인 모델 리스트로 매핑`() = runTest {
        coEvery { dao.getManualCountryReturns() } returns listOf(
            BearSignalManualCountryReturnEntity("RTS", -1.0, -2.0, -3.0, -4.0, 1L)
        )

        val list = repository.getManualMarketReturns()

        assertEquals(1, list.size)
        assertEquals("RTS", list.first().name)
    }

    @Test
    fun `resetToReportBaseline은 수동 오버라이드 두 테이블을 모두 비운다`() = runTest {
        coEvery { dao.clearManualInputs() } returns Unit
        coEvery { dao.clearManualCountryReturns() } returns Unit

        repository.resetToReportBaseline()

        coVerify(exactly = 1) { dao.clearManualInputs() }
        coVerify(exactly = 1) { dao.clearManualCountryReturns() }
    }

    @Test
    fun `resetToReportBaseline은 자동 수집 캐시를 건드리지 않는다`() = runTest {
        coEvery { dao.clearManualInputs() } returns Unit
        coEvery { dao.clearManualCountryReturns() } returns Unit

        repository.resetToReportBaseline()

        coVerify(exactly = 0) { dao.upsertAll(any()) }
        coVerify(exactly = 0) { dao.upsertCountryReturns(any()) }
    }

    // ── Phase 4: §4.5 웹/LLM 제안 승인 ──────────────────────────────────

    @Test
    fun `applySuggestion RATE는 GATE_RATE 키로 AUTO 엔티티 1건을 upsert한다`() = runTest {
        coEvery { dao.upsertAll(any()) } returns Unit

        repository.applySuggestion(SuggestionField.RATE, "4.50", 9_000L)

        val slot = slot<List<BearSignalAutoCacheEntity>>()
        coVerify(exactly = 1) { dao.upsertAll(capture(slot)) }
        assertEquals(1, slot.captured.size)
        val entity = slot.captured.first()
        assertEquals(BearIndicatorKey.GATE_RATE.key, entity.indicatorKey)
        assertEquals(4.50, entity.value, 1e-9)
        assertEquals(InputSource.AUTO.name, entity.source)
        assertEquals(9_000L, entity.updatedAt)
    }

    @Test
    fun `applySuggestion CREDIT는 GATE_CREDIT 신규 키로 upsert한다(기존 ManualIndicatorKey CREDIT와 별도)`() = runTest {
        coEvery { dao.upsertAll(any()) } returns Unit

        repository.applySuggestion(SuggestionField.CREDIT, "50.00", 9_000L)

        val slot = slot<List<BearSignalAutoCacheEntity>>()
        coVerify(exactly = 1) { dao.upsertAll(capture(slot)) }
        assertEquals(BearIndicatorKey.GATE_CREDIT.key, slot.captured.first().indicatorKey)
        assertEquals(50.0, slot.captured.first().value, 1e-9)
    }

    @Test
    fun `applySuggestion BIG_DEAL은 열거형 코드로 인코딩되어 upsert된다`() = runTest {
        coEvery { dao.upsertAll(any()) } returns Unit

        repository.applySuggestion(SuggestionField.BIG_DEAL, "failed", 9_000L)

        val slot = slot<List<BearSignalAutoCacheEntity>>()
        coVerify(exactly = 1) { dao.upsertAll(capture(slot)) }
        assertEquals(BearIndicatorKey.S3_BIG_DEAL.key, slot.captured.first().indicatorKey)
        assertEquals(2.0, slot.captured.first().value, 1e-9) // failed → 2.0
    }
}
