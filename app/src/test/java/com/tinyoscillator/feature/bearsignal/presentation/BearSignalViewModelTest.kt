package com.tinyoscillator.feature.bearsignal.presentation

import android.content.Context
import com.tinyoscillator.core.network.NetworkUtils
import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.AutoIndicator
import com.tinyoscillator.feature.bearsignal.domain.model.BearPhase
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalReportBaseline
import com.tinyoscillator.feature.bearsignal.domain.model.InputSource
import com.tinyoscillator.feature.bearsignal.domain.model.ManualBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.ManualFieldUpdate
import com.tinyoscillator.feature.bearsignal.domain.usecase.ComputeBearSignalUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.ObserveBearSignalStateUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshAutoInputsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshExternalAutoInputsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshMarketReturnsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.ResetToReportBaselineUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.UpdateManualInputUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [BearSignalViewModel] 상태→UI 매핑 테스트 (TASK.md §5.2 화면 조립, Phase 4).
 *
 * 스코어링 자체는 [com.tinyoscillator.feature.bearsignal.domain.usecase.ComputeBearSignalUseCaseTest]
 * 등 도메인 테스트가 이미 커버하므로, 여기서는 ViewModel의 상태 합성(refresh/reset/기간 선택/수동
 * 입력 위임/lastUpdatedAt 계산)만 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BearSignalViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var observeBearSignalStateUseCase: ObserveBearSignalStateUseCase
    private lateinit var refreshAutoInputsUseCase: RefreshAutoInputsUseCase
    private lateinit var refreshExternalAutoInputsUseCase: RefreshExternalAutoInputsUseCase
    private lateinit var refreshMarketReturnsUseCase: RefreshMarketReturnsUseCase
    private lateinit var updateManualInputUseCase: UpdateManualInputUseCase
    private lateinit var resetToReportBaselineUseCase: ResetToReportBaselineUseCase
    private lateinit var context: Context

    private val baselineInputs = BearSignalReportBaseline.toInputs()
    private val baselineResult = ComputeBearSignalUseCase()(baselineInputs)
    private val baselineState = ObserveBearSignalStateUseCase.State(
        inputs = baselineInputs,
        result = baselineResult,
        auto = null,
        manual = ManualBearSignalInputs(),
        marketsSnapshot = null,
        manualMarkets = emptyList()
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        observeBearSignalStateUseCase = mockk()
        refreshAutoInputsUseCase = mockk()
        refreshExternalAutoInputsUseCase = mockk()
        refreshMarketReturnsUseCase = mockk()
        updateManualInputUseCase = mockk(relaxed = true)
        resetToReportBaselineUseCase = mockk(relaxed = true)
        context = mockk(relaxed = true)

        // 기본값: 온라인(대부분의 테스트가 refresh() 성공 경로를 가정)
        mockkObject(NetworkUtils)
        every { NetworkUtils.isNetworkAvailable(any()) } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(state: ObserveBearSignalStateUseCase.State = baselineState): BearSignalViewModel {
        every { observeBearSignalStateUseCase(any<Flow<Int>>()) } returns flowOf(state)
        return BearSignalViewModel(
            observeBearSignalStateUseCase,
            refreshAutoInputsUseCase,
            refreshExternalAutoInputsUseCase,
            refreshMarketReturnsUseCase,
            updateManualInputUseCase,
            resetToReportBaselineUseCase,
            context
        )
    }

    /**
     * `uiState`는 `combine(...).stateIn(SharingStarted.WhileSubscribed(5_000))`로 노출되므로,
     * 활성 구독자가 없으면 상류 Flow가 아예 수집되지 않는다(`.value`가 초기 기본값에 고정).
     * `backgroundScope`로 구독을 유지해 테스트에서도 실제 화면과 동일하게 값이 갱신되게 한다.
     */
    private fun TestScope.collectEagerly(viewModel: BearSignalViewModel) {
        backgroundScope.launch { viewModel.uiState.collect() }
    }

    // ── 초기/합성 상태 ────────────────────────────────────

    @Test
    fun `초기 uiState 기본값은 리포트 기준값 골든 케이스(AMBER)다`() = runTest {
        // stateIn(WhileSubscribed) 초기값 자체(구독 시작 전)를 검증 — DEFAULT_INPUTS/DEFAULT_RESULT
        val viewModel = createViewModel()

        val initial = viewModel.uiState.value
        assertEquals(BearPhase.AMBER, initial.result.phase)
        assertEquals(BearSignalReportBaseline.PERIOD_IDX, initial.periodIdx)
        assertTrue(!initial.isRefreshing)
        assertNull(initial.errorMessage)
        // Room 4-Flow 최초 방출 전(구독 시작 전)이므로 shimmer 노출 상태여야 한다(Phase 5 §5.4)
        assertTrue(initial.isLoading)
        assertTrue(!initial.isOffline)
    }

    @Test
    fun `Room 상태가 방출되면 uiState에 반영된다`() = runTest {
        val viewModel = createViewModel()
        collectEagerly(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(baselineResult.phase, state.result.phase)
        assertEquals(baselineInputs, state.inputs)
        // Room 4-Flow가 최소 한 번 합성됐으므로 isLoading은 false로 전환된다
        assertTrue(!state.isLoading)
    }

    @Test
    fun `lastUpdatedAt은 자동 수동 지표 중 가장 최근 updatedAt이다`() = runTest {
        val auto = AutoBearSignalInputs(
            up3 = AutoIndicator(14, InputSource.AUTO, 100L),
            down3 = AutoIndicator(12, InputSource.AUTO, 100L),
            up4 = AutoIndicator(3, InputSource.AUTO, 100L),
            down4 = AutoIndicator(2, InputSource.AUTO, 100L),
            kospi2 = AutoIndicator(56.0, InputSource.AUTO, 500L)
        )
        val manual = ManualBearSignalInputs(loss = AutoIndicator(50.0, InputSource.MANUAL, 900L))
        val state = baselineState.copy(auto = auto, manual = manual)

        val viewModel = createViewModel(state)
        collectEagerly(viewModel)
        advanceUntilIdle()

        assertEquals(900L, viewModel.uiState.value.lastUpdatedAt)
    }

    // ── refresh() ────────────────────────────────────────

    @Test
    fun `refresh 성공 시 isRefreshing이 false로 돌아오고 에러가 없다`() = runTest {
        coEvery { refreshAutoInputsUseCase() } returns Result.success(mockk(relaxed = true))
        coEvery { refreshExternalAutoInputsUseCase() } returns Result.success(mockk(relaxed = true))
        coEvery { refreshMarketReturnsUseCase() } returns Result.success(mockk(relaxed = true))

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(!viewModel.uiState.value.isRefreshing)
        assertNull(viewModel.uiState.value.errorMessage)
        assertTrue(!viewModel.uiState.value.isOffline)
        coVerify(exactly = 1) { refreshAutoInputsUseCase() }
        coVerify(exactly = 1) { refreshExternalAutoInputsUseCase() }
        coVerify(exactly = 1) { refreshMarketReturnsUseCase() }
    }

    // ── 오프라인 폴백(§5.4) ────────────────────────────────

    @Test
    fun `오프라인이면 자동 지표 UseCase를 호출하지 않고 isOffline이 true가 된다`() = runTest {
        every { NetworkUtils.isNetworkAvailable(any()) } returns false

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isOffline)
        assertTrue(!viewModel.uiState.value.isRefreshing)
        coVerify(exactly = 0) { refreshAutoInputsUseCase() }
        coVerify(exactly = 0) { refreshExternalAutoInputsUseCase() }
        coVerify(exactly = 0) { refreshMarketReturnsUseCase() }
    }

    @Test
    fun `오프라인 상태에서도 캐시 데이터(inputs result)는 그대로 노출된다`() = runTest {
        every { NetworkUtils.isNetworkAvailable(any()) } returns false

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isOffline)
        assertEquals(baselineResult.phase, state.result.phase)
        assertEquals(baselineInputs, state.inputs)
    }

    @Test
    fun `네트워크 복구 후 refresh 하면 isOffline이 다시 false가 된다`() = runTest {
        coEvery { refreshAutoInputsUseCase() } returns Result.success(mockk(relaxed = true))
        coEvery { refreshExternalAutoInputsUseCase() } returns Result.success(mockk(relaxed = true))
        coEvery { refreshMarketReturnsUseCase() } returns Result.success(mockk(relaxed = true))

        every { NetworkUtils.isNetworkAvailable(any()) } returns false
        val viewModel = createViewModel()
        collectEagerly(viewModel)
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isOffline)

        every { NetworkUtils.isNetworkAvailable(any()) } returns true
        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(!viewModel.uiState.value.isOffline)
        coVerify(exactly = 1) { refreshAutoInputsUseCase() }
    }

    @Test
    fun `refresh 일부 실패 시 errorMessage가 설정된다`() = runTest {
        coEvery { refreshAutoInputsUseCase() } returns Result.failure(RuntimeException("network"))
        coEvery { refreshExternalAutoInputsUseCase() } returns Result.success(mockk(relaxed = true))
        coEvery { refreshMarketReturnsUseCase() } returns Result.success(mockk(relaxed = true))

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        viewModel.refresh()
        advanceUntilIdle()

        val message = viewModel.uiState.value.errorMessage
        assertTrue(message != null && message.contains("자동 지표"))
    }

    @Test
    fun `clearError 호출 시 errorMessage가 사라진다`() = runTest {
        coEvery { refreshAutoInputsUseCase() } returns Result.failure(RuntimeException("x"))
        coEvery { refreshExternalAutoInputsUseCase() } returns Result.success(mockk(relaxed = true))
        coEvery { refreshMarketReturnsUseCase() } returns Result.success(mockk(relaxed = true))

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.errorMessage != null)

        viewModel.clearError()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // ── selectPeriod() ────────────────────────────────────

    @Test
    fun `selectPeriod 호출 시 내부 기간 Flow가 갱신된다`() = runTest {
        val slot = slot<Flow<Int>>()
        every { observeBearSignalStateUseCase(capture(slot)) } returns flowOf(baselineState)

        val viewModel = BearSignalViewModel(
            observeBearSignalStateUseCase,
            refreshAutoInputsUseCase,
            refreshExternalAutoInputsUseCase,
            refreshMarketReturnsUseCase,
            updateManualInputUseCase,
            resetToReportBaselineUseCase,
            context
        )
        advanceUntilIdle()

        viewModel.selectPeriod(0)
        advanceUntilIdle()

        assertEquals(0, slot.captured.first())
    }

    // ── 수동 입력 위임 ─────────────────────────────────────

    @Test
    fun `updateMarketReturn은 MarketReturn 업데이트를 위임한다`() = runTest {
        val viewModel = createViewModel()
        viewModel.updateMarketReturn("RTS", listOf(-10.0, -5.0, -2.0, -1.0))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            updateManualInputUseCase(ManualFieldUpdate.MarketReturn("RTS", listOf(-10.0, -5.0, -2.0, -1.0)))
        }
    }

    @Test
    fun `updateLoss는 Loss 업데이트를 위임한다`() = runTest {
        val viewModel = createViewModel()
        viewModel.updateLoss(72.0)
        advanceUntilIdle()

        coVerify(exactly = 1) { updateManualInputUseCase(ManualFieldUpdate.Loss(72.0)) }
    }

    @Test
    fun `reset은 resetToReportBaselineUseCase를 호출한다`() = runTest {
        val viewModel = createViewModel()
        viewModel.reset()
        advanceUntilIdle()

        coVerify(exactly = 1) { resetToReportBaselineUseCase() }
    }
}
