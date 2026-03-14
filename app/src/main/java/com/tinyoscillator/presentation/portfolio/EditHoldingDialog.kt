package com.tinyoscillator.presentation.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tinyoscillator.domain.model.PortfolioHoldingItem

@Composable
fun EditHoldingDialog(
    holding: PortfolioHoldingItem,
    onDismiss: () -> Unit,
    onConfirm: (stockName: String, market: String, sector: String, targetPrice: Int) -> Unit
) {
    var stockName by remember { mutableStateOf(holding.stockName) }
    var market by remember { mutableStateOf(holding.market) }
    var sector by remember { mutableStateOf(holding.sector) }
    var targetPrice by remember {
        mutableStateOf(if (holding.targetPrice > 0) holding.targetPrice.toString() else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("종목 정보 수정") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "티커: ${holding.ticker}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = stockName,
                    onValueChange = { stockName = it },
                    label = { Text("종목명") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = market,
                    onValueChange = { market = it },
                    label = { Text("시장") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = sector,
                    onValueChange = { sector = it },
                    label = { Text("업종") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = targetPrice,
                    onValueChange = { targetPrice = it.filter { c -> c.isDigit() } },
                    label = { Text("목표가 (원)") },
                    placeholder = { Text("미설정 시 비워두세요") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (stockName.isBlank()) return@TextButton
                    onConfirm(
                        stockName.trim(),
                        market.trim(),
                        sector.trim(),
                        targetPrice.toIntOrNull() ?: 0
                    )
                },
                enabled = stockName.isNotBlank()
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
