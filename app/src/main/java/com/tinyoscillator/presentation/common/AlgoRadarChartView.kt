package com.tinyoscillator.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.RadarChart
import com.github.mikephil.charting.data.RadarData
import com.github.mikephil.charting.data.RadarDataSet
import com.github.mikephil.charting.data.RadarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.tinyoscillator.domain.model.AlgoResult

@Composable
fun AlgoRadarChartView(
    algoResults: Map<String, AlgoResult>,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            RadarChart(ctx).apply {
                description.isEnabled = false
                legend.isEnabled = true
                webLineWidth = 0.5f
                webColor = android.graphics.Color.parseColor("#33888780")
                webLineWidthInner = 0.5f
                webColorInner = android.graphics.Color.parseColor("#22888780")
                webAlpha = 200

                xAxis.apply {
                    textSize = 10f
                    textColor = android.graphics.Color.parseColor("#888780")
                    yOffset = 0f
                    xOffset = 0f
                }
                yAxis.apply {
                    axisMinimum = 0f
                    axisMaximum = 1f
                    setLabelCount(3, false)
                    textSize = 8f
                }
                isRotationEnabled = false
            }
        },
        update = { chart ->
            val sorted = algoResults.entries.sortedBy { it.key }
            val entries = sorted.map { (_, r) -> RadarEntry(r.score) }
            val labels = sorted.map { (name, _) -> ALGO_DISPLAY_NAMES[name] ?: name }

            val set = RadarDataSet(entries, "신호 강도").apply {
                color = android.graphics.Color.parseColor("#D85A30")
                fillColor = android.graphics.Color.parseColor("#33D85A30")
                lineWidth = 1.5f
                setDrawFilled(true)
                setDrawValues(false)
                fillAlpha = 80
            }

            chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            chart.data = RadarData(set)
            chart.animateXY(300, 300)
            chart.invalidate()
        },
        modifier = modifier,
    )
}
