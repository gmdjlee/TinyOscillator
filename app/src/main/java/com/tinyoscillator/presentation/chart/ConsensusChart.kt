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
 * - Y축: 가격 (원) — 현재가 LineChart + 목표가 ScatterChart
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
                text = "${chartData.stockName} 현재가 & 목표가",
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
                    chart.axisLeft.gridColor = gridColor
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
        setDrawOrder(arrayOf(CombinedChart.DrawOrder.LINE))

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
                private val nf = NumberFormat.getNumberInstance(Locale.KOREA)
                override fun getFormattedValue(value: Float): String {
                    return "${nf.format(value.toLong())}원"
                }
            }
        }

        axisRight.isEnabled = false

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

    // 현재가 (원) — 왼쪽 Y축
    val priceEntries = chartData.closePrices.mapIndexed { i, price ->
        Entry(i.toFloat(), price.toFloat())
    }
    val priceColor = Color.parseColor("#1976D2")
    val priceDataSet = LineDataSet(priceEntries, "${chartData.stockName} 현재가(원)").apply {
        color = priceColor
        lineWidth = 2f
        setDrawCircles(false)
        setDrawValues(false)
        axisDependency = YAxis.AxisDependency.LEFT
        isHighlightEnabled = true
        highLightColor = priceColor
    }

    // 목표가 (원) — 같은 Y축 (원만 표시하는 LineDataSet으로 구현, ScatterData는 CombinedChart 버그 회피)
    // MPAndroidChart requires entries sorted by x-value; unsorted entries cause NegativeArraySizeException
    val targetEntries = chartData.reportDates.mapIndexedNotNull { i, reportDate ->
        val xIndex = dates.indexOf(reportDate)
        if (xIndex >= 0) {
            Entry(xIndex.toFloat(), chartData.reportTargetPrices[i].toFloat())
        } else null
    }.sortedBy { it.x }
    val targetColor = Color.parseColor("#D32F2F")
    val targetDataSet = LineDataSet(targetEntries, "목표가(원)").apply {
        color = targetColor
        setDrawCircles(true)
        setCircleColor(targetColor)
        circleRadius = 5f
        circleHoleRadius = 2.5f
        lineWidth = 0f
        enableDashedLine(0f, Float.MAX_VALUE, 0f) // 선 숨기기
        setDrawValues(false)
        axisDependency = YAxis.AxisDependency.LEFT
        isHighlightEnabled = true
        highLightColor = targetColor
    }

    // Y축 범위: 현재가 + 목표가 전체를 포함
    val allValues = priceEntries.map { it.y } + targetEntries.map { it.y }
    if (allValues.isEmpty()) return
    val yMin = allValues.min()
    val yMax = allValues.max()
    val yPadding = (yMax - yMin) * 0.05f
    chart.axisLeft.axisMinimum = yMin - yPadding
    chart.axisLeft.axisMaximum = yMax + yPadding

    val lineDataSets = mutableListOf<LineDataSet>(priceDataSet)
    if (targetEntries.isNotEmpty()) {
        lineDataSets.add(targetDataSet)
    }
    chart.data = CombinedData().apply {
        setData(LineData(lineDataSets as List<com.github.mikephil.charting.interfaces.datasets.ILineDataSet>))
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
            0 -> "현재가: ${numberFormat.format(e.y.toLong())}원"
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
