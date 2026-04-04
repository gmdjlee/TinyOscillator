package com.tinyoscillator.presentation.chart.bridge

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.github.mikephil.charting.charts.CombinedChart

class ChartXBridge {

    data class XAxisState(
        val lowestVisibleX: Float = 0f,
        val highestVisibleX: Float = 100f,
        val contentLeft: Float = 0f,
        val contentRight: Float = 1f,
    )

    private val _state = mutableStateOf(XAxisState())
    val state: State<XAxisState> = _state

    fun update(chart: CombinedChart) {
        _state.value = XAxisState(
            lowestVisibleX = chart.lowestVisibleX,
            highestVisibleX = chart.highestVisibleX,
            contentLeft = chart.viewPortHandler.contentLeft(),
            contentRight = chart.viewPortHandler.contentRight(),
        )
    }

    fun indexToX(index: Int): Float {
        val s = _state.value
        val visibleRange = s.highestVisibleX - s.lowestVisibleX
        if (visibleRange <= 0f) return 0f
        val fraction = (index - s.lowestVisibleX) / visibleRange
        val contentWidth = s.contentRight - s.contentLeft
        return s.contentLeft + fraction * contentWidth
    }
}
