package com.tinyoscillator.presentation.chart.composable

import android.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.CandleStickChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.tinyoscillator.domain.model.OhlcvPoint
import com.tinyoscillator.presentation.chart.ext.toCandleData
import com.tinyoscillator.presentation.chart.ext.toVolumeBarData
import com.tinyoscillator.presentation.chart.formatter.IndexDateFormatter
import com.tinyoscillator.presentation.chart.formatter.KoreanPriceFormatter
import com.tinyoscillator.presentation.chart.formatter.KoreanVolumeFormatter
import com.tinyoscillator.presentation.chart.interaction.ChartSyncManager
import com.tinyoscillator.presentation.chart.interaction.InertialScrollHandler
import com.tinyoscillator.presentation.chart.marker.OhlcvMarkerView

/**
 * 한국 주식 캔들 차트 Composable
 *
 * - 상단 70%: CandleStickChart (OHLCV + 크로스헤어 MarkerView)
 * - 하단 30%: BarChart (거래량, 한국식 단위)
 * - 핀치줌 후 관성 스크롤
 * - 캔들 ↔ 거래량 크로스헤어 + 뷰포트 동기화
 */
@Composable
fun KoreanCandleChartView(
    candles: List<OhlcvPoint>,
    dateLabels: Map<Int, String>,
    patternMarkers: Map<Int, List<String>> = emptyMap(),
    onCrosshairIndex: ((Int?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // 차트 참조 보관
    var candleChart by remember { mutableStateOf<CandleStickChart?>(null) }
    var volumeChart by remember { mutableStateOf<BarChart?>(null) }
    var syncManager by remember { mutableStateOf<ChartSyncManager?>(null) }

    // 두 차트가 모두 준비되면 동기화 연결
    LaunchedEffect(candleChart, volumeChart) {
        val cc = candleChart ?: return@LaunchedEffect
        val vc = volumeChart ?: return@LaunchedEffect
        syncManager?.detach()
        val mgr = ChartSyncManager(cc, vc)
        mgr.attach()
        syncManager = mgr
    }

    DisposableEffect(Unit) {
        onDispose {
            syncManager?.detach()
        }
    }

    Column(modifier = modifier) {
        // ── 캔들 차트 (70%) ──
        AndroidView(
            factory = { ctx ->
                CandleStickChart(ctx).apply {
                    description.isEnabled = false
                    legend.isEnabled = false
                    setBackgroundColor(Color.TRANSPARENT)
                    xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM
                        granularity = 1f
                        valueFormatter = IndexDateFormatter(dateLabels)
                    }
                    axisLeft.apply {
                        valueFormatter = KoreanPriceFormatter()
                        setLabelCount(5, false)
                    }
                    axisRight.isEnabled = false

                    // 핀치줌 + 더블탭 줌
                    isScaleXEnabled = true
                    isScaleYEnabled = false
                    isDoubleTapToZoomEnabled = true

                    // 관성 스크롤
                    val handler = InertialScrollHandler(this)
                    setOnTouchListener { _, event ->
                        handler.onTouchEvent(event)
                        false // MPAndroidChart 기본 터치도 처리
                    }
                }.also { chart -> candleChart = chart }
            },
            update = { chart ->
                if (candles.isNotEmpty()) {
                    chart.data = candles.toCandleData()
                    chart.marker = OhlcvMarkerView(chart.context, dateLabels, patternMarkers).also {
                        it.chartView = chart
                    }
                    chart.notifyDataSetChanged()
                    chart.invalidate()
                }

                // 크로스헤어 콜백
                onCrosshairIndex?.let { cb ->
                    chart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                        override fun onValueSelected(e: Entry?, h: Highlight?) {
                            cb(h?.x?.toInt())
                        }
                        override fun onNothingSelected() {
                            cb(null)
                        }
                    })
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.7f)
                .testTag("CandleStickChart"),
        )

        // ── 거래량 차트 (30%) ──
        AndroidView(
            factory = { ctx ->
                BarChart(ctx).apply {
                    description.isEnabled = false
                    legend.isEnabled = false
                    setBackgroundColor(Color.TRANSPARENT)
                    xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM
                        granularity = 1f
                        setDrawLabels(false)
                    }
                    axisLeft.valueFormatter = KoreanVolumeFormatter()
                    axisRight.isEnabled = false
                    isScaleXEnabled = true
                    isScaleYEnabled = false
                }.also { chart -> volumeChart = chart }
            },
            update = { chart ->
                if (candles.isNotEmpty()) {
                    chart.data = candles.toVolumeBarData()
                    chart.notifyDataSetChanged()
                    chart.invalidate()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.3f)
                .testTag("VolumeBarChart"),
        )
    }
}
