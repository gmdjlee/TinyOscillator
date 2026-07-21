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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalResult
import com.tinyoscillator.feature.bearsignal.domain.model.MarketReturns
import com.tinyoscillator.presentation.common.FinanceCard
import com.tinyoscillator.ui.theme.signColor
import java.util.Locale

private val PERIOD_LABELS = listOf("-12개월", "-6개월", "-3개월", "-1개월")

/**
 * 국가별 수익률 표시 포맷(Phase 6-4) — `"%.1f".format(...)`은 [Locale.getDefault]를 따라 콤마
 * 소수점 로케일(예: 프랑스어)에서 "1,2"처럼 렌더돼 [String.toDoubleOrNull]로 되읽지 못하는 결함이
 * 있었다. 표시는 항상 [Locale.US]로 점(`.`) 소수점 고정한다 — 저장되는 수치 의미·정밀도는 불변.
 */
internal fun formatMarketReturnValue(value: Double): String = String.format(Locale.US, "%.1f", value)

/**
 * 섹션 3 · 신호1 상세 국가별 수익률 표 (TASK_bear_signal_console.md §5.2-3, §5.3, 부록 B #7).
 *
 * 프로토타입은 기간=행/국가=열(광폭)이지만 모바일은 **국가=행(20)×기간=열(4)로 전치**한다.
 * 기간 선택은 FilterChip(§5.3), 선택 기간이 신호1 판정(신호1 카드·`result.ma`)에 즉시 반영된다.
 * 행 탭 → 다이얼로그로 4기간 인라인 편집(§5.3 "값은 인라인 편집(수동 갱신) 가능") — 20행×4열 텍스트필드를
 * LazyColumn 내부에 직접 배치하면 포커스/성능 이슈가 크므로, 행 단위 편집 다이얼로그로 대체한다.
 *
 * @param manyCountriesBreached 신호1 "이탈국 다수" 강조 플래그(§3.0 retrofit 후속) —
 * `result.ma.neg >= BearThresholds.s1.manyCountries`를 [com.tinyoscillator.feature.bearsignal.presentation.BearSignalViewModel]에서
 * 미리 계산해 전달한다. 컴포저블이 `>= 7` 등 §3 임계치를 직접 비교하지 않도록 한다(§7 "config 구동").
 */
@Composable
fun BearSignalCountryTableSection(
    inputs: BearSignalInputs,
    result: BearSignalResult,
    manualRequiredNames: Set<String>,
    manyCountriesBreached: Boolean,
    onPeriodSelected: (Int) -> Unit,
    onEditMarket: (name: String, r: List<Double?>) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingMarket by remember { mutableStateOf<MarketReturns?>(null) }

    FinanceCard(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
            color = (if (manyCountriesBreached) levelColor(2) else MaterialTheme.colorScheme.surfaceVariant).copy(alpha = 0.15f)
        ) {
            Text(
                "이탈 지수 수 ${result.ma.neg} / 20 (선택 기간: ${PERIOD_LABELS[inputs.periodIdx]})",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (manyCountriesBreached) levelColor(2) else MaterialTheme.colorScheme.onSurfaceVariant
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
                        append(v?.let { formatMarketReturnValue(it) + "퍼센트" } ?: "값 없음")
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
                text = v?.let { "${if (it >= 0) "+" else ""}${formatMarketReturnValue(it)}" } ?: "-",
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
        market.r.map { v -> mutableStateOf(v?.let { formatMarketReturnValue(it) } ?: "") }
    }

    // 빈 문자열은 의도된 "값 없음"(null)이지만, 비어있지 않은데 파싱 불가한 입력(예: 콤마 로케일
    // 오타·문자 혼입)은 저장 시 조용히 null로 뭉개지던 결함이 있었다(Phase 6-4) — 저장 버튼을
    // 막고 에러를 표시해 사용자가 인지하게 한다. 저장되는 값의 의미·경로는 불변.
    val hasInvalidInput = fields.any { it.value.isNotBlank() && it.value.toDoubleOrNull() == null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${market.name} 수익률 편집") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PERIOD_LABELS.forEachIndexed { i, label ->
                    val isFieldInvalid = fields[i].value.isNotBlank() && fields[i].value.toDoubleOrNull() == null
                    OutlinedTextField(
                        value = fields[i].value,
                        onValueChange = { fields[i].value = it },
                        label = { Text(label) },
                        singleLine = true,
                        isError = isFieldInvalid,
                        supportingText = if (isFieldInvalid) {
                            { Text("숫자 형식이 아닙니다 (예: -1.5)") }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !hasInvalidInput,
                onClick = {
                    onConfirm(fields.map { it.value.toDoubleOrNull() })
                }
            ) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
