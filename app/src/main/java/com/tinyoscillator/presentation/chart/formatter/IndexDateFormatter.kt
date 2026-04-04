package com.tinyoscillator.presentation.chart.formatter

import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.formatter.ValueFormatter

/** X축 인덱스 → 날짜 문자열 매핑 포매터 */
class IndexDateFormatter(
    private val dateLabels: Map<Int, String>,
) : ValueFormatter() {
    override fun getAxisLabel(value: Float, axis: AxisBase?): String =
        dateLabels[value.toInt()] ?: ""
}
