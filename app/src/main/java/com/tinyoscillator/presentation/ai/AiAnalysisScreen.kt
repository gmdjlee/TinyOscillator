package com.tinyoscillator.presentation.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.presentation.common.AiAnalysisSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAnalysisScreen(
    onSettingsClick: () -> Unit,
    viewModel: AiAnalysisViewModel = hiltViewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val marketAiState by viewModel.marketAiState.collectAsStateWithLifecycle()
    val stockAiState by viewModel.stockAiState.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val selectedStock by viewModel.selectedStock.collectAsStateWithLifecycle()
    val stockDataState by viewModel.stockDataState.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 분석") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Row
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                AiTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.label) }
                    )
                }
            }

            when (selectedTab) {
                AiTab.MARKET -> {
                    MarketTabContent(
                        marketAiState = marketAiState,
                        onAnalyze = { viewModel.analyzeMarketWithAi() },
                        onDismiss = { viewModel.dismissMarketAi() }
                    )
                }

                AiTab.STOCK -> {
                    StockTabContent(
                        query = query,
                        onQueryChange = { newQuery ->
                            query = newQuery
                            viewModel.searchStock(newQuery)
                        },
                        searchResults = searchResults,
                        onStockSelect = { stock ->
                            query = stock.name
                            viewModel.searchStock("")
                            viewModel.selectStock(stock.ticker, stock.name, stock.market, stock.sector.ifBlank { null })
                        },
                        selectedStock = selectedStock,
                        stockDataState = stockDataState,
                        stockAiState = stockAiState,
                        onAnalyze = { viewModel.analyzeStockWithAi() },
                        onDismiss = { viewModel.dismissStockAi() }
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketTabContent(
    marketAiState: com.tinyoscillator.domain.model.AiAnalysisState,
    onAnalyze: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "시장 지표 AI 분석",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "KOSPI/KOSDAQ 과매수·과매도 지표와 투자자 예탁금 동향을 종합하여 시장 상태를 분석합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AiAnalysisSection(
            state = marketAiState,
            onAnalyze = onAnalyze,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun StockTabContent(
    query: String,
    onQueryChange: (String) -> Unit,
    searchResults: List<com.tinyoscillator.core.database.entity.StockMasterEntity>,
    onStockSelect: (com.tinyoscillator.core.database.entity.StockMasterEntity) -> Unit,
    selectedStock: SelectedStockInfo?,
    stockDataState: StockDataState,
    stockAiState: com.tinyoscillator.domain.model.AiAnalysisState,
    onAnalyze: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search field
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("종목명 또는 종목코드") },
                placeholder = { Text("예: 삼성전자, 005930") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (searchResults.isNotEmpty()) {
                        onStockSelect(searchResults.first())
                    }
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Content below search
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectedStock != null) {
                    // Selected stock info card
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                selectedStock.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${selectedStock.ticker} | ${selectedStock.market ?: "-"} | ${selectedStock.sector ?: "-"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Data loading state
                    when (val dataState = stockDataState) {
                        is StockDataState.Loading -> {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    Text("데이터 수집 중...")
                                }
                            }
                        }

                        is StockDataState.Loaded -> {
                            // Data summary chips
                            DataSummaryChips(dataState)

                            // AI Analysis section
                            AiAnalysisSection(
                                state = stockAiState,
                                onAnalyze = onAnalyze,
                                onDismiss = onDismiss
                            )
                        }

                        is StockDataState.Error -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    dataState.message,
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        is StockDataState.Idle -> {}
                    }
                } else {
                    // No stock selected
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        "종목을 검색하여 AI 분석을 시작하세요",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Text(
                        "오실레이터 + DeMark + 재무정보 + ETF 편입 데이터를 종합하여 분석합니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Search results dropdown overlay
        if (searchResults.isNotEmpty() && query.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 64.dp) // below search field
                    .heightIn(max = 300.dp)
                    .zIndex(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    searchResults.take(10).forEach { stock ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onStockSelect(stock) }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stock.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${stock.ticker} (${stock.market})",
                                style = MaterialTheme.typography.bodyMedium,
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
}

@Composable
private fun DataSummaryChips(dataState: StockDataState.Loaded) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DataChip("오실레이터", dataState.oscillatorRows.isNotEmpty())
        DataChip("DeMark", dataState.demarkRows.isNotEmpty())
        DataChip("재무", dataState.financialData != null)
        DataChip("ETF", dataState.etfAggregated.isNotEmpty())
    }
}

@Composable
private fun DataChip(label: String, available: Boolean) {
    SuggestionChip(
        onClick = {},
        label = {
            Text(
                if (available) "$label ✓" else "$label ✗",
                style = MaterialTheme.typography.labelSmall
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = if (available)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    )
}
