package com.tinyoscillator.presentation.chart

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import com.tinyoscillator.domain.model.ChartData

/**
 * 수급 오실레이터 차트 Composable
 *
 * - 왼쪽 Y축: 시가총액 (조원) — LineChart
 * - 오른쪽 Y축: 오실레이터 — LineChart + 마커
 * - X축: 날짜
 */
@Composable
fun OscillatorChart(
    chartData: ChartData,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "${chartData.stockName} 시가총액 & 수급오실레이터",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        AndroidView(
            factory = { context ->
                CombinedChart(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setupChart(this)
                }
            },
            update = { chart ->
                bindData(chart, chartData)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        )
    }
}

private fun setupChart(chart: CombinedChart) {
    chart.apply {
        description.isEnabled = false
        setDrawGridBackground(false)
        setDrawBarShadow(false)
        isHighlightFullBarEnabled = false
        setDrawOrder(arrayOf(CombinedChart.DrawOrder.LINE))

        val gColor = Color.parseColor("#CCCCCC")
        val dashLen = Utils.convertDpToPixel(4f)
        val dashGap = Utils.convertDpToPixel(4f)

        val labelCount = 6

        axisLeft.apply {
            setDrawGridLines(true)
            gridColor = gColor
            gridLineWidth = 0.5f
            enableGridDashedLine(dashLen, dashGap, 0f)
            textColor = Color.parseColor("#1976D2")
            textSize = 16f
            setLabelCount(labelCount, true)
        }

        axisRight.apply {
            setDrawGridLines(false)
            gridColor = gColor
            gridLineWidth = 0.5f
            enableGridDashedLine(dashLen, dashGap, 0f)
            textColor = Color.parseColor("#388E3C")
            textSize = 16f
            setLabelCount(labelCount, true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format("%.2f%%", value)
                }
            }
        }

        xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            setDrawGridLines(false)
            labelRotationAngle = -45f
            textSize = 16f
        }

        legend.apply {
            isEnabled = true
            verticalAlignment = Legend.LegendVerticalAlignment.TOP
            horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
            setDrawInside(true)
            textSize = 16f
        }

        setTouchEnabled(true)
        isDragEnabled = true
        setScaleEnabled(true)
        setPinchZoom(true)
    }
}

private fun bindData(chart: CombinedChart, chartData: ChartData) {
    val rows = chartData.rows

    val labels = rows.map { row ->
        if (row.date.length >= 8) {
            "${row.date.substring(4, 6)}/${row.date.substring(6, 8)}"
        } else row.date
    }
    chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)

    // 시가총액 (조원) — 왼쪽 Y축
    val mcapEntries = rows.mapIndexed { i, row ->
        Entry(i.toFloat(), row.marketCapTril.toFloat())
    }
    val mcapDataSet = LineDataSet(mcapEntries, "${chartData.stockName} 시가총액(조)").apply {
        color = Color.parseColor("#1976D2")
        lineWidth = 2f
        setDrawCircles(false)
        setDrawValues(false)
        axisDependency = YAxis.AxisDependency.LEFT
        isHighlightEnabled = true
        highLightColor = Color.parseColor("#1976D2")
    }

    // 왼쪽 Y축 범위를 데이터에 맞게 fitting
    val mcapValues = rows.map { it.marketCapTril.toFloat() }
    val mcapMin = mcapValues.min()
    val mcapMax = mcapValues.max()
    val mcapPadding = (mcapMax - mcapMin) * 0.05f
    chart.axisLeft.axisMinimum = mcapMin - mcapPadding
    chart.axisLeft.axisMaximum = mcapMax + mcapPadding

    // 오실레이터 (%) — 오른쪽 Y축
    val oscEntries = rows.mapIndexed { i, row ->
        Entry(i.toFloat(), (row.oscillator * 100).toFloat())
    }
    val oscDataSet = LineDataSet(oscEntries, "수급오실레이터(%)").apply {
        color = Color.parseColor("#388E3C")
        lineWidth = 1.5f
        setDrawCircles(true)
        circleRadius = 3f
        setDrawValues(false)
        axisDependency = YAxis.AxisDependency.RIGHT
        circleColors = rows.map { row ->
            if (row.oscillator >= 0) Color.parseColor("#4CAF50")
            else Color.parseColor("#F44336")
        }
        setCircleHoleColor(Color.WHITE)
        circleHoleRadius = 1.5f
        isHighlightEnabled = true
        highLightColor = Color.parseColor("#388E3C")
    }

    chart.data = CombinedData().apply {
        setData(LineData(mcapDataSet, oscDataSet))
    }

    // 마커 설정
    val marker = OscillatorMarkerView(chart.context, labels)
    marker.chartView = chart
    chart.marker = marker

    chart.invalidate()
}

/** 차트 데이터 포인트 선택 시 값을 표시하는 MarkerView */
class OscillatorMarkerView(
    context: Context,
    private val labels: List<String>
) : MarkerView(context, R.layout.chart_marker_view) {

    private val tvContent: TextView = findViewById(R.id.marker_text)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e == null || highlight == null) {
            super.refreshContent(e, highlight)
            return
        }

        val xIndex = e.x.toInt()
        val date = labels.getOrElse(xIndex) { "" }

        val valueText = when (highlight.dataSetIndex) {
            0 -> "시가총액: ${String.format("%.2f", e.y)}조"
            1 -> "오실레이터: ${String.format("%.2f%%", e.y)}"
            else -> String.format("%.2f", e.y)
        }

        tvContent.text = "$date\n$valueText"
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat())
    }
}
