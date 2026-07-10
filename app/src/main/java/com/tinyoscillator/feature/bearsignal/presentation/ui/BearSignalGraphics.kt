package com.tinyoscillator.feature.bearsignal.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyoscillator.feature.bearsignal.domain.model.BearPhase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 커스텀 그래픽(신호등·4단 게이지·레이더) — Compose Canvas/Layout (TASK.md §5.4).
 *
 * 프로토타입 SVG(`Radar`)·CSS 게이지를 1:1로 Compose Canvas로 재구현한다. 값+텍스트를 항상 병기해
 * 색각 이상 사용자도 판별 가능하게 한다.
 */

/** 종합 국면 신호등 — RED/ORANGE/AMBER/GREEN 4구 세로 배열, 현재 국면만 점등(글로우) */
@Composable
fun TrafficLightColumn(phase: BearPhase, modifier: Modifier = Modifier) {
    val order = listOf(BearPhase.RED, BearPhase.ORANGE, BearPhase.AMBER, BearPhase.GREEN)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        order.forEach { p ->
            val on = p == phase
            val color = phaseColor(p)
            Canvas(modifier = Modifier.size(18.dp)) {
                val radius = size.minDimension / 2f
                if (on) {
                    drawCircle(color = color.copy(alpha = 0.30f), radius = radius)
                }
                drawCircle(
                    color = if (on) color else color.copy(alpha = 0.22f),
                    radius = radius * 0.68f
                )
            }
        }
    }
}

/**
 * 4단 게이지(온도계) — 안전/주의/경고/위험 구간을 세그먼트 바 + 라벨로 표시.
 * 프로토타입 `Gauge` 컴포넌트와 동일한 시각 문법(현재 레벨만 강조 불투명도 1.0, 나머지 0.42).
 */
@Composable
fun SignalGauge(
    level: Int,
    labels: List<String> = listOf("안전", "주의", "경고", "위험"),
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            labels.forEachIndexed { i, _ ->
                val on = i <= level
                val cur = i == level
                val segColor = if (on) levelColor(level) else MaterialTheme.colorScheme.surfaceVariant
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (on && !cur) segColor.copy(alpha = 0.45f) else segColor)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            labels.forEachIndexed { i, label ->
                val cur = i == level
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal,
                    color = if (cur) levelColor(level) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 미니 레이더(4축: 주변부/변동성/IPO/금리, 0..3) — 프로토타입 `Radar`(SVG) 1:1 Canvas 재구현.
 */
@Composable
fun BearSignalRadar(
    s1: Int,
    s2: Int,
    s3: Int,
    gate: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val labels = listOf("주변부", "변동성", "IPO", "금리")
    val values = listOf(s1, s2, s3, gate)
    val textMeasurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 8.sp, color = labelColor)

    Canvas(modifier = modifier.size(124.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f * 0.62f

        fun point(i: Int, v: Float): Offset {
            val angle = (-PI / 2 + i * PI / 2).toFloat()
            val rr = (v / 3f) * r
            return Offset(cx + rr * cos(angle), cy + rr * sin(angle))
        }

        // 배경 그리드(레벨 1..3 다각형)
        for (g in 1..3) {
            val path = Path()
            for (i in 0..3) {
                val p = point(i, g.toFloat())
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(path, color = gridColor, style = Stroke(width = 1f))
        }
        // 축 라인
        for (i in 0..3) {
            val p = point(i, 3f)
            drawLine(gridColor, start = Offset(cx, cy), end = p, strokeWidth = 1f)
        }
        // 값 다각형
        val valuePath = Path()
        values.forEachIndexed { i, v ->
            val p = point(i, v.toFloat())
            if (i == 0) valuePath.moveTo(p.x, p.y) else valuePath.lineTo(p.x, p.y)
        }
        valuePath.close()
        drawPath(valuePath, color = color.copy(alpha = 0.22f))
        drawPath(valuePath, color = color, style = Stroke(width = 1.6.dp.toPx()))
        values.forEachIndexed { i, v ->
            drawCircle(color, radius = 2.4.dp.toPx(), center = point(i, v.toFloat()))
        }
        // 축 라벨
        labels.forEachIndexed { i, label ->
            val layout = textMeasurer.measure(text = label, style = labelStyle)
            val p = point(i, 3.55f)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f)
            )
        }
    }
}
