package com.tinyoscillator.presentation.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tinyoscillator.domain.model.TransactionItem
import kotlin.math.abs

@Composable
fun EditTransactionDialog(
    transaction: TransactionItem,
    onDismiss: () -> Unit,
    onConfirm: (shares: Int, pricePerShare: Int, date: String, memo: String) -> Unit
) {
    val isSell = transaction.shares < 0
    var shares by remember { mutableStateOf(abs(transaction.shares).toString()) }
    var price by remember { mutableStateOf(transaction.pricePerShare.toString()) }
    var date by remember { mutableStateOf(transaction.date) }
    var memo by remember { mutableStateOf(transaction.memo) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isSell) "매도 거래 수정" else "매수 거래 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = shares,
                    onValueChange = { shares = it.filter { c -> c.isDigit() } },
                    label = { Text("주식수") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.filter { c -> c.isDigit() } },
                    label = { Text(if (isSell) "매도가 (원)" else "매입가 (원)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("거래일 (yyyyMMdd)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("메모 (선택)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val sharesInt = shares.toIntOrNull() ?: return@TextButton
                    val priceInt = price.toIntOrNull() ?: return@TextButton
                    if (sharesInt <= 0 || priceInt <= 0) return@TextButton
                    val actualShares = if (isSell) -sharesInt else sharesInt
                    onConfirm(actualShares, priceInt, date, memo)
                },
                enabled = shares.toIntOrNull() != null && shares.toIntOrNull()!! > 0
                    && price.toIntOrNull() != null && price.toIntOrNull()!! > 0
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
