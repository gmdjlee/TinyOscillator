package com.tinyoscillator.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyoscillator.core.database.entity.StockMasterEntity
import kotlinx.coroutines.flow.StateFlow

/**
 * 종목 검색 결과 + 최근 검색 드롭다운 콘텐츠.
 * 각 화면의 기존 OutlinedTextField/SearchBar 안에 이 콘텐츠를 넣어 사용.
 */
@Composable
fun StockSearchDropdownContent(
    searchResults: List<StockMasterEntity>,
    recentSearches: List<StockMasterEntity>,
    query: String,
    onStockSelected: (StockMasterEntity) -> Unit,
    onClearRecent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayItems = if (query.isBlank()) recentSearches else searchResults
    if (displayItems.isEmpty()) return

    val sectionLabel = if (query.isBlank()) "최근 검색" else "검색 결과 ${displayItems.size}건"

    Column(modifier = modifier) {
        Text(
            sectionLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
            items(displayItems, key = { it.ticker }) { entry ->
                StockSearchResultItem(
                    entry = entry,
                    onClick = { onStockSelected(entry) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isBlank() && recentSearches.isNotEmpty()) {
                item {
                    TextButton(
                        onClick = onClearRecent,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text("최근 검색 지우기", fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
fun StockSearchResultItem(
    entry: StockMasterEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, fontWeight = FontWeight.Medium)
            Text(
                "${entry.ticker}  ·  ${entry.sector.ifBlank { "-" }}  ·  ${entry.market}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}
