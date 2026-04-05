package com.tinyoscillator.presentation.screener

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tinyoscillator.domain.model.MarketType
import com.tinyoscillator.domain.model.ScreenerFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenerFilterSheet(
    currentFilter: ScreenerFilter,
    sectors: List<String>,
    onApply: (ScreenerFilter) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var minSignal by remember { mutableFloatStateOf(currentFilter.minSignalScore) }
    var maxSignal by remember { mutableFloatStateOf(currentFilter.maxSignalScore) }
    var marketCapPreset by remember { mutableStateOf(marketCapPresetOf(currentFilter)) }
    var maxPbr by remember { mutableFloatStateOf(currentFilter.maxPbr.coerceAtMost(5f)) }
    var minForeign by remember { mutableFloatStateOf(currentFilter.minForeignRatio) }
    var minVolume by remember { mutableFloatStateOf(currentFilter.minVolumeRatio.coerceAtMost(5f)) }
    var selectedMarket by remember { mutableStateOf(currentFilter.marketType) }
    var selectedSector by remember { mutableStateOf(currentFilter.sectorCode) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("필터 설정", style = MaterialTheme.typography.titleMedium)

            // 신호강도 RangeSlider
            Text("신호 강도: ${(minSignal * 100).toInt()}% ~ ${(maxSignal * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall)
            RangeSlider(
                value = minSignal..maxSignal,
                onValueChange = { range -> minSignal = range.start; maxSignal = range.endInclusive },
                valueRange = 0f..1f,
                steps = 19,
            )

            // 시가총액 프리셋
            Text("시가총액", style = MaterialTheme.typography.bodySmall)
            MarketCapSelector(
                selected = marketCapPreset,
                onSelect = { marketCapPreset = it },
            )

            // PBR 최대값
            Text("PBR 최대: ${if (maxPbr >= 5f) "제한없음" else "%.1f".format(maxPbr)}",
                style = MaterialTheme.typography.bodySmall)
            Slider(
                value = maxPbr,
                onValueChange = { maxPbr = it },
                valueRange = 0f..5f,
                steps = 9,
            )

            // 외국인비중 최소
            Text("외국인 비중 최소: ${(minForeign * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall)
            Slider(
                value = minForeign,
                onValueChange = { minForeign = it },
                valueRange = 0f..0.5f,
                steps = 9,
            )

            // 거래량 배율 최소
            Text("거래량 배율 최소: ${"%.1f".format(minVolume)}x",
                style = MaterialTheme.typography.bodySmall)
            Slider(
                value = minVolume,
                onValueChange = { minVolume = it },
                valueRange = 0f..5f,
                steps = 9,
            )

            // 시장 구분
            Text("시장 구분", style = MaterialTheme.typography.bodySmall)
            MarketTypeSelector(
                selected = selectedMarket,
                onSelect = { selectedMarket = it },
            )

            // 섹터 드롭다운
            if (sectors.isNotEmpty()) {
                SectorDropdown(
                    sectors = sectors,
                    selected = selectedSector,
                    onSelect = { selectedSector = it },
                )
            }

            Spacer(Modifier.height(8.dp))

            // 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                    Text("초기화")
                }
                Button(
                    onClick = {
                        val cap = marketCapPreset.toRange()
                        onApply(
                            ScreenerFilter(
                                minSignalScore = minSignal,
                                maxSignalScore = maxSignal,
                                minMarketCapBil = cap.first,
                                maxMarketCapBil = cap.second,
                                maxPbr = if (maxPbr >= 5f) Float.MAX_VALUE else maxPbr,
                                minForeignRatio = minForeign,
                                minVolumeRatio = minVolume,
                                marketType = selectedMarket,
                                sectorCode = selectedSector,
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("적용")
                }
            }
        }
    }
}

private enum class MarketCapPreset(val label: String) {
    ALL("전체"),
    SMALL("소형(<1000억)"),
    MID("중형(1000억~1조)"),
    LARGE("대형(>1조)"),
}

private fun MarketCapPreset.toRange(): Pair<Long, Long> = when (this) {
    MarketCapPreset.ALL -> 0L to Long.MAX_VALUE
    MarketCapPreset.SMALL -> 0L to 1000L
    MarketCapPreset.MID -> 1000L to 10000L
    MarketCapPreset.LARGE -> 10000L to Long.MAX_VALUE
}

private fun marketCapPresetOf(filter: ScreenerFilter): MarketCapPreset = when {
    filter.minMarketCapBil >= 10000L -> MarketCapPreset.LARGE
    filter.minMarketCapBil >= 1000L -> MarketCapPreset.MID
    filter.maxMarketCapBil <= 1000L -> MarketCapPreset.SMALL
    else -> MarketCapPreset.ALL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketCapSelector(
    selected: MarketCapPreset,
    onSelect: (MarketCapPreset) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        MarketCapPreset.entries.forEachIndexed { index, preset ->
            SegmentedButton(
                selected = selected == preset,
                onClick = { onSelect(preset) },
                shape = SegmentedButtonDefaults.itemShape(index, MarketCapPreset.entries.size),
            ) {
                Text(preset.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketTypeSelector(
    selected: MarketType?,
    onSelect: (MarketType?) -> Unit,
) {
    val options = listOf(null to "전체", MarketType.KOSPI to "KOSPI", MarketType.KOSDAQ to "KOSDAQ")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (type, label) ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(label)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectorDropdown(
    sectors: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = selected ?: "전체 섹터"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text("섹터") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("전체 섹터") },
                onClick = { onSelect(null); expanded = false },
            )
            sectors.forEach { sector ->
                DropdownMenuItem(
                    text = { Text(sector) },
                    onClick = { onSelect(sector); expanded = false },
                )
            }
        }
    }
}
