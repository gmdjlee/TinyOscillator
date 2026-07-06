package com.tinyoscillator.presentation.etf

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.mikephil.charting.data.Entry
import com.tinyoscillator.core.ui.composable.EmptyStateContent
import com.tinyoscillator.domain.model.DateRange
import com.tinyoscillator.presentation.etf.stats.TrendLineChart

/**
 * ETF 추이 화면 공용 템플릿 — 단일 ETF 추이([StockTrendScreen])와
 * 집계 추이([AggregatedStockTrendScreen])의 동일 레이아웃(요약 카드·기간 선택·
 * 라인차트들·최근 데이터 표)을 스펙 파라미터로 통합.
 */
internal data class TrendChartSpec(
    val title: String,
    val label: String,
    val colorHex: String,
    val entries: List<Entry>
)

internal data class TrendTableColumn(
    val header: String,
    val weight: Float = 1f,
    val textAlign: TextAlign = TextAlign.End
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrendScreenTemplate(
    title: String,
    onBack: () -> Unit,
    isLoading: Boolean,
    headerTitle: String,
    headerSubtitle: String?,
    summaryItems: List<Pair<String, String>>,
    selectedRange: DateRange,
    onSelectRange: (DateRange) -> Unit,
    dateLabels: List<String>,
    charts: List<TrendChartSpec>,
    tableColumns: List<TrendTableColumn>,
    tableRows: List<List<String>>
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (dateLabels.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateContent(message = "추이 데이터가 없습니다.")
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
                            headerTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        headerSubtitle?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            summaryItems.forEach { (label, value) ->
                                SummaryColumn(label, value)
                            }
                        }
                    }
                }
            }

            // DateRange selector
            item {
                DateRangeSelector(selectedRange = selectedRange, onSelect = onSelectRange)
            }

            // Charts (엔트리 없는 차트는 미표시)
            charts.filter { it.entries.isNotEmpty() }.forEach { chart ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                chart.title,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            TrendLineChart(
                                entries = chart.entries,
                                labels = dateLabels,
                                label = chart.label,
                                color = AndroidColor.parseColor(chart.colorHex)
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    tableColumns.forEach { col ->
                        TableHeader(col.header, Modifier.weight(col.weight))
                    }
                }
                HorizontalDivider()
            }
            tableRows.forEach { row ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        row.forEachIndexed { i, cell ->
                            val col = tableColumns[i]
                            Text(
                                cell,
                                modifier = Modifier.weight(col.weight),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = col.textAlign
                            )
                        }
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

internal fun formatTrendDate(yyyyMMdd: String): String {
    if (yyyyMMdd.length != 8) return yyyyMMdd
    return "${yyyyMMdd.substring(4, 6)}/${yyyyMMdd.substring(6, 8)}"
}
