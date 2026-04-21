package com.tinyoscillator.presentation.chart.composable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tinyoscillator.domain.model.Indicator
import com.tinyoscillator.domain.model.IndicatorParams
import com.tinyoscillator.domain.model.OverlayType
import kotlin.math.roundToInt

// Enum 기반 정적 파티션 — 매 recomposition마다 filter() 재실행을 방지하기 위해 top-level로 승격
private val PRICE_INDICATORS: List<Indicator> = Indicator.entries.filter { it.overlayType == OverlayType.PRICE }
private val OSCILLATOR_INDICATORS: List<Indicator> = Indicator.entries.filter { it.overlayType == OverlayType.OSCILLATOR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndicatorSelectionSheet(
    selectedIndicators: Set<Indicator>,
    params: Map<Indicator, IndicatorParams>,
    onToggle: (Indicator) -> Unit,
    onParamsChange: (Indicator, IndicatorParams) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            // 가격 오버레이 그룹 (최대 4개)
            item { SectionHeader("가격 지표") }
            items(PRICE_INDICATORS, key = { it.name }) { ind ->
                val priceCount = selectedIndicators.count { it.overlayType == OverlayType.PRICE }
                IndicatorRow(
                    indicator = ind,
                    isSelected = ind in selectedIndicators,
                    isEnabled = ind in selectedIndicators || priceCount < 4,
                    params = params[ind] ?: ind.defaultParams,
                    onToggle = { onToggle(ind) },
                    onParamsChange = { onParamsChange(ind, it) },
                )
            }

            // 오실레이터 그룹 (한 번에 하나)
            item { SectionHeader("오실레이터") }
            items(OSCILLATOR_INDICATORS, key = { it.name }) { ind ->
                val hasOscillator = selectedIndicators.any { it.overlayType == OverlayType.OSCILLATOR }
                IndicatorRow(
                    indicator = ind,
                    isSelected = ind in selectedIndicators,
                    isEnabled = ind in selectedIndicators || !hasOscillator,
                    params = params[ind] ?: ind.defaultParams,
                    onToggle = { onToggle(ind) },
                    onParamsChange = { onParamsChange(ind, it) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun IndicatorRow(
    indicator: Indicator,
    isSelected: Boolean,
    isEnabled: Boolean,
    params: IndicatorParams,
    onToggle: () -> Unit,
    onParamsChange: (IndicatorParams) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = isEnabled) { onToggle() }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = isSelected, onCheckedChange = null, enabled = isEnabled)
            Spacer(Modifier.width(12.dp))
            Text(indicator.displayNameKo, modifier = Modifier.weight(1f))
            if (isSelected && indicator.defaultParams.period > 0) {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "파라미터 설정",
                    )
                }
            }
        }
        AnimatedVisibility(visible = expanded && isSelected) {
            Column(Modifier.padding(start = 44.dp, bottom = 8.dp)) {
                if (indicator.defaultParams.period > 0) {
                    PeriodSlider("기간", params.period, 3..200) {
                        onParamsChange(params.copy(period = it))
                    }
                }
                if (indicator == Indicator.BOLLINGER) {
                    MultiplierSlider(params.multiplier) {
                        onParamsChange(params.copy(multiplier = it))
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodSlider(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: $value", modifier = Modifier.width(80.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MultiplierSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("배수: ${"%.1f".format(value)}", modifier = Modifier.width(80.dp))
        Slider(
            value = value,
            onValueChange = { onValueChange((it * 10).roundToInt() / 10f) },
            valueRange = 1f..3f,
            modifier = Modifier.weight(1f),
        )
    }
}
