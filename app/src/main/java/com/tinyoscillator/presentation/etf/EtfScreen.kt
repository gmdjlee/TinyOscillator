package com.tinyoscillator.presentation.etf

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import com.tinyoscillator.presentation.common.ThemeToggleIcon
import com.tinyoscillator.ui.theme.LocalThemeModeState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.tinyoscillator.core.worker.EtfUpdateWorker
import com.tinyoscillator.presentation.common.CollectionProgressBar
import com.tinyoscillator.presentation.common.PillTabRow

private enum class EtfTab(val label: String) {
    THEME_LIST("테마 목록"),
    STATS("통계")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EtfScreen(
    onSettingsClick: () -> Unit,
    onEtfDetailClick: (String) -> Unit,
    onStockClick: (String) -> Unit = {},
    onStockTrendClick: (String, String) -> Unit = { _, _ -> }
) {
    var selectedTab by rememberSaveable { mutableStateOf(EtfTab.THEME_LIST) }
    val themeModeState = LocalThemeModeState.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ETF분석") },
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
            CollectionProgressBar(tag = EtfUpdateWorker.TAG)

            PillTabRow(
                tabs = EtfTab.entries.toList(),
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                tabLabel = { it.label }
            )

            when (selectedTab) {
                EtfTab.THEME_LIST -> {
                    EtfAnalysisContent(
                        onEtfClick = onEtfDetailClick,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                EtfTab.STATS -> {
                    EtfStatsContent(
                        onStockClick = onStockClick,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
