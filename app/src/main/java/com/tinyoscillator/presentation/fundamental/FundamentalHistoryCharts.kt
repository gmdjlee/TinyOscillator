package com.tinyoscillator.presentation.fundamental

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
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
    val isDarkTheme = isSystemInDarkTheme()
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary Card
        if (latest != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
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
                        .height(250.dp),
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
                        chart.legend.textColor = chartTextColor
                        setupXAxis(chart, dateLabels, chartTextColor)
                        chart.axisLeft.apply {
                            setDrawGridLines(true)
                            textColor = chartTextColor
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

        // DPS / Dividend Yield Chart
        if (data.any { it.dps != 0L || it.dividendYield != 0.0 }) {
            ChartCard(title = "배당 정보 추이") {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
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
    ChartCard(title = title) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
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
        mode = LineDataSet.Mode.CUBIC_BEZIER
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
