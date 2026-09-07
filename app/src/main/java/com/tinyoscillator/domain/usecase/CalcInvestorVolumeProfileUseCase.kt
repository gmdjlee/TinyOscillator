package com.tinyoscillator.domain.usecase

import com.tinyoscillator.domain.model.DailyInvestorFlow
import com.tinyoscillator.domain.model.InvestorPriceBin
import com.tinyoscillator.domain.model.InvestorProfileMetrics
import com.tinyoscillator.domain.model.InvestorProfileQuery
import com.tinyoscillator.domain.model.InvestorType
import com.tinyoscillator.domain.model.InvestorVolumeProfile
import com.tinyoscillator.domain.model.ProfileBasis
import com.tinyoscillator.domain.model.ProfilePeriod
import java.time.LocalDate
import kotlin.math.exp

/**
 * 기간 창 해소 결과(§5 서두, §5.6). 스윙 기간([ProfilePeriod.SINCE_SWING_HIGH]/[ProfilePeriod.SINCE_SWING_LOW])일
 * 때만 [anchorDate]/[anchorPrice]가 채워진다. 전환점을 찾지 못하면 [swingFallback]이 true이고 앵커는 null이다.
 */
internal data class ResolvedWindow(
    val rows: List<DailyInvestorFlow>,
    val swingFallback: Boolean,
    val anchorDate: LocalDate?,
    val anchorPrice: Long?
)

/**
 * 일자 순회 배분·상각(§5.1, §5.2, §5.4) 결과.
 * [totalProfile]은 전체 거래량 배분(대표가 기준, POC·밸류 에어리어 산출용 내부 변수)이다.
 * [takenSell]은 그날 실제로 보유분에서 차감된 매도량(s)의 창 전체 합, [excessSell]은 그 나머지
 * (관측 기간 이전 보유분의 매도로 추정)의 합이다. RESIDUAL이 아니면 둘 다 0으로 유지된다.
 */
internal data class AllocationResult(
    val byInvestor: Map<InvestorType, DoubleArray>,
    val totalProfile: DoubleArray,
    val takenSell: Map<InvestorType, Double>,
    val excessSell: Map<InvestorType, Double>
)

/**
 * 주체별 매물대(Investor Volume Profile) 산출 UseCase.
 * 명세: `docs/TASK_investor_volume_profile.md` §5. 순수 Kotlin(Android 의존성 없음).
 *
 * 처리 순서: ① [resolveWindow](기간 창 해소) → ② [truncateAtDiscontinuity](가격 불연속 절단) →
 * ③ [binEdges](구간 분할) → ④ [allocateWindow](일자 순회 배분·상각) → ⑤ 지표(§5.8, [buildMetrics]).
 *
 * ### 산출 기준 자동 규칙의 근거(§5.4 — 코드 근거로 반드시 유지)
 * 회전율이 높은 외국인·기관은 짧은 구간에 비례상각([ProfileBasis.RESIDUAL])을 적용하면 잔존
 * 물량이 0으로 소거된다. 삼성전자 2026-08-18 이후 14거래일 기준으로 외국인 잔존이 0주로
 * 산출되는 사례가 실측으로 확인되었다(§9.2 골든 5행). 짧은 구간에 상각을 강제하면 빈 차트가
 * 버그로 오인된다. 따라서 창 내 거래일이 [AUTO_RESIDUAL_MIN_DAYS] 미만이면 매수 누적
 * ([ProfileBasis.CUMULATIVE_BUY]), 그 이상이면 잔존 추정([ProfileBasis.RESIDUAL])을 자동
 * 적용한다(`query.basis == null`일 때, [InvestorVolumeProfile.autoBasis] = true로 표기).
 *
 * ### 일봉 단위 배분 원칙의 근거(§5.5 — 코드 근거로 반드시 유지)
 * 배분과 상각은 반드시 일봉 단위로 수행한다. 일별 매수·매도를 주 단위로 먼저 합산한 뒤 배분하면
 * 주중 회전이 상계되어 외국인·기관 잔존이 소거되는 것이 실측으로 확인되었다. 주봉 컨트롤은
 * v1.1에서 제거했으므로(§0.2-8) 이 유스케이스에는 주 단위 선합산 경로 자체가 없다 —
 * [allocateWindow]는 언제나 인자로 받은 [DailyInvestorFlow] 리스트를 그대로(일봉 단위로) 순회한다.
 */
class CalcInvestorVolumeProfileUseCase {

    /** rows는 날짜 오름차순. 창 내 행이 0이면 null(→ Empty, §6.5). */
    operator fun invoke(
        rows: List<DailyInvestorFlow>,
        query: InvestorProfileQuery,
        today: LocalDate
    ): InvestorVolumeProfile? {
        val resolved = resolveWindow(rows, query, today)
        if (resolved.rows.isEmpty()) return null

        val (windowRows, truncatedAt) = truncateAtDiscontinuity(resolved.rows)
        val bins = binEdges(windowRows, query.binCount)
        val binWidth = bins.first().let { it.upper - it.lower }

        val tradingDays = windowRows.size
        val basis = query.basis
            ?: if (tradingDays < AUTO_RESIDUAL_MIN_DAYS) ProfileBasis.CUMULATIVE_BUY else ProfileBasis.RESIDUAL
        val autoBasis = query.basis == null

        val allocation = allocateWindow(windowRows, basis, bins, binWidth)
        val basePrice = windowRows.last().close
        val averageDailyVolume = windowRows.sumOf { it.volume }.toDouble() / windowRows.size

        val metrics = buildMetrics(bins, allocation.byInvestor, allocation.totalProfile, basePrice, averageDailyVolume)

        return InvestorVolumeProfile(
            bins = bins,
            byInvestor = allocation.byInvestor,
            basePrice = basePrice,
            basis = basis,
            autoBasis = autoBasis,
            startDate = windowRows.first().date,
            endDate = windowRows.last().date,
            tradingDays = tradingDays,
            binWidth = binWidth,
            metrics = metrics,
            truncatedAt = truncatedAt,
            swingFallback = resolved.swingFallback,
            anchorDate = resolved.anchorDate,
            anchorPrice = resolved.anchorPrice
        )
    }

    // ------------------------------------------------------------------
    // ① 기간 창 해소 (§5 서두, §5.6)
    // ------------------------------------------------------------------

    internal fun resolveWindow(rows: List<DailyInvestorFlow>, query: InvestorProfileQuery, today: LocalDate): ResolvedWindow =
        when (query.period) {
            ProfilePeriod.SINCE_SWING_HIGH -> resolveSwingWindow(rows, today, isHigh = true)
            ProfilePeriod.SINCE_SWING_LOW -> resolveSwingWindow(rows, today, isHigh = false)
            ProfilePeriod.CUSTOM -> resolveCustomWindow(rows, query, today)
            else -> {
                val days = requireNotNull(query.period.days) { "${query.period}에는 days가 정의되어 있어야 한다" }
                ResolvedWindow(
                    rows = rows.filter { it.date >= today.minusDays(days) },
                    swingFallback = false,
                    anchorDate = null,
                    anchorPrice = null
                )
            }
        }

    /** 탐색 범위는 최근 1년 행(전체 rows가 그보다 짧으면 전체)이며, 창은 마지막 전환점부터 시작한다(§5.6). */
    private fun resolveSwingWindow(rows: List<DailyInvestorFlow>, today: LocalDate, isHigh: Boolean): ResolvedWindow {
        val searchRange = rows.filter { it.date >= today.minusDays(365) }
        val swingIndices = if (isHigh) findSwingHighs(searchRange) else findSwingLows(searchRange)
        val lastIndex = swingIndices.lastOrNull()
            ?: return ResolvedWindow(searchRange, swingFallback = true, anchorDate = null, anchorPrice = null)
        val anchor = searchRange[lastIndex]
        return ResolvedWindow(
            rows = searchRange.subList(lastIndex, searchRange.size),
            swingFallback = false,
            anchorDate = anchor.date,
            anchorPrice = if (isHigh) anchor.high else anchor.low
        )
    }

    /** `customStart > customEnd`이거나 null이면 ViewModel이 사전 차단하므로 여기서는 require로만 방어한다. */
    private fun resolveCustomWindow(rows: List<DailyInvestorFlow>, query: InvestorProfileQuery, today: LocalDate): ResolvedWindow {
        val start = requireNotNull(query.customStart) { "CUSTOM 기간은 customStart가 필요하다" }
        val end = requireNotNull(query.customEnd) { "CUSTOM 기간은 customEnd가 필요하다" }
        require(start <= end) { "customStart($start)는 customEnd($end) 이전이거나 같아야 한다" }
        val clampedStart = maxOf(start, today.minusDays(MAX_LOOKBACK_DAYS))
        val clampedEnd = minOf(end, today)
        val windowRows = rows.filter { it.date >= clampedStart && it.date <= clampedEnd }
        return ResolvedWindow(windowRows, swingFallback = false, anchorDate = null, anchorPrice = null)
    }

    /** 프랙탈 스윙 고점(§5.6). i∈[k, n-k-1]에서 high[i]가 [i-k, i+k] 구간의 최댓값(동률 포함). */
    internal fun findSwingHighs(rows: List<DailyInvestorFlow>, k: Int = SWING_K): List<Int> =
        (k..rows.size - k - 1).filter { i -> rows[i].high == (i - k..i + k).maxOf { j -> rows[j].high } }

    /** 프랙탈 스윙 저점. [findSwingHighs]와 대칭(최솟값). */
    internal fun findSwingLows(rows: List<DailyInvestorFlow>, k: Int = SWING_K): List<Int> =
        (k..rows.size - k - 1).filter { i -> rows[i].low == (i - k..i + k).minOf { j -> rows[j].low } }

    // ------------------------------------------------------------------
    // ② 가격 불연속(액면분할·병합·권리락) 절단 (§5.7)
    // ------------------------------------------------------------------

    // ponytail: 절단만 지원. 분할 이전 구간까지 합산하려면 FHKST03010100 adj 0/1 종가 비율로
    // 가격·수량을 소급 조정하는 §0.4 백로그 1이 필요하다.
    /** 연속 거래일 종가 비율이 [DISCONTINUITY_LOW, DISCONTINUITY_HIGH] 밖이면 그 날부터만 남긴다(마지막 불연속 기준). */
    internal fun truncateAtDiscontinuity(rows: List<DailyInvestorFlow>): Pair<List<DailyInvestorFlow>, LocalDate?> {
        var truncateIndex = 0
        var truncatedAt: LocalDate? = null
        for (i in 1 until rows.size) {
            val ratio = rows[i].close.toDouble() / rows[i - 1].close.toDouble()
            if (ratio < DISCONTINUITY_LOW || ratio > DISCONTINUITY_HIGH) {
                truncateIndex = i
                truncatedAt = rows[i].date
            }
        }
        return rows.subList(truncateIndex, rows.size) to truncatedAt
    }

    // ------------------------------------------------------------------
    // ③ 가격 구간 분할 (§5.3)
    // ------------------------------------------------------------------

    /** `minLow == maxHigh`이면 구간 폭 1.0. 반올림 없이 실수 구간으로 나눈다. */
    internal fun binEdges(rows: List<DailyInvestorFlow>, binCount: Int): List<InvestorPriceBin> {
        val minLow = rows.minOf { it.low }.toDouble()
        val maxHigh = rows.maxOf { it.high }.toDouble()
        val binWidth = if (maxHigh == minLow) 1.0 else (maxHigh - minLow) / binCount
        return (0 until binCount).map { i ->
            InvestorPriceBin(lower = minLow + i * binWidth, upper = minLow + (i + 1) * binWidth)
        }
    }

    // ------------------------------------------------------------------
    // 배분 커널 (§5.2)
    // ------------------------------------------------------------------

    /**
     * 하루치 물량을 정규분포 커널로 구간에 배분한다.
     * 불변식 1(질량 보존): `Σ결과 == volume`(상대오차 1e-9).
     * 불변식 2(1차 모멘트): `|가중평균 - center| ≤ σ + binWidth/2`.
     * [center]는 항상 `[low, high] ⊆ [창의 minLow, maxHigh]` 안이므로(§5.1 vwap 검증, 대표가 정의) 이
     * 구간을 덮는 [bins] 중 가중치가 완전히 0으로 잘리지 않는 구간이 항상 존재해 합이 0이 되지
     * 않는다 — 참조 구현의 원핫 폴백을 두지 않는다(§5.2).
     */
    internal fun allocateDay(
        volume: Double,
        center: Double,
        low: Double,
        high: Double,
        bins: List<InvestorPriceBin>,
        binWidth: Double
    ): DoubleArray {
        val sigma = maxOf((high - low) / 4.0, binWidth / 4.0)
        val weights = DoubleArray(bins.size) { i ->
            val c = bins[i].center
            if (c < low - binWidth || c > high + binWidth) {
                0.0
            } else {
                val z = (c - center) / sigma
                exp(-0.5 * z * z)
            }
        }
        val sum = weights.sum()
        for (i in weights.indices) weights[i] = weights[i] / sum * volume
        return weights
    }

    // ------------------------------------------------------------------
    // ④ 일자 순회 배분·상각 (§5.1, §5.4)
    // ------------------------------------------------------------------

    internal fun allocateWindow(
        rows: List<DailyInvestorFlow>,
        basis: ProfileBasis,
        bins: List<InvestorPriceBin>,
        binWidth: Double
    ): AllocationResult {
        val byInvestor = InvestorType.entries.associateWith { DoubleArray(bins.size) }
        val takenSell = InvestorType.entries.associateWith { 0.0 }.toMutableMap()
        val excessSell = InvestorType.entries.associateWith { 0.0 }.toMutableMap()
        val totalProfile = DoubleArray(bins.size)

        for (row in rows) {
            val low = row.low.toDouble()
            val high = row.high.toDouble()

            // 전체 거래량 배분(내부 변수, POC·밸류 에어리어 산출용). 대표가 = (고가+저가+종가)/3.
            val representativePrice = (row.high + row.low + row.close) / 3.0
            val totalAlloc = allocateDay(row.volume.toDouble(), representativePrice, low, high, bins, binWidth)
            for (i in totalProfile.indices) totalProfile[i] += totalAlloc[i]

            for (type in InvestorType.entries) {
                val leg = row.flows[type] ?: continue
                val h = byInvestor.getValue(type)

                if (leg.buyVolume > 0) {
                    val vwap = leg.buyVwap
                    if (vwap in low..high) {
                        val alloc = allocateDay(leg.buyVolume.toDouble(), vwap, low, high, bins, binWidth)
                        for (i in h.indices) h[i] += alloc[i]
                    }
                    // vwap이 [low, high] 밖이면 그 주체·그 날의 leg만 건너뛴다(§5.1). 거래대금이
                    // 백만원 단위로 반올림되므로 소량 매수일에는 정상적으로 발생한다. 다른 주체와
                    // 전체 거래량 배분은 그대로 유지한다(로그는 순수 Kotlin 계층이라 남기지 않는다).
                }

                if (basis == ProfileBasis.RESIDUAL && leg.sellVolume > 0) {
                    val held = h.sum()
                    val taken = minOf(leg.sellVolume.toDouble(), held)
                    if (held > 0) {
                        val factor = 1.0 - taken / held
                        for (i in h.indices) h[i] *= factor
                    }
                    takenSell[type] = takenSell.getValue(type) + taken
                    excessSell[type] = excessSell.getValue(type) + (leg.sellVolume - taken)
                }
                // CUMULATIVE_BUY: 상각 없이 매수 배분만 누적한다(§5.4).
            }
        }
        return AllocationResult(byInvestor, totalProfile, takenSell, excessSell)
    }

    // ------------------------------------------------------------------
    // ⑤ 지표 (§5.8)
    // ------------------------------------------------------------------

    private fun buildMetrics(
        bins: List<InvestorPriceBin>,
        byInvestor: Map<InvestorType, DoubleArray>,
        totalProfile: DoubleArray,
        basePrice: Long,
        averageDailyVolume: Double
    ): InvestorProfileMetrics {
        val pocIndex = requireNotNull(totalProfile.indices.maxByOrNull { totalProfile[it] }) { "bins는 비어 있을 수 없다" }
        val vaRange = valueArea(totalProfile, pocIndex)

        val basePriceD = basePrice.toDouble()
        // 주체 합계 S_i = Σ_type byInvestor[type][i] (§5.8).
        val sums = DoubleArray(bins.size) { i -> InvestorType.entries.sumOf { byInvestor.getValue(it)[i] } }

        // bins는 가격 오름차순이므로 상방(> basePrice)·하방(<= basePrice) 후보는 각각 연속 구간이다.
        val upperIndices = bins.indices.filter { bins[it].center > basePriceD }
        val lowerIndices = bins.indices.filter { bins[it].center <= basePriceD }
        val upperRange = upperIndices.firstOrNull()?.let { it..upperIndices.last() }
        val lowerRange = lowerIndices.firstOrNull()?.let { it..lowerIndices.last() }

        val capZone = upperRange?.let { zone(sums, it) }
        val supportZone = lowerRange?.let { zone(sums, it) }
        val upperSupplySum = upperIndices.sumOf { sums[it] }
        val meanSum = sums.average()

        return InvestorProfileMetrics(
            poc = bins[pocIndex].center,
            valueAreaHigh = bins[vaRange.last].center,
            valueAreaLow = bins[vaRange.first].center,
            upperSupply = InvestorType.entries.associateWith { type -> upperIndices.sumOf { byInvestor.getValue(type)[it] } },
            lowerSupply = InvestorType.entries.associateWith { type -> lowerIndices.sumOf { byInvestor.getValue(type)[it] } },
            capZone = capZone,
            capZoneVolume = capZone?.sumOf { sums[it] } ?: 0.0,
            supportZone = supportZone,
            supportZoneVolume = supportZone?.sumOf { sums[it] } ?: 0.0,
            lowVolumeNodeCount = sums.count { it < LOW_VOLUME_RATIO * meanSum },
            absorptionDays = if (averageDailyVolume > 0.0) upperSupplySum / averageDailyVolume else 0.0,
            averageDailyVolume = averageDailyVolume
        )
    }

    /**
     * 전체 거래량 배분(§5.2 대표가 기준)의 70% 밸류 에어리어. [pocIndex]에서 출발해 양쪽 중 더 큰
     * 쪽을 먼저 편입하며 확장한다. 기존 `BuildVolumeProfileUseCase.kt:33-46`과 동일 규칙(동률 시 하단 우선).
     */
    internal fun valueArea(total: DoubleArray, pocIndex: Int): IntRange {
        val sum = total.sum()
        if (sum <= 0.0) return pocIndex..pocIndex
        var lo = pocIndex
        var hi = pocIndex
        var accum = total[pocIndex]
        while (accum / sum < 0.70 && (lo > 0 || hi < total.lastIndex)) {
            val addLo = if (lo > 0) total[lo - 1] else 0.0
            val addHi = if (hi < total.lastIndex) total[hi + 1] else 0.0
            when {
                addLo >= addHi && lo > 0 -> { lo--; accum += addLo }
                hi < total.lastIndex -> { hi++; accum += addHi }
                else -> { lo--; accum += addLo }
            }
        }
        return lo..hi
    }

    // ponytail: 존 확장 임계 0.5는 휴리스틱. 조정은 상수 1개(ZONE_THRESHOLD)만 바꾼다.
    /**
     * 존(zone) 확장(§5.8). [candidates](상방 또는 하방 후보 인덱스 범위) 안에서 `S`가 최대인 구간
     * `m`에서 출발해, 같은 쪽 이웃이 `S ≥ threshold × S_m`인 동안 양옆으로 확장한다. `candidates`가
     * 비어 있거나 최댓값이 0 이하이면(해당 쪽에 물량이 없으면) null.
     */
    internal fun zone(sums: DoubleArray, candidates: IntRange, threshold: Double = ZONE_THRESHOLD): IntRange? {
        if (candidates.isEmpty()) return null
        val m = candidates.maxByOrNull { sums[it] } ?: return null
        if (sums[m] <= 0.0) return null
        var lo = m
        var hi = m
        while (lo - 1 >= candidates.first && sums[lo - 1] >= threshold * sums[m]) lo--
        while (hi + 1 <= candidates.last && sums[hi + 1] >= threshold * sums[m]) hi++
        return lo..hi
    }

    companion object {
        /** 스윙 프랙탈 좌우 봉 수(§5.6). */
        const val SWING_K = 5
        /** 창 내 거래일이 이 값 미만이면 매수 누적, 이상이면 잔존 추정을 자동 적용(§5.4). */
        const val AUTO_RESIDUAL_MIN_DAYS = 60
        /** 존 확장 임계값(§5.8). */
        const val ZONE_THRESHOLD = 0.5
        /** 종가 비율 하한(§5.7). 미만이면 불연속(액면분할 등)으로 판단한다. */
        const val DISCONTINUITY_LOW = 0.7
        /** 종가 비율 상한(§5.7). 초과면 불연속으로 판단한다. */
        const val DISCONTINUITY_HIGH = 1.3
        /** 매물 공백 구간 판정 비율(§5.8): `S_i < LOW_VOLUME_RATIO × mean(S)`. */
        const val LOW_VOLUME_RATIO = 0.2
        /** 전체 기간·CUSTOM 클램프 상한(§0.3-C). */
        const val MAX_LOOKBACK_DAYS = 365L * 3
    }
}
