package com.tinyoscillator.presentation.chart.renderer

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.YAxis
import com.tinyoscillator.domain.model.PatternResult
import com.tinyoscillator.domain.model.PatternSentiment

/**
 * MPAndroidChart 캔들 차트 위에 패턴 마커(▲/▽/◇)를 직접 그리는 렌더러.
 * chart.setOnDrawListener 또는 수동 drawMarkers 호출로 사용.
 */
class PatternMarkerRenderer(
    private val chart: CombinedChart,
    private val markerSizePx: Float = 28f,
) {
    private val bullPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD85A30.toInt()
        textSize = markerSizePx
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val bearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF378ADD.toInt()
        textSize = markerSizePx
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val neutralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF888780.toInt()
        textSize = markerSizePx
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    var patterns: List<PatternResult> = emptyList()

    fun draw(canvas: Canvas) {
        if (patterns.isEmpty()) return

        val transformer = chart.getTransformer(YAxis.AxisDependency.LEFT)
        val viewPort = chart.viewPortHandler
        val byIndex = patterns.groupBy { it.index }
        val topMargin = markerSizePx * 0.5f

        byIndex.forEach { (idx, pats) ->
            // 데이터 좌표 → 픽셀 좌표 변환
            val pts = floatArrayOf(idx.toFloat(), 0f)
            transformer.pointValuesToPixel(pts)
            val pixelX = pts[0]

            // 가시 범위 밖이면 스킵
            if (pixelX < viewPort.contentLeft() || pixelX > viewPort.contentRight()) return@forEach

            pats.forEachIndexed { stackIdx, result ->
                val y = viewPort.contentTop() + topMargin + stackIdx * (markerSizePx + 4f)

                val (symbol, paint) = when (result.type.sentiment) {
                    PatternSentiment.BULLISH -> "▲" to bullPaint
                    PatternSentiment.BEARISH -> "▽" to bearPaint
                    PatternSentiment.NEUTRAL -> "◇" to neutralPaint
                }
                paint.alpha = (result.strength * 255).toInt().coerceIn(128, 255)
                canvas.drawText(symbol, pixelX, y + markerSizePx, paint)
            }
        }
    }
}
