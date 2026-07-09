package com.tinyoscillator.feature.bearsignal.presentation.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalReportBaseline
import com.tinyoscillator.feature.bearsignal.domain.model.IpoBigConsumption
import com.tinyoscillator.feature.bearsignal.domain.model.ManualBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.usecase.RateGateInputCalculator
import kotlin.math.roundToInt

/**
 * [C]/[D] 등급 수동 입력 BottomSheet (TASK.md §5.4 "BottomSheet 수동 입력(Stepper·SegmentedButton·
 * Slider)", Phase 3 구현 항목 3).
 *
 * 전체 화면 조립(헤더·카드·표)은 Phase 4 범위 — 이 컴포저블은 입력 위젯과 상태 반영만 다룬다.
 * 값이 없으면(수동 오버라이드 미설정) 리포트 기준값([BearSignalReportBaseline])을 표시값으로 사용한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualInputBottomSheet(
    manual: ManualBearSignalInputs,
    onLossChange: (Double) -> Unit,
    onIssueRatioChange: (Double) -> Unit,
    onBigChange: (String) -> Unit,
    onDirChange: (String) -> Unit,
    onCreditChange: (Double) -> Unit,
    onMarginChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("수동 입력 — [C]/[D] 등급 지표", style = MaterialTheme.typography.titleMedium)
            Text(
                "자동 수집이 불가능한 지표입니다. 값을 직접 입력하면 즉시 재계산에 반영됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            SliderField(
                label = "적자상장 비중",
                value = manual.loss?.value ?: BearSignalReportBaseline.LOSS,
                valueRange = 0f..100f,
                unit = "%",
                onValueChange = onLossChange
            )
            SliderField(
                label = "신주 비중 (모니터링 전용)",
                value = manual.issueRatio?.value ?: 0.0,
                valueRange = 0f..100f,
                unit = "%",
                onValueChange = onIssueRatioChange
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SegmentedField(
                label = "대어(OpenAI·Anthropic 등) 공모 소화",
                options = listOf(
                    IpoBigConsumption.SMOOTH to "원활",
                    IpoBigConsumption.PENDING to "대기",
                    IpoBigConsumption.FAILED to "실패"
                ),
                selected = manual.big?.value ?: BearSignalReportBaseline.BIG,
                onSelected = onBigChange
            )
            SegmentedField(
                label = "정책 방향",
                options = listOf(
                    RateGateInputCalculator.DIR_EASE to "인하",
                    RateGateInputCalculator.DIR_HOLD to "동결",
                    RateGateInputCalculator.DIR_HIKE to "인상"
                ),
                selected = manual.dir?.value ?: BearSignalReportBaseline.DIR,
                onSelected = onDirChange
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            StepperField(
                label = "신용거래융자 잔고(조원)",
                value = manual.credit?.value ?: BearSignalReportBaseline.CREDIT,
                step = 1.0,
                range = 0.0..100.0,
                onValueChange = onCreditChange
            )
            SwitchField(
                label = "반대매매 임박",
                checked = manual.margin?.value ?: BearSignalReportBaseline.MARGIN,
                onCheckedChange = onMarginChange
            )

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                Text("리포트 기준값으로 리셋")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SliderField(
    label: String,
    value: Double,
    valueRange: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Double) -> Unit
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text("$label: ${"%.1f".format(value)}$unit", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toDouble()) },
            valueRange = valueRange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentedField(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, displayName) ->
                SegmentedButton(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                ) {
                    Text(displayName)
                }
            }
        }
    }
}

@Composable
private fun StepperField(
    label: String,
    value: Double,
    step: Double,
    range: ClosedRange<Double>,
    onValueChange: (Double) -> Unit
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onValueChange(((value - step).coerceIn(range.start, range.endInclusive))) }
            ) {
                Icon(Icons.Default.Remove, contentDescription = "감소")
            }
            Text(
                "${(value * 10).roundToInt() / 10.0}",
                modifier = Modifier.width(72.dp),
                style = MaterialTheme.typography.bodyLarge
            )
            IconButton(
                onClick = { onValueChange(((value + step).coerceIn(range.start, range.endInclusive))) }
            ) {
                Icon(Icons.Default.Add, contentDescription = "증가")
            }
        }
    }
}

@Composable
private fun SwitchField(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
