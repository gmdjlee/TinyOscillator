package com.tinyoscillator.presentation.chart.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyoscillator.domain.model.PatternResult
import com.tinyoscillator.domain.model.PatternSentiment
import com.tinyoscillator.presentation.chart.bridge.ChartXBridge

@Composable
fun PatternMarkerOverlay(
    patterns: List<PatternResult>,
    xBridge: ChartXBridge,
    markerSizeSp: Float = 12f,
    modifier: Modifier = Modifier,
) {
    if (patterns.isEmpty()) return

    val density = LocalDensity.current
    val markerSizePx = with(density) { markerSizeSp.sp.toPx() }

    val byIndex = remember(patterns) { patterns.groupBy { it.index } }

    Canvas(modifier = modifier.fillMaxSize()) {
        val topMargin = 8.dp.toPx()

        byIndex.forEach { (idx, pats) ->
            val canvasX = xBridge.indexToX(idx)
            if (canvasX < 0f || canvasX > size.width) return@forEach

            pats.forEachIndexed { stackIdx, result ->
                val y = topMargin + stackIdx * (markerSizePx + 2.dp.toPx())

                val (symbol, color) = when (result.type.sentiment) {
                    PatternSentiment.BULLISH -> "▲" to Color(0xFFD85A30)
                    PatternSentiment.BEARISH -> "▽" to Color(0xFF378ADD)
                    PatternSentiment.NEUTRAL -> "◇" to Color(0xFF888780)
                }

                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        this.color = color.toArgb()
                        textSize = markerSizePx
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        alpha = (result.strength * 255).toInt().coerceIn(128, 255)
                    }
                    drawText(symbol, canvasX, y + markerSizePx, paint)
                }
            }
        }
    }
}
