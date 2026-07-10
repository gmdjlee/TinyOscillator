package com.tinyoscillator.feature.bearsignal.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalStaticContent
import com.tinyoscillator.feature.bearsignal.domain.model.BearType
import com.tinyoscillator.feature.bearsignal.domain.model.RecoveryOutlook
import com.tinyoscillator.ui.theme.LocalFinanceColors

/**
 * 섹션 5 · 약세장 3유형 카드 (TASK.md §5.2-5, 부록 B #5) — 회복 가능성 + 모니터링 체크리스트,
 * 활성 방아쇠(유형3, `gate>=1`) 하이라이트.
 */
@Composable
fun BearSignalTypesSection(gate: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BearSignalStaticContent.TYPES.forEach { type ->
            val active = type.index == BearSignalStaticContent.ACTIVE_TYPE_INDEX && gate >= 1
            TypeCard(type = type, active = active)
        }
    }
}

@Composable
private fun TypeCard(type: BearType, active: Boolean) {
    val accent = LocalFinanceColors.current.negative // 프로토타입 C.accent(파랑)와 동일 계열 재사용
    val recoveryColor = when (type.recoveryOutlook) {
        RecoveryOutlook.LOWEST -> MaterialTheme.colorScheme.error
        RecoveryOutlook.MEDIUM -> levelColor(1)
        RecoveryOutlook.PATIENCE -> accent
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = if (active) BorderStroke(1.dp, accent.copy(alpha = 0.6f)) else null,
        colors = CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        "유형 ${type.index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(type.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        type.axis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (active) {
                Text(
                    "● 현재 활성 방아쇠 (리포트 최유력)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
            Text(type.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(type.recoveryLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = recoveryColor)
            Text("이론 · ${type.theory}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("사례 · ${type.cases}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(
                "모니터링 체크리스트",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            type.monitor.forEach { item ->
                MonitorChecklistRow(text = item, tint = recoveryColor)
            }
        }
    }
}

@Composable
private fun MonitorChecklistRow(text: String, tint: Color) {
    var checked by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(
            checked = checked,
            onCheckedChange = { checked = it },
            colors = CheckboxDefaults.colors(checkedColor = tint),
            modifier = Modifier.padding(end = 4.dp)
        )
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

/**
 * 섹션 6 · 역사 검증(일본 3충격) + 3대 모니터링 (TASK.md §5.2-6, 부록 B #6).
 */
@Composable
fun BearSignalHistorySection(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(BearSignalStaticContent.HISTORY_TITLE, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(BearSignalStaticContent.HISTORY_BODY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BearSignalStaticContent.HISTORY_METRICS.forEach { metric ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            metric.header,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            metric.body,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
