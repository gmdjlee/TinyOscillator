package com.tinyoscillator.presentation.comparison

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.presentation.common.skeleton.ComparisonSkeleton
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.tinyoscillator.domain.model.ComparisonData
import com.tinyoscillator.domain.model.ComparisonPeriod
import com.tinyoscillator.domain.model.ComparisonSeries
import com.tinyoscillator.presentation.common.StockSearchDropdownContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonScreen(
    viewModel: ComparisonViewModel = hiltViewModel(),
    onSettingsClick: () -> Unit = {},
    onTickerClick: (String) -> Unit = {},
) {
    val state by viewModel.comparisonState.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var showDropdown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("수익률 비교") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 종목 검색
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            viewModel.setSearchQuery(it)
                            showDropdown = it.isNotBlank()
                        },
                        label = { Text("종목 검색") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (showDropdown && searchResults.isNotEmpty()) {
                        StockSearchDropdownContent(
                            searchResults = searchResults,
                            recentSearches = emptyList(),
                            query = searchQuery,
                            onStockSelected = { stock ->
                                viewModel.setTicker(stock.ticker)
                                showDropdown = false
                                focusManager.clearFocus()
                            },
                            onClearRecent = {},
                        )
                    }
                }
            }

            // 기간 선택
            PeriodSelector(
                selected = selectedPeriod,
                onSelect = viewModel::setPeriod,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            when (val s = state) {
                is ComparisonUiState.Success -> {
                    // 수익률 비교 LineChart
                    ReturnComparisonChart(
                        data = s.data,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.55f)
                            .padding(horizontal = 8.dp),
                    )

                    // 요약 지표 카드
                    ComparisonSummaryRow(
                        alpha = s.data.alphaFinal,
                        beta = s.data.betaEstimate,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    // 신호 강도 LineChart
                    if (s.data.signalHistory.isNotEmpty()) {
                        SignalHistoryChart(
                            signalHistory = s.data.signalHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.35f)
                                .padding(horizontal = 8.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier.weight(0.35f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "신호 이력 없음",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                is ComparisonUiState.Loading -> {
                    ComparisonSkeleton(modifier = Modifier.weight(1f))
                }
                is ComparisonUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                s.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.retry() }) {
                                Text("다시 시도")
                            }
                        }
                    }
                }
                is ComparisonUiState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "종목을 선택하세요",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PeriodSelector(
    selected: ComparisonPeriod,
    onSelect: (ComparisonPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ComparisonPeriod.entries
            .filter { it != ComparisonPeriod.CUSTOM }
            .forEach { period ->
                FilterChip(
                    selected = selected == period,
                    onClick = { onSelect(period) },
                    label = { Text(period.label) },
                )
            }
    }
}

@Composable
fun ReturnComparisonChart(data: ComparisonData, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled = false
                legend.isEnabled = true
                legend.textSize = 11f
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false)
                xAxis.granularity = 1f
                axisRight.isEnabled = false
                axisLeft.addLimitLine(LimitLine(0f, "").apply {
                    lineColor = android.graphics.Color.parseColor("#33888780")
                    lineWidth = 0.5f
                    enableDashedLine(8f, 4f, 0f)
                })
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setNoDataText("데이터를 로드 중...")
            }
        },
        update = { chart ->
            val dataSets = mutableListOf<ILineDataSet>()
            fun ComparisonSeries.toDataSet(): LineDataSet {
                val entries = returns.mapIndexed { i, r ->
                    Entry(i.toFloat(), r * 100f)  // % 단위
                }
                return LineDataSet(entries, label).apply {
                    this.color = this@toDataSet.color
                    lineWidth = 1.8f
                    setDrawCircles(false)
                    setDrawValues(false)
                    isHighlightEnabled = false
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                }
            }
            dataSets += data.targetSeries.toDataSet()
            data.sectorSeries?.let { dataSets += it.toDataSet() }
            dataSets += data.kospiSeries.toDataSet()
            chart.data = LineData(dataSets)
            chart.notifyDataSetChanged()
            chart.invalidate()
        },
        modifier = modifier,
    )
}

@Composable
fun SignalHistoryChart(
    signalHistory: List<Pair<Long, Float>>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            "신호 강도",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
        )
        AndroidView(
            factory = { ctx ->
                LineChart(ctx).apply {
                    description.isEnabled = false
                    legend.isEnabled = false
                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    xAxis.setDrawGridLines(false)
                    xAxis.setDrawLabels(false)
                    axisRight.isEnabled = false
                    axisLeft.axisMinimum = 0f
                    axisLeft.axisMaximum = 1f
                    axisLeft.granularity = 0.25f
                    axisLeft.addLimitLine(LimitLine(0.5f, "").apply {
                        lineColor = android.graphics.Color.parseColor("#33888780")
                        lineWidth = 0.5f
                        enableDashedLine(6f, 4f, 0f)
                    })
                    setTouchEnabled(true)
                    isDragEnabled = true
                    setScaleEnabled(false)
                    setNoDataText("")
                }
            },
            update = { chart ->
                val entries = signalHistory.mapIndexed { i, (_, score) ->
                    Entry(i.toFloat(), score)
                }
                val dataSet = LineDataSet(entries, "신호").apply {
                    color = android.graphics.Color.parseColor("#D85A30")
                    lineWidth = 1.5f
                    setDrawCircles(true)
                    circleRadius = 2.5f
                    setCircleColor(android.graphics.Color.parseColor("#D85A30"))
                    setDrawValues(false)
                    isHighlightEnabled = false
                    setDrawFilled(true)
                    fillColor = android.graphics.Color.parseColor("#D85A30")
                    fillAlpha = 30
                }
                chart.data = LineData(dataSet)
                chart.notifyDataSetChanged()
                chart.invalidate()
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun ComparisonSummaryRow(alpha: Float, beta: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryMetricCard(
            label = "초과수익률 (α)",
            value = "${if (alpha >= 0) "+" else ""}${"%.1f".format(alpha * 100)}%",
            valueColor = if (alpha >= 0) Color(0xFFD85A30) else Color(0xFF378ADD),
            modifier = Modifier.weight(1f),
        )
        SummaryMetricCard(
            label = "베타 (β)",
            value = "%.2f".format(beta),
            valueColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryMetricCard(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor,
            )
        }
    }
}
