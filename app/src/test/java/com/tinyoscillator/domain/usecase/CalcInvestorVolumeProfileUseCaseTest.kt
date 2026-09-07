package com.tinyoscillator.domain.usecase

import com.tinyoscillator.domain.model.DailyInvestorFlow
import com.tinyoscillator.domain.model.InvestorLeg
import com.tinyoscillator.domain.model.InvestorPriceBin
import com.tinyoscillator.domain.model.InvestorProfileQuery
import com.tinyoscillator.domain.model.InvestorType
import com.tinyoscillator.domain.model.InvestorVolumeProfile
import com.tinyoscillator.domain.model.ProfileBasis
import com.tinyoscillator.domain.model.ProfilePeriod
import java.time.LocalDate
import kotlin.math.abs
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `CalcInvestorVolumeProfileUseCase` 검증. 명세: docs/TASK_investor_volume_profile.md §9.
 * 골든 기대값(§9.2)은 참조 구현 `docs/fixtures/investor_volume_profile_v2.py`의 알고리즘을
 * 그대로 옮긴 독립 Python 오라클로 60행 픽스처에 대해 재산출해 spec 표와 대조 확인했다
 * (모든 필드 일치, 2026-09-07). 소수점이 적게 표기된 필드(예: absorptionDays)는 스펙 표의
 * 반올림 표기 대신 오라클의 전체 정밀도 값을 리터럴로 사용해 §9.2가 요구하는 상대 1e-6
 * 허용오차를 문자 그대로 지킨다.
 */
class CalcInvestorVolumeProfileUseCaseTest {

    private val useCase = CalcInvestorVolumeProfileUseCase()
    private val today: LocalDate = LocalDate.of(2026, 9, 4)
    private val fixtureRows: List<DailyInvestorFlow> = InvestorProfileFixture.rows()

    // ------------------------------------------------------------------
    // 허용 오차 헬퍼 (§9.2: 합계류 상대 1e-6, 구간폭·중심 절대 0.01)
    // ------------------------------------------------------------------

    private fun assertRelative(expected: Double, actual: Double, tol: Double = 1e-6, msg: String = "") {
        val denom = maxOf(abs(expected), 1.0)
        assertTrue("$msg expected=$expected actual=$actual diff=${abs(expected - actual)}", abs(expected - actual) / denom <= tol)
    }

    private fun assertAbs(expected: Double, actual: Double, tol: Double = 0.01, msg: String = "") {
        assertTrue("$msg expected=$expected actual=$actual", abs(expected - actual) <= tol)
    }

    // ==================================================================
    // §9.2 골든 테스트 (기대값 10행, 절대 변경 금지)
    // ==================================================================

    @Test
    fun `골든1 - 프랙탈 k=5 스윙 고점 전체 60행`() {
        val indices = useCase.findSwingHighs(fixtureRows)
        val result = indices.map { fixtureRows[it].date to fixtureRows[it].high }
        assertEquals(
            listOf(
                LocalDate.of(2026, 6, 19) to 374_500L,
                LocalDate.of(2026, 7, 31) to 267_000L,
                LocalDate.of(2026, 8, 18) to 288_000L
            ),
            result
        )
    }

    @Test
    fun `골든2 - 프랙탈 k=5 스윙 저점`() {
        val indices = useCase.findSwingLows(fixtureRows)
        val result = indices.map { fixtureRows[it].date to fixtureRows[it].low }
        assertEquals(
            listOf(
                LocalDate.of(2026, 6, 23) to 310_000L,
                LocalDate.of(2026, 7, 20) to 240_000L,
                LocalDate.of(2026, 7, 29) to 189_200L,
                LocalDate.of(2026, 8, 11) to 227_500L,
                LocalDate.of(2026, 8, 25) to 245_000L
            ),
            result
        )
    }

    @Test
    fun `골든3 - 18구간 매수누적 전체기간`() {
        val query = InvestorProfileQuery(period = ProfilePeriod.ALL, basis = ProfileBasis.CUMULATIVE_BUY, binCount = 18)
        val profile = checkNotNull(useCase(fixtureRows, query, today))

        assertNull("픽스처 전체 구간은 절단이 없어야 한다", profile.truncatedAt)
        assertAbs(189_200.0, profile.bins.first().lower, msg = "minLow")
        assertAbs(374_500.0, profile.bins.last().upper, msg = "maxHigh")
        assertAbs(10_294.44, profile.binWidth, msg = "binWidth")

        assertEquals(6, profile.bins.indexOfFirst { abs(it.center - 256_113.89) < 0.01 })
        assertAbs(256_113.89, profile.metrics.poc, msg = "poc")
        assertAbs(250_966.7, profile.bins[6].lower, tol = 0.1, msg = "poc bin lower")
        assertAbs(261_261.1, profile.bins[6].upper, tol = 0.1, msg = "poc bin upper")

        assertRelative(485_363_394.0, profile.byInvestor.getValue(InvestorType.INDIVIDUAL).sum(), msg = "개인 합")
        assertRelative(580_627_279.0, profile.byInvestor.getValue(InvestorType.FOREIGN).sum(), msg = "외국인 합")
        assertRelative(589_250_289.0, profile.byInvestor.getValue(InvestorType.INSTITUTION).sum(), msg = "기관 합")

        assertRelative(357_410_789.0, profile.metrics.upperSupply.getValue(InvestorType.INDIVIDUAL), msg = "개인 상방")
    }

    @Test
    fun `골든4 - 20구간 자동기준 전체60거래일`() {
        val query = InvestorProfileQuery(period = ProfilePeriod.ALL, basis = null, binCount = 20)
        val profile = checkNotNull(useCase(fixtureRows, query, today))

        assertEquals(60, profile.tradingDays)
        assertEquals(ProfileBasis.RESIDUAL, profile.basis)
        assertTrue(profile.autoBasis)
        assertAbs(9_265.0, profile.binWidth)
        assertAbs(258_687.5, profile.metrics.poc)

        assertRelative(50_209_362.0, profile.byInvestor.getValue(InvestorType.INDIVIDUAL).sum(), msg = "개인 합")
        assertRelative(2_998_694.0, profile.byInvestor.getValue(InvestorType.FOREIGN).sum(), msg = "외국인 합")
        assertRelative(2_489_812.0, profile.byInvestor.getValue(InvestorType.INSTITUTION).sum(), msg = "기관 합")

        assertRelative(26_610_378.0, profile.metrics.upperSupply.getValue(InvestorType.INDIVIDUAL), msg = "개인 상방")
        assertRelative(23_598_984.0, profile.metrics.lowerSupply.getValue(InvestorType.INDIVIDUAL), msg = "개인 하방")
        assertRelative(2_314_476.0, profile.metrics.upperSupply.getValue(InvestorType.INSTITUTION), msg = "기관 상방")

        // 스펙 표기 "1.0951"은 반올림. 오라클 전체정밀도(1.0951373610741717)로 상대 1e-6 확인.
        assertRelative(1.0951373610741717, profile.metrics.absorptionDays, msg = "흡수일수")
        assertEquals(12, profile.metrics.lowVolumeNodeCount)

        val expectedAvg = fixtureRows.sumOf { it.volume }.toDouble() / fixtureRows.size
        assertRelative(expectedAvg, profile.metrics.averageDailyVolume, msg = "일평균거래량")

        assertEquals(7..7, profile.metrics.capZone)
        assertAbs(254_055.0, profile.bins[7].lower)
        assertAbs(263_320.0, profile.bins[7].upper)
        assertRelative(18_647_628.0, profile.metrics.capZoneVolume, msg = "캡 존 물량")

        assertEquals(6..6, profile.metrics.supportZone)
        assertAbs(244_790.0, profile.bins[6].lower)
        assertAbs(254_055.0, profile.bins[6].upper)
        assertRelative(17_160_129.0, profile.metrics.supportZoneVolume, msg = "지지 존 물량")
    }

    @Test
    fun `골든5 - 20구간 잔존추정 SINCE_SWING_HIGH 14거래일`() {
        val query = InvestorProfileQuery(period = ProfilePeriod.SINCE_SWING_HIGH, basis = ProfileBasis.RESIDUAL, binCount = 20)
        val profile = checkNotNull(useCase(fixtureRows, query, today))

        assertEquals(14, profile.tradingDays)
        assertEquals(LocalDate.of(2026, 8, 18), profile.anchorDate)
        assertEquals(288_000L, profile.anchorPrice)
        assertFalse(profile.swingFallback)
        assertAbs(2_250.0, profile.binWidth)

        assertRelative(0.0, profile.byInvestor.getValue(InvestorType.FOREIGN).sum(), msg = "외국인 소거")
        assertRelative(2_489_812.0, profile.byInvestor.getValue(InvestorType.INSTITUTION).sum(), msg = "기관")
        assertRelative(3_565_032.0, profile.byInvestor.getValue(InvestorType.INDIVIDUAL).sum(), msg = "개인")

        assertAbs(259_875.0, profile.metrics.poc, msg = "poc")
        val pocIndex = profile.bins.indexOfFirst { abs(it.center - profile.metrics.poc) < 1e-6 }
        assertEquals(7, pocIndex)
    }

    @Test
    fun `골든6 - 20구간 매수누적 SINCE_SWING_HIGH 동일창`() {
        val query = InvestorProfileQuery(period = ProfilePeriod.SINCE_SWING_HIGH, basis = ProfileBasis.CUMULATIVE_BUY, binCount = 20)
        val profile = checkNotNull(useCase(fixtureRows, query, today))

        assertRelative(71_887_984.0, profile.byInvestor.getValue(InvestorType.INDIVIDUAL).sum(), msg = "개인")
        assertRelative(86_179_825.0, profile.byInvestor.getValue(InvestorType.FOREIGN).sum(), msg = "외국인")
        assertRelative(103_455_270.0, profile.byInvestor.getValue(InvestorType.INSTITUTION).sum(), msg = "기관")

        // 스펙 표기 "8.3290"은 반올림. 오라클 전체정밀도(8.329001756583738)로 상대 1e-6 확인.
        assertRelative(8.329001756583738, profile.metrics.absorptionDays, msg = "흡수일수")

        val expectedAvg = fixtureRows.takeLast(14).sumOf { it.volume }.toDouble() / 14
        assertRelative(expectedAvg, profile.metrics.averageDailyVolume, msg = "일평균거래량")

        assertEquals(6..10, profile.metrics.capZone)
        assertAbs(256_500.0, profile.bins[6].lower)
        assertAbs(267_750.0, profile.bins[10].upper)
        assertRelative(103_221_544.0, profile.metrics.capZoneVolume, msg = "캡 존 물량")

        assertEquals(2..5, profile.metrics.supportZone)
        assertAbs(247_500.0, profile.bins[2].lower)
        assertAbs(256_500.0, profile.bins[5].upper)
        assertRelative(85_783_190.0, profile.metrics.supportZoneVolume, msg = "지지 존 물량")

        assertEquals(LocalDate.of(2026, 8, 18), profile.anchorDate)
        assertEquals(288_000L, profile.anchorPrice)
    }

    @Test
    fun `골든7 - 20구간 자동기준 SINCE_SWING_HIGH는 매수누적과 동일`() {
        val autoQuery = InvestorProfileQuery(period = ProfilePeriod.SINCE_SWING_HIGH, basis = null, binCount = 20)
        val cumulativeQuery = InvestorProfileQuery(period = ProfilePeriod.SINCE_SWING_HIGH, basis = ProfileBasis.CUMULATIVE_BUY, binCount = 20)
        val autoProfile = checkNotNull(useCase(fixtureRows, autoQuery, today))
        val cumulativeProfile = checkNotNull(useCase(fixtureRows, cumulativeQuery, today))

        assertEquals(ProfileBasis.CUMULATIVE_BUY, autoProfile.basis)
        assertTrue(autoProfile.autoBasis)
        assertRelative(cumulativeProfile.byInvestor.getValue(InvestorType.INDIVIDUAL).sum(), autoProfile.byInvestor.getValue(InvestorType.INDIVIDUAL).sum())
        assertRelative(cumulativeProfile.byInvestor.getValue(InvestorType.FOREIGN).sum(), autoProfile.byInvestor.getValue(InvestorType.FOREIGN).sum())
        assertRelative(cumulativeProfile.byInvestor.getValue(InvestorType.INSTITUTION).sum(), autoProfile.byInvestor.getValue(InvestorType.INSTITUTION).sum())
        assertEquals(cumulativeProfile.metrics.capZone, autoProfile.metrics.capZone)
        assertEquals(cumulativeProfile.metrics.supportZone, autoProfile.metrics.supportZone)
    }

    @Test
    fun `골든8 - 자동규칙 경계 60행은 RESIDUAL 59행은 CUMULATIVE_BUY`() {
        val query = InvestorProfileQuery(period = ProfilePeriod.ALL, basis = null, binCount = 20)
        val full60 = checkNotNull(useCase(fixtureRows, query, today))
        val drop59 = checkNotNull(useCase(fixtureRows.drop(1), query, today))

        assertEquals(60, full60.tradingDays)
        assertEquals(ProfileBasis.RESIDUAL, full60.basis)
        assertEquals(59, drop59.tradingDays)
        assertEquals(ProfileBasis.CUMULATIVE_BUY, drop59.basis)
    }

    @Test
    fun `골든9 - 20구간 잔존추정 SINCE_SWING_LOW 9거래일`() {
        val query = InvestorProfileQuery(period = ProfilePeriod.SINCE_SWING_LOW, basis = ProfileBasis.RESIDUAL, binCount = 20)
        val profile = checkNotNull(useCase(fixtureRows, query, today))

        assertEquals(9, profile.tradingDays)
        assertEquals(LocalDate.of(2026, 8, 25), profile.anchorDate)
        assertEquals(245_000L, profile.anchorPrice)
        assertAbs(1_400.0, profile.binWidth)

        assertRelative(0.0, profile.byInvestor.getValue(InvestorType.INDIVIDUAL).sum(), msg = "개인 소거")
        assertRelative(0.0, profile.byInvestor.getValue(InvestorType.FOREIGN).sum(), msg = "외국인 소거")
        assertRelative(2_489_812.0, profile.byInvestor.getValue(InvestorType.INSTITUTION).sum(), msg = "기관")
    }

    @Test
    fun `골든10 - 20구간 자동기준 SINCE_SWING_LOW 9거래일`() {
        val query = InvestorProfileQuery(period = ProfilePeriod.SINCE_SWING_LOW, basis = null, binCount = 20)
        val profile = checkNotNull(useCase(fixtureRows, query, today))

        assertEquals(ProfileBasis.CUMULATIVE_BUY, profile.basis)
        assertTrue(profile.autoBasis)
        assertAbs(1_400.0, profile.binWidth)

        val expectedAvg = fixtureRows.takeLast(9).sumOf { it.volume }.toDouble() / 9
        assertRelative(expectedAvg, profile.metrics.averageDailyVolume, msg = "일평균거래량")
        assertRelative(16_626_820.0, profile.metrics.averageDailyVolume, msg = "일평균거래량(스펙 표기)")

        assertEquals(9..14, profile.metrics.capZone)
        assertAbs(255_600.0, profile.bins[9].lower)
        assertAbs(264_000.0, profile.bins[14].upper)
        assertRelative(55_230_228.0, profile.metrics.capZoneVolume, msg = "캡 존 물량")

        assertEquals(4..8, profile.metrics.supportZone)
        assertAbs(248_600.0, profile.bins[4].lower)
        assertAbs(255_600.0, profile.bins[8].upper)
        assertRelative(51_865_967.0, profile.metrics.supportZoneVolume, msg = "지지 존 물량")

        assertEquals(LocalDate.of(2026, 8, 25), profile.anchorDate)
        assertEquals(245_000L, profile.anchorPrice)
    }

    // ==================================================================
    // §9.3 단위 테스트
    // ==================================================================

    private fun emptyLeg() = InvestorLeg(buyVolume = 0, buyAmount = 0, sellVolume = 0, sellAmount = 0)
    private fun flatLegs(): Map<InvestorType, InvestorLeg> = InvestorType.entries.associateWith { emptyLeg() }

    @Test
    fun `vwap 범위 이탈 leg는 해당 주체만 건너뛰고 다른 주체는 유지된다`() {
        val row = DailyInvestorFlow(
            date = today,
            high = 200_000, low = 100_000, close = 150_000, volume = 1_000,
            flows = mapOf(
                // vwap = 3,000,000 / 10 = 300,000원 → [100_000, 200_000] 밖
                InvestorType.INDIVIDUAL to InvestorLeg(buyVolume = 10, buyAmount = 3_000_000, sellVolume = 0, sellAmount = 0),
                // vwap = 1,500,000 / 10 = 150,000원 → 범위 안
                InvestorType.FOREIGN to InvestorLeg(buyVolume = 10, buyAmount = 1_500_000, sellVolume = 0, sellAmount = 0),
                InvestorType.INSTITUTION to InvestorLeg(buyVolume = 10, buyAmount = 1_500_000, sellVolume = 0, sellAmount = 0)
            )
        )
        val query = InvestorProfileQuery(period = ProfilePeriod.M1, basis = ProfileBasis.CUMULATIVE_BUY, binCount = 10)
        val profile = checkNotNull(useCase(listOf(row), query, today))

        assertEquals(0.0, profile.byInvestor.getValue(InvestorType.INDIVIDUAL).sum(), 1e-9)
        assertRelative(10.0, profile.byInvestor.getValue(InvestorType.FOREIGN).sum())
        assertRelative(10.0, profile.byInvestor.getValue(InvestorType.INSTITUTION).sum())
    }

    @Test
    fun `스윙 미검출시 1년 폴백과 swingFallback true`() {
        // k=5는 2k+1=11행이 있어야 후보가 하나라도 나온다. 5행뿐이므로 항상 미검출.
        val rows = (0 until 5).map { i ->
            DailyInvestorFlow(
                date = today.minusDays((4 - i).toLong()),
                high = 100_000 + i * 1_000L, low = 99_000 + i * 1_000L, close = 99_500 + i * 1_000L,
                volume = 1_000L,
                flows = flatLegs()
            )
        }
        val query = InvestorProfileQuery(period = ProfilePeriod.SINCE_SWING_HIGH, binCount = 10)
        val profile = checkNotNull(useCase(rows, query, today))

        assertTrue(profile.swingFallback)
        assertNull(profile.anchorDate)
        assertNull(profile.anchorPrice)
    }

    @Test
    fun `종가 비율이 0,7~1,3 밖이면 절단되고 truncatedAt이 기록된다`() {
        val d0 = today.minusDays(2)
        val d1 = today.minusDays(1)
        val d2 = today
        val rows = listOf(
            DailyInvestorFlow(d0, high = 210_000, low = 190_000, close = 200_000, volume = 1_000, flows = flatLegs()),
            // 100,000 / 200,000 = 0.5 → [0.7, 1.3] 밖(불연속)
            DailyInvestorFlow(d1, high = 105_000, low = 95_000, close = 100_000, volume = 1_000, flows = flatLegs()),
            DailyInvestorFlow(d2, high = 106_000, low = 94_000, close = 101_000, volume = 1_000, flows = flatLegs())
        )

        val (truncated, truncatedAt) = useCase.truncateAtDiscontinuity(rows)
        assertEquals(d1, truncatedAt)
        assertEquals(listOf(d1, d2), truncated.map { it.date })

        val query = InvestorProfileQuery(period = ProfilePeriod.M1, binCount = 10)
        val profile = checkNotNull(useCase(rows, query, today))
        assertEquals(d1, profile.truncatedAt)
        assertEquals(d1, profile.startDate)
    }

    @Test
    fun `창 내 행이 0이면 null을 반환한다`() {
        val rows = listOf(
            DailyInvestorFlow(today.minusDays(200), high = 100_000, low = 90_000, close = 95_000, volume = 1_000, flows = flatLegs())
        )
        // M1 = 30일 창. 200일 전 행은 창 밖이라 결과 행이 0이다.
        val query = InvestorProfileQuery(period = ProfilePeriod.M1, binCount = 10)
        assertNull(useCase(rows, query, today))
    }

    @Test
    fun `maxHigh와 minLow가 같으면 구간폭은 1,0이다`() {
        val rows = (0 until 3).map { i ->
            DailyInvestorFlow(today.minusDays((2 - i).toLong()), high = 100_000, low = 100_000, close = 100_000, volume = 500, flows = flatLegs())
        }
        val bins = useCase.binEdges(rows, 10)
        assertEquals(1.0, bins.first().upper - bins.first().lower, 1e-12)
    }

    @Test
    fun `CUSTOM 기간은 시작종료로 필터되고 3년 상한으로 클램프된다`() {
        val excludedByLookback = today.minusDays(365 * 4)
        val included = today.minusDays(365 * 2)
        val excludedByCustomEnd = today.minusDays(10)
        val rows = listOf(
            DailyInvestorFlow(excludedByLookback, 90_000, 80_000, 85_000, 1_000, flatLegs()),
            DailyInvestorFlow(included, 110_000, 100_000, 105_000, 1_000, flatLegs()),
            DailyInvestorFlow(excludedByCustomEnd, 130_000, 120_000, 125_000, 1_000, flatLegs())
        )
        val query = InvestorProfileQuery(
            period = ProfilePeriod.CUSTOM,
            binCount = 10,
            customStart = today.minusDays(365 * 5),
            customEnd = today.minusDays(365)
        )
        val profile = checkNotNull(useCase(rows, query, today))

        assertEquals(1, profile.tradingDays)
        assertEquals(included, profile.startDate)
        assertEquals(included, profile.endDate)
    }

    @Test
    fun `zone은 50퍼센트 이상인 이웃을 포함하고 미만은 제외한다`() {
        // 최댓값 100.0(idx2). idx1=50.0(경계, 포함) · idx3=49.9(경계 미만, 제외)
        val sums = doubleArrayOf(0.0, 50.0, 100.0, 49.9, 0.0)
        assertEquals(1..2, useCase.zone(sums, 0..4))
    }

    @Test
    fun `zone은 한쪽이 전부 0이면 null이다`() {
        val sums = doubleArrayOf(0.0, 0.0, 0.0)
        assertNull(useCase.zone(sums, 0..2))
    }

    @Test
    fun `zone은 candidates가 비어있으면 null이다`() {
        val sums = doubleArrayOf(10.0, 20.0)
        assertNull(useCase.zone(sums, 1..0))
    }

    @Test
    fun `앵커는 스윙 기간에서만 non-null이다`() {
        val nonSwing = checkNotNull(useCase(fixtureRows, InvestorProfileQuery(period = ProfilePeriod.M6, binCount = 10), today))
        assertNull(nonSwing.anchorDate)
        assertNull(nonSwing.anchorPrice)

        val swing = checkNotNull(useCase(fixtureRows, InvestorProfileQuery(period = ProfilePeriod.SINCE_SWING_HIGH, binCount = 10), today))
        assertNotNull(swing.anchorDate)
        assertNotNull(swing.anchorPrice)
    }

    // ==================================================================
    // §9.1 속성 기반 테스트 (Random(42), 1,000회)
    // ==================================================================

    private fun randomLeg(rnd: Random, low: Long, high: Long): InvestorLeg {
        val buyVolume = rnd.nextLong(0, 50_000_000L)
        val buyPrice = if (buyVolume > 0) rnd.nextLong(low, high + 1) else 0L
        val sellVolume = rnd.nextLong(0, 50_000_000L)
        val sellPrice = if (sellVolume > 0) rnd.nextLong(low, high + 1) else 0L
        return InvestorLeg(
            buyVolume = buyVolume,
            buyAmount = buyPrice * buyVolume,
            sellVolume = sellVolume,
            sellAmount = sellPrice * sellVolume
        )
    }

    /** 가격 1,000~1,000,000, 일간 변동폭 0~30%, 거래량 0~1e8의 임의 rows(오름차순, 마지막=today). */
    private fun randomRows(rnd: Random, dayCount: Int, referenceToday: LocalDate): List<DailyInvestorFlow> {
        var prevClose = rnd.nextLong(1_000, 1_000_000)
        return (0 until dayCount).map { i ->
            val date = referenceToday.minusDays((dayCount - 1 - i).toLong())
            val base = prevClose.coerceIn(1_000L, 1_000_000L)
            val rangeRatio = rnd.nextDouble(0.0, 0.30)
            val low = maxOf(1L, (base * (1 - rangeRatio / 2)).toLong())
            val high = maxOf(low + 1, (base * (1 + rangeRatio / 2)).toLong())
            val close = rnd.nextLong(low, high + 1)
            prevClose = close
            val volume = rnd.nextLong(0, 100_000_001L)
            val flows = InvestorType.entries.associateWith { randomLeg(rnd, low, high) }
            DailyInvestorFlow(date, high, low, close, volume, flows)
        }
    }

    private fun assertMassAndMoment(
        alloc: DoubleArray,
        volume: Double,
        center: Double,
        low: Double,
        high: Double,
        binWidth: Double,
        bins: List<InvestorPriceBin>
    ) {
        if (volume <= 0.0) return
        val sum = alloc.sum()
        assertRelative(volume, sum, tol = 1e-9, msg = "질량 보존")
        val sigma = maxOf((high - low) / 4.0, binWidth / 4.0)
        val weightedMean = alloc.indices.sumOf { alloc[it] * bins[it].center } / sum
        val bound = sigma + binWidth / 2 + 1e-6
        assertTrue(
            "1차 모멘트 |mean-center|=${abs(weightedMean - center)} bound=$bound",
            abs(weightedMean - center) <= bound
        )
    }

    @Test
    fun `속성 - 1000회 랜덤 창이 불변식을 만족한다`() {
        val rnd = Random(42)
        val binCounts = listOf(10, 15, 20, 25)

        repeat(1000) {
            val binCount = binCounts[rnd.nextInt(binCounts.size)]
            val dayCount = rnd.nextInt(1, 121)
            val rows = randomRows(rnd, dayCount, today)

            val query = InvestorProfileQuery(period = ProfilePeriod.ALL, basis = null, binCount = binCount)
            val profile = checkNotNull(useCase(rows, query, today))

            // 자동 규칙(대략적 확인: ALL 기간에서는 절단이 사실상 발생하지 않으므로 tradingDays == dayCount)
            val expectedBasis = if (profile.tradingDays < CalcInvestorVolumeProfileUseCase.AUTO_RESIDUAL_MIN_DAYS) {
                ProfileBasis.CUMULATIVE_BUY
            } else {
                ProfileBasis.RESIDUAL
            }
            assertEquals(expectedBasis, profile.basis)
            assertTrue(profile.autoBasis)

            // 비음수
            profile.byInvestor.values.forEach { arr -> arr.forEach { assertTrue("byInvestor >= 0 위반: $it", it >= 0.0) } }

            // 구간 분할: 창 내 모든 low/high가 bins 범위 안
            val binsLower = profile.bins.first().lower
            val binsUpper = profile.bins.last().upper
            rows.forEach { row ->
                assertTrue(row.low.toDouble() >= binsLower - 1e-6)
                assertTrue(row.high.toDouble() <= binsUpper + 1e-6)
            }

            // allocateDay 질량 보존 + 1차 모멘트(전체 거래량 배분 + 매수 leg 전부)
            val bins = useCase.binEdges(rows, binCount)
            val binWidth = bins.first().let { it.upper - it.lower }
            rows.forEach { row ->
                val low = row.low.toDouble()
                val high = row.high.toDouble()
                val rep = (row.high + row.low + row.close) / 3.0
                val totalAlloc = useCase.allocateDay(row.volume.toDouble(), rep, low, high, bins, binWidth)
                assertMassAndMoment(totalAlloc, row.volume.toDouble(), rep, low, high, binWidth, bins)

                InvestorType.entries.forEach { type ->
                    val leg = row.flows.getValue(type)
                    if (leg.buyVolume > 0) {
                        val alloc = useCase.allocateDay(leg.buyVolume.toDouble(), leg.buyVwap, low, high, bins, binWidth)
                        assertMassAndMoment(alloc, leg.buyVolume.toDouble(), leg.buyVwap, low, high, binWidth, bins)
                    }
                }
            }

            // RESIDUAL 상각 정합(§5.4 항등식). basis와 무관하게 강제로 RESIDUAL 경로를 검증한다.
            val allocation = useCase.allocateWindow(rows, ProfileBasis.RESIDUAL, bins, binWidth)
            InvestorType.entries.forEach { type ->
                val totalBuy = rows.sumOf { it.flows.getValue(type).buyVolume }.toDouble()
                val totalSell = rows.sumOf { it.flows.getValue(type).sellVolume }.toDouble()
                val hFinal = allocation.byInvestor.getValue(type).sum()
                val taken = allocation.takenSell.getValue(type)
                val excess = allocation.excessSell.getValue(type)
                assertRelative(totalBuy, hFinal + taken, tol = 1e-9, msg = "ΣH_final+Σs=Σ매수량 [$type]")
                assertRelative(totalSell - taken, excess, tol = 1e-9, msg = "excessSell [$type]")
                assertTrue("excessSell >= 0 위반 [$type]: $excess", excess >= -1e-6)
            }

            // 자동 규칙 경계(59/60)를 매회 별도로 확정 검증한다.
            val rows59 = randomRows(rnd, 59, today)
            val profile59 = checkNotNull(useCase(rows59, InvestorProfileQuery(period = ProfilePeriod.ALL, binCount = binCount), today))
            assertEquals(ProfileBasis.CUMULATIVE_BUY, profile59.basis)

            val rows60 = randomRows(rnd, 60, today)
            val profile60 = checkNotNull(useCase(rows60, InvestorProfileQuery(period = ProfilePeriod.ALL, binCount = binCount), today))
            assertEquals(ProfileBasis.RESIDUAL, profile60.basis)

            // CUSTOM 창 클램프: [today-3년, today] 밖으로 새지 않는다.
            val a = today.minusDays(rnd.nextLong(0, 2000))
            val b = today.minusDays(rnd.nextLong(-30, 60))
            val customStart = if (a <= b) a else b
            val customEnd = if (a <= b) b else a
            val customQuery = InvestorProfileQuery(period = ProfilePeriod.CUSTOM, binCount = binCount, customStart = customStart, customEnd = customEnd)
            val customProfile = useCase(rows, customQuery, today)
            if (customProfile != null) {
                val clampedStart = maxOf(customStart, today.minusDays(CalcInvestorVolumeProfileUseCase.MAX_LOOKBACK_DAYS))
                val clampedEnd = minOf(customEnd, today)
                assertFalse(customProfile.startDate.isBefore(clampedStart))
                assertFalse(customProfile.endDate.isAfter(clampedEnd))
            }
        }
    }

    // ==================================================================
    // §8 성능 예산
    // ==================================================================

    @Test
    fun `성능 - 750행 3주체 20구간 RESIDUAL은 300ms 이내`() {
        val rows = randomRows(Random(123), 750, today)
        val query = InvestorProfileQuery(period = ProfilePeriod.ALL, basis = ProfileBasis.RESIDUAL, binCount = 20)

        var profile: InvestorVolumeProfile? = null
        val elapsedMs = measureTimeMillis { profile = useCase(rows, query, today) }

        println("[perf] CalcInvestorVolumeProfileUseCase 750rows x 3investors x 20bins RESIDUAL: ${elapsedMs}ms")
        assertNotNull(profile)
        assertTrue("엔진 연산이 예산(300ms)을 초과했다: ${elapsedMs}ms", elapsedMs < 300)
    }
}
