package com.tinyoscillator.presentation.keyword

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.core.worker.EtfUpdateWorker
import com.tinyoscillator.domain.model.KeywordGroup
import com.tinyoscillator.domain.model.KeywordSortMode
import com.tinyoscillator.presentation.common.CollectionProgressBar
import com.tinyoscillator.ui.theme.signColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 탐색 탭에 임베드되는 키워드 목록 콘텐츠 ([ThemeListContent] 미러, TopAppBar 없음). */
@Composable
fun KeywordListContent(
    viewModel: KeywordViewModel = hiltViewModel(),
    onKeywordClick: (keyword: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val groupCount by viewModel.groupCount.collectAsStateWithLifecycle()
    val lastUpdatedAt by viewModel.lastUpdatedAt.collectAsStateWithLifecycle()
    val includeKeywords by viewModel.includeKeywords.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        CollectionProgressBar(tag = EtfUpdateWorker.TAG)

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("키워드 검색") },
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
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "키워드 ${groupCount}개",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                lastUpdatedAt?.let { ts ->
                    Text(
                        "마지막 갱신: ${DATE_FMT.format(Date(ts))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "ETF 갱신",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (groups.isEmpty()) {
            EmptyView(
                noKeywords = includeKeywords.isEmpty(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(groups, key = { it.keyword }) { group ->
                    KeywordCard(
                        group = group,
                        onClick = { onKeywordClick(group.keyword) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SortChipsRow(
    selected: KeywordSortMode,
    onSelect: (KeywordSortMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KeywordSortMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = { Text(sortLabel(mode)) },
            )
        }
    }
}

private fun sortLabel(mode: KeywordSortMode): String = when (mode) {
    KeywordSortMode.ETF_COUNT -> "ETF수"
    KeywordSortMode.AVG_RETURN -> "평균등락"
    KeywordSortMode.NAME -> "키워드명"
}

@Composable
private fun EmptyView(noKeywords: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (noKeywords) "등록된 필터 키워드가 없습니다" else "분류할 ETF가 없습니다",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (noKeywords) "설정에서 필터 키워드를 등록해 주세요."
                else "ETF 목록 탭에서 데이터를 수집해 주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KeywordCard(group: KeywordGroup, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    group.keyword,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "ETF ${group.etfCount}개",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "평균 ${formatSignedPercent(group.avgChangeRate)}",
                style = MaterialTheme.typography.bodyMedium,
                color = signColor(group.avgChangeRate),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun formatSignedPercent(value: Double): String {
    val sign = if (value > 0) "+" else ""
    return "$sign${"%.2f".format(value)}%"
}

private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
