package com.tinyoscillator.presentation.chart.bridge

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.github.mikephil.charting.charts.CombinedChart

data class ChartAxisRange(
    val yMin: Float,
    val yMax: Float,
    val chartHeight: Float,
)

class ChartAxisBridge {
    private val _axisRange = mutableStateOf(ChartAxisRange(0f, 1f, 1f))
    val axisRange: State<ChartAxisRange> = _axisRange

    fun update(chart: CombinedChart) {
        _axisRange.value = ChartAxisRange(
            yMin = chart.axisLeft.axisMinimum,
            yMax = chart.axisLeft.axisMaximum,
            chartHeight = chart.viewPortHandler.contentHeight(),
        )
    }
}
