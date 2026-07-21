package com.tinyoscillator.feature.bearsignal.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.core.ui.composable.StaleBanner
import com.tinyoscillator.feature.bearsignal.domain.model.GateState
import com.tinyoscillator.feature.bearsignal.domain.model.GlobalIndexRegistry
import com.tinyoscillator.feature.bearsignal.presentation.BearSignalSectionKey
import com.tinyoscillator.feature.bearsignal.presentation.BearSignalUiState
import com.tinyoscillator.feature.bearsignal.presentation.BearSignalViewModel
import com.tinyoscillator.feature.bearsignal.presentation.ManualInputViewModel
import com.tinyoscillator.presentation.common.AccordionCard
import com.tinyoscillator.presentation.common.CategoryBadge
import com.tinyoscillator.presentation.common.WindowType
import com.tinyoscillator.presentation.common.skeleton.BearSignalScreenSkeleton
import com.tinyoscillator.ui.theme.LocalExtendedColors

/**
 * BearSignal 메인 화면 — TASK_bear_signal_console.md §5.2 7섹션.
 *
 * **T9(Jade Terminal P3) 반응형 재편**: 화면 폭에 따라 두 레이아웃으로 갈라진다.
 * - **폰(COMPACT)**: 종합 국면 헤더는 항상 표시, 세부 7섹션은 [AccordionCard]로 접어(기본 전부
 *   접힘) 스크롤 길이를 줄인다. Pull-to-refresh 유지.
 * - **태블릿/폴더블(MEDIUM·EXPANDED)**: 좌측(352dp) 요약+세부 목록 / 우측 상세 마스터-디테일.
 *
 * "정세 업데이트"(§4.7)는 유형·역사 두 SectionHeader에 중복돼 있던 것을 TopAppBar 상단 액션
 * 1곳으로 통합했다. 리셋은 폭 확보를 위해 overflow 메뉴로 이동한다.
 *
 * 표시 계층·UI 파생 상태만 다룬다 — 스코어링/임계치/[com.tinyoscillator.feature.bearsignal.domain.usecase.ComputeBearSignalUseCase]
 * 무접촉.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BearSignalScreen(onBack: () -> Unit, windowType: WindowType = WindowType.COMPACT) {
    val viewModel: BearSignalViewModel = hiltViewModel()
    val manualViewModel: ManualInputViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val manualUiState by manualViewModel.uiState.collectAsStateWithLifecycle()
    val expandedSections by viewModel.expandedSections.collectAsStateWithLifecycle()
    val selectedSection by viewModel.selectedSection.collectAsStateWithLifecycle()

    var showManualSheet by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val pullState = rememberPullToRefreshState()

    LaunchedEffect(uiState.isRefreshing) {
        if (!uiState.isRefreshing) pullState.endRefresh()
    }
    if (pullState.isRefreshing) {
        LaunchedEffect(Unit) { viewModel.refresh() }
    }
    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("시장 국면 · 리스크", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    // §4.7 "정세 업데이트" — 유형·역사 SectionHeader 중복을 상단 1곳으로 통합.
                    TextButton(
                        onClick = viewModel::fetchAiContextUpdates,
                        enabled = !uiState.aiContextLoading
                    ) {
                        Text(if (uiState.aiContextLoading) "조회 중…" else "정세 업데이트")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "자동 지표 새로고침")
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "더 보기")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("리포트 기준값 리셋") },
                                onClick = {
                                    showOverflowMenu = false
                                    showResetConfirm = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        // Room 캐시(4-Flow) 최초 방출 전에만 노출되는 shimmer(§5.4 "성능: shimmer 로딩")
        if (uiState.isLoading) {
            BearSignalScreenSkeleton(modifier = Modifier.padding(padding).fillMaxSize())
            return@Scaffold
        }

        val manualRequiredNames = (
            uiState.marketsSnapshot?.manualRequiredNames?.takeIf { it.isNotEmpty() }
                ?: GlobalIndexRegistry.MANUAL_REQUIRED_NAMES
            ).toSet()

        val onManualInputClick = { showManualSheet = true }

        if (windowType == WindowType.COMPACT) {
            BearSignalCompactContent(
                padding = padding,
                pullState = pullState,
                uiState = uiState,
                expandedSections = expandedSections,
                manualRequiredNames = manualRequiredNames,
                viewModel = viewModel,
                onManualInputClick = onManualInputClick
            )
        } else {
            BearSignalTwoPaneContent(
                padding = padding,
                uiState = uiState,
                selectedSection = selectedSection,
                manualRequiredNames = manualRequiredNames,
                viewModel = viewModel,
                onManualInputClick = onManualInputClick
            )
        }
    }

    if (showManualSheet) {
        ManualInputBottomSheet(
            manual = manualUiState.manual,
            onLossChange = manualViewModel::updateLoss,
            onIssueRatioChange = manualViewModel::updateIssueRatio,
            onBigChange = manualViewModel::updateBig,
            onDirChange = manualViewModel::updateDir,
            onCreditChange = manualViewModel::updateCredit,
            onMarginChange = manualViewModel::updateMargin,
            onReset = {
                manualViewModel.reset()
                showManualSheet = false
            },
            onDismiss = { showManualSheet = false }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("리포트 기준값(2026-06-30)으로 리셋") },
            text = {
                Text("모든 수동 입력값이 삭제되고 리포트 기준값으로 되돌아갑니다. 자동 수집 캐시는 유지됩니다.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reset()
                    showResetConfirm = false
                }) { Text("리셋") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("취소") }
            }
        )
    }
}

// ── COMPACT: 요약 항상 + 세부 7섹션 아코디언 ──────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BearSignalCompactContent(
    padding: PaddingValues,
    pullState: PullToRefreshState,
    uiState: BearSignalUiState,
    expandedSections: Set<BearSignalSectionKey>,
    manualRequiredNames: Set<String>,
    viewModel: BearSignalViewModel,
    onManualInputClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .nestedScroll(pullState.nestedScrollConnection)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 오프라인 시 캐시 데이터는 그대로 렌더하고 배너로만 안내(§5.4 "오프라인 우선 렌더")
            if (uiState.isOffline) {
                item(key = "offline_banner") {
                    StaleBanner(
                        message = "오프라인 · 마지막 저장 데이터를 표시 중입니다",
                        onRetry = { viewModel.refresh() }
                    )
                }
            }

            // 종합 국면 헤더 — 항상 표시(요약)
            item(key = "header") {
                BearSignalHeaderSection(result = uiState.result)
            }

            // §6.1 신선도 제안 배너 — 제안이 있을 때만(승인 없이 자동 반영하지 않음, §7)
            uiState.updateSuggestion?.let { suggestion ->
                item(key = "snapshot_update_banner") {
                    SnapshotUpdateSuggestionBanner(
                        suggestion = suggestion,
                        onAccept = viewModel::acceptUpdateSuggestion
                    )
                }
            }

            // §4.7 "정세 업데이트" 승인 미리보기 — 상단 액션 직후 최상위 배치
            if (uiState.shouldShowAiContextPanel()) {
                item(key = "ai_context_panel") {
                    AiContextUpdatePanelBound(uiState = uiState, viewModel = viewModel)
                }
            }

            // 세부 7섹션 — 각 아코디언 카드 하나(기본 전부 접힘)
            items(BearSignalSectionKey.entries, key = { it.name }) { key ->
                AccordionCard(
                    title = key.title,
                    subtitle = key.subtitle,
                    expanded = key in expandedSections,
                    onToggle = { viewModel.toggleSection(key) }
                ) {
                    BearSignalSectionContent(
                        section = key,
                        uiState = uiState,
                        manualRequiredNames = manualRequiredNames,
                        viewModel = viewModel,
                        onManualInputClick = onManualInputClick
                    )
                }
            }

            item(key = "footer") {
                BearSignalFooterSection(lastUpdatedAt = uiState.lastUpdatedAt)
            }
        }

        PullToRefreshContainer(
            state = pullState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

// ── MEDIUM·EXPANDED: 좌측 마스터(352dp) + 우측 상세 ────────────────────────

@Composable
private fun BearSignalTwoPaneContent(
    padding: PaddingValues,
    uiState: BearSignalUiState,
    selectedSection: BearSignalSectionKey,
    manualRequiredNames: Set<String>,
    viewModel: BearSignalViewModel,
    onManualInputClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
    ) {
        // 마스터: 요약 + 세부 목록 (2-pane에서는 pull-to-refresh 미적용 — 상단 Refresh 버튼 사용)
        Column(
            modifier = Modifier
                .width(352.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.isOffline) {
                StaleBanner(
                    message = "오프라인 · 마지막 저장 데이터를 표시 중입니다",
                    onRetry = { viewModel.refresh() }
                )
            }
            BearSignalHeaderSection(result = uiState.result)
            uiState.updateSuggestion?.let { suggestion ->
                SnapshotUpdateSuggestionBanner(
                    suggestion = suggestion,
                    onAccept = viewModel::acceptUpdateSuggestion
                )
            }
            if (uiState.shouldShowAiContextPanel()) {
                AiContextUpdatePanelBound(uiState = uiState, viewModel = viewModel)
            }
            Text(
                text = "세부 진단",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            BearSignalSectionKey.entries.forEach { key ->
                BearSignalSectionListItem(
                    section = key,
                    selected = key == selectedSection,
                    uiState = uiState,
                    onClick = { viewModel.selectSection(key) }
                )
            }
            BearSignalFooterSection(lastUpdatedAt = uiState.lastUpdatedAt)
        }

        VerticalDivider()

        // 디테일: 선택 섹션 상세
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            AnimatedContent(
                targetState = selectedSection,
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
                label = "bearDetailPane"
            ) { section ->
                Column {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = section.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    BearSignalSectionContent(
                        section = section,
                        uiState = uiState,
                        manualRequiredNames = manualRequiredNames,
                        viewModel = viewModel,
                        onManualInputClick = onManualInputClick
                    )
                }
            }
        }
    }
}

/** 2-pane 마스터 목록의 한 행 — 상태 도트 + 제목/부제 + (해당 시) 레벨 칩. */
@Composable
private fun BearSignalSectionListItem(
    section: BearSignalSectionKey,
    selected: Boolean,
    uiState: BearSignalUiState,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val (dotColor, chipText, chipColor) = sectionLevelIndicator(section, uiState)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(1.dp, primary.copy(alpha = 0.35f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = section.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (chipText != null && chipColor != null) {
                CategoryBadge(text = chipText, color = chipColor)
            }
        }
    }
}

/**
 * 2-pane 목록 행의 상태 도트·레벨 칩 매핑 — **uiState에 이미 계산돼 있는 표시 필드만 사용**하고
 * 어떤 임계치도 재계산하지 않는다(§3 스코어링 무접촉). 도트는 색만으로 구분하지 않도록 칩(텍스트)을
 * 함께 노출한다(§5.4 접근성).
 *
 * @return (도트 색, 칩 텍스트 or null, 칩 색 or null)
 */
@Composable
private fun sectionLevelIndicator(
    section: BearSignalSectionKey,
    uiState: BearSignalUiState
): Triple<Color, String?, Color?> {
    val outline = MaterialTheme.colorScheme.outline
    return when (section) {
        BearSignalSectionKey.LEADING -> {
            val warn = uiState.result.warn
            val (label, color) = when {
                warn >= 2 -> "경고" to MaterialTheme.colorScheme.error
                warn == 1 -> "주의" to MaterialTheme.colorScheme.secondary
                else -> "안정" to MaterialTheme.colorScheme.primary
            }
            Triple(color, label, color)
        }
        BearSignalSectionKey.COUNTRY -> {
            if (uiState.manyCountriesBreached) {
                val color = LocalExtendedColors.current.warn
                Triple(color, "경고", color)
            } else {
                Triple(outline, null, null)
            }
        }
        BearSignalSectionKey.GATE -> {
            val color = levelColor(uiState.result.gate)
            Triple(color, GateState.entries[uiState.result.gate].label, color)
        }
        else -> Triple(outline, null, null)
    }
}

// ── 섹션 콘텐츠 공용 렌더러 (아코디언 본문 · 2-pane 상세가 공유) ────────────────

/**
 * [BearSignalSectionKey] → 실제 섹션 컴포저블 매핑 — COMPACT 아코디언 본문과 2-pane 상세 페인이
 * 이 한 곳에서 배선을 공유한다(중복 방지). 각 섹션 컴포저블의 인자는 기존 화면과 동일하다.
 */
@Composable
private fun BearSignalSectionContent(
    section: BearSignalSectionKey,
    uiState: BearSignalUiState,
    manualRequiredNames: Set<String>,
    viewModel: BearSignalViewModel,
    onManualInputClick: () -> Unit
) {
    when (section) {
        BearSignalSectionKey.TREND -> BearSignalSparklineSection(
            history = uiState.snapshotHistory,
            transitions = uiState.transitions
        )
        BearSignalSectionKey.LEADING -> BearSignalLeadingSignalsSection(
            inputs = uiState.inputs,
            result = uiState.result,
            auto = uiState.auto,
            manyCountriesBreached = uiState.manyCountriesBreached,
            deepeningBreached = uiState.deepeningBreached,
            onManualInputClick = onManualInputClick
        )
        BearSignalSectionKey.COUNTRY -> BearSignalCountryTableSection(
            inputs = uiState.inputs,
            result = uiState.result,
            manualRequiredNames = manualRequiredNames,
            manyCountriesBreached = uiState.manyCountriesBreached,
            onPeriodSelected = viewModel::selectPeriod,
            onEditMarket = viewModel::updateMarketReturn
        )
        BearSignalSectionKey.GATE -> BearSignalGateAmpSection(
            inputs = uiState.inputs,
            result = uiState.result,
            auto = uiState.auto,
            onManualInputClick = onManualInputClick
        )
        BearSignalSectionKey.AI_SUGGEST -> SuggestionPanel(
            suggestions = uiState.suggestions,
            isLoading = uiState.suggestionsLoading,
            groupErrors = uiState.suggestionGroupErrors,
            onFetch = viewModel::fetchSuggestions,
            onApprove = viewModel::approveSuggestion,
            onApproveAll = viewModel::approveAllSuggestions,
            onDismiss = viewModel::dismissSuggestion,
            searchWidgetsHtml = uiState.suggestionSearchWidgetsHtml
        )
        BearSignalSectionKey.TYPES -> BearSignalTypesSection(
            gate = uiState.result.gate,
            approved = uiState.aiContextApproved
        )
        BearSignalSectionKey.HISTORY -> BearSignalHistorySection(
            approved = uiState.aiContextApproved
        )
    }
}

/**
 * §4.7 "정세 업데이트" 패널 렌더 여부 — 대기 클레임/로딩/오류/검색 위젯/조회 완료 이력
 * 중 하나라도 있을 때만 노출한다(패널이 항상 떠 있으면 레이아웃이 불필요하게 길어짐).
 * [BearSignalUiState.aiContextHasFetched]를 포함해, 조회는 성공했지만 새 클레임이 0건인 경우에도
 * 패널이 무피드백으로 사라지지 않고 "새 업데이트 없음"을 보여주도록 한다(Phase 6-3).
 * COMPACT/2-pane 두 레이아웃이 동일 조건을 공유하도록 확장 함수로 둔다.
 */
private fun BearSignalUiState.shouldShowAiContextPanel(): Boolean =
    aiContextPending.isNotEmpty() ||
        aiContextLoading ||
        aiContextGroupErrors.isNotEmpty() ||
        aiContextSearchWidgetsHtml.isNotEmpty() ||
        aiContextHasFetched

/** [AiContextUpdatePanel]을 uiState/viewModel에 바인딩한 공용 래퍼(COMPACT·2-pane 공유). */
@Composable
private fun AiContextUpdatePanelBound(uiState: BearSignalUiState, viewModel: BearSignalViewModel) {
    AiContextUpdatePanel(
        pending = uiState.aiContextPending,
        provider = uiState.aiContextProvider,
        isLoading = uiState.aiContextLoading,
        hasFetched = uiState.aiContextHasFetched,
        groupErrors = uiState.aiContextGroupErrors,
        searchWidgetsHtml = uiState.aiContextSearchWidgetsHtml,
        onApprove = viewModel::approveAiContextClaim,
        onApproveAll = viewModel::approveAllAiContextClaims,
        onDismissAll = viewModel::dismissAllAiContextClaims,
        onDismiss = viewModel::dismissAiContextClaim
    )
}
