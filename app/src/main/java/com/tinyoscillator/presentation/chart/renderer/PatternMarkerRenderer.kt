package com.tinyoscillator.presentation.chart.renderer

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.CandleDataSet
import com.github.mikephil.charting.data.CombinedData
import com.tinyoscillator.domain.model.PatternResult
import com.tinyoscillator.domain.model.PatternSentiment

/**
 * MPAndroidChart 캔들 차트 위에 패턴 마커를 직접 그리는 렌더러.
 *
 * 각 패턴은 PatternType.marker (고유 문자)와 PatternType.colorArgb (고유 색상)로 표시.
 * - 상승(BULLISH): 캔들 저가 아래에 배치
 * - 하락(BEARISH)/중립(NEUTRAL): 캔들 고가 위에 배치
 * - 배경 라운드렉트로 가독성 확보
 */
class PatternMarkerRenderer(
    private val chart: CombinedChart,
    private val markerSizePx: Float = 28f,
) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = markerSizePx * 0.52f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        color = 0xFFFFFFFF.toInt()
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    var patterns: List<PatternResult> = emptyList()

    fun draw(canvas: Canvas) {
        if (patterns.isEmpty()) return

        val transformer = chart.getTransformer(YAxis.AxisDependency.LEFT)
        val viewPort = chart.viewPortHandler

        val combinedData = chart.data as? CombinedData ?: return
        val candleDataSet = combinedData.candleData?.dataSets?.firstOrNull()
            as? CandleDataSet ?: return

        val padding = markerSizePx * 0.4f
        val badgeH = markerSizePx * 0.72f
        val byIndex = patterns.groupBy { it.index }

        byIndex.forEach { (idx, pats) ->
            val candleEntry = candleDataSet.getEntryForIndex(idx) ?: return@forEach

            // x 픽셀 좌표
            val xPts = floatArrayOf(idx.toFloat(), 0f)
            transformer.pointValuesToPixel(xPts)
            val pixelX = xPts[0]
            if (pixelX < viewPort.contentLeft() || pixelX > viewPort.contentRight()) return@forEach

            // 고가/저가 y 픽셀 좌표
            val highPts = floatArrayOf(0f, candleEntry.high)
            transformer.pointValuesToPixel(highPts)
            val highPixelY = highPts[1]

            val lowPts = floatArrayOf(0f, candleEntry.low)
            transformer.pointValuesToPixel(lowPts)
            val lowPixelY = lowPts[1]

            pats.forEachIndexed { stackIdx, result ->
                val type = result.type
                val label = type.marker

                // 텍스트 너비 기반 배지 크기
                val textW = textPaint.measureText(label)
                val badgeW = textW + markerSizePx * 0.5f

                // y 좌표: 상승=저가 아래, 하락/중립=고가 위
                val centerY = when (type.sentiment) {
                    PatternSentiment.BULLISH ->
                        lowPixelY + padding + badgeH / 2f + stackIdx * (badgeH + 3f)
                    else ->
                        highPixelY - padding - badgeH / 2f - stackIdx * (badgeH + 3f)
                }

                if (centerY - badgeH / 2f < viewPort.contentTop() ||
                    centerY + badgeH / 2f > viewPort.contentBottom()
                ) return@forEachIndexed

                // 배경 라운드렉트
                bgPaint.color = type.colorArgb.toInt()
                bgPaint.alpha = (result.strength * 255).toInt().coerceIn(160, 255)
                rect.set(
                    pixelX - badgeW / 2f,
                    centerY - badgeH / 2f,
                    pixelX + badgeW / 2f,
                    centerY + badgeH / 2f,
                )
                canvas.drawRoundRect(rect, badgeH * 0.3f, badgeH * 0.3f, bgPaint)

                // 텍스트 (흰색)
                val textY = centerY + textPaint.textSize * 0.35f
                canvas.drawText(label, pixelX, textY, textPaint)
            }
        }
    }
}
