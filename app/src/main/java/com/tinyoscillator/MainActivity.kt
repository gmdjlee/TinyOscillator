package com.tinyoscillator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
import com.tinyoscillator.presentation.chart.composable.KoreanCandleChartView
import com.tinyoscillator.presentation.chart.composable.PatternSummaryCard
import com.tinyoscillator.presentation.chart.ext.toOhlcvPoints
import com.tinyoscillator.presentation.chart.ext.toDateLabels
import com.tinyoscillator.domain.usecase.CandlePatternDetector
import com.tinyoscillator.presentation.common.WindowType
import com.tinyoscillator.presentation.common.calculateWindowType
import com.tinyoscillator.presentation.common.ScrollablePillTabRow
import com.tinyoscillator.presentation.common.TwoPaneLayout
import com.tinyoscillator.presentation.demark.DemarkTDContent
import com.tinyoscillator.presentation.etf.AggregatedStockTrendScreen
import com.tinyoscillator.presentation.etf.EtfScreen
import com.tinyoscillator.presentation.etf.StockTrendScreen
import com.tinyoscillator.domain.model.ConsensusReport
import com.tinyoscillator.presentation.consensus.ConsensusContent
import com.tinyoscillator.presentation.financial.DuPontContent
import com.tinyoscillator.presentation.financial.FinancialInfoContent
import com.tinyoscillator.presentation.financial.NaverStockWebScreen
import com.tinyoscillator.presentation.fundamental.FundamentalHistoryContent
import com.tinyoscillator.presentation.ai.AiAnalysisScreen
import com.tinyoscillator.presentation.market.MarketIndicatorScreen
import com.tinyoscillator.presentation.marketanalysis.MarketAnalysisScreen
import com.tinyoscillator.presentation.portfolio.PortfolioScreen
import com.tinyoscillator.presentation.common.HeatmapScreen
import com.tinyoscillator.presentation.report.ReportDetailScreen
import com.tinyoscillator.presentation.report.ReportScreen
import com.tinyoscillator.presentation.screener.ScreenerScreen
import com.tinyoscillator.presentation.settings.SettingsScreen
import com.tinyoscillator.presentation.viewmodel.OscillatorDateRange
import com.tinyoscillator.presentation.viewmodel.OscillatorUiState
import com.tinyoscillator.presentation.viewmodel.OscillatorViewModel
import com.tinyoscillator.presentation.viewmodel.StockMasterStatus
import com.tinyoscillator.presentation.common.ThemeToggleIcon
import com.tinyoscillator.ui.theme.LocalThemeModeState
import com.tinyoscillator.ui.theme.TinyOscillatorTheme
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result — no action needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
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

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

private enum class BottomNavItem(val label: String, val icon: ImageVector) {
    MARKET_ANALYSIS("시장분석", Icons.AutoMirrored.Filled.TrendingUp),
    STOCK_ANALYSIS("종목분석", Icons.AutoMirrored.Filled.ShowChart),
    ETF_ANALYSIS("ETF분석", Icons.Default.PieChart),
    REPORT("리포트", Icons.Default.Description),
    MARKET_INDICATOR("시장지표", Icons.Default.BarChart),
    AI_ANALYSIS("AI분석", Icons.Default.Psychology),
    SCREENER("스크리너", Icons.Default.Tune),
    PORTFOLIO("포트폴리오", Icons.Default.AccountBalance)
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: OscillatorViewModel = hiltViewModel()
    val configuration = LocalConfiguration.current
    val windowType = calculateWindowType(configuration.screenWidthDp.dp)

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScaffold(
                viewModel = viewModel,
                windowType = windowType,
                onSettingsClick = { navController.navigate("settings") },
                onEtfDetailClick = { ticker -> navController.navigate("etf_detail/$ticker") },
                onStockClick = { stockTicker -> navController.navigate("stock_aggregated/$stockTicker") },
                onStockTrendClick = { etfTicker, stockTicker -> navController.navigate("stock_trend/$etfTicker/$stockTicker") },
                onReportDetailClick = { report ->
                    navController.navigate("report_detail/${report.stockTicker}/${report.writeDate}")
                },
                onHeatmapClick = { navController.navigate("heatmap") }
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
        composable("etf_detail/{ticker}") { backStackEntry ->
            val ticker = backStackEntry.arguments?.getString("ticker") ?: return@composable
            com.tinyoscillator.presentation.etf.EtfDetailScreen(
                ticker = ticker,
                onBack = { navController.popBackStack() },
                onStockTrendClick = { etfTicker, stockTicker ->
                    navController.navigate("stock_trend/$etfTicker/$stockTicker")
                }
            )
        }
        composable("stock_trend/{etfTicker}/{stockTicker}") {
            StockTrendScreen(onBack = { navController.popBackStack() })
        }
        composable("stock_aggregated/{stockTicker}") {
            AggregatedStockTrendScreen(onBack = { navController.popBackStack() })
        }
        composable("report_detail/{ticker}/{writeDate}") {
            ReportDetailScreen(onBack = { navController.popBackStack() })
        }
        composable("heatmap") {
            HeatmapScreen(
                onTickerClick = { ticker ->
                    navController.navigate("stock_aggregated/$ticker")
                }
            )
        }
    }
}

@Composable
private fun MainScaffold(
    viewModel: OscillatorViewModel,
    windowType: WindowType = WindowType.COMPACT,
    onSettingsClick: () -> Unit,
    onEtfDetailClick: (String) -> Unit,
    onStockClick: (String) -> Unit = {},
    onStockTrendClick: (String, String) -> Unit = { _, _ -> },
    onReportDetailClick: (ConsensusReport) -> Unit = {},
    onHeatmapClick: () -> Unit = {}
) {
    var selectedNav by rememberSaveable { mutableStateOf(BottomNavItem.MARKET_ANALYSIS) }

    val screenContent: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier = modifier) {
            when (selectedNav) {
                BottomNavItem.MARKET_ANALYSIS -> {
                    MarketAnalysisScreen(
                        onSettingsClick = onSettingsClick
                    )
                }
                BottomNavItem.STOCK_ANALYSIS -> {
                    OscillatorScreen(
                        viewModel = viewModel,
                        onSettingsClick = onSettingsClick,
                        windowType = windowType
                    )
                }
                BottomNavItem.ETF_ANALYSIS -> {
                    EtfScreen(
                        onSettingsClick = onSettingsClick,
                        onEtfDetailClick = onEtfDetailClick,
                        onStockClick = onStockClick,
                        onStockTrendClick = onStockTrendClick,
                        windowType = windowType
                    )
                }
                BottomNavItem.REPORT -> {
                    ReportScreen(
                        onSettingsClick = onSettingsClick,
                        onReportClick = onReportDetailClick
                    )
                }
                BottomNavItem.MARKET_INDICATOR -> {
                    MarketIndicatorScreen(
                        onSettingsClick = onSettingsClick
                    )
                }
                BottomNavItem.AI_ANALYSIS -> {
                    AiAnalysisScreen(
                        onSettingsClick = onSettingsClick,
                        onHeatmapClick = onHeatmapClick
                    )
                }
                BottomNavItem.SCREENER -> {
                    ScreenerScreen(
                        onSettingsClick = onSettingsClick,
                        onTickerClick = onStockClick,
                    )
                }
                BottomNavItem.PORTFOLIO -> {
                    PortfolioScreen(
                        onSettingsClick = onSettingsClick
                    )
                }
            }
        }
    }

    if (windowType == WindowType.COMPACT) {
        // 일반 스마트폰: 기존 Bottom Navigation
        Scaffold(
            bottomBar = {
                NavigationBar {
                    BottomNavItem.entries.forEach { item ->
                        NavigationBarItem(
                            selected = selectedNav == item,
                            onClick = { selectedNav = item },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        ) { padding ->
            screenContent(Modifier.padding(padding))
        }
    } else {
        // 폴더블/태블릿: Navigation Rail + 넓은 콘텐츠
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail {
                Spacer(modifier = Modifier.weight(1f))
                BottomNavItem.entries.forEach { item ->
                    NavigationRailItem(
                        selected = selectedNav == item,
                        onClick = { selectedNav = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }
            screenContent(Modifier.fillMaxSize())
        }
    }
}

private enum class MainTab(val label: String) {
    OSCILLATOR("오실레이터"),
    DEMARK("DeMark"),
    FINANCIAL("재무정보"),
    CONSENSUS("컨센서스"),
    INDICATOR("지표"),
    DUPONT("DuPont"),
    NAVER_STOCK("네이버증권")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OscillatorScreen(
    viewModel: OscillatorViewModel,
    onSettingsClick: () -> Unit,
    windowType: WindowType = WindowType.COMPACT
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val analysisHistory by viewModel.analysisHistory.collectAsStateWithLifecycle()
    val stockMasterStatus by viewModel.stockMasterStatus.collectAsStateWithLifecycle()
    val isIntradayMerged by viewModel.isIntradayMerged.collectAsStateWithLifecycle()
    val autoRefreshEnabled by viewModel.autoRefreshEnabled.collectAsStateWithLifecycle()
    val selectedRange by viewModel.selectedRange.collectAsStateWithLifecycle()
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

    val themeModeState = LocalThemeModeState.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("종목분석") },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshStockMaster() },
                        enabled = stockMasterStatus !is StockMasterStatus.Loading
                    ) {
                        if (stockMasterStatus is StockMasterStatus.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "종목리스트 새로고침")
                        }
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (status.count > 0) "종목 DB: ${status.count}개 로드됨"
                            else "종목 DB: 데이터 없음",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = { viewModel.refreshStockMaster() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "종목 DB 새로고침",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                is StockMasterStatus.Unknown -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "종목 DB 준비 중...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = { viewModel.refreshStockMaster() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "종목 DB 새로고침",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 검색 + 탭 + 탭 콘텐츠를 Box로 감싸서 드롭다운 오버레이 지원
            val density = LocalDensity.current
            var textFieldHeightPx by remember { mutableIntStateOf(0) }

            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 검색 입력 (Stitch: 둥근 검색바)
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            viewModel.searchStock(it)
                            showHistory = false
                        },
                        placeholder = { Text("종목명 또는 종목코드 검색") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (searchResults.isNotEmpty()) {
                                val first = searchResults.first()
                                viewModel.analyze(first.ticker, first.name, selectedRange.analysisDays, selectedRange.displayDays)
                                query = first.name
                                viewModel.searchStock("") // 검색 결과 초기화
                            }
                        }),
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .onGloballyPositioned { textFieldHeightPx = it.size.height }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Main Tab Row (Stitch Pill Style)
                    ScrollablePillTabRow(
                        tabs = MainTab.entries.toList(),
                        selectedTab = selectedMainTab,
                        onTabSelected = { selectedMainTab = it },
                        tabLabel = { it.label }
                    )

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
                                    // 기간 선택
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OscillatorDateRange.entries.forEach { range ->
                                                FilterChip(
                                                    selected = selectedRange == range,
                                                    onClick = { viewModel.selectDateRange(range) },
                                                    label = { Text(range.label) }
                                                )
                                            }
                                        }
                                    }
                                    // 실시간 데이터 상태 표시
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            if (isIntradayMerged) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Badge(
                                                        containerColor = MaterialTheme.colorScheme.primary
                                                    ) {
                                                        Text(
                                                            "실시간",
                                                            modifier = Modifier.padding(horizontal = 4.dp),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    Text(
                                                        "장중 수급 데이터 반영 중",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    "종가 기준 데이터",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            // 자동 갱신 토글
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    "자동갱신",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                IconButton(
                                                    onClick = { viewModel.toggleAutoRefresh() },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        if (autoRefreshEnabled) Icons.Default.Pause
                                                        else Icons.Default.PlayArrow,
                                                        contentDescription = if (autoRefreshEnabled) "자동갱신 중지" else "자동갱신 시작",
                                                        modifier = Modifier.size(18.dp),
                                                        tint = if (autoRefreshEnabled) MaterialTheme.colorScheme.primary
                                                               else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    // 1. 수급 오실레이터 차트
                                    item {
                                        OscillatorChart(chartData = state.chartData)
                                    }
                                    // 2. 캔들 차트 + 3. 패턴 요약 카드
                                    if (state.dailyData.isNotEmpty()) {
                                        item {
                                            val candlePoints = remember(state.dailyData) {
                                                state.dailyData.toOhlcvPoints()
                                            }
                                            val dateLabels = remember(state.dailyData) {
                                                state.dailyData.toDateLabels()
                                            }
                                            val patterns = remember(candlePoints) {
                                                CandlePatternDetector.detect(candlePoints)
                                            }
                                            val patternMarkers = remember(patterns) {
                                                patterns.groupBy { it.index }
                                                    .mapValues { (_, pats) -> pats.map { it.type.labelKo } }
                                            }
                                            Column {
                                                KoreanCandleChartView(
                                                    candles = candlePoints,
                                                    dateLabels = dateLabels,
                                                    detectedPatterns = patterns,
                                                    patternMarkers = patternMarkers,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(360.dp),
                                                )
                                                PatternSummaryCard(
                                                    patterns = patterns,
                                                    dateLabels = dateLabels,
                                                )
                                            }
                                        }
                                    }
                                }

                                is OscillatorUiState.Idle -> { /* 초기 상태 */ }
                            }
                        }
                    }

                    MainTab.DEMARK -> {
                        DemarkTDContent(
                            ticker = currentTicker,
                            stockName = currentStockName,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    MainTab.FINANCIAL -> {
                        FinancialInfoContent(
                            ticker = currentTicker,
                            stockName = currentStockName,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    MainTab.CONSENSUS -> {
                        ConsensusContent(
                            ticker = currentTicker,
                            stockName = currentStockName,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    MainTab.INDICATOR -> {
                        FundamentalHistoryContent(
                            ticker = currentTicker,
                            stockName = currentStockName,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    MainTab.DUPONT -> {
                        DuPontContent(
                            ticker = currentTicker,
                            stockName = currentStockName,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    MainTab.NAVER_STOCK -> {
                        NaverStockWebScreen(
                            ticker = currentTicker,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    }
                    } // Tab content Box
                } // Inner Column

                // Autocomplete dropdown overlay - 텍스트필드 바로 아래에 표시
                val dropdownTopPadding = with(density) { textFieldHeightPx.toDp() }
                if (searchResults.isNotEmpty() && query.isNotBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = dropdownTopPadding)
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
                                            viewModel.analyze(stock.ticker, stock.name, selectedRange.analysisDays, selectedRange.displayDays)
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

                // History dropdown overlay - 텍스트필드 바로 아래에 표시
                if (showHistory && analysisHistory.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = dropdownTopPadding)
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
                                            viewModel.analyze(history.ticker, history.name, selectedRange.analysisDays, selectedRange.displayDays)
                                            showHistory = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } // Outer Box
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
