package com.tinyoscillator.feature.bearsignal.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalResult
import com.tinyoscillator.feature.bearsignal.domain.model.GateState

/**
 * 섹션 1 · 종합 국면 헤더 (TASK.md §5.2-1, 부록 B #4) — 신호등·선행점수 게이지·방아쇠 상태·
 * 증폭 배수·레이더(4축)를 한 카드에 담는다.
 */
@Composable
fun BearSignalHeaderSection(result: BearSignalResult, modifier: Modifier = Modifier) {
    val meta = phaseMeta(result.phase)
    val color = phaseColor(result.phase)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "현재 국면 ${meta.label}, 선행 신호 점수 ${result.leadPct}점, " +
                    "금리 방아쇠 ${GateState.entries[result.gate].label}, 증폭 배수 ${"%.2f".format(result.amp)}배"
            }
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TrafficLightColumn(phase = result.phase)
                Column(Modifier.weight(1f)) {
                    Text(
                        "현 국면",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        meta.label,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Text(
                        meta.sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "선행 신호 점수 (온도계 3종)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${result.leadPct}/100",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
                LinearProgressIndicator(
                    progress = { result.leadPct / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Readout(
                    label = "금리 방아쇠",
                    value = GateState.entries[result.gate].label,
                    color = levelColor(result.gate)
                )
                Readout(
                    label = "집중 증폭",
                    value = "×${"%.2f".format(result.amp)}",
                    color = if (result.amp >= 1.3) levelColor(2) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Readout(
                    label = "경고↑ 신호",
                    value = "${result.warn} / 3",
                    color = if (result.warn >= 2) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                BearSignalRadar(s1 = result.s1, s2 = result.s2, s3 = result.s3, gate = result.gate, color = color)
            }

            Text(
                meta.desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Readout(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}
