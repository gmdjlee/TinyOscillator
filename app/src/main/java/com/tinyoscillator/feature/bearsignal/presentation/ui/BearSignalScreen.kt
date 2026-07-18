package com.tinyoscillator.feature.bearsignal.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.core.ui.composable.StaleBanner
import com.tinyoscillator.feature.bearsignal.domain.model.GlobalIndexRegistry
import com.tinyoscillator.feature.bearsignal.presentation.BearSignalViewModel
import com.tinyoscillator.feature.bearsignal.presentation.ManualInputViewModel
import com.tinyoscillator.presentation.common.SectionHeader
import com.tinyoscillator.presentation.common.skeleton.BearSignalScreenSkeleton

/**
 * BearSignal 메인 화면(Phase 4) — TASK_bear_signal_console.md §5.2 7섹션을 `LazyColumn`으로 조립한다.
 *
 * 1. 종합 국면 헤더 · 2. 선행 신호 3 카드 · 3. 국가별 수익률 표(전치) · 4. 방아쇠·증폭 카드 ·
 * 5. 약세장 3유형 · 6. 역사 검증 · 7. 지표 매핑·면책·전체 최신 갱신일.
 *
 * Pull-to-refresh([A]/[B] 자동 수집), 리포트 기준값 리셋(헤더 액션, 부록 B #8), 수동 입력
 * BottomSheet(Phase 3 [ManualInputBottomSheet] 재사용)를 연결한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BearSignalScreen(onBack: () -> Unit) {
    val viewModel: BearSignalViewModel = hiltViewModel()
    val manualViewModel: ManualInputViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val manualUiState by manualViewModel.uiState.collectAsStateWithLifecycle()

    var showManualSheet by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
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
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "자동 지표 새로고침")
                    }
                    IconButton(onClick = { showResetConfirm = true }) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "리포트 기준값(2026-06-30) 리셋")
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
        // Room 캐시(4-Flow) 최초 방출 전에만 노출되는 shimmer(§5.4 "성능: shimmer 로딩, 기존 패턴")
        if (uiState.isLoading) {
            BearSignalScreenSkeleton(modifier = Modifier.padding(padding).fillMaxSize())
            return@Scaffold
        }

        val manualRequiredNames = (
            uiState.marketsSnapshot?.manualRequiredNames?.takeIf { it.isNotEmpty() }
                ?: GlobalIndexRegistry.MANUAL_REQUIRED_NAMES
            ).toSet()

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .nestedScroll(pullState.nestedScrollConnection)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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

                item(key = "header") {
                    BearSignalHeaderSection(result = uiState.result)
                }

                // §5.2-1 "헤더 바로 아래 Sparkline+TransitionLog 배치"(§6.1) — 신선도 제안 배너는
                // 제안이 있을 때만(승인 없이 자동 반영하지 않음, §7) 노출한다.
                uiState.updateSuggestion?.let { suggestion ->
                    item(key = "snapshot_update_banner") {
                        SnapshotUpdateSuggestionBanner(
                            suggestion = suggestion,
                            onAccept = viewModel::acceptUpdateSuggestion
                        )
                    }
                }
                item(key = "sparkline_title") {
                    SectionHeader(title = "국면 추이", subtitle = "스코어 이력과 전이 로그")
                }
                item(key = "sparkline") {
                    BearSignalSparklineSection(
                        history = uiState.snapshotHistory,
                        transitions = uiState.transitions
                    )
                }

                item(key = "leading_title") {
                    SectionHeader(title = "선행 신호", subtitle = "위험선호가 어디까지 식었나 · 온도계 3종")
                }
                item(key = "leading_cards") {
                    BearSignalLeadingSignalsSection(
                        inputs = uiState.inputs,
                        result = uiState.result,
                        auto = uiState.auto,
                        manyCountriesBreached = uiState.manyCountriesBreached,
                        deepeningBreached = uiState.deepeningBreached,
                        onManualInputClick = { showManualSheet = true }
                    )
                }

                item(key = "country_table") {
                    BearSignalCountryTableSection(
                        inputs = uiState.inputs,
                        result = uiState.result,
                        manualRequiredNames = manualRequiredNames,
                        manyCountriesBreached = uiState.manyCountriesBreached,
                        onPeriodSelected = viewModel::selectPeriod,
                        onEditMarket = viewModel::updateMarketReturn
                    )
                }

                item(key = "gate_amp_title") {
                    SectionHeader(title = "방아쇠 · 증폭", subtitle = "금리(결정타)와 집중(증폭 계수)")
                }
                item(key = "gate_amp") {
                    BearSignalGateAmpSection(
                        inputs = uiState.inputs,
                        result = uiState.result,
                        auto = uiState.auto,
                        onManualInputClick = { showManualSheet = true }
                    )
                }

                item(key = "suggestion_panel_title") {
                    SectionHeader(title = "AI 제안", subtitle = "웹/LLM 데이터 갱신 · 승인 필요(§4.5)")
                }
                item(key = "suggestion_panel") {
                    SuggestionPanel(
                        suggestions = uiState.suggestions,
                        isLoading = uiState.suggestionsLoading,
                        groupErrors = uiState.suggestionGroupErrors,
                        onFetch = viewModel::fetchSuggestions,
                        onApprove = viewModel::approveSuggestion,
                        onApproveAll = viewModel::approveAllSuggestions,
                        onDismiss = viewModel::dismissSuggestion,
                        searchWidgetsHtml = uiState.suggestionSearchWidgetsHtml
                    )
                }

                item(key = "types_title") {
                    SectionHeader(
                        title = "유형 진단",
                        subtitle = "약세장 3유형과 회복 가능성 · 주도주 하락세 판단",
                        action = {
                            TextButton(
                                onClick = viewModel::fetchAiContextUpdates,
                                enabled = !uiState.aiContextLoading
                            ) {
                                Text(if (uiState.aiContextLoading) "조회 중…" else "정세 업데이트")
                            }
                        }
                    )
                }
                // §4.7 "정세 업데이트" 승인 미리보기 — 대기 클레임/로딩/오류/검색 위젯 중 하나라도
                // 있을 때만 렌더한다(패널이 항상 떠 있으면 §5.2 레이아웃이 불필요하게 길어짐).
                val showAiContextPanel = uiState.aiContextPending.isNotEmpty() ||
                    uiState.aiContextLoading ||
                    uiState.aiContextGroupErrors.isNotEmpty() ||
                    uiState.aiContextSearchWidgetsHtml.isNotEmpty()
                if (showAiContextPanel) {
                    item(key = "ai_context_panel") {
                        AiContextUpdatePanel(
                            pending = uiState.aiContextPending,
                            provider = uiState.aiContextProvider,
                            isLoading = uiState.aiContextLoading,
                            groupErrors = uiState.aiContextGroupErrors,
                            searchWidgetsHtml = uiState.aiContextSearchWidgetsHtml,
                            onApprove = viewModel::approveAiContextClaim,
                            onApproveAll = viewModel::approveAllAiContextClaims,
                            onDismiss = viewModel::dismissAiContextClaim
                        )
                    }
                }
                item(key = "types") {
                    BearSignalTypesSection(gate = uiState.result.gate, approved = uiState.aiContextApproved)
                }

                item(key = "history_title") {
                    SectionHeader(
                        title = "역사 검증",
                        subtitle = "최악의 조합 · 3충격 동시 결합",
                        action = {
                            TextButton(
                                onClick = viewModel::fetchAiContextUpdates,
                                enabled = !uiState.aiContextLoading
                            ) {
                                Text(if (uiState.aiContextLoading) "조회 중…" else "정세 업데이트")
                            }
                        }
                    )
                }
                item(key = "history") {
                    BearSignalHistorySection(approved = uiState.aiContextApproved)
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
