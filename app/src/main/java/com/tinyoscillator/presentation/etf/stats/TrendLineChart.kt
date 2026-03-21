package com.tinyoscillator.presentation.etf.stats

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

@Composable
fun TrendLineChart(
    entries: List<Entry>,
    labels: List<String>,
    label: String,
    color: Int,
    modifier: Modifier = Modifier
) {
    val lastBound = remember { arrayOfNulls<Any>(1) }
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false
                setTouchEnabled(true)
                setScaleEnabled(false)

                val textColor = if (isDarkTheme) AndroidColor.WHITE else AndroidColor.DKGRAY

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    valueFormatter = IndexAxisValueFormatter(labels)
                    granularity = 1f
                    setDrawGridLines(false)
                    this.textColor = textColor
                    labelRotationAngle = -45f
                }
                axisLeft.apply {
                    this.textColor = textColor
                    setDrawGridLines(true)
                    gridColor = if (isDarkTheme) AndroidColor.GRAY else AndroidColor.LTGRAY
                }
                axisRight.isEnabled = false

                val dataSet = LineDataSet(entries, label).apply {
                    this.color = color
                    setCircleColor(color)
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawValues(false)
                    setDrawFilled(true)
                    fillColor = color
                    fillAlpha = 30
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                }

                this.data = LineData(dataSet)
                invalidate()
            }
        },
        update = { chart ->
            val textColor = if (isDarkTheme) AndroidColor.WHITE else AndroidColor.DKGRAY

            chart.xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                this.textColor = textColor
            }
            chart.axisLeft.textColor = textColor

            val boundKey = Triple(entries, labels, label)
            if (boundKey != lastBound[0]) {
                val dataSet = LineDataSet(entries, label).apply {
                    this.color = color
                    setCircleColor(color)
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawValues(false)
                    setDrawFilled(true)
                    fillColor = color
                    fillAlpha = 30
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                }

                chart.data = LineData(dataSet)
                lastBound[0] = boundKey
            }
            chart.invalidate()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
    )
}
