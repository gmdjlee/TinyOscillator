package com.tinyoscillator.presentation.financial

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.tinyoscillator.domain.model.FinancialState
import com.tinyoscillator.domain.model.FinancialSummary
import com.tinyoscillator.domain.model.formatPercent

@Composable
fun DuPontContent(
    ticker: String?,
    stockName: String?,
    modifier: Modifier = Modifier,
    viewModel: FinancialInfoViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    LaunchedEffect(ticker, stockName) {
        if (ticker != null && stockName != null) {
            viewModel.loadForStock(ticker, stockName)
        } else {
            viewModel.clearStock()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (val currentState = state) {
            is FinancialState.NoStock -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "종목을 선택해주세요.\n검색 화면에서 종목을 검색하고 선택하세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is FinancialState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "재무정보를 불러오는 중...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is FinancialState.NoApiKey -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "API 키가 설정되지 않았습니다.\n설정 화면에서 KIS API 키를 입력해주세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is FinancialState.Success -> {
                // Refresh button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "새로고침",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    DuPontCharts(
                        summary = currentState.summary,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isRefreshing) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                        )
                    }
                }
            }

            is FinancialState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "[ERROR]",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = currentState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                            TextButton(onClick = { viewModel.retry() }) {
                                Text("다시 시도")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ========== DuPont Charts ==========

@Composable
private fun DuPontCharts(
    summary: FinancialSummary,
    modifier: Modifier = Modifier
) {
    if (!summary.hasDuPontData) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "듀퐁 분석 데이터가 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val chartTextColor = if (isDarkTheme) AndroidColor.WHITE else AndroidColor.DKGRAY

    val totalQuarters = summary.periods.size
    var selectedQuarterCount by remember(totalQuarters) { mutableIntStateOf(totalQuarters) }
    val trimmed = remember(summary, selectedQuarterCount) {
        summary.trimToLast(selectedQuarterCount)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // DuPont 요약 카드
        DuPontSummaryCard(summary)

        // 분기 선택
        if (totalQuarters > FinancialSummary.MIN_DISPLAY_QUARTERS) {
            QuarterSelector(
                totalQuarters = totalQuarters,
                selectedCount = selectedQuarterCount,
                onSelect = { selectedQuarterCount = it }
            )
        }

        // 듀퐁 3요소 + ROE 추이 차트 (Double-Y Axis)
        ChartCard(title = "듀퐁 3요소 + ROE 추이") {
            DuPontCombinedChart(
                roes = trimmed.roes,
                netProfitMargins = trimmed.netProfitMargins,
                assetTurnovers = trimmed.assetTurnovers,
                equityMultipliers = trimmed.equityMultipliers,
                labels = trimmed.displayPeriods,
                chartTextColor = chartTextColor
            )
        }

        // 개별 차트: 순이익률
        if (trimmed.netProfitMargins.any { it != 0.0 }) {
            ChartCard(title = "순이익률 (Net Profit Margin)") {
                DuPontLineChart(
                    dataSets = listOf(ChartLine("순이익률", trimmed.netProfitMargins, "#4CAF50")),
                    labels = trimmed.displayPeriods,
                    chartTextColor = chartTextColor,
                    yAxisFormatter = { "%.1f%%".format(it) },
                    height = 220.dp,
                    showLegend = false
                )
            }
        }

        // 개별 차트: 총자산회전율
        if (trimmed.assetTurnovers.any { it != 0.0 }) {
            ChartCard(title = "총자산회전율 (Asset Turnover)") {
                DuPontLineChart(
                    dataSets = listOf(ChartLine("총자산회전율", trimmed.assetTurnovers, "#2196F3")),
                    labels = trimmed.displayPeriods,
                    chartTextColor = chartTextColor,
                    yAxisFormatter = { "%.2fx".format(it) },
                    height = 220.dp,
                    showLegend = false
                )
            }
        }

        // 개별 차트: 재무레버리지
        if (trimmed.equityMultipliers.any { it != 0.0 }) {
            ChartCard(title = "재무레버리지 (Equity Multiplier)") {
                DuPontLineChart(
                    dataSets = listOf(ChartLine("재무레버리지", trimmed.equityMultipliers, "#FF9800")),
                    labels = trimmed.displayPeriods,
                    chartTextColor = chartTextColor,
                    yAxisFormatter = { "%.2fx".format(it) },
                    height = 220.dp,
                    showLegend = false
                )
            }
        }
    }
}

// ========== DuPont Summary Card ==========

@Composable
private fun DuPontSummaryCard(summary: FinancialSummary) {
    val latestRoe = summary.roes.lastOrNull() ?: 0.0
    val latestNpm = summary.netProfitMargins.lastOrNull() ?: 0.0
    val latestAt = summary.assetTurnovers.lastOrNull() ?: 0.0
    val latestEm = summary.equityMultipliers.lastOrNull() ?: 0.0

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "${summary.name} DuPont 분석",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider()

            // ROE 공식 표시
            Text(
                "ROE = 순이익률 × 총자산회전율 × 재무레버리지",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ROE
            DuPontRow(
                label = "ROE",
                value = formatPercent(latestRoe),
                evaluation = evaluateRoe(latestRoe)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 3 components
            DuPontRow(
                label = "순이익률",
                value = formatPercent(latestNpm),
                description = "당기순이익 / 매출액"
            )
            DuPontRow(
                label = "총자산회전율",
                value = "%.2fx".format(latestAt),
                description = "매출액 / 총자산"
            )
            DuPontRow(
                label = "재무레버리지",
                value = "%.2fx".format(latestEm),
                description = "총자산 / 총자본"
            )
        }
    }
}

@Composable
private fun DuPontRow(
    label: String,
    value: String,
    description: String? = null,
    evaluation: Pair<String, Color>? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (evaluation != null) {
                Surface(
                    color = evaluation.second.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = evaluation.first,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = evaluation.second,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun evaluateRoe(value: Double): Pair<String, Color> = when {
    value >= 15.0 -> "우수" to Color(0xFF4CAF50)
    value >= 10.0 -> "양호" to Color(0xFF2196F3)
    value >= 5.0 -> "보통" to Color(0xFFFF9800)
    value > 0.0 -> "저조" to Color(0xFFF44336)
    else -> "적자" to Color(0xFFF44336)
}

// ========== Chart Components ==========

/**
 * 듀퐁 3요소 + ROE CombinedChart (Double-Y Axis)
 *
 * - 왼쪽 Y축: 순이익률(%) 라인 + ROE(%) 막대
 * - 오른쪽 Y축: 총자산회전율(x) 라인 + 재무레버리지(x) 라인
 */
@Composable
private fun DuPontCombinedChart(
    roes: List<Double>,
    netProfitMargins: List<Double>,
    assetTurnovers: List<Double>,
    equityMultipliers: List<Double>,
    labels: List<String>,
    chartTextColor: Int
) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        factory = { context ->
            CombinedChart(context).apply {
                description.isEnabled = false
                setDrawGridBackground(false)
                setDrawBarShadow(false)
                isHighlightFullBarEnabled = false
                setDrawOrder(arrayOf(
                    CombinedChart.DrawOrder.BAR,
                    CombinedChart.DrawOrder.LINE
                ))
                setExtraOffsets(8f, 8f, 8f, 8f)
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
            }
        },
        update = { chart ->
            // --- ROE 막대 (왼쪽 Y축) ---
            val barEntries = roes.mapIndexed { i, v ->
                BarEntry(i.toFloat(), v.toFloat())
            }
            val roeBarSet = BarDataSet(barEntries, "ROE(%) [좌]").apply {
                color = AndroidColor.parseColor("#F44336")
                setDrawValues(false)
                axisDependency = YAxis.AxisDependency.LEFT
            }

            // --- 순이익률 라인 (왼쪽 Y축) ---
            val npmEntries = netProfitMargins.mapIndexed { i, v ->
                Entry(i.toFloat(), v.toFloat())
            }
            val npmLineSet = LineDataSet(npmEntries, "순이익률(%) [좌]").apply {
                color = AndroidColor.parseColor("#4CAF50")
                setCircleColor(AndroidColor.parseColor("#4CAF50"))
                lineWidth = 2f
                circleRadius = 3f
                setDrawValues(false)
                setDrawCircleHole(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                axisDependency = YAxis.AxisDependency.LEFT
            }

            // --- 총자산회전율 라인 (오른쪽 Y축) ---
            val atEntries = assetTurnovers.mapIndexed { i, v ->
                Entry(i.toFloat(), v.toFloat())
            }
            val atLineSet = LineDataSet(atEntries, "총자산회전율(x) [우]").apply {
                color = AndroidColor.parseColor("#2196F3")
                setCircleColor(AndroidColor.parseColor("#2196F3"))
                lineWidth = 2f
                circleRadius = 3f
                setDrawValues(false)
                setDrawCircleHole(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                axisDependency = YAxis.AxisDependency.RIGHT
            }

            // --- 재무레버리지 라인 (오른쪽 Y축) ---
            val emEntries = equityMultipliers.mapIndexed { i, v ->
                Entry(i.toFloat(), v.toFloat())
            }
            val emLineSet = LineDataSet(emEntries, "재무레버리지(x) [우]").apply {
                color = AndroidColor.parseColor("#FF9800")
                setCircleColor(AndroidColor.parseColor("#FF9800"))
                lineWidth = 2f
                circleRadius = 3f
                setDrawValues(false)
                setDrawCircleHole(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                axisDependency = YAxis.AxisDependency.RIGHT
            }

            // --- CombinedData 구성 ---
            val barData = BarData(roeBarSet).apply {
                barWidth = 0.4f
            }
            val lineData = LineData(npmLineSet, atLineSet, emLineSet)

            chart.data = CombinedData().apply {
                setData(barData)
                setData(lineData)
            }

            // --- X축 (막대 폭을 고려한 여유 공간 확보) ---
            chart.xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(labels)
                granularity = 1f
                setDrawGridLines(false)
                textColor = chartTextColor
                axisMinimum = -0.5f
                axisMaximum = labels.size - 0.5f
            }

            // --- 왼쪽 Y축: % 단위 (순이익률, ROE) ---
            chart.axisLeft.apply {
                setDrawGridLines(true)
                textColor = chartTextColor
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String =
                        "%.1f%%".format(value)
                }
                setLabelCount(6, false)
                axisMinimum = 0f
            }

            // --- 오른쪽 Y축: 배수 단위 (총자산회전율, 재무레버리지) ---
            chart.axisRight.apply {
                isEnabled = true
                setDrawGridLines(false)
                textColor = chartTextColor
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String =
                        "%.2fx".format(value)
                }
                setLabelCount(6, false)
            }

            // --- 범례 ---
            chart.legend.apply {
                isEnabled = true
                verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
                setDrawInside(false)
                textColor = chartTextColor
                isWordWrapEnabled = true
            }

            // --- 마커 ---
            chart.marker = DuPontCombinedMarkerView(chart.context, labels)
            chart.isHighlightPerTapEnabled = true

            chart.invalidate()
        }
    )
}

/**
 * 듀퐁 CombinedChart 전용 마커뷰
 * - 막대(ROE): % 형식
 * - 라인 0(순이익률): % 형식
 * - 라인 1(총자산회전율): x 형식
 * - 라인 2(재무레버리지): x 형식
 */
private class DuPontCombinedMarkerView(
    context: android.content.Context,
    private val labels: List<String>
) : com.github.mikephil.charting.components.MarkerView(
    context, com.tinyoscillator.R.layout.chart_marker_view
) {
    private val tvContent: android.widget.TextView = findViewById(com.tinyoscillator.R.id.marker_text)

    override fun refreshContent(e: Entry?, highlight: com.github.mikephil.charting.highlight.Highlight?) {
        if (e == null || highlight == null) {
            super.refreshContent(e, highlight)
            return
        }

        val xIndex = e.x.toInt()
        val date = labels.getOrElse(xIndex) { "" }

        val valueText = when {
            // BarEntry → ROE
            e is BarEntry -> "ROE: ${"%.1f%%".format(e.y)}"
            // LineData sets: index 0=순이익률, 1=총자산회전율, 2=재무레버리지
            highlight.dataSetIndex == 0 -> "순이익률: ${"%.1f%%".format(e.y)}"
            highlight.dataSetIndex == 1 -> "총자산회전율: ${"%.2fx".format(e.y)}"
            highlight.dataSetIndex == 2 -> "재무레버리지: ${"%.2fx".format(e.y)}"
            else -> "%.2f".format(e.y)
        }

        tvContent.text = "$date\n$valueText"
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): com.github.mikephil.charting.utils.MPPointF {
        return com.github.mikephil.charting.utils.MPPointF(-(width / 2f), -height.toFloat())
    }
}

private data class ChartLine(
    val label: String,
    val data: List<Double>,
    val colorHex: String
)

@Composable
private fun DuPontLineChart(
    dataSets: List<ChartLine>,
    labels: List<String>,
    chartTextColor: Int,
    yAxisFormatter: (Float) -> String,
    height: androidx.compose.ui.unit.Dp = 280.dp,
    showLegend: Boolean = true
) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = showLegend
                legend.textColor = chartTextColor
                setExtraOffsets(8f, 8f, 8f, 8f)
            }
        },
        update = { chart ->
            chart.legend.textColor = chartTextColor
            chart.legend.isEnabled = showLegend

            val lineDataSets = dataSets.map { line ->
                val entries = line.data.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
                LineDataSet(entries, line.label).apply {
                    color = AndroidColor.parseColor(line.colorHex)
                    setCircleColor(AndroidColor.parseColor(line.colorHex))
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawValues(false)
                    setDrawCircleHole(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                }
            }

            chart.data = LineData(lineDataSets)
            chart.xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(labels)
                granularity = 1f
                setDrawGridLines(false)
                textColor = chartTextColor
            }
            chart.axisLeft.apply {
                setDrawGridLines(true)
                textColor = chartTextColor
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = yAxisFormatter(value)
                }
            }
            chart.axisRight.isEnabled = false

            chart.marker = FinancialMarkerView(
                chart.context, labels
            ) { value -> yAxisFormatter(value) }
            chart.isHighlightPerTapEnabled = true

            chart.invalidate()
        }
    )
}
