package com.tinyoscillator.presentation.market

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.domain.model.DateRangeOption
import com.tinyoscillator.domain.model.MarketDepositChartData
import com.tinyoscillator.domain.model.MarketDepositState
import com.tinyoscillator.core.ui.composable.DefaultErrorContent
import com.tinyoscillator.presentation.financial.FinancialMarkerView

@Composable
fun MarketDepositTab(
    viewModel: MarketDepositViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedRange by viewModel.selectedRange.collectAsStateWithLifecycle()
    val depositData by viewModel.depositData.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // State Display
            when (val currentState = state) {
                is MarketDepositState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (currentState.progress in 0..100) {
                            LinearProgressIndicator(
                                progress = { currentState.progress / 100f },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                            )
                        }
                        if (currentState.message.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                currentState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is MarketDepositState.Success -> {
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(3000)
                        viewModel.clearMessage()
                    }
                }
                is MarketDepositState.Error -> {
                    DefaultErrorContent(
                        message = currentState.message,
                        onRetry = { viewModel.clearMessage() }
                    )
                }
                is MarketDepositState.Idle -> {}
            }

            // Main Content
            if (depositData.dates.isNotEmpty()) {
                val lastIdx = depositData.dates.size - 1

                // Summary Section
                DepositSummarySection(
                    depositAmount = depositData.depositAmounts[lastIdx],
                    depositChange = depositData.depositChanges[lastIdx],
                    creditAmount = depositData.creditAmounts[lastIdx],
                    creditChange = depositData.creditChanges[lastIdx]
                )

                // Stats Row
                StatsRow(data = depositData)

                // Date Range Selector
                DateRangeSelector(
                    selectedRange = selectedRange,
                    onRangeSelected = { viewModel.updateDateRange(it) }
                )

                // Chart
                ChartSection(data = depositData)

                // Disclaimer
                Text(
                    "* 증시 자금 동향은 시장 유동성 및 투자심리를 파악하는\n보조 지표로 활용하는 것이 좋습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DepositSummarySection(
    depositAmount: Double,
    depositChange: Double,
    creditAmount: Double,
    creditChange: Double
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = String.format("%.0f", depositAmount / 10000),
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "조원",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val depositChangeColor = if (depositChange > 0) Color(0xFF4CAF50) else Color(0xFFF44336)
        Surface(
            shape = RoundedCornerShape(50),
            color = depositChangeColor.copy(alpha = 0.1f)
        ) {
            Text(
                text = String.format("전일 대비 %+.0f억원", depositChange),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = depositChangeColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("신용잔고:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                String.format("%.0f억원", creditAmount),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
            val creditChangeColor = if (creditChange > 0) Color(0xFF4CAF50) else Color(0xFFF44336)
            Text(
                String.format("(%+.0f)", creditChange),
                style = MaterialTheme.typography.bodyMedium,
                color = creditChangeColor
            )
        }
    }
}

@Composable
private fun StatsRow(data: MarketDepositChartData) {
    val lastIdx = data.dates.size - 1
    val yesterday = if (lastIdx >= 1) data.depositAmounts[lastIdx - 1] else null
    val weekAgo = if (lastIdx >= 5) data.depositAmounts[lastIdx - 5] else null
    val monthAgo = if (lastIdx >= 20) data.depositAmounts[lastIdx - 20] else null

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatBox("어제", yesterday?.let { String.format("%.0f", it / 10000) } ?: "—", "조", Modifier.weight(1f))
        StatBox("1주일 전", weekAgo?.let { String.format("%.0f", it / 10000) } ?: "—", "조", Modifier.weight(1f))
        StatBox("1달 전", monthAgo?.let { String.format("%.0f", it / 10000) } ?: "—", "조", Modifier.weight(1f))
    }
}

@Composable
private fun StatBox(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                if (value != "—") {
                    Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ChartSection(data: MarketDepositChartData) {
    val isDark = isSystemInDarkTheme()
    val depositColor = Color(0xFF2196F3) // Blue
    val creditColor = Color(0xFFFF9800) // Orange
    val textColorValue = if (isDark) Color.White.toArgb() else Color.Black.toArgb()
    val gridColor = if (isDark) Color(0xFF444444).toArgb() else Color(0xFFE0E0E0).toArgb()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "예탁금/신용잔고 추이",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            AndroidView(
                factory = { context ->
                    CombinedChart(context).apply {
                        description.isEnabled = false
                        setTouchEnabled(true)
                        isDragEnabled = true
                        setScaleEnabled(true)
                        setPinchZoom(true)
                        setDrawGridBackground(false)
                        setExtraBottomOffset(10f)
                        setDrawOrder(arrayOf(CombinedChart.DrawOrder.LINE, CombinedChart.DrawOrder.LINE))

                        xAxis.apply {
                            position = XAxis.XAxisPosition.BOTTOM
                            setDrawGridLines(true)
                            gridLineWidth = 1f
                            setGridColor(gridColor)
                            enableGridDashedLine(10f, 5f, 0f)
                            setTextColor(textColorValue)
                            granularity = 1f
                            labelRotationAngle = -45f
                            setAvoidFirstLastClipping(true)
                        }

                        axisLeft.apply {
                            setDrawGridLines(true)
                            gridLineWidth = 1f
                            setGridColor(gridColor)
                            enableGridDashedLine(10f, 5f, 0f)
                            setTextColor(depositColor.toArgb())
                            setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                            valueFormatter = object : ValueFormatter() {
                                override fun getFormattedValue(value: Float): String = String.format("%.0f조", value / 10000)
                            }
                        }

                        axisRight.apply {
                            isEnabled = true
                            setDrawGridLines(false)
                            setTextColor(creditColor.toArgb())
                            setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                            valueFormatter = object : ValueFormatter() {
                                override fun getFormattedValue(value: Float): String = String.format("%.0f조", value / 10000)
                            }
                        }

                        legend.apply {
                            isEnabled = true
                            textSize = 12f
                            setTextColor(textColorValue)
                        }
                    }
                },
                update = { chart ->
                    val dataCount = data.dates.size
                    val optimalLabelCount = when {
                        dataCount <= 7 -> dataCount
                        dataCount <= 30 -> 6
                        dataCount <= 90 -> 8
                        else -> 10
                    }

                    chart.xAxis.apply {
                        setLabelCount(optimalLabelCount, false)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val index = value.toInt()
                                return if (index in data.dates.indices) {
                                    val date = data.dates[index]
                                    if (dataCount <= 30) date.substring(5) else date.substring(2, 7)
                                } else ""
                            }
                        }
                    }

                    val depositEntries = data.depositAmounts.mapIndexed { index, value ->
                        Entry(index.toFloat(), value.toFloat())
                    }
                    val depositDataSet = LineDataSet(depositEntries, "고객예탁금").apply {
                        axisDependency = YAxis.AxisDependency.LEFT
                        color = depositColor.toArgb()
                        lineWidth = 2.5f
                        setCircleColor(depositColor.toArgb())
                        circleRadius = 2f
                        setDrawCircleHole(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        highLightColor = depositColor.toArgb()
                    }

                    val creditEntries = data.creditAmounts.mapIndexed { index, value ->
                        Entry(index.toFloat(), value.toFloat())
                    }
                    val creditDataSet = LineDataSet(creditEntries, "신용잔고").apply {
                        axisDependency = YAxis.AxisDependency.RIGHT
                        color = creditColor.toArgb()
                        lineWidth = 2.5f
                        setCircleColor(creditColor.toArgb())
                        circleRadius = 2f
                        setDrawCircleHole(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        highLightColor = creditColor.toArgb()
                    }

                    val lineData = LineData(depositDataSet, creditDataSet)
                    val combinedData = CombinedData().apply { setData(lineData) }
                    chart.data = combinedData

                    chart.marker = FinancialMarkerView(
                        chart.context, data.dates
                    ) { value -> String.format("%.0f억원", value) }
                    chart.isHighlightPerTapEnabled = true

                    chart.invalidate()
                },
                modifier = Modifier.fillMaxWidth().height(250.dp)
            )
        }
    }
}
