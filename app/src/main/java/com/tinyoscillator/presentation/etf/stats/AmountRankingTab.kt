package com.tinyoscillator.presentation.etf.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tinyoscillator.domain.model.AmountRankingItem

private enum class SortColumn { AMOUNT, ETF_COUNT, NEW, INCREASED, DECREASED, REMOVED }
private enum class SortOrder { ASC, DESC }
private data class SortSpec(val column: SortColumn, val order: SortOrder)

private fun encodeSortSpecs(specs: List<SortSpec>): String =
    specs.joinToString(",") { "${it.column.name}:${it.order.name}" }

private fun decodeSortSpecs(encoded: String): List<SortSpec> {
    if (encoded.isEmpty()) return emptyList()
    return encoded.split(",").map { s ->
        val (col, ord) = s.split(":")
        SortSpec(SortColumn.valueOf(col), SortOrder.valueOf(ord))
    }
}

@Composable
fun AmountRankingTab(
    items: List<AmountRankingItem>,
    sortEncoded: String,
    onSortChange: (String) -> Unit,
    onStockClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "데이터가 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // 다중 정렬: 클릭 순서대로 우선순위 부여, 3-state 순환 (중립→DESC→ASC→중립)
    val sortSpecs by remember(sortEncoded) { derivedStateOf { decodeSortSpecs(sortEncoded) } }

    val onHeaderClick = { column: SortColumn ->
        val existing = sortSpecs.find { it.column == column }
        val newSpecs = when {
            existing == null -> sortSpecs + SortSpec(column, SortOrder.DESC)
            existing.order == SortOrder.DESC -> sortSpecs.map {
                if (it.column == column) it.copy(order = SortOrder.ASC) else it
            }
            else -> sortSpecs.filter { it.column != column } // ASC → 중립(제거)
        }
        onSortChange(encodeSortSpecs(newSpecs))
    }

    val sortedItems = remember(items, sortSpecs) {
        if (sortSpecs.isEmpty()) return@remember items
        val comparator = sortSpecs
            .map { spec ->
                val base = when (spec.column) {
                    SortColumn.AMOUNT -> compareBy<AmountRankingItem> { it.totalAmountBillion }
                    SortColumn.ETF_COUNT -> compareBy { it.etfCount }
                    SortColumn.NEW -> compareBy { it.newCount }
                    SortColumn.INCREASED -> compareBy { it.increasedCount }
                    SortColumn.DECREASED -> compareBy { it.decreasedCount }
                    SortColumn.REMOVED -> compareBy { it.removedCount }
                }
                if (spec.order == SortOrder.DESC) base.reversed() else base
            }
            .reduce { acc, c -> acc.then(c) }
        items.sortedWith(comparator)
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HeaderCell("#", 36.dp)
                HeaderCell("시장", 48.dp)
                HeaderCell("업종", 72.dp)
                HeaderCell("종목명", 150.dp)
                SortableHeaderCell("금액(억)", 64.dp, sortSpecs, SortColumn.AMOUNT) { onHeaderClick(SortColumn.AMOUNT) }
                SortableHeaderCell("ETF수", 64.dp, sortSpecs, SortColumn.ETF_COUNT) { onHeaderClick(SortColumn.ETF_COUNT) }
                SortableHeaderCell("신규", 64.dp, sortSpecs, SortColumn.NEW) { onHeaderClick(SortColumn.NEW) }
                SortableHeaderCell("증가", 64.dp, sortSpecs, SortColumn.INCREASED) { onHeaderClick(SortColumn.INCREASED) }
                SortableHeaderCell("감소", 64.dp, sortSpecs, SortColumn.DECREASED) { onHeaderClick(SortColumn.DECREASED) }
                SortableHeaderCell("제외", 64.dp, sortSpecs, SortColumn.REMOVED) { onHeaderClick(SortColumn.REMOVED) }
                if (sortSpecs.isNotEmpty()) {
                    Text(
                        "↺",
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .clickable { onSortChange("") },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            HorizontalDivider()
        }

        items(sortedItems, key = { it.stockTicker }) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStockClick(item.stockTicker) }
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "${item.rank}",
                    modifier = Modifier.width(36.dp),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                MarketLabel(item.market, 48.dp)
                SectorLabel(item.sector, 72.dp)
                Text(
                    item.stockName,
                    modifier = Modifier.width(150.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "%.1f".format(item.totalAmountBillion),
                    modifier = Modifier.width(64.dp),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End
                )
                Text(
                    "${item.etfCount}",
                    modifier = Modifier.width(64.dp),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                CountBadge(item.newCount, MaterialTheme.colorScheme.primary, 64.dp)
                CountBadge(item.increasedCount, MaterialTheme.colorScheme.tertiary, 64.dp)
                CountBadge(item.decreasedCount, MaterialTheme.colorScheme.error, 64.dp)
                CountBadge(item.removedCount, MaterialTheme.colorScheme.outline, 64.dp)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    Text(
        text,
        modifier = Modifier.width(width),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SortableHeaderCell(
    text: String,
    width: Dp,
    sortSpecs: List<SortSpec>,
    column: SortColumn,
    onClick: () -> Unit
) {
    val spec = sortSpecs.find { it.column == column }
    val priority = if (spec != null) sortSpecs.indexOf(spec) + 1 else 0
    val arrow = when (spec?.order) {
        SortOrder.DESC -> "▼"
        SortOrder.ASC -> "▲"
        null -> ""
    }
    // 다중 정렬 시 우선순위 번호 표시 (2개 이상 선택된 경우만)
    val priorityLabel = if (priority > 0 && sortSpecs.size > 1) "$priority" else ""
    val label = if (spec != null) "$text$priorityLabel$arrow" else text
    Text(
        label,
        modifier = Modifier
            .width(width)
            .clickable(onClick = onClick),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = if (spec != null) MaterialTheme.colorScheme.primary
               else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CountBadge(
    count: Int,
    color: androidx.compose.ui.graphics.Color,
    width: Dp
) {
    if (count > 0) {
        Surface(
            modifier = Modifier.width(width),
            shape = MaterialTheme.shapes.extraSmall,
            color = color.copy(alpha = 0.15f)
        ) {
            Text(
                "$count",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Spacer(modifier = Modifier.width(width))
    }
}
