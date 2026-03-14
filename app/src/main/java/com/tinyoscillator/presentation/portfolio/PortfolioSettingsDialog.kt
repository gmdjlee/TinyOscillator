package com.tinyoscillator.presentation.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun PortfolioSettingsDialog(
    name: String,
    maxWeightPercent: Int,
    totalAmountLimit: Long?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, maxWeightPercent: Int, totalAmountLimit: Long?) -> Unit
) {
    var editName by remember { mutableStateOf(name) }
    var editMaxWeight by remember { mutableStateOf(maxWeightPercent.toString()) }
    var editLimit by remember { mutableStateOf(totalAmountLimit?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("포트폴리오 설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("포트폴리오명") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editMaxWeight,
                    onValueChange = { editMaxWeight = it.filter { c -> c.isDigit() } },
                    label = { Text("최대 비중 (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    supportingText = { Text("이 비중을 초과하면 리밸런싱 신호 표시") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editLimit,
                    onValueChange = { editLimit = it.filter { c -> c.isDigit() } },
                    label = { Text("총 자산 (원)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    placeholder = { Text("미설정 시 보유종목 평가합계 기준") },
                    supportingText = { Text("비중 계산의 기준 금액 (현금+주식 총액)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val maxWeight = editMaxWeight.toIntOrNull() ?: maxWeightPercent
                    val limit = editLimit.toLongOrNull()
                    onConfirm(editName.ifBlank { name }, maxWeight.coerceIn(1, 100), limit)
                }
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
