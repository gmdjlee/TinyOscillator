package com.tinyoscillator.presentation.etf.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tinyoscillator.domain.model.AmountRankingItem

@Composable
fun AmountRankingTab(
    items: List<AmountRankingItem>,
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
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HeaderCell("#", 32.dp)
                HeaderCell("종목명", 100.dp)
                HeaderCell("금액(억)", 64.dp)
                HeaderCell("ETF수", 48.dp)
                HeaderCell("신규", 40.dp)
                HeaderCell("증가", 40.dp)
                HeaderCell("감소", 40.dp)
                HeaderCell("제외", 40.dp)
            }
            HorizontalDivider()
        }

        items(items, key = { it.stockTicker }) { item ->
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
                    modifier = Modifier.width(32.dp),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                Text(
                    item.stockName,
                    modifier = Modifier.width(100.dp),
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
                    modifier = Modifier.width(48.dp),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                CountBadge(item.newCount, MaterialTheme.colorScheme.primary, 40.dp)
                CountBadge(item.increasedCount, MaterialTheme.colorScheme.tertiary, 40.dp)
                CountBadge(item.decreasedCount, MaterialTheme.colorScheme.error, 40.dp)
                CountBadge(item.removedCount, MaterialTheme.colorScheme.outline, 40.dp)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
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
private fun CountBadge(
    count: Int,
    color: androidx.compose.ui.graphics.Color,
    width: androidx.compose.ui.unit.Dp
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
