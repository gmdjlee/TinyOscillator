package com.tinyoscillator.presentation.etf

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mikephil.charting.data.Entry
import com.tinyoscillator.domain.model.DateRange
import com.tinyoscillator.presentation.etf.stats.TrendLineChart
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AggregatedStockTrendScreen(
    onBack: () -> Unit,
    viewModel: AggregatedStockTrendViewModel = hiltViewModel()
) {
    val stockName by viewModel.stockName.collectAsStateWithLifecycle()
    val data by viewModel.filteredData.collectAsStateWithLifecycle()
    val selectedRange by viewModel.selectedRange.collectAsStateWithLifecycle()
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stockName ?: viewModel.getStockTicker()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        if (data.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("데이터가 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary card
            item {
                val latest = data.last()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "${stockName ?: viewModel.getStockTicker()} (${viewModel.getStockTicker()})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SummaryColumn("총금액", "${numberFormat.format(latest.totalAmount / 100_000_000)}억원")
                            SummaryColumn("ETF수", "${latest.etfCount}개")
                            SummaryColumn("최대비중", latest.maxWeight?.let { "%.2f%%".format(it) } ?: "-")
                            SummaryColumn("평균비중", latest.avgWeight?.let { "%.2f%%".format(it) } ?: "-")
                        }
                    }
                }
            }

            // DateRange selector
            item {
                DateRangeSelector(selectedRange = selectedRange, onSelect = { viewModel.selectRange(it) })
            }

            // Total amount chart
            item {
                val entries = data.mapIndexed { index, d ->
                    Entry(index.toFloat(), (d.totalAmount / 100_000_000f))
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("총금액 추이", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                        TrendLineChart(
                            entries = entries,
                            labels = data.map { formatShortDate(it.date) },
                            label = "총금액(억)",
                            color = AndroidColor.parseColor("#0077B6")
                        )
                    }
                }
            }

            // Max weight chart
            item {
                val entries = data.mapIndexedNotNull { index, d ->
                    d.maxWeight?.let { Entry(index.toFloat(), it.toFloat()) }
                }
                if (entries.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("최대비중 추이", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                            TrendLineChart(
                                entries = entries,
                                labels = data.map { formatShortDate(it.date) },
                                label = "최대비중(%)",
                                color = AndroidColor.parseColor("#6750A4")
                            )
                        }
                    }
                }
            }

            // Avg weight chart
            item {
                val entries = data.mapIndexedNotNull { index, d ->
                    d.avgWeight?.let { Entry(index.toFloat(), it.toFloat()) }
                }
                if (entries.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("평균비중 추이", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                            TrendLineChart(
                                entries = entries,
                                labels = data.map { formatShortDate(it.date) },
                                label = "평균비중(%)",
                                color = AndroidColor.parseColor("#2D6A4F")
                            )
                        }
                    }
                }
            }

            // Recent data table
            item {
                Text("최근 데이터", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TableHeader("날짜", Modifier.weight(1f))
                    TableHeader("총금액(억)", Modifier.weight(1f))
                    TableHeader("ETF수", Modifier.weight(0.6f))
                    TableHeader("최대비중", Modifier.weight(0.8f))
                    TableHeader("평균비중", Modifier.weight(0.8f))
                }
                HorizontalDivider()
            }
            val recentData = data.takeLast(5).reversed()
            recentData.forEach { row ->
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatShortDate(row.date), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(numberFormat.format(row.totalAmount / 100_000_000), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                        Text("${row.etfCount}", modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        Text(row.maxWeight?.let { "%.2f".format(it) } ?: "-", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                        Text(row.avgWeight?.let { "%.2f".format(it) } ?: "-", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
private fun SummaryColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun TableHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun formatShortDate(yyyyMMdd: String): String {
    if (yyyyMMdd.length != 8) return yyyyMMdd
    return "${yyyyMMdd.substring(4, 6)}/${yyyyMMdd.substring(6, 8)}"
}
