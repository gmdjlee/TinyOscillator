package com.tinyoscillator.presentation.chart.ext

import android.graphics.Color
import android.graphics.Paint
import com.github.mikephil.charting.data.*
import com.tinyoscillator.domain.model.OhlcvPoint

/** OhlcvPoint 리스트 → MPAndroidChart CandleData */
fun List<OhlcvPoint>.toCandleData(): CandleData {
    val entries = mapIndexed { i, c ->
        CandleEntry(i.toFloat(), c.high, c.low, c.open, c.close)
    }
    val set = CandleDataSet(entries, "").apply {
        decreasingColor = Color.parseColor("#378ADD")   // 음봉 청색
        increasingColor = Color.parseColor("#D85A30")   // 양봉 적색
        shadowColor = Color.parseColor("#888780")
        decreasingPaintStyle = Paint.Style.FILL
        increasingPaintStyle = Paint.Style.FILL
        setDrawValues(false)
    }
    return CandleData(set)
}

/** OhlcvPoint 리스트 → 거래량 BarData (양봉/음봉 색 구분) */
fun List<OhlcvPoint>.toVolumeBarData(): BarData {
    val entries = mapIndexed { i, c ->
        BarEntry(i.toFloat(), c.volume.toFloat())
    }
    val colors = map { c ->
        if (c.close >= c.open)
            Color.parseColor("#55D85A30")   // 양봉 볼륨: 연한 적색
        else
            Color.parseColor("#55378ADD")   // 음봉 볼륨: 연한 청색
    }
    val set = BarDataSet(entries, "").apply {
        this.colors = colors
        setDrawValues(false)
    }
    return BarData(set).apply { barWidth = 0.7f }
}
