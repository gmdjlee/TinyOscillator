package com.tinyoscillator.presentation.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tinyoscillator.core.worker.EtfUpdateWorker
import com.tinyoscillator.domain.model.ConsensusReport
import com.tinyoscillator.presentation.common.CollectionProgressBar
import com.tinyoscillator.presentation.common.ScrollablePillTabRow
import com.tinyoscillator.presentation.common.ThemeToggleIcon
import com.tinyoscillator.presentation.common.TwoPaneLayout
import com.tinyoscillator.presentation.common.WindowType
import com.tinyoscillator.presentation.etf.EtfAnalysisContent
import com.tinyoscillator.presentation.etf.EtfDetailContent
import com.tinyoscillator.presentation.etf.EtfStatsContent
import com.tinyoscillator.presentation.report.ReportContent
import com.tinyoscillator.presentation.theme.ThemeListContent
import com.tinyoscillator.ui.theme.LocalThemeModeState

private enum class ExploreTab(val label: String) {
    ETF_LIST("ETF"),
    ETF_STATS("통계"),
    THEME("테마"),
    REPORT("리포트")
}

/**
 * 탐색 탭: ETF 목록·통계, 테마, 리포트를 하나의 하단 탭으로 통합.
 * 기존 ETF분석/테마/리포트 3개 하단 탭을 대체 — 모두 "그룹/리서치 기준 종목 탐색" 성격.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onSettingsClick: () -> Unit,
    onEtfDetailClick: (String) -> Unit,
    onStockClick: (String) -> Unit = {},
    onStockTrendClick: (String, String) -> Unit = { _, _ -> },
    onOpenFullAnalysis: (String, String) -> Unit = { _, _ -> },
    onOpenProbabilityAnalysis: (String, String) -> Unit = { _, _ -> },
    onThemeClick: (String, String) -> Unit = { _, _ -> },
    onReportClick: (ConsensusReport) -> Unit = {},
    windowType: WindowType = WindowType.COMPACT
) {
    var selectedTab by rememberSaveable { mutableStateOf(ExploreTab.ETF_LIST) }
    var selectedEtfTicker by rememberSaveable { mutableStateOf<String?>(null) }
    val themeModeState = LocalThemeModeState.current
    val isTwoPane = windowType != WindowType.COMPACT

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("탐색") },
                actions = {
                    ThemeToggleIcon(themeModeState)
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollablePillTabRow(
                tabs = ExploreTab.entries.toList(),
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                tabLabel = { it.label }
            )

            when (selectedTab) {
                ExploreTab.ETF_LIST -> {
                    CollectionProgressBar(tag = EtfUpdateWorker.TAG)
                    if (isTwoPane) {
                        TwoPaneLayout(
                            windowType = windowType,
                            listPane = {
                                EtfAnalysisContent(
                                    onEtfClick = { ticker -> selectedEtfTicker = ticker },
                                    modifier = Modifier.fillMaxSize()
                                )
                            },
                            detailPane = {
                                val ticker = selectedEtfTicker
                                if (ticker != null) {
                                    EtfDetailContent(
                                        ticker = ticker,
                                        onStockTrendClick = onStockTrendClick,
                                        onOpenFullAnalysis = onOpenFullAnalysis,
                                        onOpenProbabilityAnalysis = onOpenProbabilityAnalysis,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "ETF를 선택해주세요.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            singlePane = {
                                EtfAnalysisContent(
                                    onEtfClick = onEtfDetailClick,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        )
                    } else {
                        EtfAnalysisContent(
                            onEtfClick = onEtfDetailClick,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                ExploreTab.ETF_STATS -> {
                    CollectionProgressBar(tag = EtfUpdateWorker.TAG)
                    EtfStatsContent(
                        onStockClick = onStockClick,
                        onOpenFullAnalysis = onOpenFullAnalysis,
                        onOpenProbabilityAnalysis = onOpenProbabilityAnalysis,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ExploreTab.THEME -> {
                    ThemeListContent(
                        onThemeClick = onThemeClick,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ExploreTab.REPORT -> {
                    ReportContent(
                        onReportClick = onReportClick,
                        onOpenFullAnalysis = onOpenFullAnalysis,
                        onOpenProbabilityAnalysis = onOpenProbabilityAnalysis,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
