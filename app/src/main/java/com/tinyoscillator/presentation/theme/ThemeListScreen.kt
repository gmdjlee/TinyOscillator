package com.tinyoscillator.presentation.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.domain.model.ThemeGroup
import com.tinyoscillator.domain.model.ThemeSortMode
import com.tinyoscillator.core.worker.ThemeUpdateWorker
import com.tinyoscillator.presentation.common.CollectionProgressBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeListScreen(
    viewModel: ThemeViewModel = hiltViewModel(),
    onThemeClick: (themeCode: String, themeName: String) -> Unit = { _, _ -> },
    onSettingsClick: () -> Unit = {},
) {
    val themes by viewModel.themes.collectAsStateWithLifecycle()
    val themeCount by viewModel.themeCount.collectAsStateWithLifecycle()
    val lastUpdatedAt by viewModel.lastUpdatedAt.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("테마") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "테마 갱신")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        ) {
            CollectionProgressBar(tag = ThemeUpdateWorker.TAG)

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("테마명 검색") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )

            SortChipsRow(
                selected = sortMode,
                onSelect = viewModel::onSortModeChange,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "테마 ${themeCount}개",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                lastUpdatedAt?.let { ts ->
                    Text(
                        "마지막 갱신: ${DATE_FMT.format(Date(ts))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (themes.isEmpty()) {
                EmptyView(modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(themes, key = { it.themeCode }) { theme ->
                        ThemeCard(
                            theme = theme,
                            onClick = { onThemeClick(theme.themeCode, theme.themeName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortChipsRow(
    selected: ThemeSortMode,
    onSelect: (ThemeSortMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeSortMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = { Text(sortLabel(mode)) },
            )
        }
    }
}

private fun sortLabel(mode: ThemeSortMode): String = when (mode) {
    ThemeSortMode.TOP_RETURN -> "기간수익률"
    ThemeSortMode.FLU_RATE -> "등락률"
    ThemeSortMode.NAME -> "테마명"
    ThemeSortMode.STOCK_COUNT -> "종목수"
}

@Composable
private fun EmptyView(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "테마 데이터가 없습니다",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "상단 새로고침 버튼을 눌러 키움 테마 데이터를 수집해 주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThemeCard(theme: ThemeGroup, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        theme.themeName.ifBlank { "(이름 없음)" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "코드 ${theme.themeCode} · 종목 ${theme.stockCount}개",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${formatSignedPercent(theme.periodReturnRate)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = signColor(theme.periodReturnRate),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "등락 ${formatSignedPercent(theme.fluRate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = signColor(theme.fluRate),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "상승 ${theme.riseCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    "하락 ${theme.fallCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (theme.mainStocks.isNotBlank()) {
                Text(
                    "주요종목: ${theme.mainStocks}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun signColor(value: Double): androidx.compose.ui.graphics.Color = when {
    value > 0 -> MaterialTheme.colorScheme.error      // 한국식: 상승=빨강
    value < 0 -> MaterialTheme.colorScheme.primary    // 하락=파랑
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun formatSignedPercent(value: Double): String {
    val sign = if (value > 0) "+" else ""
    return "$sign${"%.2f".format(value)}%"
}

private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
