package com.tinyoscillator.presentation.fundamental

import android.content.Context
import android.graphics.Color as AndroidColor
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.tinyoscillator.R
import com.tinyoscillator.domain.model.FundamentalHistoryData
import com.tinyoscillator.presentation.financial.ChartCard
import com.tinyoscillator.presentation.financial.FinancialMarkerView
import java.text.DecimalFormat

@Composable
fun FundamentalHistoryCharts(
    data: List<FundamentalHistoryData>,
    stockName: String,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val chartTextColor = if (isDarkTheme) AndroidColor.WHITE else AndroidColor.DKGRAY

    // 날짜 라벨: yyyyMMdd → MM/dd
    val dateLabels = data.map { d ->
        if (d.date.length == 8) "${d.date.substring(4, 6)}/${d.date.substring(6, 8)}"
        else d.date
    }

    val latest = data.lastOrNull()
    val numberFmt = DecimalFormat("#,##0")
    val ratioFmt = DecimalFormat("#,##0.00")

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary Card
        if (latest != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "$stockName 투자지표 요약",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider()
                    SummaryRow("종가", numberFmt.format(latest.close))
                    SummaryRow("PER", ratioFmt.format(latest.per))
                    SummaryRow("PBR", ratioFmt.format(latest.pbr))
                    SummaryRow("EPS", numberFmt.format(latest.eps))
                    SummaryRow("BPS", numberFmt.format(latest.bps))
                    SummaryRow("DPS", numberFmt.format(latest.dps))
                    SummaryRow("배당수익률", "${ratioFmt.format(latest.dividendYield)}%")
                }
            }
        }

        // PER Chart
        if (data.any { it.per != 0.0 }) {
            FundamentalLineChart(
                title = "PER 추이",
                entries = data.mapIndexed { i, d -> Entry(i.toFloat(), d.per.toFloat()) },
                labels = dateLabels,
                colorHex = "#2196F3",
                chartTextColor = chartTextColor,
                valueFormat = { "%.2f".format(it) }
            )
        }

        // PBR Chart
        if (data.any { it.pbr != 0.0 }) {
            FundamentalLineChart(
                title = "PBR 추이",
                entries = data.mapIndexed { i, d -> Entry(i.toFloat(), d.pbr.toFloat()) },
                labels = dateLabels,
                colorHex = "#4CAF50",
                chartTextColor = chartTextColor,
                valueFormat = { "%.2f".format(it) }
            )
        }

        // EPS / BPS Chart
        if (data.any { it.eps != 0L || it.bps != 0L }) {
            ChartCard(title = "EPS / BPS 추이") {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    factory = { context ->
                        LineChart(context).apply {
                            description.isEnabled = false
                            legend.isEnabled = true
                            legend.textColor = chartTextColor
                            setExtraOffsets(8f, 8f, 8f, 8f)
                        }
                    },
                    update = { chart ->
                        val epsEntries = data.mapIndexed { i, d -> Entry(i.toFloat(), d.eps.toFloat()) }
                        val bpsEntries = data.mapIndexed { i, d -> Entry(i.toFloat(), d.bps.toFloat()) }

                        val epsSet = createLineDataSet(epsEntries, "EPS", "#FF9800")
                        val bpsSet = createLineDataSet(bpsEntries, "BPS", "#9C27B0")

                        chart.data = LineData(epsSet, bpsSet)
                        val gridColor = if (isDarkTheme) AndroidColor.parseColor("#444444") else AndroidColor.parseColor("#CCCCCC")
                        chart.legend.textColor = chartTextColor
                        setupXAxis(chart, dateLabels, chartTextColor)
                        chart.axisLeft.apply {
                            setDrawGridLines(true)
                            textColor = chartTextColor
                            this.gridColor = gridColor
                            valueFormatter = object : ValueFormatter() {
                                override fun getFormattedValue(value: Float): String =
                                    numberFmt.format(value.toLong())
                            }
                        }
                        chart.axisRight.isEnabled = false
                        chart.marker = FinancialMarkerView(
                            chart.context, dateLabels
                        ) { value -> numberFmt.format(value.toLong()) }
                        chart.isHighlightPerTapEnabled = true
                        chart.invalidate()
                    }
                )
            }
        }

        // DPS / Dividend Yield Chart (Line)
        if (data.any { it.dps != 0L || it.dividendYield != 0.0 }) {
            ChartCard(title = "배당 정보 추이") {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    factory = { context ->
                        LineChart(context).apply {
                            description.isEnabled = false
                            legend.isEnabled = true
                            legend.textColor = chartTextColor
                            setExtraOffsets(8f, 8f, 8f, 8f)
                        }
                    },
                    update = { chart ->
                        val dpsEntries = data.mapIndexed { i, d -> Entry(i.toFloat(), d.dps.toFloat()) }
                        val yieldEntries = data.mapIndexed { i, d -> Entry(i.toFloat(), d.dividendYield.toFloat()) }

                        val dpsSet = createLineDataSet(dpsEntries, "DPS", "#E91E63")
                        val yieldSet = createLineDataSet(yieldEntries, "배당수익률(%)", "#00BCD4")

                        chart.data = LineData(dpsSet, yieldSet)
                        chart.legend.textColor = chartTextColor
                        setupXAxis(chart, dateLabels, chartTextColor)
                        chart.axisLeft.apply {
                            setDrawGridLines(true)
                            textColor = chartTextColor
                            gridColor = if (isDarkTheme) AndroidColor.parseColor("#444444") else AndroidColor.parseColor("#CCCCCC")
                        }
                        chart.axisRight.isEnabled = false
                        chart.marker = FinancialMarkerView(
                            chart.context, dateLabels
                        ) { value -> numberFmt.format(value.toLong()) }
                        chart.isHighlightPerTapEnabled = true
                        chart.invalidate()
                    }
                )
            }
        }

        // 분기별 데이터 집계 (각 분기 마지막 데이터 포인트 사용, EPS/BPS 0인 불완전 데이터 제외)
        val quarterlyData = data
            .filter { it.date.length == 8 && (it.eps != 0L || it.bps != 0L) }
            .groupBy { d ->
                val month = d.date.substring(4, 6).toIntOrNull() ?: 0
                val year = d.date.substring(0, 4)
                val q = when {
                    month <= 3 -> "Q1"
                    month <= 6 -> "Q2"
                    month <= 9 -> "Q3"
                    else -> "Q4"
                }
                "$year $q"
            }
            .mapValues { (_, items) -> items.last() }
            .toSortedMap()
            .values.toList()

        val quarterLabels = quarterlyData.map { d ->
            val year = d.date.substring(2, 4)
            val month = d.date.substring(4, 6).toIntOrNull() ?: 0
            val q = when {
                month <= 3 -> "Q1"
                month <= 6 -> "Q2"
                month <= 9 -> "Q3"
                else -> "Q4"
            }
            "$year.$q"
        }

        // ROE 추이 Chart (분기별, EPS / BPS 기반)
        val quarterlyRoe = quarterlyData.filter { it.bps != 0L }
        if (quarterlyRoe.isNotEmpty()) {
            val roeLabels = quarterlyRoe.map { d ->
                val year = d.date.substring(2, 4)
                val month = d.date.substring(4, 6).toIntOrNull() ?: 0
                val q = when {
                    month <= 3 -> "Q1"; month <= 6 -> "Q2"
                    month <= 9 -> "Q3"; else -> "Q4"
                }
                "$year.$q"
            }
            val roeEntries = quarterlyRoe.mapIndexed { i, d ->
                Entry(i.toFloat(), (d.eps.toDouble() / d.bps.toDouble() * 100).toFloat())
            }
            FundamentalLineChart(
                title = "ROE 추이 (분기)",
                entries = roeEntries,
                labels = roeLabels,
                colorHex = "#FF5722",
                chartTextColor = chartTextColor,
                valueFormat = { "%.2f%%".format(it) }
            )
        }

        // PBR vs ROE 산점도 (분기 순서 라인 연결)
        val quarterlyPbrRoe = quarterlyData.filter { it.bps != 0L && it.pbr != 0.0 }
        if (quarterlyPbrRoe.size >= 2) {
            ChartCard(title = "PBR vs ROE (분기)") {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    factory = { context ->
                        LineChart(context).apply {
                            description.isEnabled = false
                            legend.isEnabled = false
                            setExtraOffsets(12f, 8f, 12f, 8f)
                        }
                    },
                    update = { chart ->
                        // 분기 순서대로 (PBR, ROE) — PBR(x축) 기준 정렬 필수 (MPAndroidChart 요구사항)
                        val entries = quarterlyPbrRoe.map { d ->
                            val roe = (d.eps.toDouble() / d.bps.toDouble() * 100).toFloat()
                            val year = d.date.substring(2, 4)
                            val month = d.date.substring(4, 6).toIntOrNull() ?: 0
                            val q = when {
                                month <= 3 -> "Q1"; month <= 6 -> "Q2"
                                month <= 9 -> "Q3"; else -> "Q4"
                            }
                            Entry(d.pbr.toFloat(), roe).also { it.data = "$year.$q" }
                        }.sortedBy { it.x }

                        val dataSet = LineDataSet(entries, "PBR vs ROE").apply {
                            color = AndroidColor.parseColor("#9C27B0")
                            setCircleColor(AndroidColor.parseColor("#9C27B0"))
                            lineWidth = 1.5f
                            circleRadius = 5f
                            setDrawCircleHole(true)
                            circleHoleRadius = 2.5f
                            setDrawValues(true)
                            valueTextSize = 9f
                            valueTextColor = chartTextColor
                            valueFormatter = object : ValueFormatter() {
                                override fun getPointLabel(entry: Entry): String =
                                    entry.data as? String ?: ""
                            }
                            mode = LineDataSet.Mode.LINEAR
                        }

                        chart.data = LineData(dataSet)

                        // 데이터 범위 기반 축 설정
                        val pbrValues = entries.map { it.x }
                        val roeValues = entries.map { it.y }
                        val pbrMin = pbrValues.min()
                        val pbrMax = pbrValues.max()
                        val roeMin = roeValues.min()
                        val roeMax = roeValues.max()
                        val pbrRange = (pbrMax - pbrMin).coerceAtLeast(0.01f)
                        val roeRange = (roeMax - roeMin).coerceAtLeast(0.1f)
                        val pbrMargin = pbrRange * 0.15f
                        val roeMargin = roeRange * 0.15f

                        val pbrRoeGridColor = if (isDarkTheme) AndroidColor.parseColor("#444444") else AndroidColor.parseColor("#CCCCCC")
                        chart.xAxis.apply {
                            position = XAxis.XAxisPosition.BOTTOM
                            setDrawGridLines(true)
                            textColor = chartTextColor
                            gridColor = pbrRoeGridColor
                            axisMinimum = (pbrMin - pbrMargin).coerceAtLeast(0f)
                            axisMaximum = pbrMax + pbrMargin
                            // 데이터 범위에 따라 틱 간격 조정
                            granularity = when {
                                pbrRange < 0.1f -> 0.01f
                                pbrRange < 0.5f -> 0.05f
                                pbrRange < 1f -> 0.1f
                                else -> 0.2f
                            }
                            labelCount = 6
                            valueFormatter = object : ValueFormatter() {
                                override fun getFormattedValue(value: Float): String =
                                    "%.2f".format(value)
                            }
                        }
                        chart.axisLeft.apply {
                            setDrawGridLines(true)
                            textColor = chartTextColor
                            gridColor = pbrRoeGridColor
                            axisMinimum = roeMin - roeMargin
                            axisMaximum = roeMax + roeMargin
                            granularity = when {
                                roeRange < 1f -> 0.1f
                                roeRange < 5f -> 0.5f
                                roeRange < 10f -> 1f
                                else -> 2f
                            }
                            labelCount = 6
                            valueFormatter = object : ValueFormatter() {
                                override fun getFormattedValue(value: Float): String =
                                    "%.1f%%".format(value)
                            }
                        }
                        chart.axisRight.isEnabled = false

                        chart.marker = PbrRoeMarkerView(chart.context)
                        (chart.marker as MarkerView).chartView = chart
                        chart.isHighlightPerTapEnabled = true

                        chart.invalidate()
                    }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "X축: PBR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Y축: ROE(%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun FundamentalLineChart(
    title: String,
    entries: List<Entry>,
    labels: List<String>,
    colorHex: String,
    chartTextColor: Int,
    valueFormat: (Float) -> String
) {
    val isDark = chartTextColor == AndroidColor.WHITE
    ChartCard(title = title) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            factory = { context ->
                LineChart(context).apply {
                    description.isEnabled = false
                    legend.isEnabled = false
                    setExtraOffsets(8f, 8f, 8f, 8f)
                }
            },
            update = { chart ->
                val dataSet = createLineDataSet(entries, title, colorHex).apply {
                    setDrawFilled(true)
                    fillColor = AndroidColor.parseColor(colorHex)
                    fillAlpha = 30
                }

                chart.data = LineData(dataSet)
                setupXAxis(chart, labels, chartTextColor)
                chart.axisLeft.apply {
                    setDrawGridLines(true)
                    textColor = chartTextColor
                    gridColor = if (isDark) AndroidColor.parseColor("#444444") else AndroidColor.parseColor("#CCCCCC")
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String = valueFormat(value)
                    }
                }
                chart.axisRight.isEnabled = false
                chart.marker = FinancialMarkerView(
                    chart.context, labels
                ) { value -> valueFormat(value) }
                chart.isHighlightPerTapEnabled = true
                chart.invalidate()
            }
        )
    }
}

private fun createLineDataSet(entries: List<Entry>, label: String, colorHex: String): LineDataSet {
    return LineDataSet(entries, label).apply {
        color = AndroidColor.parseColor(colorHex)
        setCircleColor(AndroidColor.parseColor(colorHex))
        lineWidth = 2f
        circleRadius = 3f
        setDrawValues(false)
        setDrawCircleHole(false)
        mode = if (entries.size >= 2) LineDataSet.Mode.CUBIC_BEZIER else LineDataSet.Mode.LINEAR
    }
}

private fun setupXAxis(chart: LineChart, labels: List<String>, chartTextColor: Int) {
    chart.xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        valueFormatter = IndexAxisValueFormatter(labels)
        granularity = 1f
        setDrawGridLines(false)
        textColor = chartTextColor
        labelRotationAngle = -45f
    }
}

/** PBR vs ROE 산점도 마커 — Entry.data에 분기 라벨이 저장되어 있음 */
private class PbrRoeMarkerView(
    context: Context
) : MarkerView(context, R.layout.chart_marker_view) {
    private val tvContent: TextView = findViewById(R.id.marker_text)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e != null) {
            val quarter = e.data as? String ?: ""
            tvContent.text = "$quarter\nPBR: ${"%.2f".format(e.x)}\nROE: ${"%.1f%%".format(e.y)}"
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat())
    }
}
