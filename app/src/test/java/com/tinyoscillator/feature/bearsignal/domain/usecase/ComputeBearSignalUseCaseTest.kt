package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.BearPhase
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalReportBaseline
import com.tinyoscillator.feature.bearsignal.domain.model.Depth
import com.tinyoscillator.feature.bearsignal.domain.model.MarketReturns
import com.tinyoscillator.feature.bearsignal.domain.usecase.ComputeBearSignalUseCase.Companion.amplifier
import com.tinyoscillator.feature.bearsignal.domain.usecase.ComputeBearSignalUseCase.Companion.analyzeMarkets
import com.tinyoscillator.feature.bearsignal.domain.usecase.ComputeBearSignalUseCase.Companion.scoreGate
import com.tinyoscillator.feature.bearsignal.domain.usecase.ComputeBearSignalUseCase.Companion.scoreS1
import com.tinyoscillator.feature.bearsignal.domain.usecase.ComputeBearSignalUseCase.Companion.scoreS2
import com.tinyoscillator.feature.bearsignal.domain.usecase.ComputeBearSignalUseCase.Companion.scoreS3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ComputeBearSignalUseCase 순수 스코어링 검증 (TASK.md §3 SSOT — 골든/경계 케이스).
 *
 * 골든 케이스는 프로토타입 bear_signal_dashboard.jsx `MARKETS` 상수(도표48 전체 20지수,
 * `BearSignalReportBaseline.MARKETS`로 이관 완료)를 그대로 사용한다. 경계 케이스 전용
 * 테스트는 합성 픽스처(newDropout 등)를 사용한다.
 */
class ComputeBearSignalUseCaseTest {

    private val useCase = ComputeBearSignalUseCase()

    companion object {
        private const val DELTA = 1e-9
    }

    // -- 헬퍼 --

    /** 신규 이탈(12M > 0, 선택 기간 음수) 합성 지수 */
    private fun newDropout(name: String, oneMonth: Double) =
        MarketReturns(name, listOf(10.0, 5.0, 2.0, oneMonth))

    /** 중립 기본값 입력 — 모든 서브스코어 0 (필요 필드만 오버라이드) */
    private fun inputs(
        markets: List<MarketReturns> = emptyList(),
        periodIdx: Int = 3,
        up: Int = 100,
        down: Int = 10,
        deepening: Boolean = false,
        loss: Double = 0.0,
        etf: String = "up",
        big: String = "smooth",
        rate: Double = 3.0,
        dir: String = "hold",
        credit: Double = 0.0,
        margin: Boolean = false,
        semi: Double = 0.0,
        kospi2: Double = 0.0,
        buffer: Boolean = true
    ) = BearSignalInputs(
        markets = markets, periodIdx = periodIdx,
        up = up, down = down, deepening = deepening,
        loss = loss, etf = etf, big = big,
        rate = rate, dir = dir, credit = credit, margin = margin,
        semi = semi, kospi2 = kospi2, buffer = buffer
    )

    // ==========================================================
    // 골든 케이스 (리포트 2026.6.30 기준값 → AMBER)
    // ==========================================================

    @Test
    fun `골든 케이스 — 2026-6-30 기준값 s1 s2 s3 gate 모두 1, amp 1_30, AMBER`() {
        // 도표48 전체 20지수(BearSignalReportBaseline.MARKETS) 실데이터 사용.
        // 1M 기준 neg=11(태국·베트남·상하이·S&P·DAX·나스닥·멕시코·브라질·인니·항생·RTS),
        // worstNew=-5.1(나스닥, 12M+25.4→신규 이탈) → SHALLOW(>-6) → s1=1.
        val result = useCase(BearSignalReportBaseline.toInputs(BearSignalReportBaseline.MARKETS))

        assertEquals(1, result.s1)
        assertEquals(1, result.s2)
        assertEquals(1, result.s3)
        assertEquals(1, result.gate)
        assertEquals(1.30, result.amp, DELTA)
        assertEquals(3, result.lead)
        assertEquals(33, result.leadPct)
        assertEquals(0, result.warn)
        assertEquals(BearPhase.AMBER, result.phase)
        assertEquals(11, result.ma.neg)
        assertEquals(-5.1, result.ma.worstNew, DELTA)
        assertEquals(Depth.SHALLOW, result.ma.depth)
    }

    @Test
    fun `골든 서브스코어 — scoreS2 up14 down12 deepening은 1`() {
        assertEquals(1, scoreS2(up = 14, down = 12, deepening = true))
    }

    @Test
    fun `골든 서브스코어 — scoreS3 loss45 etf up big pending은 1`() {
        assertEquals(1, scoreS3(loss = 45.0, etf = "up", big = "pending"))
    }

    @Test
    fun `골든 서브스코어 — scoreGate rate3_75 hike credit38 margin false는 1`() {
        assertEquals(1, scoreGate(rate = 3.75, dir = "hike", credit = 38.0, margin = false))
    }

    @Test
    fun `골든 서브스코어 — amplifier semi23_1 kospi2 56 buffer true는 1_30`() {
        assertEquals(1.30, amplifier(semi = 23.1, kospi2 = 56.0, buffer = true), DELTA)
    }

    @Test
    fun `시드 — 부록 C 도표48 전체 20지수 값 검증`() {
        assertEquals(20, BearSignalReportBaseline.MARKETS.size)
        assertEquals(listOf(173.1, 103.7, 54.0, 4.5), BearSignalReportBaseline.KOSPI.r)
        assertTrue(BearSignalReportBaseline.KOSPI.lead)
        assertEquals(listOf(75.2, 36.7, 29.4, 6.7), BearSignalReportBaseline.NIKKEI.r)
        assertEquals(listOf(98.2, 56.1, 33.7, 2.4), BearSignalReportBaseline.TAIWAN.r)
        assertEquals(listOf(-5.8, -7.6, 3.2, 0.6), BearSignalReportBaseline.INDIA.r)
        assertEquals(listOf(-14.5, -30.9, -17.7, -3.8), BearSignalReportBaseline.INDONESIA.r)
        assertEquals(listOf(-6.8, -12.2, -8.8, -11.4), BearSignalReportBaseline.HANG_SENG.r)
        assertEquals(listOf(-17.1, -16.4, -13.7, -17.6), BearSignalReportBaseline.RTS.r)
        assertEquals("코스피", BearSignalReportBaseline.MARKETS.first().name)
        assertEquals("RTS", BearSignalReportBaseline.MARKETS.last().name)
    }

    // ==========================================================
    // scoreS1 — 경계 (neg 6/7 × depth)
    // ==========================================================

    @Test
    fun `scoreS1 — SHALLOW에서 neg 6은 0, neg 7은 1`() {
        assertEquals(0, scoreS1(6, Depth.SHALLOW))
        assertEquals(1, scoreS1(7, Depth.SHALLOW))
    }

    @Test
    fun `scoreS1 — DEEPENING에서 neg 6은 0, neg 7은 2`() {
        assertEquals(0, scoreS1(6, Depth.DEEPENING))
        assertEquals(2, scoreS1(7, Depth.DEEPENING))
    }

    @Test
    fun `scoreS1 — DEEP에서 neg 6은 1, neg 7은 3`() {
        assertEquals(1, scoreS1(6, Depth.DEEP))
        assertEquals(3, scoreS1(7, Depth.DEEP))
    }

    // ==========================================================
    // analyzeMarkets — 신규 이탈·null·depth 경계
    // ==========================================================

    @Test
    fun `analyzeMarkets — 만성 약세국은 neg 집계, worstNew 제외`() {
        // RTS: 12M −17.1 < 0 → 신규 이탈 아님 → worstNew는 −17.6이 아니라 신규 이탈 −3.0
        val ma = analyzeMarkets(listOf(BearSignalReportBaseline.RTS, newDropout("A", -3.0)), 3)
        assertEquals(2, ma.neg)
        assertEquals(-3.0, ma.worstNew, DELTA)
        assertEquals(Depth.SHALLOW, ma.depth)
    }

    @Test
    fun `analyzeMarkets — 만성 약세국만 있으면 worstNew 0 유지`() {
        val ma = analyzeMarkets(listOf(BearSignalReportBaseline.RTS), 3)
        assertEquals(1, ma.neg)
        assertEquals(0.0, ma.worstNew, DELTA)
        assertEquals(Depth.SHALLOW, ma.depth)
    }

    @Test
    fun `analyzeMarkets — 선택 기간 null은 neg 미집계`() {
        val ma = analyzeMarkets(listOf(MarketReturns("N", listOf(10.0, 5.0, 2.0, null))), 3)
        assertEquals(0, ma.neg)
        assertEquals(0.0, ma.worstNew, DELTA)
    }

    @Test
    fun `analyzeMarkets — 12M null이면 neg 집계하되 worstNew 미갱신`() {
        val ma = analyzeMarkets(listOf(MarketReturns("N", listOf(null, 5.0, 2.0, -9.0))), 3)
        assertEquals(1, ma.neg)
        assertEquals(0.0, ma.worstNew, DELTA)
        assertEquals(Depth.SHALLOW, ma.depth)
    }

    @Test
    fun `analyzeMarkets — depth 경계 -12 DEEP, -11_9 DEEPENING, -6 DEEPENING, -5_9 SHALLOW`() {
        assertEquals(Depth.DEEP, analyzeMarkets(listOf(newDropout("A", -12.0)), 3).depth)
        assertEquals(Depth.DEEPENING, analyzeMarkets(listOf(newDropout("A", -11.9)), 3).depth)
        assertEquals(Depth.DEEPENING, analyzeMarkets(listOf(newDropout("A", -6.0)), 3).depth)
        assertEquals(Depth.SHALLOW, analyzeMarkets(listOf(newDropout("A", -5.9)), 3).depth)
    }

    // ==========================================================
    // scoreS2 — ratio 경계
    // ==========================================================

    @Test
    fun `scoreS2 — ratio 0_94는 deepening true면 1, false면 0`() {
        assertEquals(1, scoreS2(100, 94, true))
        assertEquals(0, scoreS2(100, 94, false))
    }

    @Test
    fun `scoreS2 — ratio 0_95는 2`() {
        assertEquals(2, scoreS2(100, 95, false))
    }

    @Test
    fun `scoreS2 — ratio 1_0은 2, 3 아님`() {
        assertEquals(2, scoreS2(100, 100, false))
    }

    @Test
    fun `scoreS2 — down이 up 초과면 3`() {
        assertEquals(3, scoreS2(100, 101, false))
        assertEquals(3, scoreS2(1, 2, false))
    }

    @Test
    fun `scoreS2 — up 0이면 r 9로 3`() {
        assertEquals(3, scoreS2(0, 0, false))
        assertEquals(3, scoreS2(0, 5, false))
    }

    @Test
    fun `scoreS2 — ratio 0_69는 deepening이어도 0`() {
        assertEquals(0, scoreS2(100, 69, true))
    }

    // ==========================================================
    // scoreS3 — loss·etf·big 경계
    // ==========================================================

    @Test
    fun `scoreS3 — loss 사다리 44는 0, 45는 1, 59는 1, 60은 2, 79는 2, 80은 3`() {
        assertEquals(0, scoreS3(44.0, "up", "smooth"))
        assertEquals(1, scoreS3(45.0, "up", "smooth"))
        assertEquals(1, scoreS3(59.0, "up", "smooth"))
        assertEquals(2, scoreS3(60.0, "up", "smooth"))
        assertEquals(2, scoreS3(79.0, "up", "smooth"))
        assertEquals(3, scoreS3(80.0, "up", "smooth"))
    }

    @Test
    fun `scoreS3 — etf down이면 loss 0이어도 최소 2`() {
        assertEquals(2, scoreS3(0.0, "down", "smooth"))
    }

    @Test
    fun `scoreS3 — big failed면 무조건 3`() {
        assertEquals(3, scoreS3(0.0, "up", "failed"))
        assertEquals(3, scoreS3(0.0, "down", "failed"))
        assertEquals(3, scoreS3(80.0, "up", "failed"))
    }

    @Test
    fun `scoreS3 — big pending이면 loss 0이어도 최소 1`() {
        assertEquals(1, scoreS3(0.0, "up", "pending"))
    }

    // ==========================================================
    // scoreGate — rate·dir·credit·margin 경계
    // ==========================================================

    @Test
    fun `scoreGate — rate 사다리 4_49는 2, 4_5는 3, 4_0은 2`() {
        assertEquals(2, scoreGate(4.49, "hold", 0.0, false))
        assertEquals(3, scoreGate(4.5, "hold", 0.0, false))
        assertEquals(2, scoreGate(4.0, "hold", 0.0, false))
    }

    @Test
    fun `scoreGate — 4_0 미만은 dir hike만 1`() {
        assertEquals(1, scoreGate(3.99, "hike", 0.0, false))
        assertEquals(0, scoreGate(3.99, "hold", 0.0, false))
        assertEquals(0, scoreGate(3.99, "ease", 0.0, false))
    }

    @Test
    fun `scoreGate — margin true면 최소 2`() {
        assertEquals(2, scoreGate(3.0, "hold", 0.0, true))
    }

    @Test
    fun `scoreGate — credit 35는 최소 1로 상향, 34_9는 미상향`() {
        assertEquals(1, scoreGate(3.0, "hold", 35.0, false))
        assertEquals(0, scoreGate(3.0, "hold", 34.9, false))
    }

    @Test
    fun `scoreGate — margin이 credit 분기에 우선`() {
        // margin=true → max(lv,2), credit 분기 미적용
        assertEquals(2, scoreGate(3.0, "hold", 100.0, true))
        // margin=false → credit은 최대 1까지만 상향
        assertEquals(1, scoreGate(3.0, "hold", 100.0, false))
    }

    // ==========================================================
    // amplifier — 가산·경계·상한
    // ==========================================================

    @Test
    fun `amplifier — 트리거 없음은 1_0`() {
        assertEquals(1.0, amplifier(19.99, 49.99, true), DELTA)
    }

    @Test
    fun `amplifier — 3중 트리거는 1_5, 상한 1_6 이내`() {
        val a = amplifier(20.0, 50.0, false)
        assertEquals(1.5, a, DELTA) // 가산 합계 최대치 — 상한 1.6에 미도달
        assertTrue("min(a, 1.6) 상한 준수", a <= 1.6)
    }

    @Test
    fun `amplifier — semi 경계 20은 +0_15, 19_99는 +0`() {
        assertEquals(1.15, amplifier(20.0, 0.0, true), DELTA)
        assertEquals(1.0, amplifier(19.99, 0.0, true), DELTA)
    }

    @Test
    fun `amplifier — kospi2 경계 50은 +0_15, 49_99는 +0`() {
        assertEquals(1.15, amplifier(0.0, 50.0, true), DELTA)
        assertEquals(1.0, amplifier(0.0, 49.99, true), DELTA)
    }

    // ==========================================================
    // composite — phase 경계 (상태 기계)
    // ==========================================================

    @Test
    fun `composite — gate 3 그리고 warn 1 이상이면 RED`() {
        // s2=3 (down>up) → warn=1, rate 4.5 → gate=3
        val r = useCase(inputs(up = 1, down = 2, rate = 4.5))
        assertEquals(3, r.gate)
        assertTrue(r.warn >= 1)
        assertEquals(BearPhase.RED, r.phase)
    }

    @Test
    fun `composite — gate 3 그리고 warn 0이면 ORANGE`() {
        val r = useCase(inputs(rate = 4.5))
        assertEquals(3, r.gate)
        assertEquals(0, r.warn)
        assertEquals(BearPhase.ORANGE, r.phase)
    }

    @Test
    fun `composite — gate 2면 ORANGE`() {
        val r = useCase(inputs(rate = 4.0))
        assertEquals(2, r.gate)
        assertEquals(BearPhase.ORANGE, r.phase)
    }

    @Test
    fun `composite — lead 6 그리고 gate 1이면 ORANGE`() {
        // s1=3 (7개국 신규 이탈 deep) + s3=3 (big failed) → lead=6, gate=1 (hike)
        val deepMarkets = (1..7).map { newDropout("M$it", -15.0) }
        val r = useCase(inputs(markets = deepMarkets, big = "failed", dir = "hike"))
        assertEquals(6, r.lead)
        assertEquals(1, r.gate)
        assertEquals(BearPhase.ORANGE, r.phase)
    }

    @Test
    fun `composite — lead 6 그리고 gate 0이면 AMBER`() {
        val deepMarkets = (1..7).map { newDropout("M$it", -15.0) }
        val r = useCase(inputs(markets = deepMarkets, big = "failed"))
        assertEquals(6, r.lead)
        assertEquals(0, r.gate)
        assertEquals(BearPhase.AMBER, r.phase)
    }

    @Test
    fun `composite — lead 3 그리고 gate 0이면 AMBER`() {
        val r = useCase(inputs(big = "failed")) // s3=3만
        assertEquals(3, r.lead)
        assertEquals(0, r.gate)
        assertEquals(BearPhase.AMBER, r.phase)
    }

    @Test
    fun `composite — gate 1 그리고 lead 0이면 AMBER`() {
        val r = useCase(inputs(dir = "hike"))
        assertEquals(0, r.lead)
        assertEquals(1, r.gate)
        assertEquals(BearPhase.AMBER, r.phase)
    }

    @Test
    fun `composite — lead 2 그리고 gate 0이면 GREEN`() {
        val r = useCase(inputs(up = 100, down = 95)) // s2=2 → lead=2 (warn=1이어도 gate<3)
        assertEquals(2, r.lead)
        assertEquals(0, r.gate)
        assertEquals(BearPhase.GREEN, r.phase)
    }
}
