package com.tinyoscillator.presentation.chart.ext

import android.graphics.Paint
import androidx.core.graphics.ColorUtils
import com.github.mikephil.charting.data.*
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.OhlcvPoint
import com.tinyoscillator.presentation.chart.ChartTheme

/** DailyTrading 리스트 → OhlcvPoint 리스트 (캔들 차트용) */
fun List<DailyTrading>.toOhlcvPoints(): List<OhlcvPoint> =
    mapIndexed { i, d ->
        OhlcvPoint(
            index = i,
            open = d.openPrice.toFloat(),
            high = d.highPrice.toFloat(),
            low = d.lowPrice.toFloat(),
            close = d.closePrice.toFloat(),
            volume = d.volume,
            date = d.date,
        )
    }

/** DailyTrading 리스트 → 날짜 라벨 맵 (인덱스 → "MM/dd") */
fun List<DailyTrading>.toDateLabels(): Map<Int, String> =
    mapIndexed { i, d ->
        val raw = d.date // "yyyyMMdd"
        val label = if (raw.length == 8) "${raw.substring(4, 6)}/${raw.substring(6, 8)}" else raw
        i to label
    }.toMap()

/** OhlcvPoint 리스트 → MPAndroidChart CandleData */
fun List<OhlcvPoint>.toCandleData(theme: ChartTheme): CandleData {
    val entries = mapIndexed { i, c ->
        CandleEntry(i.toFloat(), c.high, c.low, c.open, c.close)
    }
    val set = CandleDataSet(entries, "").apply {
        decreasingColor = theme.negative   // 음봉 청색
        increasingColor = theme.positive   // 양봉 적색
        shadowColor = theme.neutral        // 심지 무채색
        decreasingPaintStyle = Paint.Style.FILL
        increasingPaintStyle = Paint.Style.FILL
        setDrawValues(false)
    }
    return CandleData(set)
}

/** OhlcvPoint 리스트 → 거래량 BarData (양봉/음봉 색 구분) */
fun List<OhlcvPoint>.toVolumeBarData(theme: ChartTheme): BarData {
    val entries = mapIndexed { i, c ->
        BarEntry(i.toFloat(), c.volume.toFloat())
    }
    val colors = map { c ->
        if (c.close >= c.open)
            ColorUtils.setAlphaComponent(theme.positive, 0x55)   // 양봉 볼륨: 연한 적색
        else
            ColorUtils.setAlphaComponent(theme.negative, 0x55)   // 음봉 볼륨: 연한 청색
    }
    val set = BarDataSet(entries, "").apply {
        this.colors = colors
        setDrawValues(false)
    }
    return BarData(set).apply { barWidth = 0.7f }
}
