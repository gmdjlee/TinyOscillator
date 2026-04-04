package com.tinyoscillator.presentation.chart.composable

import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.tinyoscillator.domain.indicator.IndicatorCalculator
import com.tinyoscillator.domain.model.OhlcvPoint
import com.tinyoscillator.domain.model.PatternResult
import com.tinyoscillator.domain.model.VolumeProfile
import com.tinyoscillator.presentation.chart.bridge.ChartAxisBridge
import com.tinyoscillator.presentation.chart.overlay.VolumeProfileOverlay
import com.tinyoscillator.presentation.chart.renderer.PatternCombinedChart
import com.tinyoscillator.presentation.chart.ext.toCandleData
import com.tinyoscillator.presentation.chart.ext.toCandlePriceOverlay
import com.tinyoscillator.presentation.chart.ext.toVolumeBarData
import com.tinyoscillator.presentation.chart.formatter.IndexDateFormatter
import com.tinyoscillator.presentation.chart.formatter.KoreanPriceFormatter
import com.tinyoscillator.presentation.chart.formatter.KoreanVolumeFormatter
import com.tinyoscillator.presentation.chart.interaction.ChartSyncManager
import com.tinyoscillator.presentation.chart.interaction.InertialScrollHandler
import com.tinyoscillator.presentation.chart.marker.OhlcvMarkerView
import com.tinyoscillator.presentation.chart.renderer.PatternMarkerRenderer

/**
 * 한국 주식 캔들 차트 Composable
 *
 * - 상단 70%: CombinedChart (캔들 + EMA/볼린저 오버레이 + 패턴 마커)
 * - 하단 30%: BarChart (거래량, 한국식 단위)
 * - 핀치줌 후 관성 스크롤
 * - 캔들 ↔ 거래량 크로스헤어 + 뷰포트 동기화
 */
@Composable
fun KoreanCandleChartView(
    candles: List<OhlcvPoint>,
    dateLabels: Map<Int, String>,
    indicatorData: IndicatorCalculator.IndicatorData? = null,
    volumeProfile: VolumeProfile? = null,
    detectedPatterns: List<PatternResult> = emptyList(),
    patternMarkers: Map<Int, List<String>> = emptyMap(),
    onCrosshairIndex: ((Int?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var candleChart by remember { mutableStateOf<PatternCombinedChart?>(null) }
    var volumeChart by remember { mutableStateOf<BarChart?>(null) }
    var syncManager by remember { mutableStateOf<ChartSyncManager?>(null) }
    val axisBridge = remember { ChartAxisBridge() }

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
        // ── 캔들 + 지표 오버레이 차트 (70%) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.7f),
        ) {
            AndroidView(
                factory = { ctx ->
                    PatternCombinedChart(ctx).apply {
                        description.isEnabled = false
                        legend.isEnabled = false
                        setBackgroundColor(Color.TRANSPARENT)
                        drawOrder = arrayOf(
                            CombinedChart.DrawOrder.CANDLE,
                            CombinedChart.DrawOrder.LINE,
                        )
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
                        isScaleXEnabled = true
                        isScaleYEnabled = false
                        isDoubleTapToZoomEnabled = true

                        val handler = InertialScrollHandler(this)
                        setOnTouchListener { _, event ->
                            handler.onTouchEvent(event)
                            false
                        }

                        // 패턴 마커 렌더러 — onDraw에서 캔들 위에 직접 그림
                        patternRenderer = PatternMarkerRenderer(this).apply {
                            patterns = detectedPatterns
                        }
                    }.also { chart -> candleChart = chart }
                },
                update = { chart ->
                    if (candles.isNotEmpty()) {
                        chart.data = CombinedData().apply {
                            setData(candles.toCandleData())
                            val overlay = indicatorData?.toCandlePriceOverlay()
                            if (overlay != null && overlay.dataSetCount > 0) {
                                setData(overlay)
                            }
                        }
                        chart.marker = OhlcvMarkerView(chart.context, dateLabels, patternMarkers).also {
                            it.chartView = chart
                        }
                        // 패턴 렌더러 업데이트
                        chart.patternRenderer?.patterns = detectedPatterns
                        chart.notifyDataSetChanged()
                        chart.invalidate()
                        axisBridge.update(chart)
                    }

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
                    .fillMaxSize()
                    .testTag("CandleStickChart"),
            )
            VolumeProfileOverlay(
                profile = volumeProfile,
                axisRange = axisBridge.axisRange.value,
                modifier = Modifier.fillMaxSize(),
            )
        }

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
