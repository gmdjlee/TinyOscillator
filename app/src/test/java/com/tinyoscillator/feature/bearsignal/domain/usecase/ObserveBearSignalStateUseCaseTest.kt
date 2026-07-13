package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.AutoIndicator
import com.tinyoscillator.feature.bearsignal.domain.model.BearPhase
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalReportBaseline
import com.tinyoscillator.feature.bearsignal.domain.model.BearThresholdsFixture
import com.tinyoscillator.feature.bearsignal.domain.model.InputSource
import com.tinyoscillator.feature.bearsignal.domain.model.ManualBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.repository.BearSignalRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ObserveBearSignalStateUseCase] 화면 조립 상태 스트림 테스트 (TASK_bear_signal_console.md §2, Phase 4).
 *
 * Repository Flow 4종 + 기간 선택 Flow를 합성해 [MergeBearSignalInputsUseCase] → [ComputeBearSignalUseCase]로
 * 이어지는 파이프라인이 골든 케이스를 재현하고, 수동 오버라이드·기간 변경이 즉시 반영되는지 검증한다.
 * §3.0 임계치 외부화(retrofit) 이후 [ComputeBearSignalUseCase]는 `bear_thresholds.json`을 미러링한
 * [BearThresholdsFixture.DEFAULT]로 구성한다.
 */
class ObserveBearSignalStateUseCaseTest {

    private val merge = MergeBearSignalInputsUseCase()
    private val compute = ComputeBearSignalUseCase(BearThresholdsFixture.DEFAULT)

    @Test
    fun `Room 캐시가 전부 비어있으면 골든 케이스 AMBER를 재현한다`() = runTest {
        val repository = mockk<BearSignalRepository>()
        every { repository.observeAutoInputs() } returns flowOf(null)
        every { repository.observeManualInputs() } returns flowOf(ManualBearSignalInputs())
        every { repository.observeMarketReturns() } returns flowOf(null)
        every { repository.observeManualMarketReturns() } returns flowOf(emptyList())

        val useCase = ObserveBearSignalStateUseCase(repository, merge, compute)
        val state = useCase().let { flow ->
            var last: ObserveBearSignalStateUseCase.State? = null
            flow.collect { last = it }
            last!!
        }

        assertEquals(BearSignalReportBaseline.toInputs(), state.inputs)
        assertEquals(BearPhase.AMBER, state.result.phase)
        assertEquals(1, state.result.s1)
        assertEquals(1, state.result.s2)
        assertEquals(1, state.result.s3)
        assertEquals(1, state.result.gate)
        assertEquals(1.30, state.result.amp, 1e-9)
    }

    @Test
    fun `수동 오버라이드가 있으면 즉시 재계산에 반영된다`() = runTest {
        val repository = mockk<BearSignalRepository>()
        every { repository.observeAutoInputs() } returns flowOf(null)
        every { repository.observeManualInputs() } returns flowOf(
            ManualBearSignalInputs(
                loss = AutoIndicator(90.0, InputSource.MANUAL, 1L),
                big = AutoIndicator("failed", InputSource.MANUAL, 1L)
            )
        )
        every { repository.observeMarketReturns() } returns flowOf(null)
        every { repository.observeManualMarketReturns() } returns flowOf(emptyList())

        val useCase = ObserveBearSignalStateUseCase(repository, merge, compute)
        var last: ObserveBearSignalStateUseCase.State? = null
        useCase().collect { last = it }
        val state = last!!

        // scoreS3: loss>=80 -> 3, big=failed -> max(3,3)=3
        assertEquals(3, state.result.s3)
        // lead = s1(1)+s2(1)+s3(3) = 5 < 6, gate = 1(<2) -> ORANGE 조건 미충족 -> AMBER(lead>=3)
        assertEquals(BearPhase.AMBER, state.result.phase)
    }

    @Test
    fun `period 기간 선택이 신호1 판정에 반영된다`() = runTest {
        val repository = mockk<BearSignalRepository>()
        every { repository.observeAutoInputs() } returns flowOf(null)
        every { repository.observeManualInputs() } returns flowOf(ManualBearSignalInputs())
        every { repository.observeMarketReturns() } returns flowOf(null)
        every { repository.observeManualMarketReturns() } returns flowOf(emptyList())

        val useCase = ObserveBearSignalStateUseCase(repository, merge, compute)

        var last: ObserveBearSignalStateUseCase.State? = null
        useCase(flowOf(0)).collect { last = it } // 12M 기간 선택
        val state = last!!

        assertEquals(0, state.inputs.periodIdx)
        // 기준값 markets은 12M 컬럼에서 전부 신규이탈 없음(대부분 양수) — 신호1 결과가 기본(1M) 판정과 다를 수 있음
        val expectedMa = compute.analyzeMarkets(BearSignalReportBaseline.MARKETS, 0)
        assertEquals(expectedMa.neg, state.result.ma.neg)
    }
}
