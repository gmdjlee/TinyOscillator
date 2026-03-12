package com.tinyoscillator.presentation.etf.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tinyoscillator.domain.model.ChangeType
import com.tinyoscillator.domain.model.StockChange
import java.text.NumberFormat
import java.util.Locale

@Composable
fun StockChangeTab(
    changes: List<StockChange>,
    changeType: ChangeType,
    onStockClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (changes.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "변동 데이터가 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "${changes.size}건",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        items(changes, key = { "${it.etfTicker}|${it.stockTicker}" }) { change ->
            StockChangeCard(change, changeType, numberFormat, onStockClick)
        }
    }
}

@Composable
private fun StockChangeCard(
    change: StockChange,
    changeType: ChangeType,
    numberFormat: NumberFormat,
    onStockClick: (String) -> Unit
) {
    val badgeColor = when (changeType) {
        ChangeType.NEW -> MaterialTheme.colorScheme.primary
        ChangeType.REMOVED -> MaterialTheme.colorScheme.error
        ChangeType.INCREASED -> MaterialTheme.colorScheme.tertiary
        ChangeType.DECREASED -> MaterialTheme.colorScheme.error
    }
    val badgeText = when (changeType) {
        ChangeType.NEW -> "신규"
        ChangeType.REMOVED -> "제외"
        ChangeType.INCREASED -> "증가"
        ChangeType.DECREASED -> "감소"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onStockClick(change.stockTicker) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        change.stockName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            change.stockTicker,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MarketBadge(change.market)
                        SectorBadge(change.sector)
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        badgeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                change.etfName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Weight comparison
            if (changeType == ChangeType.INCREASED || changeType == ChangeType.DECREASED) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "이전: ${change.previousWeight?.let { "%.2f%%".format(it) } ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "현재: ${change.currentWeight?.let { "%.2f%%".format(it) } ?: "-"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    val diff = (change.currentWeight ?: 0.0) - (change.previousWeight ?: 0.0)
                    Text(
                        "변동: %+.2f%%p".format(diff),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (diff > 0) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.error
                    )
                }
            }

            // Amount
            val amountText = when (changeType) {
                ChangeType.NEW -> "평가금액: ${numberFormat.format(change.currentAmount)}원"
                ChangeType.REMOVED -> "이전금액: ${numberFormat.format(change.previousAmount)}원"
                else -> "평가금액: ${numberFormat.format(change.currentAmount)}원"
            }
            Text(
                amountText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
