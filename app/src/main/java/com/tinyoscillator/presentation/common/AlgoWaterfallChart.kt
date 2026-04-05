package com.tinyoscillator.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tinyoscillator.domain.model.AlgoResult
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 폭포수 차트:
 *   기준선 0.5 -> 각 알고리즘 기여분 누적 -> 최종 앙상블 점수
 *   알고리즘 기여분 = (score - 0.5) * weight
 *   양수 기여 -> 적색 바 (강세), 음수 기여 -> 청색 바 (약세)
 */
@Composable
fun AlgoWaterfallChart(
    algoResults: Map<String, AlgoResult>,
    ensembleScore: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val sorted = algoResults.entries.sortedByDescending { it.value.weight }

    val contributions: List<Pair<String, Float>> = sorted.map { (name, r) ->
        val contribution = (r.score - 0.5f) * r.weight
        name to contribution
    }

    // 누적 상한/하한 + 앙상블 편차를 미리 계산해 auto-scale
    val maxDeviation = run {
        var cum = 0f
        var maxUp = 0f
        var maxDown = 0f
        for ((_, contrib) in contributions) {
            cum += contrib
            if (cum > maxUp) maxUp = cum
            if (cum < maxDown) maxDown = cum
        }
        val ensembleDev = abs(ensembleScore - 0.5f)
        maxOf(maxUp, abs(maxDown), ensembleDev, 0.01f)
    }

    Canvas(modifier = modifier.fillMaxWidth()) {
        val labelAreaBottom = 18.dp.toPx()
        val topPad = 16.dp.toPx()          // 퍼센트 레이블 여유
        val paddingPx = 28.dp.toPx()
        val barW = (size.width - paddingPx * 2) / (contributions.size + 1).toFloat()
        val chartHeight = size.height - labelAreaBottom - topPad
        val baseline = topPad + chartHeight / 2f
        val scale = (chartHeight / 2f) / maxDeviation   // 데이터에 맞춤

        // 기준선 (dashed)
        drawLine(
            color = Color(0xFF888780),
            start = Offset(paddingPx, baseline),
            end = Offset(size.width - paddingPx, baseline),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
        )

        // 0.5 레이블
        drawContext.canvas.nativeCanvas.drawText(
            "0.5",
            14.dp.toPx(),
            baseline + 5.dp.toPx(),
            android.graphics.Paint().apply {
                textSize = 11.dp.toPx()
                color = android.graphics.Color.parseColor("#888780")
            }
        )

        var cumY = baseline

        contributions.forEachIndexed { i, (name, contrib) ->
            val x = paddingPx + i * barW + barW * 0.15f
            val barH = abs(contrib) * scale
            val top = if (contrib >= 0f) cumY - barH else cumY
            val bottom = if (contrib >= 0f) cumY else cumY + barH

            drawRect(
                color = if (contrib >= 0f) Color(0xCCD85A30) else Color(0xCC378ADD),
                topLeft = Offset(x, top),
                size = Size(barW * 0.7f, bottom - top),
            )

            // 알고리즘 레이블 (하단)
            drawContext.canvas.nativeCanvas.drawText(
                (ALGO_DISPLAY_NAMES[name] ?: name).take(4),
                x + barW * 0.35f,
                size.height - 2.dp.toPx(),
                android.graphics.Paint().apply {
                    textSize = 10.dp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    color = android.graphics.Color.parseColor("#888780")
                }
            )

            cumY = if (contrib >= 0f) top else bottom
        }

        // 최종 앙상블 점수 바
        val finalX = paddingPx + contributions.size * barW + barW * 0.15f
        val finalH = abs(ensembleScore - 0.5f) * scale
        val finalTop = if (ensembleScore >= 0.5f) baseline - finalH else baseline

        drawRect(
            color = Color(0xFFBA7517),
            topLeft = Offset(finalX, finalTop),
            size = Size(barW * 0.7f, finalH),
        )

        drawContext.canvas.nativeCanvas.drawText(
            "${(ensembleScore * 100).roundToInt()}%",
            finalX + barW * 0.35f,
            finalTop - 4.dp.toPx(),
            android.graphics.Paint().apply {
                textSize = 11.dp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                color = android.graphics.Color.parseColor("#BA7517")
                isFakeBoldText = true
            }
        )
    }
}
