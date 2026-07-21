package com.tinyoscillator.data.repository

import com.krxkt.KrxIndex
import com.krxkt.model.DerivativeIndex
import com.krxkt.model.OptionVolume
import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.core.database.dao.FearGreedDao
import com.tinyoscillator.core.database.entity.FearGreedEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [FearGreedRepository] 읽기 위임·차트 매핑·멀티소스 병합 게이트 검증 (P8-1).
 *
 * 361줄 멀티소스(옵션/채권/VKOSPI/지수) 병합이 무테스트였다. Fear&Greed 지수의 수치 계산
 * 자체는 [com.tinyoscillator.domain.usecase.FearGreedCalculatorTest]가 커버하므로, 여기서는
 * 리포지토리 고유 책임(위임·매핑·로그인/클라이언트 실패·병합 교집합 게이트)에 집중한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FearGreedRepositoryTest {

    private val dao: FearGreedDao = mockk(relaxed = true)
    private val krxApiClient: KrxApiClient = mockk()
    private val krxIndex: KrxIndex = mockk()
    private lateinit var repo: FearGreedRepository

    @Before
    fun setup() {
        repo = FearGreedRepository(dao, krxApiClient)
    }

    private fun entity(date: String) = FearGreedEntity(
        id = "KOSPI-$date", market = "KOSPI", date = date,
        indexValue = 2500.0, fearGreedValue = 0.6, oscillator = 1.2,
        rsi = 55.0, momentum = 0.1, putCallRatio = 0.9, volatility = 15.0, spread = 0.3
    )

    // ── 읽기 API 위임 ──

    @Test
    fun `getCountByMarket 위임`() = runTest {
        coEvery { dao.getCountByMarket("KOSPI") } returns 42
        assertEquals(42, repo.getCountByMarket("KOSPI"))
    }

    @Test
    fun `getLatestDate 위임`() = runTest {
        coEvery { dao.getLatestDate("KOSDAQ") } returns "2026-07-21"
        assertEquals("2026-07-21", repo.getLatestDate("KOSDAQ"))
    }

    @Test
    fun `getLastUpdateTime 위임`() = runTest {
        coEvery { dao.getLastUpdateTime("KOSPI") } returns 123L
        assertEquals(123L, repo.getLastUpdateTime("KOSPI"))
    }

    @Test
    fun `getRecentData 위임`() = runTest {
        val list = listOf(entity("2026-07-20"))
        coEvery { dao.getRecentData("KOSPI", 5) } returns list
        assertEquals(list, repo.getRecentData("KOSPI", 5))
    }

    // ── getChartData 매핑 ──

    @Test
    fun `getChartData는 엔티티를 FearGreedRow로 매핑`() = runTest {
        every { dao.getByMarketAndDateRange("KOSPI", "2026-07-01", "2026-07-21") } returns
            flowOf(listOf(entity("2026-07-20")))

        val data = repo.getChartData("KOSPI", "2026-07-01", "2026-07-21").first()

        assertEquals("KOSPI", data.market)
        assertEquals(1, data.rows.size)
        val row = data.rows[0]
        assertEquals("2026-07-20", row.date)
        assertEquals(2500.0, row.indexValue, 0.0001)
        assertEquals(0.6, row.fearGreedValue, 0.0001)
        assertEquals(1.2, row.oscillator, 0.0001)
    }

    // ── updateFearGreed 실패 경로 ──

    @Test
    fun `KRX 로그인 실패 시 failure·insertAll 미호출`() = runTest {
        coEvery { krxApiClient.login(any(), any()) } returns false

        val r = repo.updateFearGreed("id", "pw")

        assertTrue(r.isFailure)
        assertEquals("KRX 로그인 실패", r.exceptionOrNull()?.message)
        coVerify(exactly = 0) { dao.insertAll(any()) }
    }

    @Test
    fun `KRX 인덱스 클라이언트 null이면 failure`() = runTest {
        coEvery { krxApiClient.login(any(), any()) } returns true
        every { krxApiClient.getKrxIndex() } returns null

        val r = repo.updateFearGreed("id", "pw")

        assertTrue(r.isFailure)
        coVerify(exactly = 0) { dao.insertAll(any()) }
    }

    // ── 멀티소스 병합 게이트 ──

    @Test
    fun `수집 데이터 전무면 병합 0건 → failure`() = runTest {
        coEvery { krxApiClient.login(any(), any()) } returns true
        every { krxApiClient.getKrxIndex() } returns krxIndex
        coEvery { krxIndex.getCallOptionVolume(any(), any()) } returns emptyList()
        coEvery { krxIndex.getPutOptionVolume(any(), any()) } returns emptyList()
        coEvery { krxIndex.getBond5y(any(), any()) } returns emptyList()
        coEvery { krxIndex.getBond10y(any(), any()) } returns emptyList()
        coEvery { krxIndex.getVkospi(any(), any()) } returns emptyList()
        coEvery { krxIndex.getKospi(any(), any()) } returns emptyList()
        coEvery { krxIndex.getKosdaq(any(), any()) } returns emptyList()

        val r = repo.updateFearGreed("id", "pw")

        assertTrue(r.isFailure)
        assertEquals("수집된 데이터가 없습니다", r.exceptionOrNull()?.message)
        coVerify(exactly = 0) { dao.insertAll(any()) }
    }

    @Test
    fun `옵션과 채권이 서로 다른 날짜면 교집합 0건 → failure`() = runTest {
        coEvery { krxApiClient.login(any(), any()) } returns true
        every { krxApiClient.getKrxIndex() } returns krxIndex

        // 옵션은 1월, 채권/VKOSPI는 2월 — 공통 거래일 없음 → 병합 행 0
        val optionJan = (1..10).map { OptionVolume("202601%02d".format(it), 1000L + it) }
        val derivFeb = (1..10).map { DerivativeIndex("202602%02d".format(it), 3.0 + it * 0.1) }

        coEvery { krxIndex.getCallOptionVolume(any(), any()) } returns optionJan
        coEvery { krxIndex.getPutOptionVolume(any(), any()) } returns optionJan
        coEvery { krxIndex.getBond5y(any(), any()) } returns derivFeb
        coEvery { krxIndex.getBond10y(any(), any()) } returns derivFeb
        coEvery { krxIndex.getVkospi(any(), any()) } returns derivFeb
        coEvery { krxIndex.getKospi(any(), any()) } returns emptyList()
        coEvery { krxIndex.getKosdaq(any(), any()) } returns emptyList()

        val r = repo.updateFearGreed("id", "pw")

        assertTrue(r.isFailure)
        assertEquals("수집된 데이터가 없습니다", r.exceptionOrNull()?.message)
        coVerify(exactly = 0) { dao.insertAll(any()) }
    }

    // ── initializeFearGreed 실패 경로 ──

    @Test
    fun `초기 수집 로그인 실패 시 deleteAll 미호출`() = runTest {
        coEvery { krxApiClient.login(any(), any()) } returns false

        val r = repo.initializeFearGreed(days = 365, krxId = "id", krxPassword = "pw")

        assertTrue(r.isFailure)
        coVerify(exactly = 0) { dao.deleteAll() }
        coVerify(exactly = 0) { dao.insertAll(any()) }
    }
}
