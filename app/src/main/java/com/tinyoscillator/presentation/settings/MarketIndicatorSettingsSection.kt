package com.tinyoscillator.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

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
