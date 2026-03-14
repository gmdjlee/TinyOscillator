package com.tinyoscillator.presentation.portfolio

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.core.database.entity.StockMasterEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHoldingDialog(
    viewModel: PortfolioViewModel,
    onDismiss: () -> Unit,
    onConfirm: (ticker: String, stockName: String, market: String, sector: String, shares: Int, pricePerShare: Int, date: String, memo: String, targetPrice: Int) -> Unit
) {
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    var selectedStock by remember { mutableStateOf<StockMasterEntity?>(null) }
    var query by remember { mutableStateOf("") }
    var shares by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))) }
    var memo by remember { mutableStateOf("") }
    var targetPrice by remember { mutableStateOf("") }

    val showDropdown = searchResults.isNotEmpty() && selectedStock == null && query.isNotBlank()

    AlertDialog(
        onDismissRequest = {
            viewModel.searchStock("")
            onDismiss()
        },
        title = { Text("종목 추가") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Stock search + overlay dropdown
                Box {
                    Column {
                        OutlinedTextField(
                            value = query,
                            onValueChange = {
                                query = it
                                selectedStock = null
                                viewModel.searchStock(it)
                            },
                            label = { Text("종목명 또는 코드") },
                            placeholder = { Text("예: 삼성전자") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (selectedStock != null) {
                            Text(
                                "${selectedStock!!.ticker} (${selectedStock!!.market}) ${selectedStock!!.sector}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    // Overlay dropdown
                    if (showDropdown) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 60.dp)
                                .heightIn(max = 200.dp)
                                .zIndex(1f),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                searchResults.take(10).forEach { stock ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedStock = stock
                                                query = stock.name
                                                viewModel.searchStock("")
                                            }
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(stock.name, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "${stock.ticker} (${stock.market})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (stock != searchResults.take(10).last()) {
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                }

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
                    label = { Text("매입가 (원)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = targetPrice,
                    onValueChange = { targetPrice = it.filter { c -> c.isDigit() } },
                    label = { Text("목표가 (원)") },
                    placeholder = { Text("미설정 시 비워두세요") },
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
                    val stock = selectedStock ?: return@TextButton
                    val sharesInt = shares.toIntOrNull() ?: return@TextButton
                    val priceInt = price.toIntOrNull() ?: return@TextButton
                    if (sharesInt <= 0 || priceInt <= 0) return@TextButton
                    viewModel.searchStock("")
                    onConfirm(
                        stock.ticker,
                        stock.name,
                        stock.market,
                        stock.sector,
                        sharesInt,
                        priceInt,
                        date,
                        memo,
                        targetPrice.toIntOrNull() ?: 0
                    )
                },
                enabled = selectedStock != null
                    && shares.toIntOrNull() != null && shares.toIntOrNull()!! > 0
                    && price.toIntOrNull() != null && price.toIntOrNull()!! > 0
            ) {
                Text("추가")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                viewModel.searchStock("")
                onDismiss()
            }) {
                Text("취소")
            }
        }
    )
}
