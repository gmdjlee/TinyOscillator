package com.tinyoscillator.data.repository

import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.core.api.InvestmentMode
import com.tinyoscillator.core.api.KisApiClient
import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.core.database.dao.InvestorFlowDao
import com.tinyoscillator.core.database.entity.InvestorFlowEntity
import com.tinyoscillator.core.util.DateFormats
import com.tinyoscillator.data.dto.KisInvestorFlowResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * [InvestorFlowRepository] 단위 테스트. 명세: docs/TASK_investor_volume_profile.md §6.2, §9.3.
 */
class InvestorFlowRepositoryTest {

    private lateinit var kisApiClient: KisApiClient
    private lateinit var dao: InvestorFlowDao

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val encodeJson = Json { encodeDefaults = true }

    private val validConfig = KisApiKeyConfig(appKey = "k", appSecret = "s", investmentMode = InvestmentMode.PRODUCTION)
    private val mockConfig = KisApiKeyConfig(appKey = "k", appSecret = "s", investmentMode = InvestmentMode.MOCK)
    private val invalidConfig = KisApiKeyConfig(appKey = "", appSecret = "")

    // 2026-09-04 16:00 KST — 15:40 이후이므로 startCursor() == today.
    private val fixedNow: ZonedDateTime = ZonedDateTime.of(2026, 9, 4, 16, 0, 0, 0, ZoneId.of("Asia/Seoul"))
    private val today: LocalDate = fixedNow.toLocalDate()

    @Before
    fun setup() {
        kisApiClient = mockk(relaxed = true)
        dao = mockk(relaxed = true)
        coEvery { dao.latestDate(any()) } returns null
        coEvery { dao.earliestDate(any()) } returns null
        coEvery { dao.latestFetchedAt(any()) } returns null
        coEvery { dao.getRange(any(), any(), any()) } returns emptyList()
        coEvery { dao.upsertAll(any()) } just Runs
        coEvery { dao.deleteOlderThan(any(), any()) } just Runs
    }

    private fun repository(): InvestorFlowRepository =
        InvestorFlowRepository(dao, kisApiClient, json, now = { fixedNow })

    // ── 페이지 생성 헬퍼 ──────────────────────────────────────────────

    /** [date] 이하 30개(고정) 평일을 최신순으로 만든다(주말 건너뜀, 실제 KIS 페이징과 동형). */
    private fun tradingDaysOnOrBefore(date: LocalDate, count: Int = 30): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var cursor = date
        while (result.size < count) {
            if (cursor.dayOfWeek != DayOfWeek.SATURDAY && cursor.dayOfWeek != DayOfWeek.SUNDAY) {
                result += cursor
            }
            cursor = cursor.minusDays(1)
        }
        return result
    }

    private fun rowMap(date: LocalDate): Map<String, String?> = mapOf(
        "stck_bsop_date" to date.format(DateFormats.yyyyMMdd),
        "stck_hgpr" to "255500",
        "stck_lwpr" to "249500",
        "stck_clpr" to "250500",
        "acml_vol" to "15176841",
        "prsn_shnu_vol" to "4662430", "prsn_shnu_tr_pbmn" to "1175428",
        "prsn_seln_vol" to "4000000", "prsn_seln_tr_pbmn" to "1000000",
        "frgn_shnu_vol" to "3000000", "frgn_shnu_tr_pbmn" to "760000",
        "frgn_seln_vol" to "2900000", "frgn_seln_tr_pbmn" to "730000",
        "orgn_shnu_vol" to "2000000", "orgn_shnu_tr_pbmn" to "500000",
        "orgn_seln_vol" to "1900000", "orgn_seln_tr_pbmn" to "480000"
    )

    private fun pageBody(dates: List<LocalDate>, rtCd: String = "0", msgCd: String = "", msg1: String = ""): String {
        val response = KisInvestorFlowResponse(rtCd = rtCd, msgCd = msgCd, msg1 = msg1, output2 = dates.map { rowMap(it) })
        return encodeJson.encodeToString(response)
    }

    /** date1 요청마다 그 날짜 이하 30 거래일을 돌려주는 정상 페이지 스텁. */
    private fun stubDynamicPages(requestedDates: MutableList<LocalDate> = mutableListOf()) {
        coEvery {
            kisApiClient.get(any(), any(), any(), any(), any<(String) -> String>())
        } answers {
            val params = arg<Map<String, String>>(2)
            val date1 = LocalDate.parse(params.getValue("FID_INPUT_DATE_1"), DateFormats.yyyyMMdd)
            requestedDates += date1
            Result.success(pageBody(tradingDaysOnOrBefore(date1)))
        }
    }

    private fun isAdjacentTradingDay(newer: LocalDate, olderCandidate: LocalDate): Boolean {
        var d = newer.minusDays(1)
        while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.minusDays(1)
        return d == olderCandidate
    }

    /**
     * 캐시 블록 없이(stopAtOrBefore=null) [start]부터 30 거래일씩 [boundary] 이하에 닿을 때까지
     * 걷는 페이지 수를 독립적으로 재계산한다(프로덕션 커서 산식과 별개로 구현한 참조 계산).
     */
    private fun expectedForwardOnlyPageCount(start: LocalDate, boundary: LocalDate): Int {
        var cursor = start
        var pages = 0
        while (true) {
            val oldest = tradingDaysOnOrBefore(cursor).last()
            pages++
            if (!oldest.isAfter(boundary)) return pages
            cursor = oldest.minusDays(1)
        }
    }

    // ── 테스트 ──────────────────────────────────────────────────────

    @Test
    fun `콜드 캐시 전방 채움은 페이지 이음매에서 중복·공백 없이 이어붙인다`() = runTest {
        stubDynamicPages()
        val upserted = mutableListOf<InvestorFlowEntity>()
        coEvery { dao.upsertAll(any()) } answers {
            upserted += arg<List<InvestorFlowEntity>>(0)
        }
        val from = today.minusMonths(3)

        val result = repository().getFlows("005930", from, validConfig)

        assertTrue(result.isSuccess)
        val allDates = upserted.map { it.date }
        assertEquals("중복 upsert 없음", allDates.size, allDates.toSet().size)

        val sorted = allDates.toSet().map { LocalDate.parse(it, DateFormats.yyyyMMdd) }.sortedDescending()
        for (i in 0 until sorted.size - 1) {
            assertTrue(
                "이음매 공백: ${sorted[i]} -> ${sorted[i + 1]}",
                isAdjacentTradingDay(sorted[i], sorted[i + 1])
            )
        }
    }

    @Test
    fun `OPSQ2001은 date1 - 1일로 정확히 1회만 재시도한다`() = runTest {
        val requestedParams = mutableListOf<Map<String, String>>()
        var callCount = 0
        coEvery {
            kisApiClient.get(any(), any(), any(), any(), any<(String) -> String>())
        } answers {
            val params = arg<Map<String, String>>(2)
            requestedParams += params
            callCount++
            if (callCount == 1) {
                Result.success(pageBody(emptyList(), rtCd = "2", msgCd = "OPSQ2001", msg1 = "TIME LIMIT 00:00 ~ 15:40"))
            } else {
                val date1 = LocalDate.parse(params.getValue("FID_INPUT_DATE_1"), DateFormats.yyyyMMdd)
                Result.success(pageBody(tradingDaysOnOrBefore(date1)))
            }
        }
        // 후방 채움은 이 테스트 범위 밖이므로 기존 earliest를 from 이하로 고정해 격리한다.
        val from = today.minusDays(10)
        coEvery { dao.earliestDate(any()) } returns from.minusDays(10).format(DateFormats.yyyyMMdd)

        val result = repository().getFlows("005930", from, validConfig)

        assertTrue(result.isSuccess)
        coVerify(exactly = 2) { kisApiClient.get(any(), any(), any(), any(), any<(String) -> String>()) }
        assertEquals(today.format(DateFormats.yyyyMMdd), requestedParams[0].getValue("FID_INPUT_DATE_1"))
        assertEquals(today.minusDays(1).format(DateFormats.yyyyMMdd), requestedParams[1].getValue("FID_INPUT_DATE_1"))
    }

    @Test
    fun `MAX_PAGES 도달 시 중단하고 경고 로그를 남긴다`() = runTest {
        val logs = mutableListOf<String>()
        val tree = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                logs += message
            }
        }
        Timber.plant(tree)
        try {
            stubDynamicPages()
            // 최대한 먼 from — MAX_PAGES 전에는 정상 종료 조건에 닿지 않는다.
            val from = LocalDate.of(1990, 1, 1)

            val result = repository().getFlows("005930", from, validConfig)

            assertTrue(result.isSuccess)
            coVerify(exactly = InvestorFlowRepository.MAX_PAGES) {
                kisApiClient.get(any(), any(), any(), any(), any<(String) -> String>())
            }
            assertTrue("MAX_PAGES 경고 로그 없음: $logs", logs.any { it.contains("MAX_PAGES") })
        } finally {
            Timber.uproot(tree)
        }
    }

    @Test
    fun `쿨다운 이내이고 캐시가 있으면 네트워크를 타지 않는다`() = runTest {
        val from = today.minusMonths(6)
        coEvery { dao.latestDate(any()) } returns today.format(DateFormats.yyyyMMdd)
        coEvery { dao.earliestDate(any()) } returns from.format(DateFormats.yyyyMMdd) // <= from → 후방도 스킵
        coEvery { dao.latestFetchedAt(any()) } returns fixedNow.toInstant().toEpochMilli() - 60_000L // 1분 전

        val result = repository().getFlows("005930", from, validConfig)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { kisApiClient.get(any(), any(), any(), any(), any<(String) -> String>()) }
    }

    @Test
    fun `후방 채움은 oldest가 from 이하가 되면 멈춘다`() = runTest {
        val requestedParams = mutableListOf<Map<String, String>>()
        val upserted = mutableListOf<InvestorFlowEntity>()
        coEvery {
            kisApiClient.get(any(), any(), any(), any(), any<(String) -> String>())
        } answers {
            val params = arg<Map<String, String>>(2)
            requestedParams += params
            val date1 = LocalDate.parse(params.getValue("FID_INPUT_DATE_1"), DateFormats.yyyyMMdd)
            Result.success(pageBody(tradingDaysOnOrBefore(date1)))
        }
        coEvery { dao.upsertAll(any()) } answers { upserted += arg<List<InvestorFlowEntity>>(0) }

        val existingEarliest = today.minusMonths(2)
        val from = today.minusMonths(8)
        coEvery { dao.latestDate(any()) } returns today.format(DateFormats.yyyyMMdd)
        coEvery { dao.earliestDate(any()) } returns existingEarliest.format(DateFormats.yyyyMMdd)
        coEvery { dao.latestFetchedAt(any()) } returns fixedNow.toInstant().toEpochMilli() // 쿨다운 이내 → 전방 스킵

        val result = repository().getFlows("005930", from, validConfig)

        assertTrue(result.isSuccess)
        // 전방이 스킵됐으므로 유일한 시작 커서는 후방의 (existingEarliest - 1)이어야 한다.
        assertEquals(
            existingEarliest.minusDays(1).format(DateFormats.yyyyMMdd),
            requestedParams.first().getValue("FID_INPUT_DATE_1")
        )
        assertTrue(upserted.isNotEmpty())
        val allDates = upserted.map { LocalDate.parse(it.date, DateFormats.yyyyMMdd) }
        assertTrue("후방 페이지는 기존 earliest보다 과거만 수집", allDates.all { it < existingEarliest })
        assertTrue("from까지는 도달", allDates.minOrNull()!!.let { !it.isAfter(from) })
    }

    @Test
    fun `콜드 캐시 6개월 요청은 정확히 필요한 페이지 수만 호출하고 upsertAll은 1회다`() = runTest {
        stubDynamicPages()
        val from = today.minusMonths(6)

        val result = repository().getFlows("005930", from, validConfig)

        assertTrue(result.isSuccess)
        // 캔 페이지(30 거래일/콜)가 스스로 함의하는 페이지 수를 독립적으로 재계산해 정확히 검증한다.
        coVerify(exactly = expectedForwardOnlyPageCount(today, from)) {
            kisApiClient.get(any(), any(), any(), any(), any<(String) -> String>())
        }
        coVerify(exactly = 1) { dao.upsertAll(any()) } // 후방 호출 없음(전방이 이미 from에 닿았으므로 스킵)
    }

    @Test
    fun `오래된 캐시 블록이 있으면 전방 채움이 from을 지나 기존 블록까지 이어 붙인다`() = runTest {
        val requestedParams = mutableListOf<Map<String, String>>()
        val upserted = mutableListOf<InvestorFlowEntity>()
        coEvery {
            kisApiClient.get(any(), any(), any(), any(), any<(String) -> String>())
        } answers {
            val params = arg<Map<String, String>>(2)
            requestedParams += params
            val date1 = LocalDate.parse(params.getValue("FID_INPUT_DATE_1"), DateFormats.yyyyMMdd)
            Result.success(pageBody(tradingDaysOnOrBefore(date1)))
        }
        coEvery { dao.upsertAll(any()) } answers { upserted += arg<List<InvestorFlowEntity>>(0) }

        // 사용자가 1달 전 요청(from)보다 훨씬 오래된 캐시 블록만 갖고 있다(latest < from).
        val from = today.minusMonths(1)
        val latest = from.minusDays(100)
        val earliest = latest.minusDays(30)
        coEvery { dao.latestDate(any()) } returns latest.format(DateFormats.yyyyMMdd)
        coEvery { dao.earliestDate(any()) } returns earliest.format(DateFormats.yyyyMMdd)
        coEvery { dao.latestFetchedAt(any()) } returns
            fixedNow.toInstant().toEpochMilli() - InvestorFlowRepository.COOLDOWN_MS - 1L // 쿨다운 지남 → 전방 트리거

        val result = repository().getFlows("005930", from, validConfig)

        assertTrue(result.isSuccess)
        assertTrue(
            "마지막 요청 date1은 from 이하여야 한다(구멍 없이 기존 블록까지 이어 붙임)",
            LocalDate.parse(requestedParams.last().getValue("FID_INPUT_DATE_1"), DateFormats.yyyyMMdd) <= from
        )

        val allDates = upserted.map { LocalDate.parse(it.date, DateFormats.yyyyMMdd) }.distinct().sortedDescending()
        assertTrue("기존 캐시 블록(latest)까지 닿아야 한다", allDates.last() <= latest)
        for (i in 0 until allDates.size - 1) {
            assertTrue(
                "이음매 공백: ${allDates[i]} -> ${allDates[i + 1]}",
                isAdjacentTradingDay(allDates[i], allDates[i + 1])
            )
        }
        coVerify(exactly = 1) { dao.upsertAll(any()) } // 후방 호출 없음(earliest < from이라 애초에 불필요)
    }

    @Test
    fun `루프 중간 실패 시 upsertAll을 호출하지 않는다`() = runTest {
        var callCount = 0
        coEvery {
            kisApiClient.get(any(), any(), any(), any(), any<(String) -> String>())
        } answers {
            callCount++
            val params = arg<Map<String, String>>(2)
            if (callCount == 2) {
                Result.failure(RuntimeException("network boom"))
            } else {
                val date1 = LocalDate.parse(params.getValue("FID_INPUT_DATE_1"), DateFormats.yyyyMMdd)
                Result.success(pageBody(tradingDaysOnOrBefore(date1)))
            }
        }

        val result = repository().getFlows("005930", today.minusYears(1), validConfig)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { dao.upsertAll(any()) }
    }

    @Test
    fun `MOCK 모드는 네트워크 호출 없이 실패한다`() = runTest {
        val result = repository().getFlows("005930", today.minusMonths(1), mockConfig)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        coVerify(exactly = 0) { kisApiClient.get(any(), any(), any(), any(), any<(String) -> String>()) }
    }

    @Test
    fun `키 미설정이면 NoApiKeyError로 네트워크 없이 실패한다`() = runTest {
        val result = repository().getFlows("005930", today.minusMonths(1), invalidConfig)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ApiError.NoApiKeyError)
        coVerify(exactly = 0) { kisApiClient.get(any(), any(), any(), any(), any<(String) -> String>()) }
    }

    @Test
    fun `allowNetwork false면 자격증명 검사도 생략하고 캐시만 읽는다`() = runTest {
        val cached = listOf(
            InvestorFlowEntity(
                ticker = "005930", date = "20260901", high = 255_500L, low = 249_500L, close = 250_500L, volume = 1L,
                prsnBuyVol = 1, prsnBuyAmt = 1, prsnSellVol = 1, prsnSellAmt = 1,
                frgnBuyVol = 1, frgnBuyAmt = 1, frgnSellVol = 1, frgnSellAmt = 1,
                orgnBuyVol = 1, orgnBuyAmt = 1, orgnSellVol = 1, orgnSellAmt = 1,
                fetchedAt = 0L
            )
        )
        coEvery { dao.getRange(any(), any(), any()) } returns cached

        val result = repository().getFlows(
            "005930", today.minusMonths(1), invalidConfig, allowNetwork = false
        )

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        coVerify(exactly = 0) { kisApiClient.get(any(), any(), any(), any(), any<(String) -> String>()) }
        coVerify(exactly = 0) { dao.latestDate(any()) }
        coVerify(exactly = 0) { dao.upsertAll(any()) }
    }
}
