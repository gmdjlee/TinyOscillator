package com.tinyoscillator.presentation.chart.formatter

import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.NumberFormat
import java.util.Locale

/** 가격 축 한국식 포매터: 73,400 (천 단위 쉼표) */
class KoreanPriceFormatter : ValueFormatter() {
    private val nf = NumberFormat.getIntegerInstance(Locale.KOREA)

    override fun getAxisLabel(value: Float, axis: AxisBase?): String =
        nf.format(value.toLong())
}
