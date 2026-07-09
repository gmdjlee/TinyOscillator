package com.tinyoscillator.feature.bearsignal.data.repository

import com.krxkt.KrxIndex
import com.krxkt.KrxStock
import com.krxkt.model.IndexOhlcv
import com.krxkt.model.Market
import com.krxkt.model.MarketCap
import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.domain.model.KrxCredentials
import com.tinyoscillator.feature.bearsignal.data.local.BearSignalDao
import com.tinyoscillator.feature.bearsignal.data.mapper.BearSignalAutoCacheMapper
import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.AutoIndicator
import com.tinyoscillator.feature.bearsignal.domain.model.InputSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [BearSignalRepositoryImpl] 자동 수집·Room 캐시 폴백 테스트 (TASK.md §1.2, §4, Phase 1).
 *
 * KRX 실호출 없이 [KrxApiClient]/[KrxIndex]/[KrxStock]/[ApiConfigProvider]를 MockK로 대체한다.
 */
class BearSignalRepositoryImplTest {

    private lateinit var dao: BearSignalDao
    private lateinit var krxApiClient: KrxApiClient
    private lateinit var apiConfigProvider: ApiConfigProvider
    private lateinit var repository: BearSignalRepositoryImpl

    private val krxIndex: KrxIndex = mockk()
    private val krxStock: KrxStock = mockk()

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        krxApiClient = mockk(relaxed = true)
        apiConfigProvider = mockk()
        repository = BearSignalRepositoryImpl(dao, krxApiClient, apiConfigProvider)
    }

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

    // ── 정상 수집 경로 ─────────────────────────────────────────

    @Test
    fun `refreshAutoInputs 성공 시 캐시에 upsert하고 결과 반환`() = runTest {
        coEvery { apiConfigProvider.getKrxCredentials() } returns validCredentials()
        coEvery { krxApiClient.login(any(), any()) } returns true
        every { krxApiClient.getKrxIndex() } returns krxIndex
        every { krxApiClient.getKrxStock() } returns krxStock

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

    // ── 폴백 경로 ─────────────────────────────────────────────

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

    // ── 조회 ─────────────────────────────────────────────────

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
}
