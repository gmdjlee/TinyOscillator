package com.tinyoscillator.presentation.screener

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.domain.model.ScreenerResultItem
import com.tinyoscillator.domain.model.ScreenerSortKey
import com.tinyoscillator.presentation.common.skeleton.ScreenerResultSkeleton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenerScreen(
    viewModel: ScreenerViewModel = hiltViewModel(),
    onTickerClick: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val state by viewModel.screenerState.collectAsStateWithLifecycle()
    val currentFilter by viewModel.currentFilter.collectAsStateWithLifecycle()
    val sortKey by viewModel.sortKey.collectAsStateWithLifecycle()
    val sectors by viewModel.sectors.collectAsStateWithLifecycle()
    var showFilter by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("종목 스크리너") },
                actions = {
                    IconButton(onClick = { showFilter = true }) {
                        Icon(Icons.Default.FilterList, "필터")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "��정")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 정렬 버튼 행
            SortKeyRow(
                selected = sortKey,
                onSelect = viewModel::updateSort,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when (val s = state) {
                is ScreenerUiState.Loading -> {
                    LazyColumn {
                        items(8) { ScreenerResultSkeleton() }
                    }
                }
                is ScreenerUiState.Success -> {
                    Text(
                        "결과 ${s.items.size}개 (최대 50개)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    LazyColumn {
                        items(s.items, key = { it.ticker }) { item ->
                            ScreenerResultRow(
                                item = item,
                                onClick = { onTickerClick(item.ticker) },
                            )
                        }
                    }
                }
                is ScreenerUiState.Error -> {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            s.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "필터를 설정하고 스크리닝을 시작하세요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showFilter) {
        ScreenerFilterSheet(
            currentFilter = currentFilter,
            sectors = sectors,
            onApply = { newFilter ->
                viewModel.updateFilter(newFilter)
                viewModel.saveFilter()
                showFilter = false
            },
            onReset = { viewModel.resetFilter(); showFilter = false },
            onDismiss = { showFilter = false },
        )
    }
}

@Composable
private fun SortKeyRow(
    selected: ScreenerSortKey,
    onSelect: (ScreenerSortKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = mapOf(
        ScreenerSortKey.SIGNAL_SCORE to "신호강도",
        ScreenerSortKey.MARKET_CAP to "시가총액",
        ScreenerSortKey.PBR to "PBR",
        ScreenerSortKey.FOREIGN_RATIO to "외국인",
        ScreenerSortKey.VOLUME_RATIO to "거래량",
    )
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEach { (key, label) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
fun ScreenerResultRow(item: ScreenerResultItem, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(6.dp))
                SignalBadge(score = item.signalScore)
            }
        },
        supportingContent = {
            Text(
                buildString {
                    append("PBR ${item.pbr.f1()}  ")
                    append("외국인 ${(item.foreignRatio * 100).toInt()}%  ")
                    append("거래량 ${item.volumeRatio.f1()}배  ")
                    append(item.sectorName)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Text(
                "${item.marketCapBil}억",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider(thickness = 0.5.dp)
}

@Composable
private fun SignalBadge(score: Float) {
    val color = when {
        score >= 0.7f -> MaterialTheme.colorScheme.primary
        score >= 0.5f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = "${(score * 100).toInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun Float.f1() = "%.1f".format(this)
