package com.tinyoscillator.presentation.sector

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AddThemeDialog(
    onConfirm: (name: String, tickers: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var tickersText by remember { mutableStateOf("") }

    val parsedTickers = remember(tickersText) {
        tickersText.split(",", " ", "\n")
            .map { it.trim() }
            .filter { it.matches(Regex("^\\d{6}$")) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("테마 추가") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("테마명") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = tickersText,
                    onValueChange = { tickersText = it },
                    label = { Text("종목코드 (쉼표 구분)") },
                    placeholder = { Text("005930, 000660, 373220") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (parsedTickers.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${parsedTickers.size}개 종목 인식됨",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), parsedTickers) },
                enabled = name.isNotBlank() && parsedTickers.isNotEmpty(),
            ) {
                Text("추가")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
    )
}
