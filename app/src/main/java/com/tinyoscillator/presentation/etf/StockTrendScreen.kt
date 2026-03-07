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
fun StockTrendScreen(
    onBack: () -> Unit,
    viewModel: StockTrendViewModel = hiltViewModel()
) {
    val stockName by viewModel.stockName.collectAsStateWithLifecycle()
    val etfName by viewModel.etfName.collectAsStateWithLifecycle()
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
                        etfName?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val latest = data.last()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SummaryColumn("최근 비중", latest.weight?.let { "%.2f%%".format(it) } ?: "-")
                            SummaryColumn("최근 금액", "${numberFormat.format(latest.amount / 100_000_000)}억원")
                            SummaryColumn("데이터 수", "${data.size}건")
                        }
                    }
                }
            }

            // DateRange selector
            item {
                DateRangeSelector(selectedRange = selectedRange, onSelect = { viewModel.selectRange(it) })
            }

            // Weight chart
            item {
                val weightEntries = data.mapIndexedNotNull { index, d ->
                    d.weight?.let { Entry(index.toFloat(), it.toFloat()) }
                }
                if (weightEntries.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("비중 추이", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                            TrendLineChart(
                                entries = weightEntries,
                                labels = data.map { formatShortDate(it.date) },
                                label = "비중(%)",
                                color = AndroidColor.parseColor("#6750A4")
                            )
                        }
                    }
                }
            }

            // Amount chart
            item {
                val amountEntries = data.mapIndexed { index, d ->
                    Entry(index.toFloat(), (d.amount / 100_000_000f))
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("금액 추이", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                        TrendLineChart(
                            entries = amountEntries,
                            labels = data.map { formatShortDate(it.date) },
                            label = "금액(억)",
                            color = AndroidColor.parseColor("#0077B6")
                        )
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
                    TableHeader("비중(%)", Modifier.weight(1f))
                    TableHeader("금액(억)", Modifier.weight(1f))
                }
                HorizontalDivider()
            }
            val recentData = data.takeLast(5).reversed()
            recentData.forEach { row ->
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatShortDate(row.date), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(
                            row.weight?.let { "%.2f".format(it) } ?: "-",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.End
                        )
                        Text(
                            numberFormat.format(row.amount / 100_000_000),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.End
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
internal fun DateRangeSelector(
    selectedRange: DateRange,
    onSelect: (DateRange) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DateRange.entries.forEach { range ->
            FilterChip(
                selected = selectedRange == range,
                onClick = { onSelect(range) },
                label = { Text(range.label, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.weight(1f)
            )
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
