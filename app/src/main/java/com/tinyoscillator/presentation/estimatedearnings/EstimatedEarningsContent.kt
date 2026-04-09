package com.tinyoscillator.presentation.estimatedearnings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.domain.model.EstimatedEarningsInfo
import com.tinyoscillator.domain.model.EstimatedEarningsRow
import com.tinyoscillator.domain.model.EstimatedEarningsSummary
import com.tinyoscillator.ui.theme.LocalFinanceColors
import java.text.NumberFormat
import java.util.Locale

@Composable
fun EstimatedEarningsContent(
    ticker: String?,
    stockName: String?,
    modifier: Modifier = Modifier,
    viewModel: EstimatedEarningsViewModel = hiltViewModel()
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(ticker) {
        viewModel.loadData(ticker)
    }

    Column(modifier = modifier.fillMaxSize()) {
        when {
            ticker == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "종목을 검색하여 추정실적을 확인하세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "추정실적 데이터를 불러오는 중...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = error ?: "오류가 발생했습니다",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            else -> {
                val data = summary
                if (data == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "추정실적 데이터가 없습니다",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    EstimatedEarningsTable(data)
                }
            }
        }
    }
}

@Composable
private fun EstimatedEarningsTable(summary: EstimatedEarningsSummary) {
    val scrollState = rememberScrollState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Stock info header
        item {
            StockInfoCard(summary.info)
        }

        // Earnings data section (output2)
        if (summary.earningsData.isNotEmpty()) {
            item {
                Text(
                    text = "실적 / 추정",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            item {
                DataTable(
                    periods = summary.periods,
                    rows = summary.earningsData,
                    scrollState = scrollState
                )
            }
        }

        // Valuation data section (output3)
        if (summary.valuationData.isNotEmpty()) {
            item {
                Text(
                    text = "투자지표",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            item {
                DataTable(
                    periods = summary.periods,
                    rows = summary.valuationData,
                    scrollState = scrollState
                )
            }
        }
    }
}

@Composable
private fun StockInfoCard(info: EstimatedEarningsInfo) {
    val financeColors = LocalFinanceColors.current
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }

    val priceValue = info.currentPrice.replace(",", "").toLongOrNull()
    val changeValue = info.priceChange.replace(",", "").toLongOrNull()
    val isPositive = info.changeSign == "2" || info.changeSign == "1"
    val isNegative = info.changeSign == "5" || info.changeSign == "4"
    val priceColor = when {
        isPositive -> financeColors.positive
        isNegative -> financeColors.negative
        else -> MaterialTheme.colorScheme.onSurface
    }
    val signPrefix = when {
        isPositive -> "+"
        isNegative -> "-"
        else -> ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (info.stockName.isNotBlank()) {
                Text(
                    text = info.stockName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Current price
                Text(
                    text = priceValue?.let { "${numberFormat.format(it)}원" }
                        ?: info.currentPrice.ifBlank { "-" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = priceColor
                )

                // Change info
                Column(horizontalAlignment = Alignment.End) {
                    val changeText = changeValue?.let {
                        "$signPrefix${numberFormat.format(it)}"
                    } ?: info.priceChange.ifBlank { "-" }
                    val rateText = info.changeRate.ifBlank { null }

                    Text(
                        text = if (rateText != null) "$changeText ($rateText%)" else changeText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = priceColor
                    )
                }
            }
        }
    }
}

@Composable
private fun DataTable(
    periods: List<String>,
    rows: List<EstimatedEarningsRow>,
    scrollState: androidx.compose.foundation.ScrollState
) {
    val columnCount = periods.size.coerceIn(1, 4)
    val labelWidth = 80.dp
    val valueWidth = 90.dp

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier.horizontalScroll(scrollState)
        ) {
            // Period header row
            Row(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                // Empty label cell
                Box(modifier = Modifier.width(labelWidth)) {
                    Text(
                        text = "항목",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                periods.take(columnCount).forEach { period ->
                    Box(
                        modifier = Modifier.width(valueWidth),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = formatPeriod(period),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp
            )

            // Data rows
            rows.forEachIndexed { index, row ->
                val values = listOf(row.data2, row.data3, row.data4, row.data5)

                Row(
                    modifier = Modifier
                        .background(
                            if (index % 2 == 0) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Row label (data1)
                    Box(modifier = Modifier.width(labelWidth)) {
                        Text(
                            text = row.data1.ifBlank { "-" },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Value cells (data2-data5 mapped to periods)
                    values.take(columnCount).forEach { value ->
                        Box(
                            modifier = Modifier.width(valueWidth),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = formatValue(value),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = getValueColor(value)
                            )
                        }
                    }
                }

                if (index < rows.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun getValueColor(value: String): androidx.compose.ui.graphics.Color {
    val financeColors = LocalFinanceColors.current
    val cleaned = value.trim().replace(",", "")
    if (cleaned.startsWith("-") && cleaned.toDoubleOrNull() != null) {
        return financeColors.negative
    }
    return MaterialTheme.colorScheme.onSurface
}

private fun formatPeriod(period: String): String {
    // Convert "202412" or "2024/12" to "24/12" for compact display
    val cleaned = period.replace("/", "").replace(".", "")
    return if (cleaned.length >= 6) {
        "${cleaned.substring(2, 4)}/${cleaned.substring(4, 6)}"
    } else {
        period
    }
}

private fun formatValue(value: String): String {
    if (value.isBlank()) return "-"
    return value
}
