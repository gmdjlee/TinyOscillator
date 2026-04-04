package com.tinyoscillator.presentation.chart.composable

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.tinyoscillator.domain.indicator.IndicatorCalculator
import com.tinyoscillator.domain.model.Indicator
import com.tinyoscillator.presentation.chart.ext.IndicatorColors
import com.tinyoscillator.presentation.chart.ext.toLineDataSet

@Composable
fun OscillatorChartView(
    indicatorData: IndicatorCalculator.IndicatorData,
    activeOscillator: Indicator,
    modifier: Modifier = Modifier,
) {
    when (activeOscillator) {
        Indicator.MACD -> MacdChartView(indicatorData.macd, modifier)
        Indicator.RSI -> RsiChartView(indicatorData.rsi, modifier)
        Indicator.STOCHASTIC -> StochChartView(indicatorData.stochastic, modifier)
        else -> { /* 표시 안 함 */ }
    }
}

@Composable
fun MacdChartView(macd: IndicatorCalculator.MacdResult?, modifier: Modifier = Modifier) {
    if (macd == null) return
    AndroidView(
        factory = { ctx ->
            CombinedChart(ctx).apply {
                description.isEnabled = false
                legend.isEnabled = false
                setBackgroundColor(Color.TRANSPARENT)
                axisRight.isEnabled = false
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawLabels(false)
                isScaleXEnabled = true
                isScaleYEnabled = false
            }
        },
        update = { chart ->
            val histEntries = macd.histogram.indices.mapNotNull { i ->
                if (macd.histogram[i].isNaN()) null else BarEntry(i.toFloat(), macd.histogram[i])
            }
            val histColors = histEntries.map { entry ->
                if (entry.y >= 0) IndicatorColors.MACD_HIST_POS
                else IndicatorColors.MACD_HIST_NEG
            }
            val histSet = BarDataSet(histEntries, "Histogram").apply {
                colors = histColors
                setDrawValues(false)
            }
            chart.data = CombinedData().apply {
                setData(BarData(histSet))
                setData(
                    LineData(
                        macd.macdLine.toLineDataSet("MACD", IndicatorColors.MACD_LINE),
                        macd.signalLine.toLineDataSet("Signal", IndicatorColors.MACD_SIGNAL),
                    )
                )
            }
            chart.notifyDataSetChanged()
            chart.invalidate()
        },
        modifier = modifier,
    )
}

@Composable
fun RsiChartView(rsi: FloatArray?, modifier: Modifier = Modifier) {
    if (rsi == null) return
    AndroidView(
        factory = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled = false
                legend.isEnabled = false
                setBackgroundColor(Color.TRANSPARENT)
                axisRight.isEnabled = false
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawLabels(false)
                isScaleXEnabled = true
                isScaleYEnabled = false
                axisLeft.apply {
                    axisMinimum = 0f
                    axisMaximum = 100f
                    addLimitLine(LimitLine(70f, "과매수").apply {
                        lineColor = Color.parseColor("#E24B4A")
                        lineWidth = 0.8f
                    })
                    addLimitLine(LimitLine(30f, "과매도").apply {
                        lineColor = Color.parseColor("#378ADD")
                        lineWidth = 0.8f
                    })
                }
            }
        },
        update = { chart ->
            chart.data = LineData(rsi.toLineDataSet("RSI", IndicatorColors.RSI_LINE))
            chart.notifyDataSetChanged()
            chart.invalidate()
        },
        modifier = modifier,
    )
}

@Composable
fun StochChartView(stoch: IndicatorCalculator.StochResult?, modifier: Modifier = Modifier) {
    if (stoch == null) return
    AndroidView(
        factory = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled = false
                legend.isEnabled = false
                setBackgroundColor(Color.TRANSPARENT)
                axisRight.isEnabled = false
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawLabels(false)
                isScaleXEnabled = true
                isScaleYEnabled = false
                axisLeft.apply {
                    axisMinimum = 0f
                    axisMaximum = 100f
                    addLimitLine(LimitLine(80f, "과매수").apply {
                        lineColor = Color.parseColor("#E24B4A")
                        lineWidth = 0.8f
                    })
                    addLimitLine(LimitLine(20f, "과매도").apply {
                        lineColor = Color.parseColor("#378ADD")
                        lineWidth = 0.8f
                    })
                }
            }
        },
        update = { chart ->
            chart.data = LineData(
                stoch.k.toLineDataSet("%K", IndicatorColors.STOCH_K),
                stoch.d.toLineDataSet("%D", IndicatorColors.STOCH_D),
            )
            chart.notifyDataSetChanged()
            chart.invalidate()
        },
        modifier = modifier,
    )
}
