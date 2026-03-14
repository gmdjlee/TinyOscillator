package com.tinyoscillator.presentation.portfolio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.domain.model.PortfolioHoldingItem
import com.tinyoscillator.domain.model.PortfolioSummary
import com.tinyoscillator.domain.model.PortfolioUiState
import java.text.NumberFormat
import java.util.Locale

private val krwFormat = NumberFormat.getNumberInstance(Locale.KOREA)
private val gainColor = Color(0xFFD32F2F) // Red for gain (Korean convention)
private val lossColor = Color(0xFF1976D2) // Blue for loss (Korean convention)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioContent(
    viewModel: PortfolioViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedHoldingId by viewModel.selectedHoldingId.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val selectedHoldingName by viewModel.selectedHoldingName.collectAsStateWithLifecycle()
    val selectedHoldingCurrentPrice by viewModel.selectedHoldingCurrentPrice.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var addTransactionHoldingId by remember { mutableLongStateOf(0L) }
    var addTransactionIsSell by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        when (val state = uiState) {
            is PortfolioUiState.Idle -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("포트폴리오를 로딩 중입니다...")
                }
            }

            is PortfolioUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.message)
                    }
                }
            }

            is PortfolioUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is PortfolioUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Summary Card
                    item {
                        SummaryCard(summary = state.summary)
                    }

                    // Pie Chart
                    if (state.holdings.isNotEmpty()) {
                        item {
                            PortfolioPieChart(
                                holdings = state.holdings,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Holdings Table Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "보유종목 (${state.holdings.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    // Holdings Table
                    if (state.holdings.isNotEmpty()) {
                        item {
                            HoldingsTable(
                                holdings = state.holdings,
                                onHoldingClick = { holding ->
                                    viewModel.selectHolding(
                                        holding.holdingId,
                                        holding.stockName,
                                        holding.currentPrice
                                    )
                                }
                            )
                        }
                    } else {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    "보유 종목이 없습니다.\n아래 + 버튼으로 종목을 추가하세요.",
                                    modifier = Modifier.padding(24.dp),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "종목 추가")
        }
    }

    // Add Holding Dialog
    if (showAddDialog) {
        AddHoldingDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false },
            onConfirm = { ticker, stockName, market, sector, shares, price, date, memo ->
                viewModel.addHolding(ticker, stockName, market, sector, shares, price, date, memo)
                showAddDialog = false
            }
        )
    }

    // Transaction History Sheet
    if (selectedHoldingId != null) {
        TransactionHistorySheet(
            holdingName = selectedHoldingName,
            transactions = transactions,
            onDismiss = { viewModel.clearSelectedHolding() },
            onAddBuy = {
                addTransactionHoldingId = selectedHoldingId!!
                addTransactionIsSell = false
                showAddTransactionDialog = true
            },
            onAddSell = {
                addTransactionHoldingId = selectedHoldingId!!
                addTransactionIsSell = true
                showAddTransactionDialog = true
            },
            onDeleteTransaction = { viewModel.deleteTransaction(it) },
            onDeleteHolding = {
                viewModel.deleteHolding(selectedHoldingId!!)
                viewModel.clearSelectedHolding()
            }
        )
    }

    // Add Transaction Dialog
    if (showAddTransactionDialog) {
        AddTransactionDialog(
            isSell = addTransactionIsSell,
            onDismiss = { showAddTransactionDialog = false },
            onConfirm = { shares, price, date, memo ->
                val actualShares = if (addTransactionIsSell) -shares else shares
                viewModel.addTransaction(addTransactionHoldingId, actualShares, price, date, memo)
                showAddTransactionDialog = false
            }
        )
    }
}

@Composable
private fun SummaryCard(summary: PortfolioSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "포트폴리오 요약",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "총평가금액",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        "${krwFormat.format(summary.totalEvaluation)}원",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "총투자금액",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        "${krwFormat.format(summary.totalInvested)}원",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val plColor = when {
                    summary.totalProfitLoss > 0 -> gainColor
                    summary.totalProfitLoss < 0 -> lossColor
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }
                Column {
                    Text(
                        "총수익률",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        "${if (summary.totalProfitLossPercent >= 0) "+" else ""}${String.format("%.2f", summary.totalProfitLossPercent)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = plColor
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "총수익금",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        "${if (summary.totalProfitLoss >= 0) "+" else ""}${krwFormat.format(summary.totalProfitLoss)}원",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = plColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "보유종목 ${summary.holdingsCount}개",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun HoldingsTable(
    holdings: List<PortfolioHoldingItem>,
    onHoldingClick: (PortfolioHoldingItem) -> Unit
) {
    val scrollState = rememberScrollState()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(8.dp)
        ) {
            // Header
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                TableCell("종목명", 100.dp, fontWeight = FontWeight.Bold)
                TableCell("시장", 60.dp, fontWeight = FontWeight.Bold)
                TableCell("업종", 80.dp, fontWeight = FontWeight.Bold)
                TableCell("평균매입가", 90.dp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                TableCell("현재가", 90.dp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                TableCell("비중%", 60.dp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                TableCell("초과", 40.dp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                TableCell("조절주식", 70.dp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                TableCell("조절금액", 90.dp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                TableCell("수익률%", 70.dp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                TableCell("수익금", 100.dp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            }
            HorizontalDivider()

            // Rows
            holdings.forEach { holding ->
                val plColor = when {
                    holding.profitLossAmount > 0 -> gainColor
                    holding.profitLossAmount < 0 -> lossColor
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Row(
                    modifier = Modifier
                        .clickable { onHoldingClick(holding) }
                        .padding(vertical = 6.dp)
                ) {
                    TableCell(holding.stockName, 100.dp, maxLines = 1)
                    TableCell(holding.market, 60.dp)
                    TableCell(holding.sector, 80.dp, maxLines = 1)
                    TableCell(krwFormat.format(holding.avgBuyPrice), 90.dp, textAlign = TextAlign.End)
                    TableCell(
                        if (holding.currentPrice > 0) krwFormat.format(holding.currentPrice) else "-",
                        90.dp,
                        textAlign = TextAlign.End
                    )
                    TableCell(
                        String.format("%.1f", holding.weightPercent),
                        60.dp,
                        textAlign = TextAlign.End,
                        color = if (holding.isOverWeight) gainColor else null
                    )
                    TableCell(
                        if (holding.isOverWeight) "!!" else "",
                        40.dp,
                        textAlign = TextAlign.Center,
                        color = gainColor,
                        fontWeight = if (holding.isOverWeight) FontWeight.Bold else null
                    )
                    TableCell(
                        if (holding.rebalanceShares > 0) krwFormat.format(holding.rebalanceShares) else "-",
                        70.dp,
                        textAlign = TextAlign.End
                    )
                    TableCell(
                        if (holding.rebalanceAmount > 0) krwFormat.format(holding.rebalanceAmount) else "-",
                        90.dp,
                        textAlign = TextAlign.End
                    )
                    TableCell(
                        "${if (holding.profitLossPercent >= 0) "+" else ""}${String.format("%.1f", holding.profitLossPercent)}",
                        70.dp,
                        textAlign = TextAlign.End,
                        color = plColor
                    )
                    TableCell(
                        "${if (holding.profitLossAmount >= 0) "+" else ""}${krwFormat.format(holding.profitLossAmount)}",
                        100.dp,
                        textAlign = TextAlign.End,
                        color = plColor
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign = TextAlign.Start,
    color: Color? = null,
    maxLines: Int = 1
) {
    Text(
        text = text,
        modifier = Modifier.width(width).padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = fontWeight,
        textAlign = textAlign,
        color = color ?: MaterialTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}
