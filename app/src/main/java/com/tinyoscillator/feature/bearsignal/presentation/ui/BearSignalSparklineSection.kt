package com.tinyoscillator.feature.bearsignal.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tinyoscillator.feature.bearsignal.domain.model.BearSnapshot
import com.tinyoscillator.feature.bearsignal.domain.model.GateAdvance
import com.tinyoscillator.feature.bearsignal.domain.model.GateState
import com.tinyoscillator.feature.bearsignal.domain.model.PhaseChange
import com.tinyoscillator.feature.bearsignal.domain.model.Transition
import com.tinyoscillator.feature.bearsignal.domain.model.leadPct
import com.tinyoscillator.presentation.common.FinanceCard
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * 헤더 바로 아래 배치되는 "국면 추이" 섹션 (TASK_bear_signal_console.md §5.2-1 "헤더 바로 아래
 * Sparkline+TransitionLog 배치", §6.1).
 *
 * lead%(선행점수 0~100)·gate(방아쇠 레벨 0~3) 두 시계열을 각각 경량 Compose Canvas 라인차트로
 * 그리고, 국면·방아쇠 전이 로그를 그 아래 나열한다. §6.1 "이력 상태 3종 처리"에 따라 이력이
 * 비어있음/1건(단일)/2건 이상(다수)인 세 상태를 구분해 렌더한다 — Vico를 쓰지 않는 신규 그래픽은
 * Compose Canvas로 구현한다(§5.4).
 */
@Composable
fun BearSignalSparklineSection(
    history: List<BearSnapshot>,
    transitions: List<Transition>,
    modifier: Modifier = Modifier
) {
    FinanceCard(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            history.isEmpty() -> Text(
                "저장된 이력이 없습니다 — 오늘 데이터가 곧 첫 기록이 됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            history.size == 1 -> {
                val only = history.first()
                Text(
                    "이력 1건 — ${formatDay(only.day)} ${only.phase.name} " +
                        "(선행 ${only.leadPct}점 · 방아쇠 ${GateState.entries[only.gate].label})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                val latest = history.last()
                SparklineRow(
                    label = "선행점수 (0~100)",
                    latestText = "${latest.leadPct}점",
                    color = MaterialTheme.colorScheme.primary,
                    values = history.map { it.leadPct.toFloat() },
                    maxValue = 100f,
                    contentDescriptionPrefix = "선행점수 추이"
                )
                SparklineRow(
                    label = "방아쇠 레벨 (0~3)",
                    latestText = GateState.entries[latest.gate].label,
                    color = levelColor(latest.gate),
                    values = history.map { it.gate.toFloat() },
                    maxValue = 3f,
                    contentDescriptionPrefix = "방아쇠 레벨 추이"
                )
            }
        }

        TransitionLogRows(transitions)
    }
}

@Composable
private fun SparklineRow(
    label: String,
    latestText: String,
    color: Color,
    values: List<Float>,
    maxValue: Float,
    contentDescriptionPrefix: String,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(latestText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
        }
        MiniLineChart(
            values = values,
            maxValue = maxValue,
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics { contentDescription = "$contentDescriptionPrefix, 최근값 $latestText, 총 ${values.size}개 기록" }
        )
    }
}

/** 경량 라인차트 — N개 값을 좌→우(과거→최신) 순서로 균등 배치해 폴리라인 + 점으로 그린다. */
@Composable
private fun MiniLineChart(values: List<Float>, maxValue: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val stepX = size.width / (values.size - 1)
        fun y(v: Float) = size.height - (v.coerceIn(0f, maxValue) / maxValue) * size.height

        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val yy = y(v)
            if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
        }
        drawPath(path, color = color, style = Stroke(width = 2.dp.toPx()))
        values.forEachIndexed { i, v ->
            drawCircle(color, radius = 2.2.dp.toPx(), center = Offset(i * stepX, y(v)))
        }
    }
}

/** §6.1 TransitionLog — 같은 날(asOf)의 전이를 한 줄로 묶어 "6/30 GREEN→AMBER · 방아쇠 경계 접근" 형식으로 표시. */
@Composable
private fun TransitionLogRows(transitions: List<Transition>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "전이 로그",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (transitions.isEmpty()) {
            Text(
                "감지된 전이가 없습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // groupBy는 LinkedHashMap 기반이라 최초 삽입 순서(day 오름차순, DetectTransitionsUseCase 계약)를 보존한다.
            transitions.groupBy { it.asOf }.forEach { (day, group) ->
                val line = "${formatDay(day)} ${group.joinToString(" · ") { it.describe() }}"
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { contentDescription = "전이 기록: $line" }
                )
            }
        }
    }
}

private fun Transition.describe(): String = when (val k = kind) {
    is PhaseChange -> "${k.from.name}→${k.to.name}"
    is GateAdvance -> "방아쇠 ${GateState.entries[k.gate].label}"
}

/** "YYYY-MM-DD" → "M/d" (예: "2026-06-30" → "6/30", §6.1 TransitionLog 예시 형식). */
private fun formatDay(day: String): String = try {
    val d = LocalDate.parse(day)
    "${d.monthValue}/${d.dayOfMonth}"
} catch (e: DateTimeParseException) {
    day
}
