package com.tinyoscillator.presentation.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.presentation.common.PillTabRow
import com.tinyoscillator.presentation.common.ThemeToggleIcon
import com.tinyoscillator.ui.theme.LocalThemeModeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAnalysisScreen(
    onSettingsClick: () -> Unit,
    onHeatmapClick: () -> Unit = {},
    marketViewModel: AiMarketAnalysisViewModel = hiltViewModel(),
    stockViewModel: AiStockAnalysisViewModel = hiltViewModel(),
    probabilityViewModel: AiProbabilityAnalysisViewModel = hiltViewModel()
) {
    var selectedTab by rememberSaveable { mutableStateOf(AiTab.MARKET) }

    val searchResults by stockViewModel.searchResults.collectAsStateWithLifecycle()
    val selectedStock by stockViewModel.selectedStock.collectAsStateWithLifecycle()
    val stockDataState by stockViewModel.stockDataState.collectAsStateWithLifecycle()
    val stockChatMessages by stockViewModel.stockChatMessages.collectAsStateWithLifecycle()
    val stockChatLoading by stockViewModel.stockChatLoading.collectAsStateWithLifecycle()

    val marketDataPrepared by marketViewModel.marketDataPrepared.collectAsStateWithLifecycle()
    val marketDataSummary by marketViewModel.marketDataSummary.collectAsStateWithLifecycle()
    val marketDataLoading by marketViewModel.marketDataLoading.collectAsStateWithLifecycle()
    val marketChatMessages by marketViewModel.marketChatMessages.collectAsStateWithLifecycle()
    val marketChatLoading by marketViewModel.marketChatLoading.collectAsStateWithLifecycle()

    val probabilityState by probabilityViewModel.probabilityState.collectAsStateWithLifecycle()
    val interpretationState by probabilityViewModel.interpretationState.collectAsStateWithLifecycle()
    val metaLearnerStatus by probabilityViewModel.metaLearnerStatus.collectAsStateWithLifecycle()
    val ensembleProbability by probabilityViewModel.ensembleProbability.collectAsStateWithLifecycle()
    val algoAccuracy by probabilityViewModel.algoAccuracy.collectAsStateWithLifecycle()
    val algoResults by probabilityViewModel.algoResults.collectAsStateWithLifecycle()
    val snapshots by probabilityViewModel.snapshots.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    val themeModeState = LocalThemeModeState.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 분석") },
                actions = {
                    IconButton(onClick = onHeatmapClick) {
                        Icon(Icons.Default.GridView, contentDescription = "히트맵")
                    }
                    ThemeToggleIcon(themeModeState)
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
            PillTabRow(
                tabs = AiTab.entries.toList(),
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                tabLabel = { it.label }
            )

            when (selectedTab) {
                AiTab.MARKET -> {
                    MarketTabContent(
                        marketDataPrepared = marketDataPrepared,
                        marketDataSummary = marketDataSummary,
                        marketDataLoading = marketDataLoading,
                        chatMessages = marketChatMessages,
                        chatLoading = marketChatLoading,
                        onPrepareData = { marketViewModel.prepareMarketData() },
                        onSendChat = { marketViewModel.sendMarketChat(it) },
                        onClearChat = { marketViewModel.clearMarketChat() }
                    )
                }

                AiTab.STOCK -> {
                    StockTabContent(
                        query = query,
                        onQueryChange = { newQuery ->
                            query = newQuery
                            stockViewModel.searchStock(newQuery)
                        },
                        searchResults = searchResults,
                        onStockSelect = { stock ->
                            query = stock.name
                            stockViewModel.searchStock("")
                            stockViewModel.selectStock(stock.ticker, stock.name, stock.market, stock.sector.ifBlank { null })
                        },
                        selectedStock = selectedStock,
                        stockDataState = stockDataState,
                        chatMessages = stockChatMessages,
                        chatLoading = stockChatLoading,
                        onSendChat = { stockViewModel.sendStockChat(it) },
                        onClearChat = { stockViewModel.clearStockChat() }
                    )
                }

                AiTab.PROBABILITY -> {
                    ProbabilityTabContent(
                        selectedStock = selectedStock,
                        probabilityState = probabilityState,
                        interpretationState = interpretationState,
                        metaLearnerStatus = metaLearnerStatus,
                        ensembleProbability = ensembleProbability,
                        algoAccuracy = algoAccuracy,
                        algoResults = algoResults,
                        snapshots = snapshots,
                        onAnalyze = {
                            selectedStock?.let { probabilityViewModel.analyzeProbability(it) }
                        },
                        onDismiss = { probabilityViewModel.dismissProbability() },
                        onSelectStock = { selectedTab = AiTab.STOCK },
                        onInterpretLocal = { probabilityViewModel.interpretLocal() },
                        onInterpretAi = { probabilityViewModel.interpretWithAi() },
                        onDismissInterpretation = { probabilityViewModel.dismissInterpretation() }
                    )
                }
            }
        }
    }
}
