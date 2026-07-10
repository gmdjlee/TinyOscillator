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
import com.tinyoscillator.feature.bearsignal.domain.model.GlobalIndexRegistry
import com.tinyoscillator.feature.bearsignal.presentation.BearSignalViewModel
import com.tinyoscillator.feature.bearsignal.presentation.ManualInputViewModel
import com.tinyoscillator.presentation.common.SectionHeader

/**
 * BearSignal 메인 화면(Phase 4) — TASK.md §5.2 7섹션을 `LazyColumn`으로 조립한다.
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
                item(key = "header") {
                    BearSignalHeaderSection(result = uiState.result)
                }

                item(key = "leading_title") {
                    SectionHeader(title = "온도계 · 선행 신호 3종 — 위험선호가 어디까지 식었나")
                }
                item(key = "leading_cards") {
                    BearSignalLeadingSignalsSection(
                        inputs = uiState.inputs,
                        result = uiState.result,
                        auto = uiState.auto,
                        onManualInputClick = { showManualSheet = true }
                    )
                }

                item(key = "country_table") {
                    BearSignalCountryTableSection(
                        inputs = uiState.inputs,
                        result = uiState.result,
                        manualRequiredNames = manualRequiredNames,
                        onPeriodSelected = viewModel::selectPeriod,
                        onEditMarket = viewModel::updateMarketReturn
                    )
                }

                item(key = "gate_amp_title") {
                    SectionHeader(title = "방아쇠 · 증폭기 — 금리(결정타)와 집중(증폭 계수)")
                }
                item(key = "gate_amp") {
                    BearSignalGateAmpSection(
                        inputs = uiState.inputs,
                        result = uiState.result,
                        auto = uiState.auto,
                        onManualInputClick = { showManualSheet = true }
                    )
                }

                item(key = "types_title") {
                    SectionHeader(title = "유형 진단 — 주도주 하락세 판단, 약세장 3유형과 회복 가능성")
                }
                item(key = "types") {
                    BearSignalTypesSection(gate = uiState.result.gate)
                }

                item(key = "history_title") {
                    SectionHeader(title = "역사 검증 — 최악의 조합, 3충격 동시 결합")
                }
                item(key = "history") {
                    BearSignalHistorySection()
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
