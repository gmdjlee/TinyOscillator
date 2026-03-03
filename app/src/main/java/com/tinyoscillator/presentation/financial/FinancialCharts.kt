package com.tinyoscillator.presentation.financial

import android.content.Context
import android.graphics.Color as AndroidColor
import android.widget.TextView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.tinyoscillator.R
import com.tinyoscillator.domain.model.FinancialSummary
import com.tinyoscillator.domain.model.formatNumber
import com.tinyoscillator.domain.model.formatPercent

// ========== Financial Marker View ==========

class FinancialMarkerView(
    context: Context,
    private val dates: List<String>,
    private val formatter: (Float) -> String
) : MarkerView(context, R.layout.chart_marker_view) {

    private val tvContent: TextView = findViewById(R.id.marker_text)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e == null) return
        try {
            val xIndex = e.x.toInt()
            val date = dates.getOrElse(xIndex) { "" }
            val value = formatter(e.y)
            tvContent.text = "$date\n$value"
        } catch (_: Exception) {
            tvContent.text = ""
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat())
    }
}

// ========== Profitability Content ==========

@Composable
fun ProfitabilityContent(
    summary: FinancialSummary,
    modifier: Modifier = Modifier
) {
    if (!summary.hasProfitabilityData && !summary.hasGrowthData && !summary.hasAssetGrowthData) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "수익성 데이터가 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val isDarkTheme = isSystemInDarkTheme()
    val chartTextColor = if (isDarkTheme) AndroidColor.WHITE else AndroidColor.DKGRAY

    val totalQuarters = summary.periods.size
    var selectedQuarterCount by remember(totalQuarters) { mutableIntStateOf(totalQuarters) }
    val trimmedSummary = remember(summary, selectedQuarterCount) {
        summary.trimToLast(selectedQuarterCount)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${summary.name} 수익성 요약",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()
                SummaryRowWithGrowth(
                    label = "매출액",
                    value = formatNumber(summary.latestRevenue ?: 0L),
                    growthRate = summary.revenueGrowthRates.lastOrNull()
                )
                SummaryRowWithGrowth(
                    label = "영업이익",
                    value = formatNumber(summary.latestOperatingProfit ?: 0L),
                    growthRate = summary.operatingProfitGrowthRates.lastOrNull()
                )
                SummaryRowWithGrowth(
                    label = "당기순이익",
                    value = formatNumber(summary.latestNetIncome ?: 0L),
                    growthRate = summary.netIncomeGrowthRates.lastOrNull()
                )
            }
        }

        // Quarter selector
        if (totalQuarters > FinancialSummary.MIN_DISPLAY_QUARTERS) {
            QuarterSelector(
                totalQuarters = totalQuarters,
                selectedCount = selectedQuarterCount,
                onSelect = { selectedQuarterCount = it }
            )
        }

        // Income Bar Chart
        if (trimmedSummary.hasProfitabilityData) {
            ChartCard(title = "손익 추이") {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    factory = { context ->
                        BarChart(context).apply {
                            setupCommonChartProperties(this, chartTextColor)
                        }
                    },
                    update = { chart ->
                        chart.legend.textColor = chartTextColor
                        updateIncomeBarChart(chart, trimmedSummary, chartTextColor)
                    }
                )
            }
        }

        // Growth Rate Line Chart
        if (trimmedSummary.hasGrowthData) {
            ChartCard(title = "성장률 추이") {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    factory = { context ->
                        LineChart(context).apply {
                            setupCommonChartProperties(this, chartTextColor)
                        }
                    },
                    update = { chart ->
                        chart.legend.textColor = chartTextColor
                        updateGrowthLineChart(chart, trimmedSummary, chartTextColor)
                    }
                )
            }
        }

        // Asset Growth Line Chart
        if (trimmedSummary.hasAssetGrowthData) {
            ChartCard(title = "자산 성장률") {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    factory = { context ->
                        LineChart(context).apply {
                            setupCommonChartProperties(this, chartTextColor)
                        }
                    },
                    update = { chart ->
                        chart.legend.textColor = chartTextColor
                        updateAssetGrowthChart(chart, trimmedSummary, chartTextColor)
                    }
                )
            }
        }
    }
}

// ========== Stability Content ==========

@Composable
fun StabilityContent(
    summary: FinancialSummary,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    val chartTextColor = if (isDarkTheme) AndroidColor.WHITE else AndroidColor.DKGRAY

    if (!summary.hasStabilityData) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "안정성 데이터가 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val totalQuarters = summary.periods.size
    var selectedQuarterCount by remember(totalQuarters) { mutableIntStateOf(totalQuarters) }
    val trimmedSummary = remember(summary, selectedQuarterCount) {
        summary.trimToLast(selectedQuarterCount)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${summary.name} 안정성 요약",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()

                val latestDebt = summary.latestDebtRatio ?: 0.0
                val latestCurrent = summary.latestCurrentRatio ?: 0.0
                val latestBorrowing = summary.borrowingDependencies.lastOrNull() ?: 0.0

                StabilityRow(
                    label = "부채비율",
                    value = formatPercent(latestDebt),
                    evaluation = evaluateDebtRatio(latestDebt)
                )
                StabilityRow(
                    label = "유동비율",
                    value = formatPercent(latestCurrent),
                    evaluation = evaluateCurrentRatio(latestCurrent)
                )
                StabilityRow(
                    label = "차입금 의존도",
                    value = formatPercent(latestBorrowing),
                    evaluation = evaluateBorrowingDependency(latestBorrowing)
                )
            }
        }

        // Quarter selector
        if (totalQuarters > FinancialSummary.MIN_DISPLAY_QUARTERS) {
            QuarterSelector(
                totalQuarters = totalQuarters,
                selectedCount = selectedQuarterCount,
                onSelect = { selectedQuarterCount = it }
            )
        }

        // Combined stability chart
        ChartCard(title = "안정성 지표 추이") {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                factory = { context ->
                    LineChart(context).apply {
                        description.isEnabled = false
                        legend.isEnabled = true
                        legend.textColor = chartTextColor
                        setExtraOffsets(8f, 8f, 8f, 8f)
                    }
                },
                update = { chart ->
                    chart.legend.textColor = chartTextColor
                    updateCombinedStabilityChart(chart, trimmedSummary, chartTextColor)
                }
            )
        }

        // Individual charts
        if (trimmedSummary.debtRatios.any { it != 0.0 }) {
            IndividualRatioChart(
                title = "부채비율",
                data = trimmedSummary.debtRatios,
                labels = trimmedSummary.displayPeriods,
                colorHex = "#F44336",
                chartTextColor = chartTextColor
            )
        }

        if (trimmedSummary.currentRatios.any { it != 0.0 }) {
            IndividualRatioChart(
                title = "유동비율",
                data = trimmedSummary.currentRatios,
                labels = trimmedSummary.displayPeriods,
                colorHex = "#4CAF50",
                chartTextColor = chartTextColor
            )
        }

        if (trimmedSummary.borrowingDependencies.any { it != 0.0 }) {
            IndividualRatioChart(
                title = "차입금 의존도",
                data = trimmedSummary.borrowingDependencies,
                labels = trimmedSummary.displayPeriods,
                colorHex = "#FF9800",
                chartTextColor = chartTextColor
            )
        }
    }
}

// ========== Shared Components ==========

@Composable
private fun SummaryRowWithGrowth(
    label: String,
    value: String,
    growthRate: Double?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        if (growthRate != null && growthRate != 0.0) {
            val growthColor = when {
                growthRate > 0 -> Color(0xFFF44336)
                growthRate < 0 -> Color(0xFF2196F3)
                else -> Color.Unspecified
            }
            val prefix = if (growthRate > 0) "+" else ""
            Text(
                text = "$prefix${formatPercent(growthRate)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = growthColor,
                textAlign = TextAlign.End,
                modifier = Modifier.width(72.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(72.dp))
        }
    }
}

@Composable
private fun StabilityRow(
    label: String,
    value: String,
    evaluation: Pair<String, Color>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
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

@Composable
internal fun QuarterSelector(
    totalQuarters: Int,
    selectedCount: Int,
    onSelect: (Int) -> Unit
) {
    val options = buildList {
        add(FinancialSummary.MIN_DISPLAY_QUARTERS)
        if (totalQuarters > 8) add(8)
        if (totalQuarters > FinancialSummary.MIN_DISPLAY_QUARTERS) add(totalQuarters)
    }.distinct()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "표시 분기",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        options.forEach { count ->
            val label = if (count == totalQuarters) "전체(${count}Q)" else "${count}Q"
            FilterChip(
                selected = selectedCount == count,
                onClick = { onSelect(count) },
                label = {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            )
        }
    }
}

@Composable
internal fun ChartCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun IndividualRatioChart(
    title: String,
    data: List<Double>,
    labels: List<String>,
    colorHex: String,
    chartTextColor: Int
) {
    ChartCard(title = title) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            factory = { context ->
                LineChart(context).apply {
                    description.isEnabled = false
                    legend.isEnabled = false
                    setExtraOffsets(8f, 8f, 8f, 8f)
                }
            },
            update = { chart ->
                val entries = data.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
                val dataSet = LineDataSet(entries, title).apply {
                    color = AndroidColor.parseColor(colorHex)
                    setCircleColor(AndroidColor.parseColor(colorHex))
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawValues(false)
                    setDrawCircleHole(false)
                    setDrawFilled(true)
                    fillColor = AndroidColor.parseColor(colorHex)
                    fillAlpha = 30
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                }

                chart.data = LineData(dataSet)
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
                        override fun getFormattedValue(value: Float): String = "%.0f%%".format(value)
                    }
                }
                chart.axisRight.isEnabled = false

                chart.marker = FinancialMarkerView(
                    chart.context, labels
                ) { value -> "%.1f%%".format(value) }
                chart.isHighlightPerTapEnabled = true

                chart.invalidate()
            }
        )
    }
}

// ========== Chart Update Functions ==========

private fun setupCommonChartProperties(chart: com.github.mikephil.charting.charts.Chart<*>, chartTextColor: Int) {
    chart.description.isEnabled = false
    chart.legend.isEnabled = true
    chart.legend.textColor = chartTextColor
    chart.setExtraOffsets(8f, 8f, 8f, 8f)
    if (chart is BarChart) {
        chart.setFitBars(true)
    }
}

private fun updateIncomeBarChart(chart: BarChart, summary: FinancialSummary, chartTextColor: Int) {
    // 첫 번째(가장 오래된) 분기 제외 — YTD→분기 변환 시 불완전할 수 있음
    val revenues = summary.revenues.drop(1)
    val operatingProfits = summary.operatingProfits.drop(1)
    val netIncomes = summary.netIncomes.drop(1)
    val labels = summary.displayPeriods.drop(1)
    if (revenues.isEmpty()) return

    val revenueEntries = revenues.mapIndexed { i, v -> BarEntry(i.toFloat(), v.toFloat()) }
    val opProfitEntries = operatingProfits.mapIndexed { i, v -> BarEntry(i.toFloat(), v.toFloat()) }
    val netIncomeEntries = netIncomes.mapIndexed { i, v -> BarEntry(i.toFloat(), v.toFloat()) }

    val revenueSet = BarDataSet(revenueEntries, "매출액").apply {
        color = AndroidColor.parseColor("#4CAF50")
    }
    val opProfitSet = BarDataSet(opProfitEntries, "영업이익").apply {
        color = AndroidColor.parseColor("#2196F3")
    }
    val netIncomeSet = BarDataSet(netIncomeEntries, "당기순이익").apply {
        color = AndroidColor.parseColor("#FF9800")
    }

    listOf(revenueSet, opProfitSet, netIncomeSet).forEach {
        it.setDrawValues(false)
    }

    val groupSpace = 0.1f
    val barSpace = 0.02f
    val barWidth = 0.26f

    chart.data = BarData(revenueSet, opProfitSet, netIncomeSet).apply {
        this.barWidth = barWidth
    }

    chart.xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        valueFormatter = IndexAxisValueFormatter(labels)
        granularity = 1f
        setDrawGridLines(false)
        setCenterAxisLabels(true)
        axisMinimum = 0f
        axisMaximum = labels.size.toFloat()
        textColor = chartTextColor
    }

    chart.axisLeft.apply {
        setDrawGridLines(true)
        textColor = chartTextColor
        valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return formatNumber(value.toLong())
            }
        }
    }
    chart.axisRight.isEnabled = false

    chart.marker = FinancialMarkerView(
        chart.context, labels
    ) { value -> formatNumber(value.toLong()) }
    chart.isHighlightPerTapEnabled = true

    chart.groupBars(0f, groupSpace, barSpace)
    chart.invalidate()
}

private fun updateGrowthLineChart(chart: LineChart, summary: FinancialSummary, chartTextColor: Int) {
    val dataSets = mutableListOf<LineDataSet>()

    val revenueGrowth = summary.revenueGrowthRates.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
    dataSets.add(createLineDataSet(revenueGrowth, "매출액 증가율", "#4CAF50"))

    val opProfitGrowth = summary.operatingProfitGrowthRates.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
    dataSets.add(createLineDataSet(opProfitGrowth, "영업이익 증가율", "#2196F3"))

    val netIncomeGrowth = summary.netIncomeGrowthRates.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
    dataSets.add(createLineDataSet(netIncomeGrowth, "순이익 증가율", "#FF9800"))

    chart.data = LineData(dataSets.toList())
    setupLineChartXAxis(chart, summary.displayPeriods, chartTextColor)
    chart.axisLeft.apply {
        setDrawGridLines(true)
        textColor = chartTextColor
        valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = "%.1f%%".format(value)
        }
    }
    chart.axisRight.isEnabled = false

    chart.marker = FinancialMarkerView(
        chart.context, summary.displayPeriods
    ) { value -> "%.1f%%".format(value) }
    chart.isHighlightPerTapEnabled = true

    chart.invalidate()
}

private fun updateAssetGrowthChart(chart: LineChart, summary: FinancialSummary, chartTextColor: Int) {
    val dataSets = mutableListOf<LineDataSet>()

    val equityGrowth = summary.equityGrowthRates.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
    dataSets.add(createLineDataSet(equityGrowth, "자기자본 증가율", "#9C27B0"))

    val totalAssetsGrowth = summary.totalAssetsGrowthRates.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
    dataSets.add(createLineDataSet(totalAssetsGrowth, "총자산 증가율", "#00BCD4"))

    chart.data = LineData(dataSets.toList())
    setupLineChartXAxis(chart, summary.displayPeriods, chartTextColor)
    chart.axisLeft.apply {
        setDrawGridLines(true)
        textColor = chartTextColor
        valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = "%.1f%%".format(value)
        }
    }
    chart.axisRight.isEnabled = false

    chart.marker = FinancialMarkerView(
        chart.context, summary.displayPeriods
    ) { value -> "%.1f%%".format(value) }
    chart.isHighlightPerTapEnabled = true

    chart.invalidate()
}

private fun updateCombinedStabilityChart(chart: LineChart, summary: FinancialSummary, chartTextColor: Int) {
    val dataSets = mutableListOf<LineDataSet>()

    val debtEntries = summary.debtRatios.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
    dataSets.add(LineDataSet(debtEntries, "부채비율").apply {
        color = AndroidColor.parseColor("#F44336")
        setCircleColor(AndroidColor.parseColor("#F44336"))
        lineWidth = 2f; circleRadius = 3f; setDrawValues(false); setDrawCircleHole(false)
        mode = LineDataSet.Mode.CUBIC_BEZIER
    })

    val currentEntries = summary.currentRatios.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
    dataSets.add(LineDataSet(currentEntries, "유동비율").apply {
        color = AndroidColor.parseColor("#4CAF50")
        setCircleColor(AndroidColor.parseColor("#4CAF50"))
        lineWidth = 2f; circleRadius = 3f; setDrawValues(false); setDrawCircleHole(false)
        mode = LineDataSet.Mode.CUBIC_BEZIER
    })

    val borrowingEntries = summary.borrowingDependencies.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
    dataSets.add(LineDataSet(borrowingEntries, "차입금의존도").apply {
        color = AndroidColor.parseColor("#FF9800")
        setCircleColor(AndroidColor.parseColor("#FF9800"))
        lineWidth = 2f; circleRadius = 3f; setDrawValues(false); setDrawCircleHole(false)
        mode = LineDataSet.Mode.CUBIC_BEZIER
    })

    chart.data = LineData(dataSets.toList())
    chart.xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        valueFormatter = IndexAxisValueFormatter(summary.displayPeriods)
        granularity = 1f
        setDrawGridLines(false)
        textColor = chartTextColor
    }
    chart.axisLeft.apply {
        setDrawGridLines(true)
        textColor = chartTextColor
        valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = "%.0f%%".format(value)
        }
    }
    chart.axisRight.isEnabled = false

    chart.marker = FinancialMarkerView(
        chart.context, summary.displayPeriods
    ) { value -> "%.1f%%".format(value) }
    chart.isHighlightPerTapEnabled = true

    chart.invalidate()
}

// ========== Chart Helpers ==========

private fun createLineDataSet(entries: List<Entry>, label: String, colorHex: String): LineDataSet {
    return LineDataSet(entries, label).apply {
        color = AndroidColor.parseColor(colorHex)
        setCircleColor(AndroidColor.parseColor(colorHex))
        lineWidth = 2f
        circleRadius = 3f
        setDrawValues(false)
        setDrawCircleHole(false)
        mode = LineDataSet.Mode.CUBIC_BEZIER
    }
}

private fun setupLineChartXAxis(chart: LineChart, labels: List<String>, chartTextColor: Int) {
    chart.xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        valueFormatter = IndexAxisValueFormatter(labels)
        granularity = 1f
        setDrawGridLines(false)
        textColor = chartTextColor
    }
}

// ========== Evaluation Functions ==========

private val colorGreen = Color(0xFF4CAF50)
private val colorOrange = Color(0xFFFF9800)
private val colorRed = Color(0xFFF44336)

private fun evaluateDebtRatio(value: Double): Pair<String, Color> = when {
    value < 100.0 -> "양호" to colorGreen
    value < 200.0 -> "보통" to colorOrange
    else -> "주의" to colorRed
}

private fun evaluateCurrentRatio(value: Double): Pair<String, Color> = when {
    value >= 200.0 -> "양호" to colorGreen
    value >= 100.0 -> "보통" to colorOrange
    else -> "주의" to colorRed
}

private fun evaluateBorrowingDependency(value: Double): Pair<String, Color> = when {
    value < 30.0 -> "양호" to colorGreen
    value < 50.0 -> "보통" to colorOrange
    else -> "주의" to colorRed
}
