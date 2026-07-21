package com.tinyoscillator.feature.bearsignal.presentation

import android.content.Context
import com.tinyoscillator.core.network.NetworkUtils
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextClaim
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextClaimValidationResult
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextFetchResult
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextGroupOutcome
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextSectionKey
import com.tinyoscillator.feature.bearsignal.domain.model.ApprovedAiContext
import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.AutoIndicator
import com.tinyoscillator.feature.bearsignal.domain.model.BearPhase
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalReportBaseline
import com.tinyoscillator.feature.bearsignal.domain.model.BearSnapshot
import com.tinyoscillator.feature.bearsignal.domain.model.BearThresholds
import com.tinyoscillator.feature.bearsignal.domain.model.BearThresholdsFixture
import com.tinyoscillator.feature.bearsignal.domain.model.ClaimType
import com.tinyoscillator.feature.bearsignal.domain.model.InputSource
import com.tinyoscillator.feature.bearsignal.domain.model.ManualBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.ManualFieldUpdate
import com.tinyoscillator.feature.bearsignal.domain.model.PhaseChange
import com.tinyoscillator.feature.bearsignal.domain.model.Suggestion
import com.tinyoscillator.feature.bearsignal.domain.model.SuggestionField
import com.tinyoscillator.feature.bearsignal.domain.model.SuggestionFetchResult
import com.tinyoscillator.feature.bearsignal.domain.model.SuggestionGroupOutcome
import com.tinyoscillator.feature.bearsignal.domain.repository.SnapshotRepository
import com.tinyoscillator.feature.bearsignal.domain.usecase.ApplySuggestionUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.ApproveAiContextClaimsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.BuildBearSnapshotUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.ComputeBearSignalUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.DetectTransitionsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.EvaluateSnapshotFreshnessUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.FetchAiContextUpdatesUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.FetchSuggestionsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.GetApprovedAiContextUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.ObserveBearSignalStateUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshAutoInputsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshExternalAutoInputsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshMarketReturnsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.ResetToReportBaselineUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.UpdateManualInputUseCase
import io.mockk.coEvery
import io.mockk.coJustRun
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
import java.time.LocalDate

/**
 * [BearSignalViewModel] 상태→UI 매핑 테스트 (TASK_bear_signal_console.md §5.2 화면 조립, Phase 4).
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
    private lateinit var snapshotRepository: SnapshotRepository
    private lateinit var context: Context
    private lateinit var fetchSuggestionsUseCase: FetchSuggestionsUseCase
    private lateinit var applySuggestionUseCase: ApplySuggestionUseCase
    private lateinit var fetchAiContextUpdatesUseCase: FetchAiContextUpdatesUseCase
    private lateinit var approveAiContextClaimsUseCase: ApproveAiContextClaimsUseCase
    private lateinit var getApprovedAiContextUseCase: GetApprovedAiContextUseCase

    // §6.1 순수 함수 UseCase 3종 — 결정적이므로 mock 대신 실제 인스턴스를 사용한다(다른 도메인
    // 테스트가 이미 개별적으로 검증했으므로 여기서는 ViewModel의 배선만 확인하면 충분하다).
    private val buildBearSnapshotUseCase = BuildBearSnapshotUseCase()
    private val evaluateSnapshotFreshnessUseCase = EvaluateSnapshotFreshnessUseCase()
    private val detectTransitionsUseCase = DetectTransitionsUseCase()

    /**
     * §3.0 retrofit 후속 — [BearSignalUiState.manyCountriesBreached]/[BearSignalUiState.deepeningBreached]가
     * 하드코딩이 아니라 주입된 [BearThresholds]로 구동됨을 증명하기 위해 테스트별로 교체 가능하게 둔다
     * (기본값은 `bear_thresholds.json` 미러 [BearThresholdsFixture.DEFAULT]).
     */
    private var thresholds: BearThresholds = BearThresholdsFixture.DEFAULT

    private val baselineInputs = BearSignalReportBaseline.toInputs()
    private val baselineResult = ComputeBearSignalUseCase(BearThresholdsFixture.DEFAULT)(baselineInputs)
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
        fetchSuggestionsUseCase = mockk()
        applySuggestionUseCase = mockk(relaxed = true)
        fetchAiContextUpdatesUseCase = mockk()
        approveAiContextClaimsUseCase = mockk(relaxed = true)
        getApprovedAiContextUseCase = mockk()

        // 기본값: 승인 캐시 없음(대부분의 테스트는 §4.7 오버레이 배선 자체를 검증 대상으로 삼지 않음).
        coEvery { getApprovedAiContextUseCase() } returns emptyMap()

        // §6.1 스냅샷 이력 기본값 — 대부분의 테스트는 이력/신선도 배선 자체를 검증 대상으로 삼지
        // 않으므로 "이력 없음 · 제안 없음"을 기본으로 스텁하고, 필요한 테스트에서만 개별 재정의한다.
        snapshotRepository = mockk()
        every { snapshotRepository.observeRange(any(), any()) } returns flowOf(emptyList())
        coEvery { snapshotRepository.latestOrNull() } returns null
        coJustRun { snapshotRepository.upsertToday(any()) }

        // 기본값: 온라인(대부분의 테스트가 refresh() 성공 경로를 가정)
        mockkObject(NetworkUtils)
        every { NetworkUtils.isNetworkAvailable(any()) } returns true
    }

    /** [BearSnapshot] 최소 픽스처 — inputsJson/fieldMetaJson은 이 테스트들의 검증 대상이 아니다. */
    private fun fixtureSnapshot(day: String, phase: BearPhase, gate: Int, lead: Int = 3): BearSnapshot = BearSnapshot(
        day = day,
        phase = phase,
        lead = lead,
        gate = gate,
        s1 = 1,
        s2 = 1,
        s3 = 1,
        amp = 1.0,
        configBasis = "test",
        inputsJson = "{}",
        fieldMetaJson = "{}",
        createdAt = 0L
    )

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
            snapshotRepository,
            buildBearSnapshotUseCase,
            evaluateSnapshotFreshnessUseCase,
            detectTransitionsUseCase,
            thresholds,
            context,
            fetchSuggestionsUseCase,
            applySuggestionUseCase,
            fetchAiContextUpdatesUseCase,
            approveAiContextClaimsUseCase,
            getApprovedAiContextUseCase
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
        // 골든 케이스(ma.neg=11, worstNew=-5.1)를 기본 임계치(manyCountries=7, deepeningPct=-6.0)로
        // 판정: 11>=7 → true, -5.1<=-6.0 → false (§3.0 retrofit 후속, 하드코딩 아님을 아래
        // "config 구동" 테스트에서 별도로 증명한다)
        assertTrue(state.manyCountriesBreached)
        assertTrue(!state.deepeningBreached)
    }

    // ── §3.0 retrofit 후속: presentation 임계치 config 구동 ──────

    @Test
    fun `config 구동 — manyCountries를 20으로 올리면 동일 골든 상태의 manyCountriesBreached가 true에서 false로 바뀐다`() = runTest {
        // 기본 임계치(manyCountries=7)에서는 위 테스트처럼 골든 상태(neg=11)가 true다.
        thresholds = BearThresholdsFixture.DEFAULT.copy(
            s1 = BearThresholdsFixture.DEFAULT.s1.copy(manyCountries = 20)
        )
        val viewModel = createViewModel() // state는 동일한 baselineState(neg=11) 재사용 — 임계치만 교체
        collectEagerly(viewModel)
        advanceUntilIdle()

        assertTrue(!viewModel.uiState.value.manyCountriesBreached)
    }

    @Test
    fun `config 구동 — deepeningPct를 -5로 올리면 동일 골든 상태의 deepeningBreached가 false에서 true로 바뀐다`() = runTest {
        // worstNew=-5.1은 기본 임계치(-6.0) 기준으로는 false이지만, deepeningPct를 -5.0으로 올리면
        // -5.1 <= -5.0이 성립해 true로 뒤집힌다 — UI 플래그가 BearThresholds 주입값을 그대로 따라간다.
        thresholds = BearThresholdsFixture.DEFAULT.copy(
            s1 = BearThresholdsFixture.DEFAULT.s1.copy(deepeningPct = -5.0)
        )
        val viewModel = createViewModel()
        collectEagerly(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.deepeningBreached)
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

    private fun freshAutoAt(updatedAt: Long) = AutoBearSignalInputs(
        up3 = AutoIndicator(14, InputSource.AUTO, updatedAt),
        down3 = AutoIndicator(12, InputSource.AUTO, updatedAt),
        up4 = AutoIndicator(3, InputSource.AUTO, updatedAt),
        down4 = AutoIndicator(2, InputSource.AUTO, updatedAt),
        kospi2 = AutoIndicator(56.0, InputSource.AUTO, updatedAt)
    )

    @Test
    fun `5-2 최근 자동 수집이 신선도 창 이내면 refresh는 파이프라인을 건너뛴다`() = runTest {
        coEvery { refreshAutoInputsUseCase() } returns Result.success(mockk(relaxed = true))
        coEvery { refreshExternalAutoInputsUseCase() } returns Result.success(mockk(relaxed = true))
        coEvery { refreshMarketReturnsUseCase() } returns Result.success(mockk(relaxed = true))

        val viewModel = createViewModel(baselineState.copy(auto = freshAutoAt(System.currentTimeMillis())))
        collectEagerly(viewModel)
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        coVerify(exactly = 0) { refreshAutoInputsUseCase() }
        coVerify(exactly = 0) { refreshExternalAutoInputsUseCase() }
        coVerify(exactly = 0) { refreshMarketReturnsUseCase() }
        assertTrue(!viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `5-2 자동 수집이 신선도 창 밖이면 refresh는 파이프라인을 실행한다`() = runTest {
        coEvery { refreshAutoInputsUseCase() } returns Result.success(mockk(relaxed = true))
        coEvery { refreshExternalAutoInputsUseCase() } returns Result.success(mockk(relaxed = true))
        coEvery { refreshMarketReturnsUseCase() } returns Result.success(mockk(relaxed = true))

        val stale = System.currentTimeMillis() - 2 * 60 * 60 * 1000L // 2시간 전(창 1h 밖)
        val viewModel = createViewModel(baselineState.copy(auto = freshAutoAt(stale)))
        collectEagerly(viewModel)
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        coVerify(exactly = 1) { refreshAutoInputsUseCase() }
    }

    @Test
    fun `5-2 신선도 제안 수락은 신선도 창을 무시하고 강제 실행한다`() = runTest {
        coEvery { refreshAutoInputsUseCase() } returns Result.success(mockk(relaxed = true))
        coEvery { refreshExternalAutoInputsUseCase() } returns Result.success(mockk(relaxed = true))
        coEvery { refreshMarketReturnsUseCase() } returns Result.success(mockk(relaxed = true))

        val viewModel = createViewModel(baselineState.copy(auto = freshAutoAt(System.currentTimeMillis())))
        collectEagerly(viewModel)
        advanceUntilIdle()
        viewModel.acceptUpdateSuggestion()
        advanceUntilIdle()

        coVerify(exactly = 1) { refreshAutoInputsUseCase() }
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
            snapshotRepository,
            buildBearSnapshotUseCase,
            evaluateSnapshotFreshnessUseCase,
            detectTransitionsUseCase,
            thresholds,
            context,
            fetchSuggestionsUseCase,
            applySuggestionUseCase,
            fetchAiContextUpdatesUseCase,
            approveAiContextClaimsUseCase,
            getApprovedAiContextUseCase
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
    fun `reset은 resetToReportBaselineUseCase를 호출한다`() = runTest {
        val viewModel = createViewModel()
        viewModel.reset()
        advanceUntilIdle()

        coVerify(exactly = 1) { resetToReportBaselineUseCase() }
    }

    // ── §6.1 스냅샷 저장 시점(a) ────────────────────────────

    @Test
    fun `상태 최초 로드 시(세션 진입) 오늘자 스냅샷을 저장한다`() = runTest {
        val viewModel = createViewModel()
        collectEagerly(viewModel)
        advanceUntilIdle()

        coVerify(exactly = 1) { snapshotRepository.upsertToday(any()) }
    }

    @Test
    fun `refresh 성공 시 스냅샷을 다시 저장한다`() = runTest {
        coEvery { refreshAutoInputsUseCase() } returns Result.success(mockk(relaxed = true))
        coEvery { refreshExternalAutoInputsUseCase() } returns Result.success(mockk(relaxed = true))
        coEvery { refreshMarketReturnsUseCase() } returns Result.success(mockk(relaxed = true))

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        advanceUntilIdle() // 초기 로드 저장 1회

        viewModel.refresh()
        advanceUntilIdle() // refresh 저장 1회 추가

        coVerify(exactly = 2) { snapshotRepository.upsertToday(any()) }
    }

    @Test
    fun `refresh 일부 지표 실패에도 병합 상태는 유효하므로 스냅샷을 저장한다`() = runTest {
        coEvery { refreshAutoInputsUseCase() } returns Result.failure(RuntimeException("network"))
        coEvery { refreshExternalAutoInputsUseCase() } returns Result.success(mockk(relaxed = true))
        coEvery { refreshMarketReturnsUseCase() } returns Result.success(mockk(relaxed = true))

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        // 초기 로드 1회 + refresh 1회 = 2회 — "refresh 성공"을 "오프라인으로 건너뛰지 않음"으로
        // 해석했으므로 개별 지표 실패와 무관하게 저장된다(ViewModel KDoc "저장 시점 결정 2/2" 참조).
        coVerify(exactly = 2) { snapshotRepository.upsertToday(any()) }
    }

    @Test
    fun `오프라인이면 refresh가 조기 반환되어 스냅샷을 추가로 저장하지 않는다`() = runTest {
        every { NetworkUtils.isNetworkAvailable(any()) } returns false

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        advanceUntilIdle() // 초기 로드 저장 1회(네트워크 상태와 무관)

        viewModel.refresh()
        advanceUntilIdle()

        coVerify(exactly = 1) { snapshotRepository.upsertToday(any()) }
    }

    // ── §6.1 이력 Flow → Sparkline/TransitionLog(b)(d) ──────

    @Test
    fun `이력이 없으면 snapshotHistory와 transitions가 빈 리스트로 노출된다`() = runTest {
        every { snapshotRepository.observeRange(any(), any()) } returns flowOf(emptyList())

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.snapshotHistory.isEmpty())
        assertTrue(state.transitions.isEmpty())
    }

    @Test
    fun `이력이 1건(단일)이면 snapshotHistory 크기가 1이고 transitions는 비어있다`() = runTest {
        val only = fixtureSnapshot("2026-06-30", BearPhase.AMBER, gate = 1)
        every { snapshotRepository.observeRange(any(), any()) } returns flowOf(listOf(only))

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.snapshotHistory.size)
        assertEquals(only, state.snapshotHistory.first())
        assertTrue(state.transitions.isEmpty())
    }

    @Test
    fun `이력이 다수(2건 이상)이면 snapshotHistory와 transitions에 국면·방아쇠 전이가 반영된다`() = runTest {
        val prev = fixtureSnapshot("2026-06-29", BearPhase.GREEN, gate = 0)
        val next = fixtureSnapshot("2026-06-30", BearPhase.AMBER, gate = 1)
        every { snapshotRepository.observeRange(any(), any()) } returns flowOf(listOf(prev, next))

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(prev, next), state.snapshotHistory)
        // GREEN→AMBER 국면 전이 + gate 0→1 상승, 같은 날(2026-06-30)에 2건 동시 발생(§6.1 의사코드)
        assertEquals(2, state.transitions.size)
        assertTrue(state.transitions.any { it.kind is PhaseChange })
    }

    // ── §6.1 신선도 제안(승인 흐름)(c) ───────────────────────

    @Test
    fun `최신 스냅샷이 오늘보다 오래되면 updateSuggestion이 노출된다`() = runTest {
        coEvery { snapshotRepository.latestOrNull() } returns fixtureSnapshot("2020-01-01", BearPhase.GREEN, gate = 0)

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        advanceUntilIdle()

        assertEquals("2020-01-01", viewModel.uiState.value.updateSuggestion?.latestAsOf)
    }

    @Test
    fun `신선도 제안이 있어도 result와 inputs는 자동으로 바뀌지 않는다`() = runTest {
        coEvery { snapshotRepository.latestOrNull() } returns fixtureSnapshot("2020-01-01", BearPhase.GREEN, gate = 0)

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.updateSuggestion != null)
        // 제안이 존재한다는 사실 자체는 Room 병합 상태(baselineState)에 전혀 영향을 주지 않는다.
        assertEquals(baselineResult.phase, state.result.phase)
        assertEquals(baselineInputs, state.inputs)
        // 승인 없이는 refresh 관련 UseCase도 호출되지 않는다(자동 반영 금지, §7).
        coVerify(exactly = 0) { refreshAutoInputsUseCase() }
    }

    @Test
    fun `이력이 없으면(최초 사용) updateSuggestion은 노출되지 않는다`() = runTest {
        coEvery { snapshotRepository.latestOrNull() } returns null

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.updateSuggestion)
    }

    @Test
    fun `acceptUpdateSuggestion 호출 시 제안이 사라지고 refresh가 트리거된다`() = runTest {
        coEvery { snapshotRepository.latestOrNull() } returns fixtureSnapshot("2020-01-01", BearPhase.GREEN, gate = 0)
        coEvery { refreshAutoInputsUseCase() } returns Result.success(mockk(relaxed = true))
        coEvery { refreshExternalAutoInputsUseCase() } returns Result.success(mockk(relaxed = true))
        coEvery { refreshMarketReturnsUseCase() } returns Result.success(mockk(relaxed = true))

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.updateSuggestion != null)

        viewModel.acceptUpdateSuggestion()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.updateSuggestion)
        coVerify(exactly = 1) { refreshAutoInputsUseCase() }
    }

    // ── §4.5 웹/LLM 제안(Phase 4) ─────────────────────────────────────

    private fun fixtureSuggestion(
        field: SuggestionField = SuggestionField.RATE,
        current: String? = "3.75",
        next: String = "4.50",
        stale: Boolean = false
    ) = Suggestion(
        field = field,
        currentValue = current,
        nextValue = next,
        asOf = LocalDate.of(2026, 7, 10),
        origin = "Anthropic web_search",
        stale = stale
    )

    @Test
    fun `init 시점에는 fetchSuggestionsUseCase가 호출되지 않는다(비용 고려, 자동 조회 금지)`() = runTest {
        val viewModel = createViewModel()
        collectEagerly(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
        coVerify(exactly = 0) { fetchSuggestionsUseCase(any()) }
    }

    @Test
    fun `fetchSuggestions 성공 시 제안 목록과 그룹 오류가 uiState에 반영된다`() = runTest {
        val suggestion = fixtureSuggestion()
        coEvery { fetchSuggestionsUseCase(any()) } returns Result.success(
            SuggestionFetchResult(
                rateDir = SuggestionGroupOutcome(listOf(suggestion), null),
                bigDealLossRatio = SuggestionGroupOutcome(emptyList(), "대어 IPO 소화·적자상장비중: 네트워크 오류"),
                credit = SuggestionGroupOutcome(emptyList(), null)
            )
        )

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        viewModel.fetchSuggestions()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(suggestion), state.suggestions)
        assertEquals(1, state.suggestionGroupErrors.size)
        assertTrue(!state.suggestionsLoading)
    }

    @Test
    fun `fetchSuggestions가 result와 inputs를 바꾸지 않는다(승인 전 상태 불변)`() = runTest {
        val suggestion = fixtureSuggestion()
        coEvery { fetchSuggestionsUseCase(any()) } returns Result.success(
            SuggestionFetchResult(
                rateDir = SuggestionGroupOutcome(listOf(suggestion), null),
                bigDealLossRatio = SuggestionGroupOutcome(emptyList(), null),
                credit = SuggestionGroupOutcome(emptyList(), null)
            )
        )

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        viewModel.fetchSuggestions()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(baselineResult.phase, state.result.phase)
        assertEquals(baselineInputs, state.inputs)
        // ApplySuggestionUseCase.invoke는 `now: Long`에 기본값(System.currentTimeMillis())이 있어
        // 정확한 값 매칭이 불가능하므로 두 인자 모두 any()로 검증한다(실제 호출 여부만 확인).
        coVerify(exactly = 0) { applySuggestionUseCase(any(), any()) }
    }

    @Test
    fun `fetchSuggestions 최상위 실패(Claude 키 미설정) 시 groupErrors에 안내 메시지가 노출된다`() = runTest {
        coEvery { fetchSuggestionsUseCase(any()) } returns Result.failure(IllegalStateException("Claude API 키가 설정되지 않았습니다"))

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        viewModel.fetchSuggestions()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.suggestions.isEmpty())
        assertEquals(1, state.suggestionGroupErrors.size)
        assertTrue(!state.suggestionsLoading)
    }

    @Test
    fun `approveSuggestion은 ApplySuggestionUseCase를 호출하고 목록에서 제거한다`() = runTest {
        val suggestion = fixtureSuggestion()
        coEvery { fetchSuggestionsUseCase(any()) } returns Result.success(
            SuggestionFetchResult(
                rateDir = SuggestionGroupOutcome(listOf(suggestion), null),
                bigDealLossRatio = SuggestionGroupOutcome(emptyList(), null),
                credit = SuggestionGroupOutcome(emptyList(), null)
            )
        )

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        viewModel.fetchSuggestions()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.suggestions.size)

        viewModel.approveSuggestion(suggestion)
        advanceUntilIdle()

        // now(2번째 인자)는 기본값(System.currentTimeMillis())이라 any()로 매칭한다.
        coVerify(exactly = 1) { applySuggestionUseCase(suggestion, any()) }
        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
    }

    @Test
    fun `approveAllSuggestions은 모든 제안을 일괄 승인하고 목록을 비운다`() = runTest {
        val s1 = fixtureSuggestion(field = SuggestionField.RATE)
        val s2 = fixtureSuggestion(field = SuggestionField.CREDIT, current = "38.0", next = "50.0")
        coEvery { fetchSuggestionsUseCase(any()) } returns Result.success(
            SuggestionFetchResult(
                rateDir = SuggestionGroupOutcome(listOf(s1), null),
                bigDealLossRatio = SuggestionGroupOutcome(emptyList(), null),
                credit = SuggestionGroupOutcome(listOf(s2), null)
            )
        )

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        viewModel.fetchSuggestions()
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.suggestions.size)

        viewModel.approveAllSuggestions()
        advanceUntilIdle()

        // now(2번째 인자)는 기본값(System.currentTimeMillis())이라 any()로 매칭한다.
        coVerify(exactly = 1) { applySuggestionUseCase.applyAll(listOf(s1, s2), any()) }
        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
    }

    @Test
    fun `dismissSuggestion은 ApplySuggestionUseCase를 호출하지 않고 목록에서만 제거한다`() = runTest {
        val suggestion = fixtureSuggestion()
        coEvery { fetchSuggestionsUseCase(any()) } returns Result.success(
            SuggestionFetchResult(
                rateDir = SuggestionGroupOutcome(listOf(suggestion), null),
                bigDealLossRatio = SuggestionGroupOutcome(emptyList(), null),
                credit = SuggestionGroupOutcome(emptyList(), null)
            )
        )

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        viewModel.fetchSuggestions()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.suggestions.size)

        viewModel.dismissSuggestion(suggestion)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
        coVerify(exactly = 0) { applySuggestionUseCase(any(), any()) }
    }

    // ── §4.7 "정세 업데이트"(정적 참조 콘텐츠 동적 갱신, Phase 7-3) ──────────────

    private fun fixtureAiContextClaim(
        sectionKey: AiContextSectionKey = AiContextSectionKey.TYPE0_MONITOR,
        text: String = "체크리스트 항목"
    ) = AiContextClaim(
        sectionKey = sectionKey,
        text = text,
        type = ClaimType.FACT,
        sourceUrl = "https://example.com/report",
        sourceTitle = "제목",
        sourceDate = LocalDate.of(2026, 7, 17),
        quote = "원문 인용"
    )

    private fun fixtureAccepted(
        sectionKey: AiContextSectionKey = AiContextSectionKey.TYPE0_MONITOR,
        text: String = "체크리스트 항목",
        stale: Boolean = false
    ) = AiContextClaimValidationResult.Accepted(fixtureAiContextClaim(sectionKey, text), stale)

    private fun fixtureApproved(
        text: String = "기존 승인 항목",
        provider: String = "claude"
    ) = ApprovedAiContext(
        claims = listOf(fixtureAiContextClaim(text = text)),
        provider = provider,
        asOf = "2026-07-10",
        approvedAt = 1L
    )

    @Test
    fun `init 시점에는 fetchAiContextUpdatesUseCase가 호출되지 않고 승인 캐시만 로드된다`() = runTest {
        val approved = mapOf(AiContextSectionKey.TYPE0_MONITOR to fixtureApproved())
        coEvery { getApprovedAiContextUseCase() } returns approved

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        advanceUntilIdle()

        assertEquals(approved, viewModel.uiState.value.aiContextApproved)
        assertTrue(viewModel.uiState.value.aiContextPending.isEmpty())
        coVerify(exactly = 0) { fetchAiContextUpdatesUseCase() }
    }

    @Test
    fun `fetchAiContextUpdates 성공 시 대기 클레임 제공자 위젯이 반영되고 Room에는 쓰지 않는다`() = runTest {
        val accepted = fixtureAccepted()
        coEvery { fetchAiContextUpdatesUseCase() } returns Result.success(
            AiContextFetchResult(
                monitor = AiContextGroupOutcome(listOf(accepted), emptyMap(), null, "<div>widget</div>", "gemini"),
                cases = AiContextGroupOutcome(emptyList(), emptyMap(), null, null, "gemini"),
                historyCurrent = AiContextGroupOutcome(emptyList(), emptyMap(), null, null, "gemini")
            )
        )

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        viewModel.fetchAiContextUpdates()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(accepted), state.aiContextPending)
        assertEquals("gemini", state.aiContextProvider)
        assertEquals(listOf("<div>widget</div>"), state.aiContextSearchWidgetsHtml)
        assertTrue(!state.aiContextLoading)
        coVerify(exactly = 0) { approveAiContextClaimsUseCase(any(), any(), any()) }
    }

    @Test
    fun `fetchAiContextUpdates 실패 시 groupErrors에 안내 메시지가 노출된다`() = runTest {
        coEvery { fetchAiContextUpdatesUseCase() } returns Result.failure(IllegalStateException("AI 키가 없습니다"))

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        viewModel.fetchAiContextUpdates()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.aiContextPending.isEmpty())
        assertEquals(1, state.aiContextGroupErrors.size)
        assertTrue(!state.aiContextLoading)
    }

    @Test
    fun `approveAiContextClaim은 승인 유스케이스를 호출하고 대기 목록에서 제거 후 승인 캐시를 재로드한다`() = runTest {
        val accepted = fixtureAccepted()
        coEvery { fetchAiContextUpdatesUseCase() } returns Result.success(
            AiContextFetchResult(
                monitor = AiContextGroupOutcome(listOf(accepted), emptyMap(), null, null, "claude"),
                cases = AiContextGroupOutcome(emptyList(), emptyMap(), null, null, "claude"),
                historyCurrent = AiContextGroupOutcome(emptyList(), emptyMap(), null, null, "claude")
            )
        )
        val reloaded = mapOf(AiContextSectionKey.TYPE0_MONITOR to fixtureApproved())

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        viewModel.fetchAiContextUpdates()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.aiContextPending.size)

        coEvery { getApprovedAiContextUseCase() } returns reloaded
        viewModel.approveAiContextClaim(accepted)
        advanceUntilIdle()

        coVerify(exactly = 1) { approveAiContextClaimsUseCase(listOf(accepted.claim), "claude", any()) }
        assertTrue(viewModel.uiState.value.aiContextPending.isEmpty())
        assertEquals(reloaded, viewModel.uiState.value.aiContextApproved)
    }

    @Test
    fun `approveAllAiContextClaims은 대기 클레임 전체를 일괄 승인하고 목록을 비운다`() = runTest {
        val a1 = fixtureAccepted(sectionKey = AiContextSectionKey.TYPE0_MONITOR, text = "항목1")
        val a2 = fixtureAccepted(sectionKey = AiContextSectionKey.TYPE1_MONITOR, text = "항목2")
        coEvery { fetchAiContextUpdatesUseCase() } returns Result.success(
            AiContextFetchResult(
                monitor = AiContextGroupOutcome(listOf(a1, a2), emptyMap(), null, null, "claude"),
                cases = AiContextGroupOutcome(emptyList(), emptyMap(), null, null, "claude"),
                historyCurrent = AiContextGroupOutcome(emptyList(), emptyMap(), null, null, "claude")
            )
        )

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        viewModel.fetchAiContextUpdates()
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.aiContextPending.size)

        viewModel.approveAllAiContextClaims()
        advanceUntilIdle()

        coVerify(exactly = 1) { approveAiContextClaimsUseCase(listOf(a1.claim, a2.claim), "claude", any()) }
        assertTrue(viewModel.uiState.value.aiContextPending.isEmpty())
    }

    @Test
    fun `dismissAiContextClaim은 승인 유스케이스를 호출하지 않고 대기 목록에서만 제거한다`() = runTest {
        val accepted = fixtureAccepted()
        coEvery { fetchAiContextUpdatesUseCase() } returns Result.success(
            AiContextFetchResult(
                monitor = AiContextGroupOutcome(listOf(accepted), emptyMap(), null, null, "claude"),
                cases = AiContextGroupOutcome(emptyList(), emptyMap(), null, null, "claude"),
                historyCurrent = AiContextGroupOutcome(emptyList(), emptyMap(), null, null, "claude")
            )
        )

        val viewModel = createViewModel()
        collectEagerly(viewModel)
        viewModel.fetchAiContextUpdates()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.aiContextPending.size)

        viewModel.dismissAiContextClaim(accepted)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.aiContextPending.isEmpty())
        coVerify(exactly = 0) { approveAiContextClaimsUseCase(any(), any(), any()) }
    }

    // ── T9(Jade Terminal P3) UI 전용 파생 상태(아코디언/2-pane 선택) ──────────────
    // 스코어링/데이터 UseCase와 무관한 순수 표시 상태 — 어떤 UseCase도 호출하지 않음을 함께 확인한다.

    @Test
    fun `초기 UI 파생 상태는 전부 접힘이고 선택 섹션은 TREND다`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.expandedSections.value.isEmpty())
        assertEquals(BearSignalSectionKey.TREND, viewModel.selectedSection.value)
    }

    @Test
    fun `toggleSection은 펼침·접힘을 왕복 토글한다`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleSection(BearSignalSectionKey.GATE)
        assertTrue(BearSignalSectionKey.GATE in viewModel.expandedSections.value)

        // 다른 섹션을 펼쳐도 기존 펼침은 유지된다(집합 누적)
        viewModel.toggleSection(BearSignalSectionKey.HISTORY)
        assertEquals(
            setOf(BearSignalSectionKey.GATE, BearSignalSectionKey.HISTORY),
            viewModel.expandedSections.value
        )

        // 다시 토글하면 해당 섹션만 접힌다
        viewModel.toggleSection(BearSignalSectionKey.GATE)
        assertEquals(setOf(BearSignalSectionKey.HISTORY), viewModel.expandedSections.value)
    }

    @Test
    fun `selectSection은 2-pane 상세 선택 섹션을 갱신한다`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectSection(BearSignalSectionKey.COUNTRY)
        assertEquals(BearSignalSectionKey.COUNTRY, viewModel.selectedSection.value)

        viewModel.selectSection(BearSignalSectionKey.AI_SUGGEST)
        assertEquals(BearSignalSectionKey.AI_SUGGEST, viewModel.selectedSection.value)
    }

    @Test
    fun `UI 파생 상태 변경은 어떤 데이터·스코어링 UseCase도 호출하지 않는다`() = runTest {
        val viewModel = createViewModel()
        collectEagerly(viewModel)
        advanceUntilIdle()

        viewModel.toggleSection(BearSignalSectionKey.LEADING)
        viewModel.selectSection(BearSignalSectionKey.GATE)
        advanceUntilIdle()

        coVerify(exactly = 0) { refreshAutoInputsUseCase() }
        coVerify(exactly = 0) { refreshExternalAutoInputsUseCase() }
        coVerify(exactly = 0) { refreshMarketReturnsUseCase() }
        coVerify(exactly = 0) { fetchSuggestionsUseCase(any()) }
        coVerify(exactly = 0) { fetchAiContextUpdatesUseCase() }
    }
}
