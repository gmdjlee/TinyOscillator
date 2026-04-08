package com.tinyoscillator.presentation.investopinion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.tinyoscillator.domain.model.InvestOpinion
import com.tinyoscillator.domain.model.InvestOpinionSummary
import com.tinyoscillator.ui.theme.LocalFinanceColors
import java.text.NumberFormat
import java.util.Locale

@Composable
fun InvestOpinionContent(
    ticker: String?,
    stockName: String?,
    modifier: Modifier = Modifier,
    viewModel: InvestOpinionViewModel = hiltViewModel()
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(ticker) {
        viewModel.loadData(ticker, stockName)
    }

    Column(modifier = modifier.fillMaxSize()) {
        when {
            ticker == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "종목을 검색하여 증권사 투자의견을 확인하세요",
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
                            "투자의견 데이터를 불러오는 중...",
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
                if (data == null || data.opinions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "투자의견 데이터가 없습니다",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { OpinionSummaryCard(data) }
                        item { OpinionTableHeader() }
                        items(data.opinions) { opinion ->
                            OpinionRow(opinion)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpinionSummaryCard(summary: InvestOpinionSummary) {
    val financeColors = LocalFinanceColors.current
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "투자의견 요약",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Opinion distribution bar
            if (summary.totalCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OpinionChip(
                        label = "매수",
                        count = summary.buyCount,
                        color = financeColors.positive,
                        modifier = Modifier.weight(1f)
                    )
                    OpinionChip(
                        label = "중립",
                        count = summary.holdCount,
                        color = financeColors.neutral,
                        modifier = Modifier.weight(1f)
                    )
                    OpinionChip(
                        label = "매도",
                        count = summary.sellCount,
                        color = financeColors.negative,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Target price & upside
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "평균 목표가",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = summary.avgTargetPrice?.let { "${numberFormat.format(it)}원" } ?: "-",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "현재가",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = summary.currentPrice?.let { "${numberFormat.format(it)}원" } ?: "-",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "괴리율",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val upside = summary.upsidePct
                    Text(
                        text = upside?.let { String.format("%+.1f%%", it) } ?: "-",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            upside == null -> MaterialTheme.colorScheme.onSurface
                            upside > 0 -> financeColors.positive
                            upside < 0 -> financeColors.negative
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OpinionChip(
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "${count}건",
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun OpinionTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "일자",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1.2f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "증권사",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1.5f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "의견",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "목표가",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1.3f),
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OpinionRow(opinion: InvestOpinion) {
    val financeColors = LocalFinanceColors.current
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }

    val opinionColor = when {
        isBuyText(opinion.opinion, opinion.opinionCode) -> financeColors.positive
        isSellText(opinion.opinion, opinion.opinionCode) -> financeColors.negative
        else -> financeColors.neutral
    }

    val formattedDate = remember(opinion.date) {
        if (opinion.date.length == 8) {
            "${opinion.date.substring(4, 6)}.${opinion.date.substring(6, 8)}"
        } else opinion.date
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1.2f),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = opinion.firmName,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = opinion.opinion.ifBlank { "-" },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = opinionColor,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = opinion.targetPrice?.let { numberFormat.format(it) } ?: "-",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1.3f),
                textAlign = TextAlign.End,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun isBuyText(opinion: String, code: String): Boolean {
    val lower = opinion.lowercase()
    return lower.contains("매수") || lower.contains("buy") ||
            lower.contains("outperform") || lower.contains("overweight") ||
            code in listOf("1", "2", "01", "02")
}

private fun isSellText(opinion: String, code: String): Boolean {
    val lower = opinion.lowercase()
    return lower.contains("매도") || lower.contains("sell") ||
            lower.contains("underperform") || lower.contains("underweight") ||
            code in listOf("4", "5", "04", "05")
}
