package com.tinyoscillator.feature.bearsignal.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalResult
import com.tinyoscillator.feature.bearsignal.domain.model.GateState
import com.tinyoscillator.presentation.common.FinanceCard

/**
 * 섹션 4 · 방아쇠(금리)·증폭(집중) 카드 (TASK.md §5.2-4, 부록 B #2·#3).
 */
@Composable
fun BearSignalGateAmpSection(
    inputs: BearSignalInputs,
    result: BearSignalResult,
    auto: AutoBearSignalInputs?,
    onManualInputClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FinanceCard(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "금리 방아쇠 ${GateState.entries[result.gate].label}, " +
                        "기준금리 상단 ${"%.2f".format(inputs.rate)}퍼센트, 정책방향 ${dirLabel(inputs.dir)}"
                },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("신호 4 · 금리 [ 결정타 ]", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("세 신호를 하락으로 바꾸는 방아쇠", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                LevelChip(level = result.gate, labels = GateState.entries.map { it.label })
            }
            SignalGauge(level = result.gate, labels = GateState.entries.map { it.label }, modifier = Modifier.fillMaxWidth())
            ReadoutRow(
                listOf(
                    Triple("기준금리 상단", "${"%.2f".format(inputs.rate)}%", MaterialTheme.colorScheme.onSurfaceVariant),
                    Triple("정책 방향", dirLabel(inputs.dir), MaterialTheme.colorScheme.onSurfaceVariant),
                    Triple("신용잔고", "${"%.0f".format(inputs.credit)}조", MaterialTheme.colorScheme.onSurfaceVariant)
                )
            )
            Text(
                "반대매매 임박: ${if (inputs.margin) "예" else "아니오"}",
                style = MaterialTheme.typography.bodySmall,
                color = if (inputs.margin) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SourceBadge(indicator = auto?.rate)
                TextButton(onClick = onManualInputClick) { Text("수동 입력") }
            }
            Text(
                "임계 4.5% = 진짜 긴축 · 매수 주체가 국내 신용으로 이동하며 방아쇠가 한국은행으로 국산화",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FinanceCard(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "집중 증폭 계수 ${"%.2f".format(result.amp)}배, " +
                        "반도체 수출비중 ${"%.1f".format(inputs.semi)}퍼센트, " +
                        "완충산업 ${if (inputs.buffer) "건재" else "부재"}"
                },
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("유형 4 · 집중 [ 증폭 계수 ]", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("충격을 키우는 계수", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "×${"%.2f".format(result.amp)}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = if (result.amp >= 1.3) levelColor(2) else MaterialTheme.colorScheme.onSurface
            )
            Text(
                "방아쇠가 아니라 이미 당겨진 충격에 곱해지는 값",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ReadoutRow(
                listOf(
                    Triple("반도체 수출 비중", "${"%.1f".format(inputs.semi)}%", MaterialTheme.colorScheme.onSurfaceVariant),
                    Triple("삼성+SK 코스피 비중", "${"%.0f".format(inputs.kospi2)}%", MaterialTheme.colorScheme.onSurfaceVariant),
                    Triple(
                        "완충 산업",
                        if (inputs.buffer) "건재" else "부재(핀란드형)",
                        if (inputs.buffer) MaterialTheme.colorScheme.onSurfaceVariant else levelColor(2)
                    )
                )
            )
            Row {
                SourceBadge(indicator = auto?.semi)
            }
            Text(
                "완충 산업 건재 시 핀란드형 파국 회피 — 한국은 자동차·기계·석유제품 등 완충 존재",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun dirLabel(dir: String): String = when (dir) {
    "hike" -> "인상"
    "ease" -> "인하"
    else -> "동결"
}
