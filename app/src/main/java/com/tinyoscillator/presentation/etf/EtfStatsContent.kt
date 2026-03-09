package com.tinyoscillator.presentation.etf

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.domain.model.ChangeType
import com.tinyoscillator.presentation.etf.stats.AmountRankingTab
import com.tinyoscillator.presentation.etf.stats.CashDepositTab
import com.tinyoscillator.presentation.etf.stats.StockAnalysisTab
import com.tinyoscillator.presentation.etf.stats.StockChangeTab

private enum class StatsTab(val label: String) {
    AMOUNT_RANKING("금액 순위"),
    NEW("신규 편입"),
    REMOVED("제외"),
    INCREASED("비중 증가"),
    DECREASED("비중 감소"),
    CASH_DEPOSIT("원화예금"),
    ANALYSIS("분석")
}

@Composable
fun EtfStatsContent(
    onStockClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: EtfStatsViewModel = hiltViewModel()
) {
    val dates by viewModel.dates.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val comparisonDate by viewModel.comparisonDate.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val amountRanking by viewModel.amountRanking.collectAsStateWithLifecycle()
    val newStocks by viewModel.newStocks.collectAsStateWithLifecycle()
    val removedStocks by viewModel.removedStocks.collectAsStateWithLifecycle()
    val increasedStocks by viewModel.increasedStocks.collectAsStateWithLifecycle()
    val decreasedStocks by viewModel.decreasedStocks.collectAsStateWithLifecycle()
    val cashDeposit by viewModel.cashDeposit.collectAsStateWithLifecycle()
    val stockSearchResults by viewModel.stockSearchResults.collectAsStateWithLifecycle()
    val stockAnalysis by viewModel.stockAnalysis.collectAsStateWithLifecycle()
    val selectedStockName by viewModel.selectedStockName.collectAsStateWithLifecycle()

    var selectedStatsTab by rememberSaveable { mutableStateOf(StatsTab.AMOUNT_RANKING) }

    Column(modifier = modifier) {
        // Date selector row
        if (dates.isNotEmpty()) {
            DateSelectorRow(
                dates = dates,
                selectedDate = selectedDate,
                comparisonDate = comparisonDate,
                onDateSelect = { viewModel.selectDate(it) }
            )
        }

        // Sub-tab row
        ScrollableTabRow(
            selectedTabIndex = selectedStatsTab.ordinal,
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 4.dp
        ) {
            StatsTab.entries.forEach { tab ->
                Tab(
                    selected = selectedStatsTab == tab,
                    onClick = { selectedStatsTab = tab },
                    text = { Text(tab.label, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            when (selectedStatsTab) {
                StatsTab.AMOUNT_RANKING -> AmountRankingTab(
                    items = amountRanking,
                    onStockClick = onStockClick,
                    modifier = Modifier.fillMaxSize()
                )
                StatsTab.NEW -> StockChangeTab(
                    changes = newStocks,
                    changeType = ChangeType.NEW,
                    onStockClick = onStockClick,
                    modifier = Modifier.fillMaxSize()
                )
                StatsTab.REMOVED -> StockChangeTab(
                    changes = removedStocks,
                    changeType = ChangeType.REMOVED,
                    onStockClick = onStockClick,
                    modifier = Modifier.fillMaxSize()
                )
                StatsTab.INCREASED -> StockChangeTab(
                    changes = increasedStocks,
                    changeType = ChangeType.INCREASED,
                    onStockClick = onStockClick,
                    modifier = Modifier.fillMaxSize()
                )
                StatsTab.DECREASED -> StockChangeTab(
                    changes = decreasedStocks,
                    changeType = ChangeType.DECREASED,
                    onStockClick = onStockClick,
                    modifier = Modifier.fillMaxSize()
                )
                StatsTab.CASH_DEPOSIT -> CashDepositTab(
                    data = cashDeposit,
                    modifier = Modifier.fillMaxSize()
                )
                StatsTab.ANALYSIS -> StockAnalysisTab(
                    searchResults = stockSearchResults,
                    analysisResults = stockAnalysis,
                    selectedStockName = selectedStockName,
                    onSearch = { viewModel.searchStock(it) },
                    onSelectStock = { viewModel.analyzeStock(it) },
                    onStockClick = onStockClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun DateSelectorRow(
    dates: List<String>,
    selectedDate: String?,
    comparisonDate: String?,
    onDateSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayDate = selectedDate?.let { formatDisplayDate(it) } ?: "날짜 선택"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("기준일:", style = MaterialTheme.typography.bodyMedium)

        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(displayDate)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                dates.forEach { date ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                formatDisplayDate(date),
                                color = if (date == selectedDate) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            onDateSelect(date)
                            expanded = false
                        }
                    )
                }
            }
        }

        if (comparisonDate != null) {
            Text(
                "비교: ${formatDisplayDate(comparisonDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDisplayDate(yyyyMMdd: String): String {
    if (yyyyMMdd.length != 8) return yyyyMMdd
    return "${yyyyMMdd.substring(0, 4)}-${yyyyMMdd.substring(4, 6)}-${yyyyMMdd.substring(6, 8)}"
}
