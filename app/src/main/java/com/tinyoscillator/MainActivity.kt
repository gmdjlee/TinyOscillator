package com.tinyoscillator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tinyoscillator.core.database.entity.AnalysisHistoryEntity
import com.tinyoscillator.presentation.chart.OscillatorChart
import com.tinyoscillator.presentation.financial.FinancialInfoContent
import com.tinyoscillator.presentation.settings.SettingsScreen
import com.tinyoscillator.presentation.viewmodel.OscillatorUiState
import com.tinyoscillator.presentation.viewmodel.OscillatorViewModel
import com.tinyoscillator.presentation.viewmodel.StockMasterStatus
import com.tinyoscillator.ui.theme.TinyOscillatorTheme
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TinyOscillatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: OscillatorViewModel = hiltViewModel()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            OscillatorScreen(
                viewModel = viewModel,
                onSettingsClick = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = {
                    viewModel.invalidateApiConfig()
                    navController.popBackStack()
                }
            )
        }
    }
}

private enum class MainTab(val label: String) {
    OSCILLATOR("오실레이터"),
    FINANCIAL("재무정보")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OscillatorScreen(
    viewModel: OscillatorViewModel,
    onSettingsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val analysisHistory by viewModel.analysisHistory.collectAsStateWithLifecycle()
    val stockMasterStatus by viewModel.stockMasterStatus.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    var selectedMainTab by remember { mutableStateOf(MainTab.OSCILLATOR) }

    // Track current analyzed stock for financial tab
    val currentTicker = remember(uiState) {
        (uiState as? OscillatorUiState.Success)?.chartData?.ticker
    }
    val currentStockName = remember(uiState) {
        (uiState as? OscillatorUiState.Success)?.chartData?.stockName
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("수급 오실레이터") },
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
            // 종목 마스터 상태 표시
            when (val status = stockMasterStatus) {
                is StockMasterStatus.Loading -> {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            "종목 DB 로딩 중...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is StockMasterStatus.Ready -> {
                    if (status.count > 0) {
                        Text(
                            "종목 DB: ${status.count}개 로드됨",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
                is StockMasterStatus.Error -> {
                    Text(
                        "종목 DB 오류: ${status.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                is StockMasterStatus.Unknown -> {}
            }

            // 검색 입력
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.searchStock(it)
                    showHistory = false
                },
                label = { Text("종목명 또는 종목코드") },
                placeholder = { Text("예: 삼성전자, 005930") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (searchResults.isNotEmpty()) {
                        val first = searchResults.first()
                        viewModel.analyze(first.ticker, first.name)
                        query = first.name
                        viewModel.searchStock("") // 검색 결과 초기화
                    }
                }),
                trailingIcon = {
                    if (analysisHistory.isNotEmpty()) {
                        IconButton(onClick = { showHistory = !showHistory }) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = "분석 기록",
                                tint = if (showHistory) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Main Tab Row (오실레이터 / 재무정보)
            TabRow(
                selectedTabIndex = selectedMainTab.ordinal,
                modifier = Modifier.fillMaxWidth()
            ) {
                MainTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedMainTab == tab,
                        onClick = { selectedMainTab = tab },
                        text = { Text(tab.label) }
                    )
                }
            }

            // Tab content
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedMainTab) {
                    MainTab.OSCILLATOR -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 상태별 UI
                            when (val state = uiState) {
                                is OscillatorUiState.Loading -> {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator()
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(state.message)
                                            }
                                        }
                                    }
                                }

                                is OscillatorUiState.Error -> {
                                    item {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = state.message,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(16.dp)
                                            )
                                        }
                                    }
                                }

                                is OscillatorUiState.Success -> {
                                    item {
                                        OscillatorChart(chartData = state.chartData)
                                    }
                                }

                                is OscillatorUiState.Idle -> { /* 초기 상태 */ }
                            }
                        }
                    }

                    MainTab.FINANCIAL -> {
                        FinancialInfoContent(
                            ticker = currentTicker,
                            stockName = currentStockName,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Autocomplete dropdown overlay (visible on all tabs)
                if (searchResults.isNotEmpty() && query.isNotBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
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
                                        .clickable {
                                            query = stock.name
                                            viewModel.searchStock("") // 검색 결과 초기화
                                            viewModel.analyze(stock.ticker, stock.name)
                                        }
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

                // History dropdown overlay (visible on all tabs)
                if (showHistory && analysisHistory.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .heightIn(max = 350.dp)
                            .zIndex(1f),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "최근 분석 기록",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider()
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            ) {
                                analysisHistory.forEach { history ->
                                    HistoryItem(
                                        history = history,
                                        onClick = {
                                            query = history.name
                                            viewModel.searchStock("")
                                            viewModel.analyze(history.ticker, history.name)
                                            showHistory = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    history: AnalysisHistoryEntity,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    val formattedDate = remember(history.lastAnalyzedAt) {
        dateFormat.format(Date(history.lastAnalyzedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    history.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    history.ticker,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                formattedDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
