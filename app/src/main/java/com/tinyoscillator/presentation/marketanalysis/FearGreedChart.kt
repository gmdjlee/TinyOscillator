package com.tinyoscillator.presentation.marketanalysis

import android.content.Context
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.github.mikephil.charting.utils.Utils
import com.tinyoscillator.R
import com.tinyoscillator.domain.model.FearGreedChartData
import com.tinyoscillator.presentation.chart.ChartTheme
import com.tinyoscillator.presentation.chart.rememberChartTheme

/**
 * Fear & Greed 차트 Composable
 *
 * - 왼쪽 Y축: 시장 지수 — neutralLine
 * - 오른쪽 Y축: F&G 오실레이터 (%) — emphasisLine + 부호별 positive/negative 원
 * - X축: 날짜 (MM/dd)
 */
@Composable
fun FearGreedChart(
    chartData: FearGreedChartData,
    modifier: Modifier = Modifier
) {
    val lastBound = remember { arrayOfNulls<FearGreedChartData>(1) }
    val theme = rememberChartTheme()

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${chartData.market} Fear & Greed 오실레이터",
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
                        setupFearGreedChart(this, theme)
                    }
                },
                update = { chart ->
                    chart.xAxis.textColor = theme.axisText
                    chart.legend.textColor = theme.axisText
                    chart.axisLeft.textColor = theme.axisText
                    chart.axisRight.textColor = theme.axisText
                    chart.axisLeft.gridColor = theme.grid
                    chart.axisRight.gridColor = theme.grid
                    if (chartData != lastBound[0]) {
                        bindFearGreedData(chart, chartData, theme)
                        lastBound[0] = chartData
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )
        }
    }
}

private fun setupFearGreedChart(chart: CombinedChart, theme: ChartTheme) {
    chart.apply {
        description.isEnabled = false
        setDrawGridBackground(false)
        setDrawBarShadow(false)
        isHighlightFullBarEnabled = false
        setDrawOrder(arrayOf(CombinedChart.DrawOrder.LINE))

        val gColor = theme.grid
        val dashLen = Utils.convertDpToPixel(4f)
        val dashGap = Utils.convertDpToPixel(4f)

        val labelCount = 6

        setExtraOffsets(8f, 8f, 8f, 8f)

        // 왼쪽 Y축: 시장 지수
        axisLeft.apply {
            setDrawGridLines(true)
            gridColor = gColor
            gridLineWidth = 0.5f
            enableGridDashedLine(dashLen, dashGap, 0f)
            textColor = theme.axisText
            setLabelCount(labelCount, true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format("%.0f", value)
                }
            }
        }

        // 오른쪽 Y축: F&G 오실레이터 (%)
        axisRight.apply {
            setDrawGridLines(false)
            gridColor = gColor
            gridLineWidth = 0.5f
            enableGridDashedLine(dashLen, dashGap, 0f)
            textColor = theme.axisText
            setLabelCount(labelCount, true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format("%.1f%%", value)
                }
            }
        }

        xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            setDrawGridLines(false)
            textColor = theme.axisText
        }

        legend.apply {
            isEnabled = true
            verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
            setDrawInside(false)
            textColor = theme.axisText
        }

        setTouchEnabled(true)
        isDragEnabled = true
        setScaleEnabled(true)
        setPinchZoom(true)
    }
}

private fun bindFearGreedData(
    chart: CombinedChart,
    chartData: FearGreedChartData,
    theme: ChartTheme
) {
    val rows = chartData.rows

    // X축 날짜 레이블 (MM/dd)
    val labels = rows.map { row ->
        if (row.date.length >= 10) {
            // ISO format: yyyy-MM-dd
            "${row.date.substring(5, 7)}/${row.date.substring(8, 10)}"
        } else if (row.date.length >= 8) {
            // yyyyMMdd format
            "${row.date.substring(4, 6)}/${row.date.substring(6, 8)}"
        } else row.date
    }
    chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)

    // 시장 지수 — 왼쪽 Y축
    val indexEntries = rows.mapIndexed { i, row ->
        Entry(i.toFloat(), row.indexValue.toFloat())
    }
    val indexDataSet = LineDataSet(indexEntries, "${chartData.market} 지수").apply {
        color = theme.neutralLine
        lineWidth = 2f
        setDrawCircles(false)
        setDrawValues(false)
        axisDependency = YAxis.AxisDependency.LEFT
        isHighlightEnabled = true
        highLightColor = theme.neutralLine
    }

    // 왼쪽 Y축 범위 fitting
    val indexValues = rows.map { it.indexValue.toFloat() }
    if (indexValues.isEmpty()) return
    val indexMin = indexValues.min()
    val indexMax = indexValues.max()
    val indexPadding = (indexMax - indexMin) * 0.05f
    chart.axisLeft.axisMinimum = indexMin - indexPadding
    chart.axisLeft.axisMaximum = indexMax + indexPadding

    // F&G 오실레이터 (%) — 오른쪽 Y축
    val oscEntries = rows.mapIndexed { i, row ->
        Entry(i.toFloat(), (row.oscillator * 100).toFloat())
    }
    val oscDataSet = LineDataSet(oscEntries, "F&G 오실레이터(%)").apply {
        color = theme.emphasisLine
        lineWidth = 1.5f
        setDrawCircles(true)
        circleRadius = 3f
        setDrawValues(false)
        axisDependency = YAxis.AxisDependency.RIGHT
        circleColors = rows.map { row ->
            if (row.oscillator >= 0) theme.positive else theme.negative
        }
        setCircleHoleColor(theme.holeFill)
        circleHoleRadius = 1.5f
        isHighlightEnabled = true
        highLightColor = theme.emphasisLine
    }

    // 오른쪽 Y축 범위 fitting
    val oscValues = rows.map { (it.oscillator * 100).toFloat() }
    val oscMin = oscValues.min()
    val oscMax = oscValues.max()
    val oscPadding = (oscMax - oscMin) * 0.05f
    chart.axisRight.axisMinimum = oscMin - oscPadding
    chart.axisRight.axisMaximum = oscMax + oscPadding

    chart.data = CombinedData().apply {
        setData(LineData(indexDataSet, oscDataSet))
    }

    // 마커 설정
    val marker = FearGreedMarkerView(chart.context, labels, chartData)
    marker.chartView = chart
    chart.marker = marker

    chart.invalidate()
}

/** Fear & Greed 차트 데이터 포인트 선택 시 값을 표시하는 MarkerView */
class FearGreedMarkerView(
    context: Context,
    private val labels: List<String>,
    private val chartData: FearGreedChartData
) : MarkerView(context, R.layout.chart_marker_view) {

    private val tvContent: TextView = findViewById(R.id.marker_text)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e == null || highlight == null) {
            super.refreshContent(e, highlight)
            return
        }

        val xIndex = e.x.toInt()
        val date = labels.getOrElse(xIndex) { "" }
        val row = chartData.rows.getOrNull(xIndex)

        val text = buildString {
            append(date)
            if (row != null) {
                append("\nF&G: ${String.format("%.1f", row.fearGreedValue * 100)}%")
                append("\n오실레이터: ${String.format("%.2f%%", row.oscillator * 100)}")
                append("\n지수: ${String.format("%.2f", row.indexValue)}")
            } else {
                when (highlight.dataSetIndex) {
                    0 -> append("\n지수: ${String.format("%.2f", e.y)}")
                    1 -> append("\n오실레이터: ${String.format("%.2f%%", e.y)}")
                    else -> append("\n${String.format("%.2f", e.y)}")
                }
            }
        }

        tvContent.text = text
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat())
    }
}
