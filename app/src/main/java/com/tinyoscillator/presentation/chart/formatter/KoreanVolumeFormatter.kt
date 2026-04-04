package com.tinyoscillator.presentation.chart.formatter

import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.formatter.ValueFormatter

/**
 * 거래량 축 한국식 단위 포매터
 * 1조 이상 → "1조", 1억 이상 → "100억", 1만 이상 → "50만", 미만 → 정수
 */
class KoreanVolumeFormatter : ValueFormatter() {
    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
        val v = value.toDouble()
        return when {
            v >= 999_999_000_000.0 -> "${Math.round(v / 1e12)}조"
            v >= 100_000_000.0     -> "${Math.round(v / 1e8)}억"
            v >= 10_000.0          -> "${Math.round(v / 1e4)}만"
            else                   -> value.toInt().toString()
        }
    }
}
