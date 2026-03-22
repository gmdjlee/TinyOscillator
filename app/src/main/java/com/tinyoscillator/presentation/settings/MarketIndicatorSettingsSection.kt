package com.tinyoscillator.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tinyoscillator.presentation.common.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketIndicatorTab(
    marketOscCollectionDays: Int,
    onMarketOscCollectionDaysChange: (Int) -> Unit,
    marketDepositCollectionDays: Int,
    onMarketDepositCollectionDaysChange: (Int) -> Unit,
    saveMessage: String?,
    onSave: (oscDays: Int, depositDays: Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // === 과매수/과매도 수집 기간 ===
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("과매수/과매도 데이터 수집 기간", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            CollectionPeriodRow(
                daysBack = marketOscCollectionDays,
                onDaysBackChange = onMarketOscCollectionDaysChange,
                onSave = { onSave(marketOscCollectionDays, marketDepositCollectionDays) }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "초기 수집 또는 전체 새로고침 시 수집할 기간입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // === 자금 동향 수집 기간 ===
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("자금 동향 데이터 수집 기간", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            CollectionPeriodRow(
                daysBack = marketDepositCollectionDays,
                onDaysBackChange = onMarketDepositCollectionDaysChange,
                onSave = { onSave(marketOscCollectionDays, marketDepositCollectionDays) }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "초기 수집 또는 전체 새로고침 시 수집할 기간입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        saveMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionPeriodRow(
    daysBack: Int,
    onDaysBackChange: (Int) -> Unit,
    onSave: () -> Unit
) {
    val unitOptions = listOf("주" to 7, "월" to 30, "년" to 365)

    val initialUnit = when {
        daysBack % 365 == 0 -> 365
        daysBack % 30 == 0 -> 30
        daysBack % 7 == 0 -> 7
        else -> 30
    }
    val initialValue = when (initialUnit) {
        365 -> daysBack / 365
        30 -> daysBack / 30
        7 -> daysBack / 7
        else -> daysBack / 30
    }.coerceAtLeast(1)

    var selectedUnitMultiplier by remember(daysBack) { mutableIntStateOf(initialUnit) }
    var periodValue by remember(daysBack) { mutableStateOf(initialValue.toString()) }
    var unitExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = periodValue,
            onValueChange = { v ->
                val filtered = v.filter { it.isDigit() }.take(3)
                periodValue = filtered
                filtered.toIntOrNull()?.let { num ->
                    if (num > 0) onDaysBackChange(num * selectedUnitMultiplier)
                }
            },
            label = { Text("기간") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.weight(1f)
        )

        ExposedDropdownMenuBox(
            expanded = unitExpanded,
            onExpandedChange = { unitExpanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = unitOptions.first { it.second == selectedUnitMultiplier }.first,
                onValueChange = {},
                readOnly = true,
                label = { Text("단위") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = unitExpanded,
                onDismissRequest = { unitExpanded = false }
            ) {
                unitOptions.forEach { (label, multiplier) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            selectedUnitMultiplier = multiplier
                            unitExpanded = false
                            periodValue.toIntOrNull()?.let { num ->
                                if (num > 0) onDaysBackChange(num * multiplier)
                            }
                        }
                    )
                }
            }
        }

        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f)
        ) {
            Text("저장")
        }
    }
}
