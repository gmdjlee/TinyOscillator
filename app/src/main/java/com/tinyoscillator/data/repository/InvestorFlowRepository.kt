package com.tinyoscillator.data.repository

import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.core.api.InvestmentMode
import com.tinyoscillator.core.api.KisApiClient
import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.core.database.dao.InvestorFlowDao
import com.tinyoscillator.core.database.entity.InvestorFlowEntity
import com.tinyoscillator.core.util.DateFormats
import com.tinyoscillator.data.dto.KisInvestorFlowResponse
import com.tinyoscillator.data.dto.toDailyInvestorFlowOrNull
import com.tinyoscillator.domain.model.DailyInvestorFlow
import com.tinyoscillator.domain.model.InvestorLeg
import com.tinyoscillator.domain.model.InvestorType
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.coroutines.cancellation.CancellationException

/**
 * KIS FHPTJ04160001(투자자매매동향, 종목별 일자별) 페이징 수집 + Room 증분 캐시.
 * 명세: docs/TASK_investor_volume_profile.md §6.2.
 *
 * 날짜 스텝 페이징(`tr_cont` 무효, 30행/호출)이므로 다음 페이지 커서는 항상
 * "이번 페이지에 반환된 최고(最古) 일자 − 1일(달력일)"이다. 전방 채움(오늘 방향)과
 * 후방 채움(과거 방향) 두 루프는 [MAX_PAGES]를 이 호출(getFlows 1회) 전체에서
 * 누적 공유한다(§6.2-4 "누적 MAX_PAGES 도달"). 각 루프는 페이지를 메모리에 모았다가
 * 루프 완료 후 한 번에 upsert하므로, 루프 중간에 실패하면 아무것도 저장되지 않는다.
 */
class InvestorFlowRepository(
    private val dao: InvestorFlowDao,
    private val kisApiClient: KisApiClient,
    private val json: Json,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.of("Asia/Seoul")) }
) {
    /**
     * `[from, today]` 구간을 캐시 우선으로 반환한다. 부족분만 KIS에서 수집해 Room에 증분 저장한다.
     *
     * [allowNetwork]가 false면 자격증명 검사도 생략하고 캐시만 읽는다(오프라인 경로).
     * [onProgress]는 페이지를 수집할 때마다 `(완료 페이지 수, MAX_PAGES)`를 알린다.
     */
    suspend fun getFlows(
        ticker: String,
        from: LocalDate,
        config: KisApiKeyConfig,
        allowNetwork: Boolean = true,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Result<List<DailyInvestorFlow>> {
        val today = now().toLocalDate()
        return try {
            if (allowNetwork) {
                if (!config.isValid()) return Result.failure(ApiError.NoApiKeyError())
                if (config.investmentMode == InvestmentMode.MOCK) {
                    return Result.failure(
                        IllegalStateException(
                            "모의투자 모드에서는 투자자매매동향을 조회할 수 없습니다. 설정에서 실전투자로 전환해 주세요."
                        )
                    )
                }
                syncCache(ticker, from, today, config, onProgress)?.let { return Result.failure(it) }
            }

            val rows = dao.getRange(ticker, from.format(DateFormats.yyyyMMdd), today.format(DateFormats.yyyyMMdd))
            Result.success(rows.map { it.toDomain() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "투자자매매동향 조회 실패: %s", ticker)
            Result.failure(e)
        }
    }

    /**
     * 전방·후방 채움을 수행하고 오래된 행을 정리한다. 실패하면 원인 예외를 반환하고, 성공하면 null.
     *
     * 캐시는 항상 연속 구간이다: 전방 채움은 기존 캐시 블록이 있으면(`latest != null`) `from`을
     * 지나쳐도 멈추지 않고 그 블록까지 이어 붙인다(그렇지 않으면 `latest < from`인 오래된 캐시가
     * 새 데이터와 사이에 구멍이 남는다). 전방 채움이 이미 `from`까지 닿았으면 후방 채움은 생략한다.
     */
    private suspend fun syncCache(
        ticker: String,
        from: LocalDate,
        today: LocalDate,
        config: KisApiKeyConfig,
        onProgress: (Int, Int) -> Unit
    ): Throwable? {
        val latest = dao.latestDate(ticker)?.let { parseYyyyMMdd(it) }
        val earliest = dao.earliestDate(ticker)?.let { parseYyyyMMdd(it) }
        val fetchedAtCached = dao.latestFetchedAt(ticker)
        val fetchedAtNow = now().toInstant().toEpochMilli()

        var pagesUsed = 0
        var forwardOldest: LocalDate? = null

        val needsForward = latest == null || fetchedAtCached == null ||
            fetchedAtNow - fetchedAtCached > COOLDOWN_MS
        if (needsForward) {
            val outcome = collectPages(
                ticker = ticker,
                config = config,
                startDate1 = startCursor(),
                stopBoundary = from,
                stopAtOrBefore = latest,
                pagesAlreadyUsed = pagesUsed,
                fetchedAt = fetchedAtNow,
                onProgress = onProgress
            ).getOrElse { e -> return e }
            if (outcome.rows.isNotEmpty()) dao.upsertAll(outcome.rows)
            pagesUsed = outcome.pagesUsed
            forwardOldest = outcome.oldestFetched
        }

        // 전방 채움이 이미 from까지 닿았으면(콜드 캐시 포함) 후방 채움은 낭비 호출이므로 생략한다.
        val needsBackward = (earliest == null || earliest > from) &&
            (forwardOldest == null || forwardOldest.isAfter(from))
        if (needsBackward) {
            val backwardStart = (earliest ?: forwardOldest)?.minusDays(1)
            if (backwardStart != null) {
                val outcome = collectPages(
                    ticker = ticker,
                    config = config,
                    startDate1 = backwardStart,
                    stopBoundary = from,
                    stopAtOrBefore = null,
                    pagesAlreadyUsed = pagesUsed,
                    fetchedAt = fetchedAtNow,
                    onProgress = onProgress
                ).getOrElse { e -> return e }
                if (outcome.rows.isNotEmpty()) dao.upsertAll(outcome.rows)
            }
        }

        dao.deleteOlderThan(ticker, today.minusDays(RETENTION_DAYS).format(DateFormats.yyyyMMdd))
        return null
    }

    /** 한 방향(전방 또는 후방) 페이지 루프의 결과. */
    private data class PageLoopOutcome(
        val rows: List<InvestorFlowEntity>,
        val oldestFetched: LocalDate?,
        val pagesUsed: Int
    )

    /**
     * [startDate1]부터 과거 방향으로 페이지를 모은다. 빈 페이지·페이지 미진행·누적 [MAX_PAGES]에
     * 이르면 항상 멈춘다. 페이지는 모았다가 호출부가 한 번에 upsert한다(중간 실패 시 미저장 보장).
     *
     * 캐시 연속성 불변식: [stopAtOrBefore](기존 캐시 블록의 최신일)가 있으면 그 블록까지 반드시
     * 이어 붙여야 하므로 [stopBoundary]([from])에 닿아도 멈추지 않는다 — 그렇지 않으면 `latest < from`인
     * 오래된 캐시와 새로 수집한 구간 사이에 구멍이 남는다. [stopAtOrBefore]가 없을 때만(후방 채움,
     * 또는 캐시가 아예 없는 전방 채움) [stopBoundary] 도달로 멈춘다.
     */
    private suspend fun collectPages(
        ticker: String,
        config: KisApiKeyConfig,
        startDate1: LocalDate,
        stopBoundary: LocalDate,
        stopAtOrBefore: LocalDate?,
        pagesAlreadyUsed: Int,
        fetchedAt: Long,
        onProgress: (Int, Int) -> Unit
    ): Result<PageLoopOutcome> {
        val collected = mutableListOf<InvestorFlowEntity>()
        var cursor = startDate1
        var pagesUsed = pagesAlreadyUsed
        var oldestSeen: LocalDate? = null

        while (pagesUsed < MAX_PAGES) {
            val page = fetchPage(ticker, config, cursor, fetchedAt).getOrElse { return Result.failure(it) }
            pagesUsed++
            onProgress(pagesUsed, MAX_PAGES)

            val oldest = page.oldestDate ?: break // 빈 페이지
            collected += page.rows
            oldestSeen = oldest

            val reachedCached = stopAtOrBefore != null && !oldest.isAfter(stopAtOrBefore)
            val reachedFrom = stopAtOrBefore == null && !oldest.isAfter(stopBoundary)
            if (reachedCached || reachedFrom) break

            if (!oldest.isBefore(cursor)) {
                Timber.w("InvestorFlowRepository: 페이지 커서가 진행되지 않아 중단(ticker=%s, cursor=%s)", ticker, cursor)
                break
            }
            cursor = oldest.minusDays(1)
        }
        if (pagesUsed >= MAX_PAGES) {
            Timber.w("InvestorFlowRepository: MAX_PAGES(%d) 도달(ticker=%s)", MAX_PAGES, ticker)
        }
        return Result.success(PageLoopOutcome(collected, oldestSeen, pagesUsed))
    }

    private data class SinglePage(val rows: List<InvestorFlowEntity>, val oldestDate: LocalDate?)

    /**
     * 페이지 1회 조회. `msg_cd == OPSQ2001`(당일 15:40 이전 호출 거부)이면 [date1] − 1일로
     * 딱 1회 재시도한다(§6.2 `startCursor()` 문단). rate limit·재시도는 [KisApiClient]가 처리하므로
     * 여기서 `delay`를 넣지 않는다.
     */
    private suspend fun fetchPage(
        ticker: String,
        config: KisApiKeyConfig,
        date1: LocalDate,
        fetchedAt: Long,
        allowOpsqRetry: Boolean = true
    ): Result<SinglePage> {
        val queryParams = mapOf(
            "FID_COND_MRKT_DIV_CODE" to "J",
            "FID_INPUT_ISCD" to ticker,
            "FID_INPUT_DATE_1" to date1.format(DateFormats.yyyyMMdd),
            "FID_ORG_ADJ_PRC" to "",
            "FID_ETC_CLS_CODE" to ""
        )
        val body = kisApiClient.get(TR_ID, ENDPOINT, queryParams, config) { it }
            .getOrElse { return Result.failure(it) }
        val response = json.decodeFromString<KisInvestorFlowResponse>(body)

        if (response.msgCd == "OPSQ2001" && allowOpsqRetry) {
            Timber.d("InvestorFlowRepository: OPSQ2001 — date1-1로 재시도(ticker=%s, date1=%s)", ticker, date1)
            return fetchPage(ticker, config, date1.minusDays(1), fetchedAt, allowOpsqRetry = false)
        }
        if (response.rtCd != "0") {
            return Result.failure(RuntimeException("KIS API 오류 [${response.msgCd}]: ${response.msg1}"))
        }

        val output2 = response.output2.orEmpty()
        val dates = output2.mapNotNull { row ->
            row["stck_bsop_date"]?.let { runCatching { LocalDate.parse(it, DateFormats.yyyyMMdd) }.getOrNull() }
        }
        val oldest = dates.minOrNull() ?: return Result.success(SinglePage(emptyList(), null))
        val rows = output2.mapNotNull { it.toDailyInvestorFlowOrNull() }.map { it.toEntity(ticker, fetchedAt) }
        return Result.success(SinglePage(rows, oldest))
    }

    /** `now() < 15:40 KST`면 어제, 아니면 오늘(§6.2). */
    private fun startCursor(): LocalDate {
        val current = now()
        return if (current.toLocalTime().isBefore(CUTOFF_TIME)) {
            current.toLocalDate().minusDays(1)
        } else {
            current.toLocalDate()
        }
    }

    private fun parseYyyyMMdd(value: String): LocalDate = LocalDate.parse(value, DateFormats.yyyyMMdd)

    private fun DailyInvestorFlow.toEntity(ticker: String, fetchedAt: Long): InvestorFlowEntity {
        val prsn = flows.getValue(InvestorType.INDIVIDUAL)
        val frgn = flows.getValue(InvestorType.FOREIGN)
        val orgn = flows.getValue(InvestorType.INSTITUTION)
        return InvestorFlowEntity(
            ticker = ticker,
            date = date.format(DateFormats.yyyyMMdd),
            high = high,
            low = low,
            close = close,
            volume = volume,
            prsnBuyVol = prsn.buyVolume,
            prsnBuyAmt = prsn.buyAmount,
            prsnSellVol = prsn.sellVolume,
            prsnSellAmt = prsn.sellAmount,
            frgnBuyVol = frgn.buyVolume,
            frgnBuyAmt = frgn.buyAmount,
            frgnSellVol = frgn.sellVolume,
            frgnSellAmt = frgn.sellAmount,
            orgnBuyVol = orgn.buyVolume,
            orgnBuyAmt = orgn.buyAmount,
            orgnSellVol = orgn.sellVolume,
            orgnSellAmt = orgn.sellAmount,
            fetchedAt = fetchedAt
        )
    }

    private fun InvestorFlowEntity.toDomain(): DailyInvestorFlow = DailyInvestorFlow(
        date = parseYyyyMMdd(date),
        high = high,
        low = low,
        close = close,
        volume = volume,
        flows = mapOf(
            InvestorType.INDIVIDUAL to InvestorLeg(prsnBuyVol, prsnBuyAmt, prsnSellVol, prsnSellAmt),
            InvestorType.FOREIGN to InvestorLeg(frgnBuyVol, frgnBuyAmt, frgnSellVol, frgnSellAmt),
            InvestorType.INSTITUTION to InvestorLeg(orgnBuyVol, orgnBuyAmt, orgnSellVol, orgnSellAmt)
        )
    )

    companion object {
        /** 3년 ≈ 750거래일을 30행/페이지로 덮는 상한(§0.2-13). getFlows 1회 호출 전체에서 공유. */
        const val MAX_PAGES = 26
        const val COOLDOWN_MS = 60 * 60 * 1000L
        const val RETENTION_DAYS = 365L * 3 + 30

        private const val TR_ID = "FHPTJ04160001"
        private const val ENDPOINT = "/uapi/domestic-stock/v1/quotations/investor-trade-by-stock-daily"
        private val CUTOFF_TIME: LocalTime = LocalTime.of(15, 40)
    }
}
