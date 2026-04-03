package com.tinyoscillator.presentation.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import com.tinyoscillator.presentation.common.ThemeToggleIcon
import com.tinyoscillator.ui.theme.LocalThemeModeState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.domain.model.*
import com.tinyoscillator.presentation.common.AiAnalysisSection
import com.tinyoscillator.presentation.common.GlassCard
import com.tinyoscillator.ui.theme.LocalFinanceColors
import com.tinyoscillator.presentation.common.PillTabRow

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
    val probabilityState by viewModel.probabilityState.collectAsStateWithLifecycle()
    val interpretationState by viewModel.interpretationState.collectAsStateWithLifecycle()
    val metaLearnerStatus by viewModel.metaLearnerStatus.collectAsStateWithLifecycle()
    val ensembleProbability by viewModel.ensembleProbability.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    val themeModeState = LocalThemeModeState.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 분석") },
                actions = {
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
            // Tab Row
            PillTabRow(
                tabs = AiTab.entries.toList(),
                selectedTab = selectedTab,
                onTabSelected = { viewModel.selectTab(it) },
                tabLabel = { it.label }
            )

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

                AiTab.PROBABILITY -> {
                    ProbabilityTabContent(
                        selectedStock = selectedStock,
                        probabilityState = probabilityState,
                        interpretationState = interpretationState,
                        metaLearnerStatus = metaLearnerStatus,
                        ensembleProbability = ensembleProbability,
                        onAnalyze = { viewModel.analyzeProbability() },
                        onDismiss = { viewModel.dismissProbability() },
                        onSelectStock = { viewModel.selectTab(AiTab.STOCK) },
                        onInterpretLocal = { viewModel.interpretLocal() },
                        onInterpretAi = { viewModel.interpretWithAi() },
                        onDismissInterpretation = { viewModel.dismissInterpretation() }
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
    val financeColors = LocalFinanceColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Glass Card — 시장 종합 분석 요약
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "시장 종합 분석",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "KOSPI/KOSDAQ 과매수·과매도 지표와 투자자 예탁금 동향을 종합하여 시장 상태를 분석합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            // 시장 심리 인디케이터
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "시장 심리 지표",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { 0.72f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = financeColors.neutral,
                trackColor = MaterialTheme.colorScheme.surfaceContainerLow,
            )
        }

        // 2x2 시장 지표 카드 그리드
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MarketIndicatorMiniCard(
                label = "KOSPI",
                value = "—",
                change = null,
                modifier = Modifier.weight(1f)
            )
            MarketIndicatorMiniCard(
                label = "KOSDAQ",
                value = "—",
                change = null,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MarketIndicatorMiniCard(
                label = "USD/KRW",
                value = "—",
                change = null,
                modifier = Modifier.weight(1f)
            )
            MarketIndicatorMiniCard(
                label = "예탁금",
                value = "—",
                change = null,
                modifier = Modifier.weight(1f)
            )
        }

        AiAnalysisSection(
            state = marketAiState,
            onAnalyze = onAnalyze,
            onDismiss = onDismiss
        )
    }
}

/** 시장 지표 미니 카드 (2x2 그리드용) */
@Composable
private fun MarketIndicatorMiniCard(
    label: String,
    value: String,
    change: String?,
    modifier: Modifier = Modifier
) {
    val financeColors = LocalFinanceColors.current
    Card(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            change?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (it.startsWith("+") || it.startsWith("▲")) financeColors.positive
                    else if (it.startsWith("-") || it.startsWith("▼")) financeColors.negative
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (available) FontWeight.Bold else FontWeight.Normal,
                color = if (available) Color(0xFF1B5E20) else Color(0xFF9E9E9E)
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = if (available) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)
        ),
        border = if (available) BorderStroke(1.dp, Color(0xFF66BB6A)) else null
    )
}

// ─── 확률 분석 탭 ───

@Composable
private fun ProbabilityTabContent(
    selectedStock: SelectedStockInfo?,
    probabilityState: ProbabilityAnalysisState,
    interpretationState: InterpretationState,
    metaLearnerStatus: com.tinyoscillator.domain.model.MetaLearnerStatus,
    ensembleProbability: Double?,
    onAnalyze: () -> Unit,
    onDismiss: () -> Unit,
    onSelectStock: () -> Unit,
    onInterpretLocal: () -> Unit,
    onInterpretAi: () -> Unit,
    onDismissInterpretation: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 설명 카드
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("확률적 기대값 분석", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("7개 통계 알고리즘을 병렬 실행하여 상승/하락 확률을 산출합니다. API 키 없이 로컬에서 실행됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (selectedStock == null) {
            // 종목 미선택
            Spacer(Modifier.height(24.dp))
            Text("종목을 먼저 선택하세요",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            OutlinedButton(
                onClick = onSelectStock,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text("종목 탭으로 이동") }
        } else {
            // 종목 정보
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(selectedStock.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text("${selectedStock.ticker} | ${selectedStock.market ?: "-"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 분석 버튼
            Button(
                onClick = onAnalyze,
                modifier = Modifier.fillMaxWidth(),
                enabled = probabilityState is ProbabilityAnalysisState.Idle ||
                        probabilityState is ProbabilityAnalysisState.Success ||
                        probabilityState is ProbabilityAnalysisState.Error
            ) {
                Icon(Icons.Default.Analytics, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("확률 분석 실행")
            }

            // 상태 표시
            when (val state = probabilityState) {
                is ProbabilityAnalysisState.Idle -> {}

                is ProbabilityAnalysisState.Computing -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(state.message)
                        }
                    }
                }

                is ProbabilityAnalysisState.Error -> {
                    Card(modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(state.message, modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }

                is ProbabilityAnalysisState.Success -> {
                    // 해석 제공자 선택 버튼
                    InterpretationProviderSelector(
                        interpretationState = interpretationState,
                        onInterpretLocal = onInterpretLocal,
                        onInterpretAi = onInterpretAi
                    )

                    // 해석 결과 표시
                    InterpretationResultCard(
                        interpretationState = interpretationState,
                        onDismiss = onDismissInterpretation,
                        onRetryLocal = onInterpretLocal,
                        onRetryAi = onInterpretAi
                    )

                    // 앙상블 확률 + 메타 학습기 상태
                    EnsembleProbabilityCard(
                        ensembleProbability = ensembleProbability,
                        metaLearnerStatus = metaLearnerStatus
                    )

                    // 포지션 가이드 카드
                    state.result.positionRecommendation?.let { posRec ->
                        PositionGuideCard(positionRecommendation = posRec)
                    }

                    // 확률 분석 결과
                    ProbabilityResultContent(
                        result = state.result,
                        interpretationState = interpretationState
                    )
                }
            }
        }
    }
}

/** 해석 제공자 선택 (로컬/AI) */
@Composable
private fun InterpretationProviderSelector(
    interpretationState: InterpretationState,
    onInterpretLocal: () -> Unit,
    onInterpretAi: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("결과 해석", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onInterpretLocal,
                    modifier = Modifier.weight(1f),
                    enabled = interpretationState !is InterpretationState.Loading
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("로컬 분석", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onInterpretAi,
                    modifier = Modifier.weight(1f),
                    enabled = interpretationState !is InterpretationState.Loading
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("AI 분석", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** 해석 결과 카드 */
@Composable
private fun InterpretationResultCard(
    interpretationState: InterpretationState,
    onDismiss: () -> Unit,
    onRetryLocal: () -> Unit,
    onRetryAi: () -> Unit
) {
    when (interpretationState) {
        is InterpretationState.Idle -> {}

        is InterpretationState.Loading -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("AI 해석 중...")
                }
            }
        }

        is InterpretationState.Success -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                if (interpretationState.provider == InterpretationProvider.AI)
                                    Icons.Default.AutoAwesome else Icons.Default.Lightbulb,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "${interpretationState.provider.label} 해석",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        TextButton(onClick = onDismiss) {
                            Text("닫기", color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        interpretationState.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        is InterpretationState.Error -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(interpretationState.message,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRetryAi) { Text("AI 재시도") }
                        OutlinedButton(onClick = onRetryLocal) { Text("로컬로 전환") }
                    }
                }
            }
        }

        is InterpretationState.NoApiKey -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("AI 분석을 사용하려면 설정에서 AI API 키를 입력해주세요.",
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRetryLocal) { Text("로컬 분석 사용") }
                }
            }
        }
    }
}

/** 앙상블 확률 + 메타 학습기 상태 카드 */
@Composable
private fun EnsembleProbabilityCard(
    ensembleProbability: Double?,
    metaLearnerStatus: com.tinyoscillator.domain.model.MetaLearnerStatus
) {
    val financeColors = LocalFinanceColors.current

    ensembleProbability?.let { prob ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Text("앙상블 상승 확률", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)

                    // 메타 학습기 상태 배지
                    val source = if (metaLearnerStatus.isFitted) "Meta-Learner" else "가중합"
                    SuggestionChip(
                        onClick = {},
                        label = { Text(source, style = MaterialTheme.typography.labelSmall) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (metaLearnerStatus.isFitted)
                                Color(0xFF4CAF50).copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = null
                    )
                }

                Spacer(Modifier.height(8.dp))

                // 확률 표시
                val probPct = String.format("%.1f%%", prob * 100)
                val probColor = when {
                    prob >= 0.6 -> financeColors.positive
                    prob <= 0.4 -> financeColors.negative
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Text(probPct,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = probColor)

                // 메타 학습기 상세 (학습된 경우)
                if (metaLearnerStatus.isFitted) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "학습 샘플: ${metaLearnerStatus.nTrainingSamples} | " +
                        "최근 학습: ${metaLearnerStatus.lastFitDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (metaLearnerStatus.topAlgo.isNotEmpty()) {
                        Text(
                            "핵심 알고리즘: ${metaLearnerStatus.topAlgo} " +
                            "(${String.format("%.1f%%", metaLearnerStatus.topAlgoWeight * 100)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "학습 데이터 축적 중 (최소 60건 필요)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ProbabilityResultContent(
    result: StatisticalResult,
    interpretationState: InterpretationState
) {
    val financeColors = LocalFinanceColors.current
    val engineInterpretations = (interpretationState as? InterpretationState.Success)
        ?.engineInterpretations ?: emptyMap()

    // 종합 요약 카드
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("분석 완료", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                // 캐시 상태 표시
                val isCached = result.executionMetadata.totalTimeMs < 50
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            if (isCached) "Cached" else "Live",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (isCached)
                            MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                    border = null
                )
            }
            Spacer(Modifier.height(8.dp))

            // 시장 레짐 배지
            result.marketRegimeResult?.let { regime ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val regimeColor = when (regime.regimeName) {
                        "BULL_LOW_VOL" -> Color(0xFF4CAF50)
                        "BEAR_HIGH_VOL" -> Color(0xFFFF9800)
                        "SIDEWAYS" -> Color(0xFF9E9E9E)
                        "CRISIS" -> Color(0xFFF44336)
                        else -> Color(0xFF9E9E9E)
                    }
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                "${regime.regimeDescription} (${String.format("%.0f", regime.confidence * 100)}%)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = regimeColor.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, regimeColor.copy(alpha = 0.7f))
                    )
                    Text("${regime.regimeDurationDays}일 지속",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
            }

            // 매크로 환경 배지
            result.macroSignalResult?.let { macro ->
                if (macro.unavailableReason == null) {
                    val macroColor = when (macro.macroEnv) {
                        "EASING" -> Color(0xFF4CAF50)
                        "TIGHTENING" -> Color(0xFFF44336)
                        "STAGFLATION" -> Color(0xFFFF9800)
                        else -> Color(0xFF9E9E9E)
                    }
                    val macroLabel = when (macro.macroEnv) {
                        "EASING" -> "완화"
                        "TIGHTENING" -> "긴축"
                        "STAGFLATION" -> "스태그플레이션"
                        else -> "중립"
                    }
                    var showMacroSheet by remember { mutableStateOf(false) }
                    SuggestionChip(
                        onClick = { showMacroSheet = !showMacroSheet },
                        label = {
                            Text(
                                "매크로: $macroLabel",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = macroColor.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, macroColor.copy(alpha = 0.7f))
                    )
                    AnimatedVisibility(visible = showMacroSheet) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("매크로 YoY 변화율", fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.height(4.dp))
                                Text("기준금리: ${String.format("%+.2f", macro.baseRateYoy)}pp",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("M2 통화량: ${String.format("%+.1f", macro.m2Yoy)}%",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("산업생산: ${String.format("%+.1f", macro.iipYoy)}%",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("USD/KRW: ${String.format("%+.1f", macro.usdKrwYoy)}%",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("소비자물가: ${String.format("%+.1f", macro.cpiYoy)}%",
                                    style = MaterialTheme.typography.bodySmall)
                                if (macro.referenceMonth.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("기준월: ${macro.referenceMonth}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Bayes 확률 요약 (한국 주식 관례: 상승=빨강, 하락=파랑, 횡보=회색)
            result.bayesResult?.let { bayes ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProbChip("상승", bayes.upProbability, Color(0xFFEF5350))
                    ProbChip("하락", bayes.downProbability, Color(0xFF42A5F5))
                    ProbChip("횡보", bayes.sidewaysProbability, Color(0xFF9E9E9E))
                }
            }

            // Signal Score
            result.signalScoringResult?.let { signal ->
                Spacer(Modifier.height(8.dp))
                Text("신호 점수: ${signal.totalScore}/100 (${signal.dominantDirection})",
                    style = MaterialTheme.typography.bodyMedium)
            }

            // Bayesian Posterior
            result.bayesianUpdateResult?.let { bu ->
                Text("베이지안 확률: ${pctFmt(bu.priorProbability)} → ${pctFmt(bu.finalPosterior)}",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    // 개별 엔진 결과 (Expandable + 해석)
    result.bayesResult?.let { bayes ->
        ProbExpandableCard("나이브 베이즈 (샘플 ${bayes.sampleCount}건)") {
            Text("상승: ${pctFmt(bayes.upProbability)} | 하락: ${pctFmt(bayes.downProbability)} | 횡보: ${pctFmt(bayes.sidewaysProbability)}")
            if (bayes.dominantFeatures.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("주요 피처:", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold)
                bayes.dominantFeatures.take(5).forEach { f ->
                    Text("  · ${f.featureName}: ${String.format("%.2f", f.likelihoodRatio)}x",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            EngineInterpretationBlock(engineInterpretations["bayes"])
        }
    }

    result.logisticResult?.let { lr ->
        ProbExpandableCard("로지스틱 회귀: ${lr.score0to100}/100") {
            Text("상승 확률: ${pctFmt(lr.probability)}")
            Spacer(Modifier.height(4.dp))
            lr.featureValues.forEach { (name, value) ->
                Text("  · $name: ${String.format("%.3f", value)}",
                    style = MaterialTheme.typography.bodySmall)
            }
            EngineInterpretationBlock(engineInterpretations["logistic"])
        }
    }

    result.hmmResult?.let { hmm ->
        ProbExpandableCard("HMM 레짐: ${hmm.regimeDescription}") {
            Text("R0(저변동↑): ${pctFmt(hmm.regimeProbabilities[0])}")
            Text("R1(저변동→): ${pctFmt(hmm.regimeProbabilities[1])}")
            Text("R2(고변동↑): ${pctFmt(hmm.regimeProbabilities[2])}")
            Text("R3(고변동↓): ${pctFmt(hmm.regimeProbabilities[3])}")
            if (hmm.recentRegimePath.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("최근 경로: ${hmm.recentRegimePath.takeLast(10).joinToString("→")}",
                    style = MaterialTheme.typography.bodySmall)
            }
            EngineInterpretationBlock(engineInterpretations["hmm"])
        }
    }

    result.marketRegimeResult?.let { regime ->
        ProbExpandableCard("시장 레짐: ${regime.regimeDescription}") {
            val probLabels = listOf("안정적 상승장", "변동성 하락장", "박스권 횡보", "위기 구간")
            regime.probaVec.forEachIndexed { i, p ->
                if (i < probLabels.size) {
                    Text("${probLabels[i]}: ${pctFmt(p)}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("신뢰도: ${pctFmt(regime.confidence)} | 지속: ${regime.regimeDurationDays}일",
                style = MaterialTheme.typography.bodySmall)
            EngineInterpretationBlock(engineInterpretations["regime"])
        }
    }

    result.patternAnalysis?.let { pa ->
        ProbExpandableCard("패턴 분석 (활성 ${pa.activePatterns.size}/${pa.allPatterns.size})") {
            if (pa.activePatterns.isEmpty()) {
                Text("현재 활성 패턴 없음", style = MaterialTheme.typography.bodySmall)
            } else {
                pa.activePatterns.forEach { p ->
                    Text("${p.patternDescription}", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall)
                    Text("  20일 승률: ${pctFmt(p.winRate20d)} | 평균수익: ${pctFmt(p.avgReturn20d)} | 발생: ${p.totalOccurrences}회",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            EngineInterpretationBlock(engineInterpretations["pattern"])
        }
    }

    result.signalScoringResult?.let { ss ->
        ProbExpandableCard("신호 점수: ${ss.totalScore}/100") {
            ss.contributions.filter { it.signal > 0 }.forEach { c ->
                val dir = if (c.direction > 0) "매수" else if (c.direction < 0) "매도" else "중립"
                Text("  · ${c.name}: $dir (기여 ${String.format("%.1f", c.contributionPercent)}%)",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (ss.conflictingSignals.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                ss.conflictingSignals.forEach { c ->
                    Text("⚠ ${c.description}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            EngineInterpretationBlock(engineInterpretations["signal"])
        }
    }

    result.correlationAnalysis?.let { ca ->
        if (ca.correlations.isNotEmpty()) {
            ProbExpandableCard("상관 분석 (${ca.correlations.size}쌍)") {
                ca.correlations.forEach { c ->
                    Text("${c.indicator1} ↔ ${c.indicator2}: r=${String.format("%.2f", c.pearsonR)} (${c.strength.label})",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (ca.leadLagResults.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("선행-후행:", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium)
                    ca.leadLagResults.forEach { ll ->
                        Text("  · ${ll.interpretation}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                EngineInterpretationBlock(engineInterpretations["correlation"])
            }
        }
    }

    result.bayesianUpdateResult?.let { bu ->
        ProbExpandableCard("베이지안 갱신 (${bu.updateHistory.size}단계)") {
            Text("사전: ${pctFmt(bu.priorProbability)} → 사후: ${pctFmt(bu.finalPosterior)}")
            Spacer(Modifier.height(4.dp))
            bu.updateHistory.forEach { u ->
                val arrow = if (u.deltaProb > 0) "↑" else "↓"
                Text("  · ${u.signalName} $arrow${pctFmt(kotlin.math.abs(u.deltaProb))}",
                    style = MaterialTheme.typography.bodySmall)
            }
            EngineInterpretationBlock(engineInterpretations["bayesian"])
        }
    }

    result.orderFlowResult?.let { of ->
        val dirLabel = when (of.flowDirection) {
            "BUY" -> "매수 우위"
            "SELL" -> "매도 우위"
            else -> "중립"
        }
        ProbExpandableCard("투자자 자금흐름: $dirLabel (${of.flowStrength})") {
            Text("종합 점수: ${pctFmt(of.buyerDominanceScore)}")
            Spacer(Modifier.height(4.dp))
            Text("OFI(5일): ${String.format("%.3f", of.ofi5d)} | OFI(20일): ${String.format("%.3f", of.ofi20d)}",
                style = MaterialTheme.typography.bodySmall)
            Text("외국인 매수 압력: ${String.format("%.3f", of.foreignBuyPressure)}",
                style = MaterialTheme.typography.bodySmall)
            Text("기관-외국인 괴리: ${pctFmt(of.institutionalDivergence)}",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text("추세 정렬: ${pctFmt(of.trendAlignment)} | 평균회귀: ${pctFmt(of.meanReversionSignal)}",
                style = MaterialTheme.typography.bodySmall)
            EngineInterpretationBlock(engineInterpretations["orderflow"])
        }
    }

    result.dartEventResult?.let { de ->
        if (de.nEvents > 0 || de.unavailableReason == null) {
            val title = if (de.nEvents > 0) {
                "DART 공시 이벤트: ${DartEventType.toKorean(de.dominantEventType)} (${de.nEvents}건)"
            } else {
                "DART 공시 이벤트: 최근 공시 없음"
            }
            ProbExpandableCard(title) {
                if (de.nEvents > 0) {
                    Text("신호 점수: ${pctFmt(de.signalScore)}")
                    Text("최근 CAR: ${String.format("%+.2f%%", de.latestCar * 100)}",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    de.eventStudies.forEach { es ->
                        Text("${es.eventDate} ${DartEventType.toKorean(es.eventType)}: " +
                                "CAR=${String.format("%+.2f%%", es.carFinal * 100)} " +
                                "(t=${String.format("%.2f", es.tStat)}${if (es.significant) " ★" else ""})",
                            style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text("최근 30일 내 주요 공시가 없습니다.",
                        style = MaterialTheme.typography.bodySmall)
                }
                EngineInterpretationBlock(engineInterpretations["dartevent"])
            }
        }
    }

    result.korea5FactorResult?.let { k5 ->
        if (k5.unavailableReason == null) {
            val alphaDir = when {
                k5.alphaZscore > 1.0 -> "강한 양(+)"
                k5.alphaZscore > 0.3 -> "양(+)"
                k5.alphaZscore < -1.0 -> "강한 음(-)"
                k5.alphaZscore < -0.3 -> "음(-)"
                else -> "중립"
            }
            ProbExpandableCard("5팩터 알파: $alphaDir (z=${String.format("%+.2f", k5.alphaZscore)})") {
                Text("신호 점수: ${pctFmt(k5.signalScore)}")
                Text("alpha: ${String.format("%+.4f", k5.alphaRaw)} (${k5.nObs}개월 윈도우)",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Text("팩터 베타:", style = MaterialTheme.typography.bodySmall)
                Text("  MKT=${String.format("%.2f", k5.betas.mkt)}, SMB=${String.format("%+.3f", k5.betas.smb)}, HML=${String.format("%+.3f", k5.betas.hml)}",
                    style = MaterialTheme.typography.bodySmall)
                Text("  RMW=${String.format("%+.3f", k5.betas.rmw)}, CMA=${String.format("%+.3f", k5.betas.cma)}",
                    style = MaterialTheme.typography.bodySmall)
                Text("R²: ${String.format("%.1f%%", k5.rSquared * 100)}",
                    style = MaterialTheme.typography.bodySmall)
                EngineInterpretationBlock(engineInterpretations["korea5factor"])
            }
        }
    }

    result.macroSignalResult?.let { macro ->
        if (macro.unavailableReason == null) {
            val envLabel = when (macro.macroEnv) {
                "EASING" -> "완화"
                "TIGHTENING" -> "긴축"
                "STAGFLATION" -> "스태그플레이션"
                else -> "중립"
            }
            ProbExpandableCard("매크로 환경: $envLabel") {
                Text("기준금리 YoY: ${String.format("%+.2f", macro.baseRateYoy)}pp",
                    style = MaterialTheme.typography.bodySmall)
                Text("M2 통화량 YoY: ${String.format("%+.1f", macro.m2Yoy)}%",
                    style = MaterialTheme.typography.bodySmall)
                Text("산업생산 YoY: ${String.format("%+.1f", macro.iipYoy)}%",
                    style = MaterialTheme.typography.bodySmall)
                Text("USD/KRW YoY: ${String.format("%+.1f", macro.usdKrwYoy)}%",
                    style = MaterialTheme.typography.bodySmall)
                Text("소비자물가 YoY: ${String.format("%+.1f", macro.cpiYoy)}%",
                    style = MaterialTheme.typography.bodySmall)
                if (macro.referenceMonth.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("기준월: ${macro.referenceMonth}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                EngineInterpretationBlock(engineInterpretations["macro"])
            }
        }
    }

    // 메타데이터
    Text("실행: ${result.executionMetadata.totalTimeMs}ms" +
            if (result.executionMetadata.failedEngines.isNotEmpty())
                " | 실패: ${result.executionMetadata.failedEngines.joinToString()}" else "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 개별 엔진 해석 블록 — 로컬 분석 시 각 카드 하단에 표시 */
@Composable
private fun EngineInterpretationBlock(interpretation: String?) {
    if (interpretation.isNullOrBlank()) return

    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(6.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            Icons.Default.Lightbulb,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.tertiary
        )
        Text("해석", style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary)
    }
    Spacer(Modifier.height(4.dp))
    Text(
        interpretation,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ProbChip(label: String, probability: Double, color: Color) {
    SuggestionChip(
        onClick = {},
        label = {
            Text(
                "$label ${pctFmt(probability)}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = color.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.7f))
    )
}

@Composable
private fun ProbExpandableCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) { content() }
            }
        }
    }
}

/** 포지션 가이드 카드 — Kelly + CVaR 기반 추천 비중 */
@Composable
private fun PositionGuideCard(
    positionRecommendation: com.tinyoscillator.domain.model.PositionRecommendation
) {
    val financeColors = LocalFinanceColors.current
    val pr = positionRecommendation

    // 사용 불가 시 간략 표시
    if (pr.unavailableReason != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Position Guide", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(pr.unavailableReason!!, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val maxPos = 0.20 // 최대 비중 표시 기준
    val recPct = pr.recommendedPct
    val barFraction = (recPct / maxPos).toFloat().coerceIn(0f, 1f)

    val barColor = when {
        pr.sizeReasonCode == com.tinyoscillator.domain.model.SizeReasonCode.NO_EDGE ->
            MaterialTheme.colorScheme.onSurfaceVariant
        recPct >= 0.15 -> financeColors.positive
        recPct >= 0.08 -> Color(0xFFFF9800)
        recPct > 0.0 -> Color(0xFF2196F3)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val reasonLabel = when (pr.sizeReasonCode) {
        com.tinyoscillator.domain.model.SizeReasonCode.KELLY_BOUND -> "켈리 제한"
        com.tinyoscillator.domain.model.SizeReasonCode.CVAR_BOUND -> "CVaR 제한"
        com.tinyoscillator.domain.model.SizeReasonCode.MAX_POSITION -> "최대 비중"
        com.tinyoscillator.domain.model.SizeReasonCode.NO_EDGE -> "신호 우위 없음"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Analytics, contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary)
                Text("Position Guide", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                SuggestionChip(
                    onClick = {},
                    label = { Text(reasonLabel, style = MaterialTheme.typography.labelSmall) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = barColor.copy(alpha = 0.2f)
                    ),
                    border = null
                )
            }

            Spacer(Modifier.height(12.dp))

            // 추천 비중 텍스트
            Text(
                String.format("%.1f%%", recPct * 100),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = barColor
            )

            Spacer(Modifier.height(8.dp))

            // 수평 바: 0% → recommended% → max_position%
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
            ) {
                // 배경 바 (0% ~ max%)
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 배경
                    drawRoundRect(
                        color = Color.Gray.copy(alpha = 0.2f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f)
                    )
                    // 채워진 영역
                    if (barFraction > 0f) {
                        drawRoundRect(
                            color = barColor.copy(alpha = 0.7f),
                            size = size.copy(width = size.width * barFraction),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f)
                        )
                    }
                }
            }

            // 바 범례
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0%", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(String.format("%.1f%%", maxPos * 100),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))

            // 세부 정보
            var showDetails by remember { mutableStateOf(false) }
            TextButton(
                onClick = { showDetails = !showDetails },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(if (showDetails) "상세 접기" else "상세 보기",
                    style = MaterialTheme.typography.labelSmall)
            }

            AnimatedVisibility(visible = showDetails) {
                Column {
                    Text("신호 우위: ${String.format("%+.1f%%p", pr.signalEdge * 100)}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Kelly(원시): ${String.format("%.1f%%", pr.kellyRaw * 100)} | " +
                            "Kelly(분율): ${String.format("%.1f%%", pr.kellyFractional * 100)}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("실현 변동성(20일): ${String.format("%.1f%%", pr.realizedVol * 100)} | " +
                            "W/L Ratio: ${String.format("%.2f", pr.winLossRatio)}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("1일 CVaR(95%): ${String.format("%.2f%%", pr.cvar1d * 100)} | " +
                            "CVaR 한도: ${String.format("%.1f%%", pr.cvarLimit * 100)}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(8.dp))

            // 면책 조항 (규제 요건)
            Text(
                "분석 참고용 자료이며 투자 조언이 아닙니다. 투자 판단의 책임은 본인에게 있습니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private fun pctFmt(value: Double): String = "${String.format("%.1f", value * 100)}%"
