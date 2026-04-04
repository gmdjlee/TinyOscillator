package com.tinyoscillator.presentation.chart.ext

import android.graphics.Color
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.tinyoscillator.domain.indicator.IndicatorCalculator
import com.tinyoscillator.domain.model.Indicator

object IndicatorColors {
    val EMA_SHORT = Color.parseColor("#EF9F27")       // amber
    val EMA_MID = Color.parseColor("#378ADD")         // blue
    val EMA_LONG = Color.parseColor("#1D9E75")        // teal
    val BOLL_UPPER = Color.parseColor("#88D85A30")    // coral 반투명
    val BOLL_MID = Color.parseColor("#88888780")      // gray 반투명
    val BOLL_LOWER = Color.parseColor("#88D85A30")
    val MACD_LINE = Color.parseColor("#378ADD")
    val MACD_SIGNAL = Color.parseColor("#EF9F27")
    val MACD_HIST_POS = Color.parseColor("#88D85A30")
    val MACD_HIST_NEG = Color.parseColor("#88378ADD")
    val RSI_LINE = Color.parseColor("#7F77DD")
    val STOCH_K = Color.parseColor("#378ADD")
    val STOCH_D = Color.parseColor("#EF9F27")
}

/** FloatArray(NaN 포함) -> LineDataSet (NaN은 건너뜀) */
fun FloatArray.toLineDataSet(label: String, color: Int, width: Float = 1.5f): LineDataSet {
    val entries = indices.mapNotNull { i ->
        if (this[i].isNaN()) null else Entry(i.toFloat(), this[i])
    }
    return LineDataSet(entries, label).apply {
        this.color = color
        lineWidth = width
        setDrawCircles(false)
        setDrawValues(false)
        isHighlightEnabled = false
    }
}

/** IndicatorData -> 캔들 차트 위에 오버레이할 LineData (EMA + 볼린저) */
fun IndicatorCalculator.IndicatorData.toCandlePriceOverlay(): LineData {
    val sets = mutableListOf<ILineDataSet>()
    emaSeries.forEach { (ind, arr) ->
        val color = when (ind) {
            Indicator.EMA_SHORT -> IndicatorColors.EMA_SHORT
            Indicator.EMA_MID -> IndicatorColors.EMA_MID
            else -> IndicatorColors.EMA_LONG
        }
        sets += arr.toLineDataSet(ind.displayNameKo, color)
    }
    bollinger?.let {
        sets += it.upper.toLineDataSet("BB Upper", IndicatorColors.BOLL_UPPER, 1f)
        sets += it.mid.toLineDataSet("BB Mid", IndicatorColors.BOLL_MID, 1f)
        sets += it.lower.toLineDataSet("BB Lower", IndicatorColors.BOLL_LOWER, 1f)
    }
    return LineData(sets)
}
