package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.AutoIndicator
import com.tinyoscillator.feature.bearsignal.domain.model.AutoMarketReturn
import com.tinyoscillator.feature.bearsignal.domain.model.BearPhase
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalReportBaseline
import com.tinyoscillator.feature.bearsignal.domain.model.BearThresholdsFixture
import com.tinyoscillator.feature.bearsignal.domain.model.InputSource
import com.tinyoscillator.feature.bearsignal.domain.model.ManualBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.ManualMarketReturn
import com.tinyoscillator.feature.bearsignal.domain.model.MarketCoverage
import com.tinyoscillator.feature.bearsignal.domain.model.MarketReturnsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MergeBearSignalInputsUseCase] 병합 우선순위(MANUAL > AUTO > 리포트 기준값) 계약 테스트
 * (TASK_bear_signal_console.md §1.2 하이브리드 데이터 아키텍처, Phase 3 완료 조건).
 */
class MergeBearSignalInputsUseCaseTest {

    private val merge = MergeBearSignalInputsUseCase()

    // ── 골든 케이스 재현 — 전부 미설정(AUTO/MANUAL 없음) → 리포트 기준값 그대로 ──────

    @Test
    fun `AUTO와 MANUAL이 전부 없으면 리포트 기준값으로 조립되고 골든 케이스 AMBER를 재현한다`() {
        val merged = merge(auto = null, manual = null, marketsSnapshot = null)

        assertEquals(BearSignalReportBaseline.toInputs(), merged)

        val result = ComputeBearSignalUseCase(BearThresholdsFixture.DEFAULT)(merged)
        assertEquals(1, result.s1)
        assertEquals(1, result.s2)
        assertEquals(1, result.s3)
        assertEquals(1, result.gate)
        assertEquals(1.30, result.amp, 1e-9)
        assertEquals(BearPhase.AMBER, result.phase)
    }

    // ── 스칼라 지표 — MANUAL 우선순위 ────────────────────────────────────

    @Test
    fun `credit는 MANUAL이 있으면 그 값, 없으면 기준값(AUTO 미설정)`() {
        val manual = ManualBearSignalInputs(credit = AutoIndicator(50.0, InputSource.MANUAL, 1L))
        val merged = merge(auto = null, manual = manual, marketsSnapshot = null)
        assertEquals(50.0, merged.credit, 1e-9)

        val mergedNoManual = merge(auto = null, manual = null, marketsSnapshot = null)
        assertEquals(BearSignalReportBaseline.CREDIT, mergedNoManual.credit, 1e-9)
    }

    @Test
    fun `loss는 MANUAL이 있으면 그 값, 없으면 기준값(AUTO 미설정)`() {
        val manual = ManualBearSignalInputs(loss = AutoIndicator(72.0, InputSource.MANUAL, 1L))
        val merged = merge(auto = null, manual = manual, marketsSnapshot = null)
        assertEquals(72.0, merged.loss, 1e-9)
    }

    @Test
    fun `big는 MANUAL이 있으면 그 값, 없으면 기준값(AUTO 미설정)`() {
        val manual = ManualBearSignalInputs(big = AutoIndicator("failed", InputSource.MANUAL, 1L))
        val merged = merge(auto = null, manual = manual, marketsSnapshot = null)
        assertEquals("failed", merged.big)

        val mergedNoManual = merge(auto = null, manual = null, marketsSnapshot = null)
        assertEquals(BearSignalReportBaseline.BIG, mergedNoManual.big)
    }

    // ── Phase 4(§4.5) 확장 — loss/big/credit AUTO(LLM 제안 승인) 경로 ──────────

    @Test
    fun `credit는 MANUAL이 없고 AUTO(LLM 제안 승인)가 있으면 AUTO 값을 사용한다`() {
        val auto = autoInputs(credit = AutoIndicator(42.0, InputSource.AUTO, 1L))
        val merged = merge(auto = auto, manual = null, marketsSnapshot = null)
        assertEquals(42.0, merged.credit, 1e-9)
    }

    @Test
    fun `credit는 MANUAL이 AUTO(LLM 제안 승인)보다 우선한다(MANUAL 불패)`() {
        val auto = autoInputs(credit = AutoIndicator(42.0, InputSource.AUTO, 1L))
        val manual = ManualBearSignalInputs(credit = AutoIndicator(50.0, InputSource.MANUAL, 2L))
        val merged = merge(auto = auto, manual = manual, marketsSnapshot = null)
        assertEquals(50.0, merged.credit, 1e-9)
    }

    @Test
    fun `loss는 MANUAL이 없고 AUTO(LLM 제안 승인)가 있으면 AUTO 값을 사용한다`() {
        val auto = autoInputs(lossRatio = AutoIndicator(65.0, InputSource.AUTO, 1L))
        val merged = merge(auto = auto, manual = null, marketsSnapshot = null)
        assertEquals(65.0, merged.loss, 1e-9)
    }

    @Test
    fun `loss는 MANUAL이 AUTO(LLM 제안 승인)보다 우선한다(MANUAL 불패)`() {
        val auto = autoInputs(lossRatio = AutoIndicator(65.0, InputSource.AUTO, 1L))
        val manual = ManualBearSignalInputs(loss = AutoIndicator(72.0, InputSource.MANUAL, 2L))
        val merged = merge(auto = auto, manual = manual, marketsSnapshot = null)
        assertEquals(72.0, merged.loss, 1e-9)
    }

    @Test
    fun `big는 MANUAL이 없고 AUTO(LLM 제안 승인)가 있으면 AUTO 값을 사용한다`() {
        val auto = autoInputs(bigDeal = AutoIndicator("pending", InputSource.AUTO, 1L))
        val merged = merge(auto = auto, manual = null, marketsSnapshot = null)
        assertEquals("pending", merged.big)
    }

    @Test
    fun `big는 MANUAL이 AUTO(LLM 제안 승인)보다 우선한다(MANUAL 불패)`() {
        val auto = autoInputs(bigDeal = AutoIndicator("pending", InputSource.AUTO, 1L))
        val manual = ManualBearSignalInputs(big = AutoIndicator("failed", InputSource.MANUAL, 2L))
        val merged = merge(auto = auto, manual = manual, marketsSnapshot = null)
        assertEquals("failed", merged.big)
    }

    @Test
    fun `margin은 MANUAL이 있으면 그 값, 없으면 기준값`() {
        val manual = ManualBearSignalInputs(margin = AutoIndicator(true, InputSource.MANUAL, 1L))
        val merged = merge(auto = null, manual = manual, marketsSnapshot = null)
        assertTrue(merged.margin)

        val mergedNoManual = merge(auto = null, manual = null, marketsSnapshot = null)
        assertEquals(BearSignalReportBaseline.MARGIN, mergedNoManual.margin)
    }

    @Test
    fun `dir는 MANUAL이 AUTO보다 우선한다`() {
        val auto = autoInputs(dir = AutoIndicator("hold", InputSource.AUTO, 100L))
        val manual = ManualBearSignalInputs(dir = AutoIndicator("hike", InputSource.MANUAL, 200L))

        val merged = merge(auto = auto, manual = manual, marketsSnapshot = null)
        assertEquals("hike", merged.dir) // MANUAL 우선
    }

    @Test
    fun `dir는 MANUAL이 없으면 AUTO 값을 사용한다`() {
        val auto = autoInputs(dir = AutoIndicator("ease", InputSource.AUTO, 100L))
        val merged = merge(auto = auto, manual = null, marketsSnapshot = null)
        assertEquals("ease", merged.dir)
    }

    @Test
    fun `dir는 AUTO와 MANUAL이 전부 없으면 기준값 사용`() {
        val merged = merge(auto = null, manual = null, marketsSnapshot = null)
        assertEquals(BearSignalReportBaseline.DIR, merged.dir)
    }

    @Test
    fun `up down kospi2 semi buffer rate etf는 AUTO가 있으면 AUTO 우선(MANUAL 경로 없음)`() {
        val auto = autoInputs(
            up3 = AutoIndicator(20, InputSource.AUTO, 1L),
            down3 = AutoIndicator(5, InputSource.AUTO, 1L),
            kospi2 = AutoIndicator(61.0, InputSource.AUTO, 1L),
            semi = AutoIndicator(30.0, InputSource.AUTO, 1L),
            buffer = AutoIndicator(false, InputSource.AUTO, 1L),
            rate = AutoIndicator(5.0, InputSource.AUTO, 1L),
            etf = AutoIndicator("down", InputSource.AUTO, 1L)
        )
        val merged = merge(auto = auto, manual = null, marketsSnapshot = null)

        assertEquals(20, merged.up)
        assertEquals(5, merged.down)
        assertEquals(61.0, merged.kospi2, 1e-9)
        assertEquals(30.0, merged.semi, 1e-9)
        assertEquals(false, merged.buffer)
        assertEquals(5.0, merged.rate, 1e-9)
        assertEquals("down", merged.etf)
    }

    @Test
    fun `issueRatio는 스코어링 입력에 포함되지 않는다`() {
        // ManualBearSignalInputs.issueRatio가 세팅돼도 BearSignalInputs에는 대응 필드가 없다(§3 SSOT 불변).
        // loss/big/credit/margin/dir 등 기존 필드가 issueRatio의 영향을 받지 않는지만 확인.
        val manual = ManualBearSignalInputs(
            issueRatio = AutoIndicator(99.0, InputSource.MANUAL, 1L),
            loss = AutoIndicator(10.0, InputSource.MANUAL, 1L)
        )
        val merged = merge(auto = null, manual = manual, marketsSnapshot = null)
        assertEquals(10.0, merged.loss, 1e-9) // issueRatio가 loss를 오염시키지 않음
    }

    // ── 국가별 지수 수익률 병합 — MANUAL > AUTO > 기준값, 기간 단위 ────────────

    @Test
    fun `미커버 해외지수는 MANUAL 오버라이드가 있으면 그 값을 사용한다`() {
        val manualMarkets = listOf(ManualMarketReturn("RTS", listOf(-10.0, -5.0, -2.0, -1.0), 1L))
        val merged = merge(auto = null, manual = null, marketsSnapshot = null, manualMarkets = manualMarkets)

        val rts = merged.markets.first { it.name == "RTS" }
        assertEquals(listOf(-10.0, -5.0, -2.0, -1.0), rts.r)
    }

    @Test
    fun `미커버 해외지수는 MANUAL이 없으면 리포트 기준값(부록C)으로 프리시드된다`() {
        val merged = merge(auto = null, manual = null, marketsSnapshot = null)
        val baselineRts = BearSignalReportBaseline.MARKETS.first { it.name == "RTS" }
        val rts = merged.markets.first { it.name == "RTS" }
        assertEquals(baselineRts.r, rts.r)
    }

    @Test
    fun `AUTO 스냅샷이 있으면 AUTO가 기준값보다 우선한다`() {
        val snapshot = MarketReturnsSnapshot(
            markets = listOf(
                AutoMarketReturn("코스피", listOf(1.0, 2.0, 3.0, 4.0), lead = true, coverage = MarketCoverage.AUTO, updatedAt = 1L)
            )
        )
        val merged = merge(auto = null, manual = null, marketsSnapshot = snapshot)
        val kospi = merged.markets.first { it.name == "코스피" }
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0), kospi.r)
        assertTrue(kospi.lead)
    }

    @Test
    fun `MANUAL은 AUTO보다 우선한다(같은 지수)`() {
        val snapshot = MarketReturnsSnapshot(
            markets = listOf(
                AutoMarketReturn("닛케이", listOf(10.0, 8.0, 5.0, 1.0), lead = false, coverage = MarketCoverage.AUTO, updatedAt = 1L)
            )
        )
        val manualMarkets = listOf(ManualMarketReturn("닛케이", listOf(-1.0, -1.0, -1.0, -1.0), 2L))
        val merged = merge(auto = null, manual = null, marketsSnapshot = snapshot, manualMarkets = manualMarkets)

        val nikkei = merged.markets.first { it.name == "닛케이" }
        assertEquals(listOf(-1.0, -1.0, -1.0, -1.0), nikkei.r)
    }

    @Test
    fun `기간별 부분 오버라이드 - MANUAL의 null 기간은 AUTO 값으로 폴백한다`() {
        val snapshot = MarketReturnsSnapshot(
            markets = listOf(
                AutoMarketReturn("다우", listOf(19.6, 6.5, 12.9, 2.8), lead = false, coverage = MarketCoverage.AUTO, updatedAt = 1L)
            )
        )
        // 1M 기간만 수동으로 정정, 나머지는 null(AUTO 값 유지)
        val manualMarkets = listOf(ManualMarketReturn("다우", listOf(null, null, null, -50.0), 2L))
        val merged = merge(auto = null, manual = null, marketsSnapshot = snapshot, manualMarkets = manualMarkets)

        val dow = merged.markets.first { it.name == "다우" }
        assertEquals(listOf(19.6, 6.5, 12.9, -50.0), dow.r)
    }

    @Test
    fun `국가 수 20개 유지 - 병합 결과가 리포트 기준값 지수 목록을 모두 포함한다`() {
        val merged = merge(auto = null, manual = null, marketsSnapshot = null)
        assertEquals(20, merged.markets.size)
        assertEquals(BearSignalReportBaseline.MARKETS.map { it.name }, merged.markets.map { it.name })
    }

    private fun autoInputs(
        up3: AutoIndicator<Int> = AutoIndicator(14, InputSource.AUTO, 1L),
        down3: AutoIndicator<Int> = AutoIndicator(12, InputSource.AUTO, 1L),
        up4: AutoIndicator<Int> = AutoIndicator(3, InputSource.AUTO, 1L),
        down4: AutoIndicator<Int> = AutoIndicator(2, InputSource.AUTO, 1L),
        kospi2: AutoIndicator<Double> = AutoIndicator(56.0, InputSource.AUTO, 1L),
        semi: AutoIndicator<Double>? = null,
        buffer: AutoIndicator<Boolean>? = null,
        rate: AutoIndicator<Double>? = null,
        dir: AutoIndicator<String>? = null,
        etf: AutoIndicator<String>? = null,
        credit: AutoIndicator<Double>? = null,
        lossRatio: AutoIndicator<Double>? = null,
        bigDeal: AutoIndicator<String>? = null
    ) = AutoBearSignalInputs(up3, down3, up4, down4, kospi2, semi, buffer, rate, dir, etf, credit, lossRatio, bigDeal)
}
