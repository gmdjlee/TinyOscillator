package com.tinyoscillator.presentation.chart

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.utils.Utils
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.tinyoscillator.R
import com.tinyoscillator.domain.model.ConsensusChartData
import java.text.NumberFormat
import java.util.Locale

/**
 * 컨센서스 차트 Composable
 *
 * - 왼쪽 Y축: 시가총액 (조원) — LineChart
 * - 오른쪽 Y축: 목표가 (원) — ScatterChart
 * - X축: 날짜
 */
@Composable
fun ConsensusChart(
    chartData: ConsensusChartData,
    modifier: Modifier = Modifier
) {
    val lastBound = remember { arrayOfNulls<ConsensusChartData>(1) }
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val chartTextColor = if (isDarkTheme) Color.WHITE else Color.DKGRAY

    val gridColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${chartData.stockName} 시가총액 & 목표가",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))

            AndroidView(
                factory = { context ->
                    CombinedChart(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setupConsensusChart(this, chartTextColor, gridColor)
                    }
                },
                update = { chart ->
                    chart.xAxis.textColor = chartTextColor
                    chart.legend.textColor = chartTextColor
                    chart.axisLeft.textColor = chartTextColor
                    chart.axisRight.textColor = chartTextColor
                    chart.axisLeft.gridColor = gridColor
                    chart.axisRight.gridColor = gridColor
                    if (chartData != lastBound[0]) {
                        bindConsensusData(chart, chartData, surfaceColor)
                        lastBound[0] = chartData
                    }
                    chart.invalidate()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )
        }
    }
}

private fun setupConsensusChart(chart: CombinedChart, chartTextColor: Int, gridColor: Int) {
    chart.apply {
        description.isEnabled = false
        setDrawGridBackground(false)
        setDrawBarShadow(false)
        isHighlightFullBarEnabled = false
        setDrawOrder(arrayOf(CombinedChart.DrawOrder.LINE, CombinedChart.DrawOrder.SCATTER))

        val dashLen = Utils.convertDpToPixel(4f)
        val dashGap = Utils.convertDpToPixel(4f)
        val labelCount = 6

        setExtraOffsets(8f, 8f, 8f, 8f)

        axisLeft.apply {
            setDrawGridLines(true)
            this.gridColor = gridColor
            gridLineWidth = 0.5f
            enableGridDashedLine(dashLen, dashGap, 0f)
            textColor = chartTextColor
            setLabelCount(labelCount, true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format("%.1f조", value)
                }
            }
        }

        axisRight.apply {
            setDrawGridLines(false)
            this.gridColor = gridColor
            gridLineWidth = 0.5f
            enableGridDashedLine(dashLen, dashGap, 0f)
            textColor = chartTextColor
            setLabelCount(labelCount, true)
            valueFormatter = object : ValueFormatter() {
                private val nf = NumberFormat.getNumberInstance(Locale.KOREA)
                override fun getFormattedValue(value: Float): String {
                    return "${nf.format(value.toLong())}원"
                }
            }
        }

        xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            setDrawGridLines(false)
            textColor = chartTextColor
        }

        legend.apply {
            isEnabled = true
            verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
            setDrawInside(false)
            textColor = chartTextColor
        }

        setTouchEnabled(true)
        isDragEnabled = true
        setScaleEnabled(true)
        setPinchZoom(true)
    }
}

private fun bindConsensusData(
    chart: CombinedChart,
    chartData: ConsensusChartData,
    surfaceColor: Int
) {
    val dates = chartData.dates
    if (dates.isEmpty()) return

    val labels = dates.map { date ->
        if (date.length >= 8) {
            "${date.substring(4, 6)}/${date.substring(6, 8)}"
        } else date
    }
    chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)

    // 시가총액 (조원) — 왼쪽 Y축
    val mcapEntries = chartData.marketCaps.mapIndexed { i, cap ->
        Entry(i.toFloat(), (cap / 1_000_000_000_000.0).toFloat())
    }
    val mcapColor = Color.parseColor("#1976D2")
    val mcapDataSet = LineDataSet(mcapEntries, "${chartData.stockName} 시가총액(조)").apply {
        color = mcapColor
        lineWidth = 2f
        setDrawCircles(false)
        setDrawValues(false)
        axisDependency = YAxis.AxisDependency.LEFT
        isHighlightEnabled = true
        highLightColor = mcapColor
    }

    // 왼쪽 Y축 범위를 데이터에 맞게 fitting
    val mcapValues = mcapEntries.map { it.y }
    val mcapMin = mcapValues.min()
    val mcapMax = mcapValues.max()
    val mcapPadding = (mcapMax - mcapMin) * 0.05f
    chart.axisLeft.axisMinimum = mcapMin - mcapPadding
    chart.axisLeft.axisMaximum = mcapMax + mcapPadding

    // 목표가 (원) — 오른쪽 Y축 (ScatterChart)
    val reportLabels = chartData.reportDates.map { date ->
        if (date.length >= 8) {
            "${date.substring(4, 6)}/${date.substring(6, 8)}"
        } else date
    }
    val scatterEntries = chartData.reportDates.mapIndexedNotNull { i, reportDate ->
        val xIndex = dates.indexOf(reportDate)
        if (xIndex >= 0) {
            Entry(xIndex.toFloat(), chartData.reportTargetPrices[i].toFloat())
        } else null
    }
    val targetColor = Color.parseColor("#D32F2F")
    val scatterDataSet = ScatterDataSet(scatterEntries, "목표가(원)").apply {
        color = targetColor
        setScatterShape(com.github.mikephil.charting.charts.ScatterChart.ScatterShape.CIRCLE)
        scatterShapeSize = 16f
        setDrawValues(false)
        axisDependency = YAxis.AxisDependency.RIGHT
        isHighlightEnabled = true
        highLightColor = targetColor
    }

    // 오른쪽 Y축 범위
    if (scatterEntries.isNotEmpty()) {
        val targetValues = scatterEntries.map { it.y }
        val targetMin = targetValues.min()
        val targetMax = targetValues.max()
        val targetPadding = (targetMax - targetMin) * 0.1f
        chart.axisRight.axisMinimum = targetMin - targetPadding
        chart.axisRight.axisMaximum = targetMax + targetPadding
    }

    chart.data = CombinedData().apply {
        setData(LineData(mcapDataSet))
        setData(ScatterData(scatterDataSet))
    }

    // 마커 설정
    val marker = ConsensusMarkerView(chart.context, labels)
    marker.chartView = chart
    chart.marker = marker

    chart.invalidate()
}

/** 차트 데이터 포인트 선택 시 값을 표시하는 MarkerView */
class ConsensusMarkerView(
    context: Context,
    private val labels: List<String>
) : MarkerView(context, R.layout.chart_marker_view) {

    private val tvContent: TextView = findViewById(R.id.marker_text)
    private val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e == null || highlight == null) {
            super.refreshContent(e, highlight)
            return
        }

        val xIndex = e.x.toInt()
        val date = labels.getOrElse(xIndex) { "" }

        val valueText = when (highlight.dataSetIndex) {
            0 -> "시가총액: ${String.format("%.2f", e.y)}조"
            1 -> "목표가: ${numberFormat.format(e.y.toLong())}원"
            else -> String.format("%.2f", e.y)
        }

        tvContent.text = "$date\n$valueText"
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat())
    }
}
