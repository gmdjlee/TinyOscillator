package com.tinyoscillator.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tinyoscillator.domain.model.HeatmapData
import kotlin.math.roundToInt

/**
 * 신호 강도 히트맵 — Compose Canvas 직접 구현.
 * 행 = 종목, 열 = 날짜, 셀 색상 = 강세(적색)~약세(청색).
 */
@Composable
fun SignalHeatmap(
    data: HeatmapData,
    onCellClick: (ticker: String, dateIndex: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    if (data.tickers.isEmpty() || data.dates.isEmpty()) return

    val cellSize = 28.dp
    val rowLabelW = 60.dp
    val colLabelH = 22.dp
    val totalWidth = rowLabelW + cellSize * data.dates.size
    val totalHeight = colLabelH + cellSize * data.tickers.size

    Box(modifier = modifier.horizontalScroll(rememberScrollState())) {
        with(LocalDensity.current) {
            val cellPx = cellSize.toPx()
            val labelWPx = rowLabelW.toPx()
            val labelHPx = colLabelH.toPx()

            Canvas(
                modifier = Modifier
                    .width(totalWidth)
                    .height(totalHeight)
                    .pointerInput(data) {
                        detectTapGestures { offset ->
                            val col = ((offset.x - labelWPx) / cellPx).toInt()
                            val row = ((offset.y - labelHPx) / cellPx).toInt()
                            if (col in data.dates.indices && row in data.tickers.indices) {
                                onCellClick(data.tickers[row], col)
                            }
                        }
                    }
            ) {
                val headerPaint = android.graphics.Paint().apply {
                    textSize = 9.dp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    color = android.graphics.Color.parseColor("#8A8580")
                    isAntiAlias = true
                }
                val rowPaint = android.graphics.Paint().apply {
                    textSize = 10.dp.toPx()
                    textAlign = android.graphics.Paint.Align.RIGHT
                    color = android.graphics.Color.parseColor("#444441")
                    isAntiAlias = true
                }
                val scorePaint = android.graphics.Paint().apply {
                    textSize = 8.dp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    color = android.graphics.Color.WHITE
                    isAntiAlias = true
                }

                // 날짜 헤더 (열 레이블)
                data.dateLabels.forEachIndexed { col, label ->
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        labelWPx + col * cellPx + cellPx / 2f,
                        labelHPx - 4.dp.toPx(),
                        headerPaint,
                    )
                }

                // 종목 레이블 (행) + 히트맵 셀
                data.tickers.forEachIndexed { row, ticker ->
                    val name = data.tickerNames[ticker] ?: ticker

                    // 종목명 레이블
                    drawContext.canvas.nativeCanvas.drawText(
                        name.take(6),
                        labelWPx - 4.dp.toPx(),
                        labelHPx + row * cellPx + cellPx / 2f + 4.dp.toPx(),
                        rowPaint,
                    )

                    // 히트맵 셀
                    data.dates.indices.forEach { col ->
                        val score = data.scoreAt(ticker, col)
                        val cellLeft = labelWPx + col * cellPx
                        val cellTop = labelHPx + row * cellPx

                        drawRect(
                            color = heatmapColor(score),
                            topLeft = Offset(cellLeft + 1.dp.toPx(), cellTop + 1.dp.toPx()),
                            size = Size(cellPx - 2.dp.toPx(), cellPx - 2.dp.toPx()),
                        )

                        // 점수 텍스트
                        if (cellPx >= 24.dp.toPx()) {
                            scorePaint.isFakeBoldText = score > 0.65f || score < 0.35f
                            drawContext.canvas.nativeCanvas.drawText(
                                "${(score * 100).roundToInt()}",
                                cellLeft + cellPx / 2f,
                                cellTop + cellPx / 2f + 4.dp.toPx(),
                                scorePaint,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 점수 → 히트맵 색상 (한국 관례: 강세=적색, 약세=청색) */
fun heatmapColor(score: Float): Color = when {
    score >= 0.75f -> Color(0xFFD05540)   // 강한 강세
    score >= 0.60f -> Color(0xFFE88A66)   // 약한 강세
    score >= 0.40f -> Color(0xFF8A8580)   // 중립
    score >= 0.25f -> Color(0xFF6699CC)   // 약한 약세
    else           -> Color(0xFF4088CC)   // 강한 약세
}
