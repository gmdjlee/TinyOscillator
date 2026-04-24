package com.tinyoscillator.presentation.sector

import android.graphics.Color
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mikephil.charting.charts.CandleStickChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.CandleData
import com.github.mikephil.charting.data.CandleDataSet
import com.github.mikephil.charting.data.CandleEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.tinyoscillator.domain.model.SectorChartPeriod
import com.tinyoscillator.domain.model.SectorIndexCandle
import com.tinyoscillator.domain.model.SectorIndexChart
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectorIndexDetailScreen(
    viewModel: SectorIndexDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.name.ifBlank { "업종지수" }, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "코드 ${state.code}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PeriodFilterRow(
                selected = state.period,
                onSelect = viewModel::selectPeriod,
            )

            state.chart?.quote?.let { q ->
                QuoteCard(price = q.currentPrice, diff = q.priorDiff, rate = q.priorRatePercent)
            }

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
                state.errorMessage != null -> {
                    Text(
                        state.errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                state.chart != null -> {
                    CandleChartCard(state.chart!!)
                    StatsCard(state.chart!!)
                }
            }
        }
    }
}

@Composable
private fun PeriodFilterRow(
    selected: SectorChartPeriod,
    onSelect: (SectorChartPeriod) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SectorChartPeriod.entries.forEach { p ->
            FilterChip(
                selected = selected == p,
                onClick = { onSelect(p) },
                label = { Text(p.label) },
            )
        }
    }
}

@Composable
private fun QuoteCard(price: Double, diff: Double, rate: Double) {
    val color = when {
        diff > 0 -> MaterialTheme.colorScheme.error // 한국식: 상승=빨강
        diff < 0 -> MaterialTheme.colorScheme.primary // 하락=파랑
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val sign = if (diff > 0) "+" else ""
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("현재가", style = MaterialTheme.typography.bodySmall)
                Text(
                    PRICE_FMT.format(price),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$sign${PRICE_FMT.format(diff)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "$sign${"%.2f".format(rate)}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                )
            }
        }
    }
}

@Composable
private fun CandleChartCard(chart: SectorIndexChart) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val textColor = if (isDark) Color.WHITE else Color.DKGRAY
    val gridColor = if (isDark) Color.parseColor("#444444") else Color.parseColor("#CCCCCC")

    val candles = chart.candles
    val lastBound = remember { arrayOfNulls<SectorIndexChart>(1) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "업종지수 차트 (${candles.size}건)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            AndroidView(
                factory = { context ->
                    CandleStickChart(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        description.isEnabled = false
                        setDrawGridBackground(false)
                        setTouchEnabled(true)
                        isDragEnabled = true
                        setScaleEnabled(true)
                        setPinchZoom(true)
                        xAxis.position = XAxis.XAxisPosition.BOTTOM
                        xAxis.setDrawGridLines(false)
                        axisRight.isEnabled = false
                        axisLeft.setDrawGridLines(true)
                    }
                },
                update = { view ->
                    view.xAxis.textColor = textColor
                    view.axisLeft.textColor = textColor
                    view.axisLeft.gridColor = gridColor
                    view.legend.textColor = textColor
                    if (chart != lastBound[0]) {
                        bindCandleData(view, candles)
                        lastBound[0] = chart
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
            )
        }
    }
}

@Composable
private fun StatsCard(chart: SectorIndexChart) {
    val candles = chart.candles
    if (candles.isEmpty()) return
    val high = candles.maxOf { it.high }
    val low = candles.minOf { it.low }
    val avgVol = candles.map { it.volume }.average().toLong()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StatRow("기간 최고", PRICE_FMT.format(high))
            StatRow("기간 최저", PRICE_FMT.format(low))
            StatRow("평균 거래량", VOL_FMT.format(avgVol))
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun bindCandleData(view: CandleStickChart, candles: List<SectorIndexCandle>) {
    if (candles.isEmpty()) {
        view.clear()
        return
    }
    val entries = candles.mapIndexed { i, c ->
        CandleEntry(i.toFloat(), c.high.toFloat(), c.low.toFloat(), c.open.toFloat(), c.close.toFloat())
    }
    val set = CandleDataSet(entries, "지수").apply {
        setDrawIcons(false)
        shadowColor = Color.DKGRAY
        shadowWidth = 0.7f
        decreasingColor = Color.parseColor("#1E88E5") // 한국식: 하락 파랑
        decreasingPaintStyle = android.graphics.Paint.Style.FILL
        increasingColor = Color.parseColor("#E53935") // 상승 빨강
        increasingPaintStyle = android.graphics.Paint.Style.FILL
        neutralColor = Color.DKGRAY
        setDrawValues(false)
    }
    view.data = CandleData(set)
    view.xAxis.valueFormatter = IndexAxisValueFormatter(candles.map { formatDate(it.date) })
    view.xAxis.granularity = 1f
    view.xAxis.setLabelCount(5, false)
    view.invalidate()
}

private fun formatDate(yyyymmdd: String): String =
    if (yyyymmdd.length == 8) "${yyyymmdd.substring(4, 6)}/${yyyymmdd.substring(6, 8)}" else yyyymmdd

private val PRICE_FMT = DecimalFormat("#,##0.00")
private val VOL_FMT = DecimalFormat("#,###")
