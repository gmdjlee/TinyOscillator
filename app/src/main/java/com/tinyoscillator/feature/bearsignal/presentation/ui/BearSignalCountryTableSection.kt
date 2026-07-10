package com.tinyoscillator.feature.bearsignal.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalResult
import com.tinyoscillator.feature.bearsignal.domain.model.MarketReturns
import com.tinyoscillator.ui.theme.signColor

private val PERIOD_LABELS = listOf("-12개월", "-6개월", "-3개월", "-1개월")

/**
 * 섹션 3 · 신호1 상세 국가별 수익률 표 (TASK.md §5.2-3, §5.3, 부록 B #7).
 *
 * 프로토타입은 기간=행/국가=열(광폭)이지만 모바일은 **국가=행(20)×기간=열(4)로 전치**한다.
 * 기간 선택은 FilterChip(§5.3), 선택 기간이 신호1 판정(신호1 카드·`result.ma`)에 즉시 반영된다.
 * 행 탭 → 다이얼로그로 4기간 인라인 편집(§5.3 "값은 인라인 편집(수동 갱신) 가능") — 20행×4열 텍스트필드를
 * LazyColumn 내부에 직접 배치하면 포커스/성능 이슈가 크므로, 행 단위 편집 다이얼로그로 대체한다.
 */
@Composable
fun BearSignalCountryTableSection(
    inputs: BearSignalInputs,
    result: BearSignalResult,
    manualRequiredNames: Set<String>,
    onPeriodSelected: (Int) -> Unit,
    onEditMarket: (name: String, r: List<Double?>) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingMarket by remember { mutableStateOf<MarketReturns?>(null) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text("신호 1 상세 · 도표 48", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("국가별 주가 수익률 비교", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PERIOD_LABELS.forEachIndexed { idx, label ->
                    FilterChip(
                        selected = inputs.periodIdx == idx,
                        onClick = { onPeriodSelected(idx) },
                        label = { Text(label) }
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = (if (result.ma.neg >= 7) levelColor(2) else MaterialTheme.colorScheme.surfaceVariant).copy(alpha = 0.15f)
            ) {
                Text(
                    "이탈 지수 수 ${result.ma.neg} / 20 (선택 기간: ${PERIOD_LABELS[inputs.periodIdx]})",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (result.ma.neg >= 7) levelColor(2) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            Row(Modifier.fillMaxWidth()) {
                Text("국가", modifier = Modifier.weight(1.3f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PERIOD_LABELS.forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider()

            Column {
                inputs.markets.forEach { market ->
                    CountryRow(
                        market = market,
                        selectedPeriodIdx = inputs.periodIdx,
                        manualRequired = market.name in manualRequiredNames,
                        onClick = { editingMarket = market }
                    )
                }
            }

            Text(
                "■ 플러스  ■ 마이너스(이탈)  선택된 기간이 신호1 판정에 반영됩니다. 행을 탭하면 값을 직접 수정할 수 있습니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    editingMarket?.let { market ->
        MarketReturnEditDialog(
            market = market,
            onDismiss = { editingMarket = null },
            onConfirm = { r ->
                onEditMarket(market.name, r)
                editingMarket = null
            }
        )
    }
}

@Composable
private fun CountryRow(
    market: MarketReturns,
    selectedPeriodIdx: Int,
    manualRequired: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp) // §5.4 접근성 — 최소 탭 타깃 48dp
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .semantics {
                contentDescription = buildString {
                    append(market.name)
                    if (market.lead) append(" 주도주")
                    if (manualRequired) append(" 수동입력 필요")
                    append(", ")
                    market.r.forEachIndexed { i, v ->
                        append(PERIOD_LABELS[i])
                        append(" ")
                        append(v?.let { "%.1f".format(it) + "퍼센트" } ?: "값 없음")
                        if (i < market.r.lastIndex) append(", ")
                    }
                    append(". 탭하여 값 수정")
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.3f)) {
            Text(
                market.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (market.lead) FontWeight.Bold else FontWeight.Normal,
                color = if (market.lead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (market.lead) {
                Text("주도", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (manualRequired) {
                Text("수동 필요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        market.r.forEachIndexed { i, v ->
            Text(
                text = v?.let { "${if (it >= 0) "+" else ""}${"%.1f".format(it)}" } ?: "-",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (i == selectedPeriodIdx) FontWeight.Bold else FontWeight.Normal,
                color = v?.let { signColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MarketReturnEditDialog(
    market: MarketReturns,
    onDismiss: () -> Unit,
    onConfirm: (List<Double?>) -> Unit
) {
    val fields = remember(market.name) {
        market.r.map { v -> mutableStateOf(v?.let { "%.1f".format(it) } ?: "") }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${market.name} 수익률 편집") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PERIOD_LABELS.forEachIndexed { i, label ->
                    OutlinedTextField(
                        value = fields[i].value,
                        onValueChange = { fields[i].value = it },
                        label = { Text(label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(fields.map { it.value.toDoubleOrNull() })
            }) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
