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
 *
 * - 상승(BULLISH) ▲: 캔들 저가 아래에 표시
 * - 하락(BEARISH) ▽: 캔들 고가 위에 표시
 * - 중립(NEUTRAL) ◇: 캔들 고가 위에 표시
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

        // CombinedData에서 CandleDataSet 조회
        val combinedData = chart.data as? com.github.mikephil.charting.data.CombinedData
        val candleDataSet = combinedData?.candleData?.dataSets?.firstOrNull()
            as? com.github.mikephil.charting.data.CandleDataSet

        val padding = markerSizePx * 0.4f
        val byIndex = patterns.groupBy { it.index }

        byIndex.forEach { (idx, pats) ->
            // 해당 인덱스의 캔들 high/low 가격 조회
            val candleEntry = candleDataSet?.getEntryForIndex(idx)
            val highPrice = candleEntry?.high ?: return@forEach
            val lowPrice = candleEntry?.low ?: return@forEach

            // x 픽셀 좌표
            val xPts = floatArrayOf(idx.toFloat(), 0f)
            transformer.pointValuesToPixel(xPts)
            val pixelX = xPts[0]

            if (pixelX < viewPort.contentLeft() || pixelX > viewPort.contentRight()) return@forEach

            // 고가/저가 → y 픽셀 좌표
            val highPts = floatArrayOf(0f, highPrice)
            transformer.pointValuesToPixel(highPts)
            val highPixelY = highPts[1]

            val lowPts = floatArrayOf(0f, lowPrice)
            transformer.pointValuesToPixel(lowPts)
            val lowPixelY = lowPts[1]

            pats.forEachIndexed { stackIdx, result ->
                val (symbol, paint) = when (result.type.sentiment) {
                    PatternSentiment.BULLISH -> "▲" to bullPaint
                    PatternSentiment.BEARISH -> "▽" to bearPaint
                    PatternSentiment.NEUTRAL -> "◇" to neutralPaint
                }

                // 상승: 저가 아래, 하락/중립: 고가 위 (스태킹 오프셋 적용)
                val y = when (result.type.sentiment) {
                    PatternSentiment.BULLISH ->
                        lowPixelY + padding + markerSizePx + stackIdx * (markerSizePx + 2f)
                    else ->
                        highPixelY - padding - stackIdx * (markerSizePx + 2f)
                }

                // 차트 영역 내 클리핑
                if (y < viewPort.contentTop() || y > viewPort.contentBottom()) return@forEachIndexed

                paint.alpha = (result.strength * 255).toInt().coerceIn(128, 255)
                canvas.drawText(symbol, pixelX, y, paint)
            }
        }
    }
}
