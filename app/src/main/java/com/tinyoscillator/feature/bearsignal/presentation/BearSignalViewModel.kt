package com.tinyoscillator.feature.bearsignal.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.network.NetworkUtils
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextClaimValidationResult
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextFetchResult
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextSectionKey
import com.tinyoscillator.feature.bearsignal.domain.model.ApprovedAiContext
import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.BearPhase
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalReportBaseline
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalResult
import com.tinyoscillator.feature.bearsignal.domain.model.BearSnapshot
import com.tinyoscillator.feature.bearsignal.domain.model.BearThresholds
import com.tinyoscillator.feature.bearsignal.domain.model.Depth
import com.tinyoscillator.feature.bearsignal.domain.model.ManualBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.ManualFieldUpdate
import com.tinyoscillator.feature.bearsignal.domain.model.ManualMarketReturn
import com.tinyoscillator.feature.bearsignal.domain.model.MarketAnalysis
import com.tinyoscillator.feature.bearsignal.domain.model.MarketReturnsSnapshot
import com.tinyoscillator.feature.bearsignal.domain.model.SnapshotUpdateSuggestion
import com.tinyoscillator.feature.bearsignal.domain.model.Suggestion
import com.tinyoscillator.feature.bearsignal.domain.model.Transition
import com.tinyoscillator.feature.bearsignal.domain.repository.SnapshotRepository
import com.tinyoscillator.feature.bearsignal.domain.usecase.ApplySuggestionUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.ApproveAiContextClaimsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.BuildBearSnapshotUseCase
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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private val DEFAULT_INPUTS: BearSignalInputs = BearSignalReportBaseline.toInputs()

/** §6.1 Sparkline 이력 조회 창(일) — [BearSignalViewModel.historyFrom] KDoc의 "90일 채택 근거" 참조. */
private const val SPARKLINE_WINDOW_DAYS = 90L

/**
 * §5.4 당겨새로고침 신선도 창(ms) — 최근 자동 수집이 이 창 이내면 [BearSignalViewModel.refresh]는
 * 전체 파이프라인(KRX 로그인 2회 + 관세청 XML 3×2.2MB + 해외지수 20종, ~30-60s) 재실행을 건너뛴다.
 * §3 스코어링 임계치와 무관한 순수 UI 성능 파라미터다. 1시간을 택해 back-to-back 당김에 의한 중복
 * 수집을 막되 시간 단위 갱신은 허용한다. 신선도 제안 "수락"([acceptUpdateSuggestion], force)은 우회한다.
 */
private const val REFRESH_FRESHNESS_WINDOW_MS = 60 * 60 * 1000L

/**
 * `stateIn`(WhileSubscribed) 콜드스타트 초기값 — Room 4-Flow 최초 방출 전(구독 시작 전)에만
 * 노출되고, [BearSignalUiState.isLoading]이 true인 이 구간은 화면이 스켈레톤으로 대체 렌더한다
 * (§5.4 shimmer, Phase 5). §3.0 임계치 외부화(retrofit) 이후
 * [com.tinyoscillator.feature.bearsignal.domain.usecase.ComputeBearSignalUseCase]는 생성자에서
 * `BearThresholds`를 요구하며 이는 Hilt가 비동기(에셋 I/O)로 주입하므로, 이 콜드스타트
 * 자리표시자는 재계산 대신 리포트 골든 케이스(2026.6.30)의 **알려진 결과값**을 리터럴로
 * 스냅샷한다 — [BearSignalReportBaseline] KDoc·`ComputeBearSignalUseCaseTest`의 골든 케이스와
 * 완전히 동일한 수치이며, §3 스코어링 로직 자체에는 임계치를 하드코딩하지 않는다.
 */
private val DEFAULT_RESULT: BearSignalResult = BearSignalResult(
    s1 = 1,
    s2 = 1,
    s3 = 1,
    gate = 1,
    amp = 1.30,
    lead = 3,
    leadPct = 33,
    warn = 0,
    phase = BearPhase.AMBER,
    ma = MarketAnalysis(neg = 11, worstNew = -5.1, depth = Depth.SHALLOW)
)

/**
 * BearSignalScreen(Phase 4) 화면 상태 — TASK_bear_signal_console.md §5.2 7섹션 조립에 필요한 모든 값을 담는다.
 *
 * @param periodIdx 현재 선택된 신호1 판정 기간(§5.3 FilterChip) — [inputs.periodIdx]와 항상 동일.
 * @param lastUpdatedAt 자동/수동 전 지표 중 가장 최근 `updatedAt` (§5.2 섹션7 "전체 최신 갱신일").
 * @param errorMessage 최근 갱신 실패 메시지(있으면 Snackbar로 1회성 노출).
 * @param isLoading Room 캐시(자동/수동/국가별 수익률 4-Flow)의 최초 방출 전 상태 — 이 구간만 shimmer
 * 스켈레톤을 노출한다(§5.4 "성능: shimmer 로딩"). `stateIn`의 초기값(`BearSignalUiState()`)에서만
 * `true`이고, [ObserveBearSignalStateUseCase] 결과가 한 번이라도 합성되면 항상 `false`로 고정된다.
 * @param isOffline 네트워크 미연결로 자동 갱신을 시도하지 않고 건너뛴 상태(§5.4 "오프라인 우선 렌더").
 * 캐시 데이터는 이미 [inputs]/[result]에 반영돼 있으므로 화면은 그대로 렌더하고 배너로만 안내한다.
 * @param manyCountriesBreached 신호1 "이탈국 다수" 강조 플래그 — `result.ma.neg >= thresholds.s1.manyCountries`를
 * ViewModel에서 미리 계산해 노출한다(§3.0 retrofit 후속 — Section 컴포저블이 §3 임계치를 직접
 * 하드코딩하지 않도록 `BearThresholds` 주입값으로 구동). `bear_thresholds.json`의 `s1.manyCountries`
 * 교체만으로 이 플래그도 코드 무수정으로 바뀐다.
 * @param deepeningBreached 신호1 "낙폭 심화" 강조 플래그 — `result.ma.worstNew <= thresholds.s1.deepeningPct`.
 * [manyCountriesBreached]와 동일한 이유로 ViewModel에서 사전 계산해 노출한다.
 * @param snapshotHistory §6.1 Sparkline 입력 — [SnapshotRepository.observeRange]("최근 90일"·day
 * 오름차순) 결과를 그대로 노출한다. 비어있음(이력 없음)/1건(단일)/2건 이상(다수) 3종 상태를 UI가
 * 구분해 렌더한다(§6.1 "이력 상태 3종 처리").
 * @param transitions §6.1 TransitionLog 입력 — [snapshotHistory]에서 [DetectTransitionsUseCase]로
 * 파생한 국면·방아쇠 전이 목록.
 * @param updateSuggestion §6.1 "state:latest 로드" 세션 진입 신선도 제안(있으면 배너 노출) —
 * [EvaluateSnapshotFreshnessUseCase] 결과를 그대로 노출한다. **자동 반영 금지**(§7 승인 흐름) —
 * 이 필드가 non-null이어도 [inputs]/[result] 등 다른 필드는 전혀 영향받지 않는다. 사용자가
 * [BearSignalViewModel.acceptUpdateSuggestion]을 명시적으로 호출해야만 [BearSignalViewModel.refresh]가
 * 트리거된다.
 * @param suggestions §4.5 웹/LLM 제안 목록(Phase 4) — [BearSignalViewModel.fetchSuggestions]가
 * 사용자의 명시적 액션("AI 제안 가져오기")으로 호출됐을 때만 채워진다(화면 진입/init 자동 호출
 * 없음). 승인 전에는 이 목록이 존재해도 [inputs]/[result]/Room에 어떤 영향도 없다(§7 승인 흐름).
 * @param suggestionsLoading §4.5 제안 조회 진행 중 여부(로딩 표시용).
 * @param suggestionGroupErrors §4.5 그룹별(rate/dir, bigDeal/lossRatio, credit) 부분 실패 메시지 —
 * 실패한 그룹이 있어도 성공한 그룹의 [suggestions]는 그대로 노출된다(부분 실패 격리).
 * @param suggestionSearchWidgetsHtml §4.5 v1.3 "Gemini 경로" — Google 검색 제안 위젯 HTML 목록
 * (ToS상 사용자 표시 의무). Claude 제공자에서는 항상 비어있다. `SuggestionPanel`이 WebView로 노출한다.
 * @param aiContextApproved §4.7 섹션별 승인 캐시(Room, 화면 진입 시 1회 로드 — 네트워크 아님) —
 * [BearSignalTypesSection]/[com.tinyoscillator.feature.bearsignal.presentation.ui.BearSignalHistorySection]
 * 오버레이 렌더 입력. 캐시 없는 섹션은 맵에서 생략되며 호출측이 정적 fallback으로 대체한다.
 * @param aiContextPending §4.7 "정세 업데이트" 조회 승인 대기 클레임(그룹④⑤⑥ 평탄화) —
 * [com.tinyoscillator.feature.bearsignal.presentation.BearSignalViewModel.fetchAiContextUpdates]가
 * 사용자의 명시적 액션("정세 업데이트" 버튼)으로 호출됐을 때만 채워진다(자동 fetch 금지, §4.5 대원칙
 * 계승). 승인 전에는 이 목록이 존재해도 [aiContextApproved]/Room에 어떤 영향도 없다.
 * @param aiContextProvider 이번 조회에 사용된 제공자("claude"|"gemini") — [aiContextPending] 일괄/개별
 * 승인 시 그대로 전달된다.
 * @param aiContextLoading §4.7 조회 진행 중 여부(로딩 표시용).
 * @param aiContextGroupErrors §4.7 그룹별(monitor/cases/history_current) 부분 실패 메시지.
 * @param aiContextSearchWidgetsHtml §4.7 Gemini 경로 검색 제안 위젯 HTML(§4.5 v1.3 계승).
 */
data class BearSignalUiState(
    val inputs: BearSignalInputs = DEFAULT_INPUTS,
    val result: BearSignalResult = DEFAULT_RESULT,
    val auto: AutoBearSignalInputs? = null,
    val manual: ManualBearSignalInputs = ManualBearSignalInputs(),
    val marketsSnapshot: MarketReturnsSnapshot? = null,
    val manualMarkets: List<ManualMarketReturn> = emptyList(),
    val periodIdx: Int = BearSignalReportBaseline.PERIOD_IDX,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val lastUpdatedAt: Long? = null,
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val manyCountriesBreached: Boolean = false,
    val deepeningBreached: Boolean = false,
    val snapshotHistory: List<BearSnapshot> = emptyList(),
    val transitions: List<Transition> = emptyList(),
    val updateSuggestion: SnapshotUpdateSuggestion? = null,
    val suggestions: List<Suggestion> = emptyList(),
    val suggestionsLoading: Boolean = false,
    val suggestionGroupErrors: List<String> = emptyList(),
    val suggestionSearchWidgetsHtml: List<String> = emptyList(),
    val aiContextApproved: Map<AiContextSectionKey, ApprovedAiContext> = emptyMap(),
    val aiContextPending: List<AiContextClaimValidationResult.Accepted> = emptyList(),
    val aiContextProvider: String? = null,
    val aiContextLoading: Boolean = false,
    val aiContextGroupErrors: List<String> = emptyList(),
    val aiContextSearchWidgetsHtml: List<String> = emptyList()
)

/**
 * §4.5 제안 관련 UI 부분 상태 — [BearSignalViewModel.uiState]의 `combine` 인자 개수를 아끼기 위해
 * (kotlinx.coroutines `combine`의 타입 지정 오버로드는 5개까지 — P7-3에서 [AiContextUiState]가
 * 추가되며 정확히 5개에 도달했다) 하나의 [MutableStateFlow]로 묶는다(기존 [BearSignalViewModel.core]
 * 패턴과 동일한 이유).
 */
private data class SuggestionUiState(
    val suggestions: List<Suggestion> = emptyList(),
    val isLoading: Boolean = false,
    val groupErrors: List<String> = emptyList(),
    val searchWidgetsHtml: List<String> = emptyList()
)

/**
 * §4.7 "정세 업데이트" UI 부분 상태 — [SuggestionUiState]와 동일한 이유로 하나의 [MutableStateFlow]로
 * 묶는다. [approved]는 [suggestions]/[pending]과 달리 조회(fetch) 성패와 무관하게 화면 진입 시
 * 1회 로드되며(Room 읽기, 네트워크 아님) 승인/무시 액션에 의해서만 갱신된다.
 */
private data class AiContextUiState(
    val approved: Map<AiContextSectionKey, ApprovedAiContext> = emptyMap(),
    val pending: List<AiContextClaimValidationResult.Accepted> = emptyList(),
    val provider: String? = null,
    val isLoading: Boolean = false,
    val groupErrors: List<String> = emptyList(),
    val searchWidgetsHtml: List<String> = emptyList()
)

@HiltViewModel
class BearSignalViewModel @Inject constructor(
    private val observeBearSignalStateUseCase: ObserveBearSignalStateUseCase,
    private val refreshAutoInputsUseCase: RefreshAutoInputsUseCase,
    private val refreshExternalAutoInputsUseCase: RefreshExternalAutoInputsUseCase,
    private val refreshMarketReturnsUseCase: RefreshMarketReturnsUseCase,
    private val updateManualInputUseCase: UpdateManualInputUseCase,
    private val resetToReportBaselineUseCase: ResetToReportBaselineUseCase,
    private val snapshotRepository: SnapshotRepository,
    private val buildBearSnapshotUseCase: BuildBearSnapshotUseCase,
    private val evaluateSnapshotFreshnessUseCase: EvaluateSnapshotFreshnessUseCase,
    private val detectTransitionsUseCase: DetectTransitionsUseCase,
    private val thresholds: BearThresholds,
    @ApplicationContext private val context: Context,
    private val fetchSuggestionsUseCase: FetchSuggestionsUseCase,
    private val applySuggestionUseCase: ApplySuggestionUseCase,
    private val fetchAiContextUpdatesUseCase: FetchAiContextUpdatesUseCase,
    private val approveAiContextClaimsUseCase: ApproveAiContextClaimsUseCase,
    private val getApprovedAiContextUseCase: GetApprovedAiContextUseCase
) : ViewModel() {

    private val periodIdx = MutableStateFlow(BearSignalReportBaseline.PERIOD_IDX)
    private val isRefreshing = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val isOffline = MutableStateFlow(false)

    // ── T9(Jade Terminal P3) UI 전용 파생 상태 — 스코어링 무접촉 ──────────────────
    // 순수 표시 계층 상태다. 데이터/스코어링 UseCase를 전혀 호출하지 않으며, [uiState]의
    // combine(5-flow) 구조에도 넣지 않는다(combine 타입 오버로드 한계·churn 방지). 화면이
    // 별도 구독한다.

    /**
     * 폰(COMPACT) 아코디언에서 펼쳐진 세부 섹션 집합 — 기본값 `emptySet()`(전부 접힘)으로 스크롤
     * 길이를 최소화한다. 태블릿 2-pane에서는 사용되지 않는다.
     */
    private val _expandedSections = MutableStateFlow<Set<BearSignalSectionKey>>(emptySet())
    val expandedSections: StateFlow<Set<BearSignalSectionKey>> = _expandedSections.asStateFlow()

    /** 아코디언 섹션 하나를 접기/펼치기 토글한다. */
    fun toggleSection(key: BearSignalSectionKey) {
        _expandedSections.update { if (key in it) it - key else it + key }
    }

    /**
     * 태블릿/폴더블(MEDIUM·EXPANDED) 2-pane에서 우측 상세 페인에 표시할 섹션 — 기본값 [BearSignalSectionKey.TREND].
     * 폰(COMPACT)에서는 사용되지 않는다.
     */
    private val _selectedSection = MutableStateFlow(BearSignalSectionKey.TREND)
    val selectedSection: StateFlow<BearSignalSectionKey> = _selectedSection.asStateFlow()

    /** 2-pane 마스터 목록에서 섹션 하나를 선택한다(우측 상세 전환). */
    fun selectSection(key: BearSignalSectionKey) {
        _selectedSection.value = key
    }

    /**
     * §4.5 제안 상태 — **init에서 채우지 않는다**. 사용자가 [fetchSuggestions]를 명시적으로 호출할
     * 때만 네트워크(설정된 AI 제공자 — Claude 또는 Gemini) 호출이 발생한다(비용 고려, §4.5 "UI/UX 지침").
     */
    private val suggestionState = MutableStateFlow(SuggestionUiState())

    /**
     * §4.7 "정세 업데이트" 상태 — [fetchAiContextUpdates]가 명시적으로 호출될 때만 [AiContextUiState.pending]이
     * 채워진다(자동 fetch 금지, §4.5 대원칙 계승). [AiContextUiState.approved]는 [init]에서 1회
     * Room 캐시를 로드해 채운다(네트워크 호출 아님 — 이미 승인·저장된 콘텐츠를 읽는 것뿐이라
     * §4.7 대원칙과 무관).
     */
    private val aiContextState = MutableStateFlow(AiContextUiState())

    /**
     * §6.1 "state:latest 로드" 신선도 제안 — 세션 진입 시([init] 1회) 평가해 채운다. 이후로는
     * [acceptUpdateSuggestion]이 명시적으로 호출될 때만 null로 지워진다 — 어떤 다른 경로도 이
     * 값을 변경하지 않는다(승인 원칙, §7).
     */
    private val updateSuggestion = MutableStateFlow<SnapshotUpdateSuggestion?>(null)

    /** Room 4-Flow(자동/수동/국면 결과) + refresh 상태를 하나로 합성한 "핵심" 상태 — [uiState] 1단계. */
    private data class CoreState(
        val state: ObserveBearSignalStateUseCase.State,
        val refreshing: Boolean,
        val error: String?,
        val offline: Boolean
    )

    private val core: Flow<CoreState> = combine(
        observeBearSignalStateUseCase(periodIdx),
        isRefreshing,
        errorMessage,
        isOffline
    ) { state, refreshing, error, offline -> CoreState(state, refreshing, error, offline) }

    /**
     * §6.1 Sparkline/TransitionLog 이력 — [SnapshotRepository.observeRange]로 "최근 90일"을 구독한다.
     *
     * **90일 채택 근거**: §6.1 "Sparkline(lead·gate 60~90일)"이 제시한 범위의 상한을 택해 대략
     * 분기(3개월) 단위 추이까지 관찰 가능하게 한다. 조회는 Room PK(`day`) `BETWEEN` 범위 쿼리라
     * 상한을 90일로 잡아도 성능 영향이 미미하다(§6.1 Phase 3.5-1에서 이미 검증된 인덱스 설계).
     * 경계는 ViewModel 생성 시점 1회 계산(세션 내내 고정) — 화면이 여러 날에 걸쳐 열려 있는
     * 경우는 드물고, 열려 있는 동안 자정을 넘겨도 하루 정도의 창 이동 오차는 Sparkline 정확도에
     * 실질적 영향이 없다.
     */
    private val historyFrom: String = LocalDate.now().minusDays(SPARKLINE_WINDOW_DAYS - 1).toString()
    private val historyTo: String = LocalDate.now().toString()
    private val history: Flow<List<BearSnapshot>> = snapshotRepository.observeRange(historyFrom, historyTo)

    val uiState: StateFlow<BearSignalUiState> = combine(
        core, history, updateSuggestion, suggestionState, aiContextState
    ) { c, hist, suggestion, sugState, aiState ->
        val state = c.state
        BearSignalUiState(
            inputs = state.inputs,
            result = state.result,
            auto = state.auto,
            manual = state.manual,
            marketsSnapshot = state.marketsSnapshot,
            manualMarkets = state.manualMarkets,
            periodIdx = state.inputs.periodIdx,
            isRefreshing = c.refreshing,
            errorMessage = c.error,
            lastUpdatedAt = lastUpdatedAt(state),
            // Room 4-Flow가 최소 한 번 합성됐다는 뜻 — 초기값(BearSignalUiState())에서만 true로 남는다.
            isLoading = false,
            isOffline = c.offline,
            manyCountriesBreached = state.result.ma.neg >= thresholds.s1.manyCountries,
            deepeningBreached = state.result.ma.worstNew <= thresholds.s1.deepeningPct,
            snapshotHistory = hist,
            transitions = detectTransitionsUseCase(hist),
            updateSuggestion = suggestion,
            suggestions = sugState.suggestions,
            suggestionsLoading = sugState.isLoading,
            suggestionGroupErrors = sugState.groupErrors,
            suggestionSearchWidgetsHtml = sugState.searchWidgetsHtml,
            aiContextApproved = aiState.approved,
            aiContextPending = aiState.pending,
            aiContextProvider = aiState.provider,
            aiContextLoading = aiState.isLoading,
            aiContextGroupErrors = aiState.groupErrors,
            aiContextSearchWidgetsHtml = aiState.searchWidgetsHtml
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        // 콜드스타트 초기값(DEFAULT_RESULT 사용, 위 KDoc 참조) — 실제 주입된 thresholds로 계산하므로
        // 이 두 플래그만큼은 재하드코딩 없이 항상 최신 config를 반영한다.
        BearSignalUiState(
            manyCountriesBreached = DEFAULT_RESULT.ma.neg >= thresholds.s1.manyCountries,
            deepeningBreached = DEFAULT_RESULT.ma.worstNew <= thresholds.s1.deepeningPct
        )
    )

    init {
        // §6.1 스냅샷 저장 시점 결정 1/2 — "세션 진입 시"(ViewModel 최초 생성 = 화면 최초 진입).
        //
        // 순서가 중요하다: (a) 먼저 이번 세션 시작 전에 저장돼 있던 "이전" 최신 스냅샷을 기준으로
        // 신선도를 평가해 [updateSuggestion]을 채운다 — 이 직후 (b)에서 오늘자 스냅샷을 저장해버리면
        // "최신 스냅샷"이 항상 오늘로 갱신되어 버려 신선도 제안이 절대 뜨지 않게 되므로, 반드시 (a)가
        // (b)보다 먼저 실행돼야 한다. (b) 저장 자체는 [SnapshotRepository.latestOrNull]/[updateSuggestion]을
        // 건드리지 않으므로 이 순서 하나만 지키면 두 관심사가 서로 간섭하지 않는다.
        //
        // uiState(WhileSubscribed)와 별개의 1회성 구독을 쓰는 이유: uiState는 화면이 실제로 구독해야만
        // 상류 Room Flow가 수집되므로(§5.4 성능 최적화, 기존 설계), "세션 진입 시 저장"을 uiState 구독
        // 여부에 의존시키면 화면을 열지 않고 백그라운드에 두는 시나리오에서 저장이 누락될 수 있다.
        // ViewModel 생성 자체를 "세션 진입"으로 보고, 독립적인 1회 구독으로 확정 실행한다.
        viewModelScope.launch {
            updateSuggestion.value = evaluateSnapshotFreshnessUseCase(snapshotRepository.latestOrNull())
            val initialState = observeBearSignalStateUseCase(periodIdx).first()
            saveSnapshot(initialState)
        }

        // §4.7 "정세 업데이트" 승인 캐시 로드 — Room 읽기일 뿐 네트워크 호출이 아니므로 화면 진입 시
        // 1회 수행해도 §4.5/§4.7 "명시 버튼 트리거만 허용(자동 fetch 금지)" 대원칙과 무관하다.
        // [fetchAiContextUpdates]는 이 init에서 절대 호출하지 않는다(대원칙 위반).
        viewModelScope.launch {
            aiContextState.value = aiContextState.value.copy(approved = getApprovedAiContextUseCase())
        }
    }

    /**
     * Pull-to-refresh(§5.4) → [A]/[B] 자동 지표 + 국가별 지수 3계열을 병렬 아님(직렬, 소스 독립) 갱신.
     * 오프라인이면 API 호출을 시도하지 않고 즉시 [isOffline]만 설정한다(§5.4 "오프라인 우선 렌더" —
     * 화면은 이미 Room 캐시로 렌더돼 있으므로 배너로만 안내, 기존 [NetworkUtils] 관례 재사용).
     *
     * §6.1 스냅샷 저장 시점 결정 2/2 — 온라인 분기를 실제로 실행했다면(오프라인 조기 반환이 아니면)
     * 개별 지표 성공/실패와 무관하게 항상 스냅샷을 저장한다. 각 `refresh*UseCase`는 이미 자체적으로
     * 실패 시 이전 캐시 폴백을 구현하므로(Phase 1/2), 이 시점의 병합 상태는 "일부 지표가 갱신되지
     * 않았더라도 항상 유효한 스코어링 결과"다 — "refresh 성공 시"를 "개별 지표 전부 성공"이 아니라
     * "갱신 시도가 실제로 완료됐다(오프라인으로 건너뛰지 않았다)"는 의미로 해석했다.
     */
    fun refresh() = launchRefresh(force = false)

    /**
     * 5-2 성능 게이트를 포함한 실제 갱신 구현.
     *
     * @param force `true`면 신선도 창을 무시하고 항상 파이프라인을 실행한다(신선도 제안 "수락" 경로).
     *              `false`(사용자 당겨새로고침)면 최근 자동 수집이 [REFRESH_FRESHNESS_WINDOW_MS]
     *              이내일 때 네트워크 호출을 건너뛴다.
     */
    private fun launchRefresh(force: Boolean) {
        if (isRefreshing.value) return
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    isOffline.value = true
                    return@launch
                }
                isOffline.value = false

                // 5-2: 신선도 게이트 — 최근 자동 수집이 창 이내면 전체 파이프라인 재실행을 skip.
                if (!force) {
                    val collectedAt = autoInputsCollectedAt(observeBearSignalStateUseCase(periodIdx).first())
                    if (collectedAt != null &&
                        System.currentTimeMillis() - collectedAt < REFRESH_FRESHNESS_WINDOW_MS
                    ) {
                        return@launch
                    }
                }

                val failed = mutableListOf<String>()
                refreshAutoInputsUseCase().onFailure { failed += "자동 지표" }
                refreshExternalAutoInputsUseCase().onFailure { failed += "외부 지표" }
                refreshMarketReturnsUseCase().onFailure { failed += "해외 지수" }
                errorMessage.value = if (failed.isEmpty()) null else "일부 갱신 실패: ${failed.joinToString()}"
                saveSnapshot(observeBearSignalStateUseCase(periodIdx).first())
            } finally {
                isRefreshing.value = false
            }
        }
    }

    /**
     * 5-2 신선도 게이트 기준 시각 — 마지막 자동 수집(핵심 일간 지표 up3/down3/up4/down4/kospi2)의
     * 최신 `updatedAt`(epoch ms). 이 5개는 [RefreshAutoInputsUseCase]가 매 수집마다 함께 채우므로
     * 실제 마지막 네트워크 수집 시각을 대표한다. 자동 지표가 없으면 null(게이트 미적용 → 항상 수집).
     */
    private fun autoInputsCollectedAt(state: ObserveBearSignalStateUseCase.State): Long? {
        val auto = state.auto ?: return null
        return maxOf(
            auto.up3.updatedAt, auto.down3.updatedAt, auto.up4.updatedAt,
            auto.down4.updatedAt, auto.kospi2.updatedAt
        )
    }

    /**
     * §6.1 신선도 제안 배너의 "수락" 액션 — 제안을 승인 없이 자동 반영하지 않는다는 원칙(§7)에 따라,
     * 오직 사용자가 이 함수를 명시적으로 호출했을 때만 [refresh]를 트리거한다. 제안 배너는 즉시
     * 닫는다(재평가는 다음 세션 진입 시).
     */
    fun acceptUpdateSuggestion() {
        updateSuggestion.value = null
        // 신선도 제안 "수락"은 명시적 갱신 의사이므로 신선도 창을 우회한다(force).
        launchRefresh(force = true)
    }

    /**
     * §4.5 "AI 제안 가져오기" 버튼 액션 — **명시적 사용자 액션에서만 호출**(비용이 드는 AI API
     * 호출 — 설정된 제공자가 Claude/Gemini 중 무엇이든 — 이므로 init/화면 진입에서 자동 호출하지
     * 않는다). 조회 자체는 [inputs]/[result]/Room 등 어떤 상태도 바꾸지 않는다 — 오직
     * [suggestionState]만 갱신되며, 실제 반영은 [approveSuggestion]/[approveAllSuggestions]가
     * 명시적으로 호출될 때만 일어난다(§7 승인 흐름).
     */
    fun fetchSuggestions() {
        if (suggestionState.value.isLoading) return
        viewModelScope.launch {
            suggestionState.value = suggestionState.value.copy(isLoading = true, groupErrors = emptyList())
            val current = observeBearSignalStateUseCase(periodIdx).first().inputs
            val result = fetchSuggestionsUseCase(current)
            suggestionState.value = result.fold(
                onSuccess = { fetchResult ->
                    SuggestionUiState(
                        suggestions = fetchResult.all,
                        isLoading = false,
                        groupErrors = fetchResult.failedGroupMessages,
                        searchWidgetsHtml = fetchResult.searchWidgetsHtml
                    )
                },
                onFailure = { e ->
                    SuggestionUiState(
                        suggestions = emptyList(),
                        isLoading = false,
                        groupErrors = listOf(e.message ?: "AI 제안 조회에 실패했습니다")
                    )
                }
            )
        }
    }

    /** 제안 하나를 승인 — 승인된 값만 `source=AUTO`로 반영되고, 승인 즉시 목록에서 제거된다. */
    fun approveSuggestion(suggestion: Suggestion) {
        viewModelScope.launch {
            applySuggestionUseCase(suggestion)
            suggestionState.value = suggestionState.value.copy(
                suggestions = suggestionState.value.suggestions.filterNot { it == suggestion }
            )
        }
    }

    /** 현재 표시된 제안 전체를 일괄 승인한다(§5.2 "일괄 승인"). */
    fun approveAllSuggestions() {
        val toApply = suggestionState.value.suggestions
        if (toApply.isEmpty()) return
        viewModelScope.launch {
            applySuggestionUseCase.applyAll(toApply)
            suggestionState.value = suggestionState.value.copy(suggestions = emptyList())
        }
    }

    /** 제안 하나를 무시(dismiss) — Room에 아무 영향 없이 목록에서만 제거한다. */
    fun dismissSuggestion(suggestion: Suggestion) {
        suggestionState.value = suggestionState.value.copy(
            suggestions = suggestionState.value.suggestions.filterNot { it == suggestion }
        )
    }

    // ── §4.7 "정세 업데이트"(정적 참조 콘텐츠 동적 갱신) ─────────────────────

    /**
     * §4.7 섹션 헤더 "정세 업데이트" 버튼 액션 — **명시적 사용자 액션에서만 호출**(비용이 드는 AI API
     * 호출). init/화면 진입에서 자동 호출하지 않는다(§4.5 대원칙 계승). 조회 자체는 [aiContextState.approved]/Room에
     * 어떤 영향도 주지 않는다 — [aiContextState.pending]만 채워지며, 실제 반영은
     * [approveAiContextClaim]/[approveAllAiContextClaims]가 명시적으로 호출될 때만 일어난다.
     */
    fun fetchAiContextUpdates() {
        if (aiContextState.value.isLoading) return
        viewModelScope.launch {
            aiContextState.value = aiContextState.value.copy(isLoading = true, groupErrors = emptyList())
            val result = fetchAiContextUpdatesUseCase()
            aiContextState.value = result.fold(
                onSuccess = { fetchResult ->
                    aiContextState.value.copy(
                        pending = fetchResult.allPending,
                        provider = firstProvider(fetchResult) ?: aiContextState.value.provider,
                        isLoading = false,
                        groupErrors = fetchResult.failedGroupMessages,
                        searchWidgetsHtml = fetchResult.searchWidgetsHtml
                    )
                },
                onFailure = { e ->
                    aiContextState.value.copy(
                        isLoading = false,
                        groupErrors = listOf(e.message ?: "정세 업데이트 조회에 실패했습니다")
                    )
                }
            )
        }
    }

    /** 그룹④⑤⑥ 중 첫 non-null 제공자("claude"|"gemini") — 한 번의 조회는 항상 단일 제공자를 쓴다. */
    private fun firstProvider(result: AiContextFetchResult): String? =
        result.monitor.provider ?: result.cases.provider ?: result.historyCurrent.provider

    /** 클레임 하나를 승인 — `bear_signal_ai_context`에 반영 후 대기 목록에서 제거하고 승인 캐시를 다시 로드한다. */
    fun approveAiContextClaim(accepted: AiContextClaimValidationResult.Accepted) {
        val provider = aiContextState.value.provider ?: return
        viewModelScope.launch {
            approveAiContextClaimsUseCase(listOf(accepted.claim), provider)
            aiContextState.value = aiContextState.value.copy(
                pending = aiContextState.value.pending.filterNot { it == accepted },
                approved = getApprovedAiContextUseCase()
            )
        }
    }

    /** 현재 대기 중인 클레임 전체를 일괄 승인한다(§4.7 "개별/일괄 적용"). */
    fun approveAllAiContextClaims() {
        val toApply = aiContextState.value.pending
        if (toApply.isEmpty()) return
        val provider = aiContextState.value.provider ?: return
        viewModelScope.launch {
            approveAiContextClaimsUseCase(toApply.map { it.claim }, provider)
            aiContextState.value = aiContextState.value.copy(
                pending = emptyList(),
                approved = getApprovedAiContextUseCase()
            )
        }
    }

    /** 클레임 하나를 무시 — Room에 아무 영향 없이 대기 목록에서만 제거한다. */
    fun dismissAiContextClaim(accepted: AiContextClaimValidationResult.Accepted) {
        aiContextState.value = aiContextState.value.copy(
            pending = aiContextState.value.pending.filterNot { it == accepted }
        )
    }

    /** 현재 대기 중인 클레임 전체를 무시 — Room에 아무 영향 없이 대기 목록만 비운다. */
    fun dismissAllAiContextClaims() {
        aiContextState.value = aiContextState.value.copy(pending = emptyList())
    }

    private suspend fun saveSnapshot(state: ObserveBearSignalStateUseCase.State) {
        val snapshot = buildBearSnapshotUseCase(
            state = state,
            day = LocalDate.now(),
            configBasis = BearSignalReportBaseline.CONFIG_BASIS,
            createdAt = System.currentTimeMillis()
        )
        snapshotRepository.upsertToday(snapshot)
    }

    /** §5.3 FilterChip 기간 선택 — 신호1 판정에 즉시 반영 */
    fun selectPeriod(idx: Int) {
        periodIdx.value = idx
    }

    /** §5.3 국가별 수익률 인라인 편집(수동 갱신) */
    fun updateMarketReturn(name: String, r: List<Double?>) {
        dispatch(ManualFieldUpdate.MarketReturn(name, r))
    }

    fun updateLoss(value: Double) = dispatch(ManualFieldUpdate.Loss(value))
    fun updateBig(value: String) = dispatch(ManualFieldUpdate.Big(value))
    fun updateIssueRatio(value: Double) = dispatch(ManualFieldUpdate.IssueRatio(value))
    fun updateCredit(value: Double) = dispatch(ManualFieldUpdate.Credit(value))
    fun updateMargin(value: Boolean) = dispatch(ManualFieldUpdate.Margin(value))
    fun updateDir(value: String) = dispatch(ManualFieldUpdate.Dir(value))

    /** 리포트 기준값(2026.6.30)으로 리셋 — 수동 오버라이드 삭제(§5.4 "리셋", 부록 B #8) */
    fun reset() {
        viewModelScope.launch { resetToReportBaselineUseCase() }
    }

    fun clearError() {
        errorMessage.value = null
    }

    private fun dispatch(update: ManualFieldUpdate) {
        viewModelScope.launch { updateManualInputUseCase(update) }
    }

    private fun lastUpdatedAt(state: ObserveBearSignalStateUseCase.State): Long? {
        val timestamps = buildList {
            state.auto?.let { auto ->
                add(auto.up3.updatedAt)
                add(auto.down3.updatedAt)
                add(auto.up4.updatedAt)
                add(auto.down4.updatedAt)
                add(auto.kospi2.updatedAt)
                auto.semi?.updatedAt?.let(::add)
                auto.buffer?.updatedAt?.let(::add)
                auto.rate?.updatedAt?.let(::add)
                auto.dir?.updatedAt?.let(::add)
                auto.etf?.updatedAt?.let(::add)
                // Phase 4(§4.5) — 웹/LLM 제안 승인 필드도 "전체 최신 갱신일"에 포함
                auto.credit?.updatedAt?.let(::add)
                auto.lossRatio?.updatedAt?.let(::add)
                auto.bigDeal?.updatedAt?.let(::add)
            }
            state.manual.loss?.updatedAt?.let(::add)
            state.manual.big?.updatedAt?.let(::add)
            state.manual.issueRatio?.updatedAt?.let(::add)
            state.manual.credit?.updatedAt?.let(::add)
            state.manual.margin?.updatedAt?.let(::add)
            state.manual.dir?.updatedAt?.let(::add)
            state.marketsSnapshot?.markets?.forEach { add(it.updatedAt) }
            state.manualMarkets.forEach { add(it.updatedAt) }
        }
        return timestamps.maxOrNull()
    }
}
