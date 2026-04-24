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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tinyoscillator.presentation.common.WindowType
import com.tinyoscillator.presentation.common.calculateWindowType
import com.tinyoscillator.presentation.etf.AggregatedStockTrendScreen
import com.tinyoscillator.presentation.etf.EtfScreen
import com.tinyoscillator.presentation.etf.StockTrendScreen
import com.tinyoscillator.domain.model.ConsensusReport
import com.tinyoscillator.presentation.ai.AiAnalysisScreen
import com.tinyoscillator.presentation.marketanalysis.MarketAnalysisScreen
import com.tinyoscillator.presentation.portfolio.PortfolioScreen
import com.tinyoscillator.presentation.common.HeatmapScreen
import com.tinyoscillator.presentation.report.ReportDetailScreen
import com.tinyoscillator.presentation.report.ReportScreen
import com.tinyoscillator.presentation.sector.SectorIndexDetailScreen
import com.tinyoscillator.presentation.sector.SectorIndexListScreen
import com.tinyoscillator.presentation.settings.SettingsScreen
import com.tinyoscillator.presentation.stock.OscillatorScreen
import com.tinyoscillator.presentation.viewmodel.OscillatorViewModel
import com.tinyoscillator.presentation.common.ThemeToggleIcon
import com.tinyoscillator.ui.theme.LocalThemeModeState
import com.tinyoscillator.ui.theme.TinyOscillatorTheme
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.AndroidEntryPoint

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
    AI_ANALYSIS("AI분석", Icons.Default.Psychology),
    SECTOR_THEME("업종지수", Icons.Default.Category),
    PORTFOLIO("포트폴리오", Icons.Default.AccountBalance)
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: OscillatorViewModel = hiltViewModel()
    val configuration = LocalConfiguration.current
    val windowType = calculateWindowType(configuration.screenWidthDp.dp)

    // 온보딩: 첫 실행 시 API 설정 안내 다이얼로그
    val context = LocalContext.current
    var showOnboarding by rememberSaveable {
        val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        mutableStateOf(!prefs.getBoolean("onboarding_completed", false))
    }
    if (showOnboarding) {
        OnboardingDialog(
            onDismiss = {
                showOnboarding = false
                context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("onboarding_completed", true).apply()
            },
            onGoToSettings = {
                showOnboarding = false
                context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("onboarding_completed", true).apply()
                navController.navigate("settings")
            }
        )
    }

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
                onHeatmapClick = { navController.navigate("heatmap") },
                onSectorIndexClick = { code, name ->
                    navController.navigate("sector_index_detail/$code/$name")
                }
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
        composable("sector_index_detail/{code}/{name}") {
            SectorIndexDetailScreen(
                onBack = { navController.popBackStack() },
            )
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
    onHeatmapClick: () -> Unit = {},
    onSectorIndexClick: (String, String) -> Unit = { _, _ -> }
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
                BottomNavItem.AI_ANALYSIS -> {
                    AiAnalysisScreen(
                        onSettingsClick = onSettingsClick,
                        onHeatmapClick = onHeatmapClick
                    )
                }
                BottomNavItem.SECTOR_THEME -> {
                    SectorIndexListScreen(
                        onSettingsClick = onSettingsClick,
                        onSectorClick = { sector ->
                            onSectorIndexClick(sector.code, sector.name)
                        },
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
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp
                ) {
                    BottomNavItem.entries.forEach { item ->
                        NavigationBarItem(
                            selected = selectedNav == item,
                            onClick = { selectedNav = item },
                            icon = {
                                Icon(
                                    item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    item.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selectedNav == item) FontWeight.SemiBold
                                                 else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        ) { padding ->
            screenContent(Modifier.padding(padding))
        }
    } else {
        // 폴더블/태블릿: Navigation Rail
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Spacer(modifier = Modifier.weight(1f))
                BottomNavItem.entries.forEach { item ->
                    NavigationRailItem(
                        selected = selectedNav == item,
                        onClick = { selectedNav = item },
                        icon = {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                item.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selectedNav == item) FontWeight.SemiBold
                                             else FontWeight.Normal
                            )
                        },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }
            screenContent(Modifier.fillMaxSize())
        }
    }
}

// OscillatorScreen moved to presentation/stock/OscillatorScreen.kt

/**
 * 첫 실행 온보딩 다이얼로그.
 * 앱 사용에 필요한 API 키 설정을 안내.
 */
@Composable
private fun OnboardingDialog(
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("TinyOscillator 시작하기")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "주식 분석 데이터를 수집하려면 API 키 설정이 필요합니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("필수 설정:", style = MaterialTheme.typography.labelLarge)
                        Text("  1. Kiwoom API 키 (주가 데이터)", style = MaterialTheme.typography.bodySmall)
                        Text("  2. KRX 계정 (ETF/지수 데이터)", style = MaterialTheme.typography.bodySmall)
                        Text("", style = MaterialTheme.typography.bodySmall)
                        Text("선택 설정:", style = MaterialTheme.typography.labelLarge)
                        Text("  3. KIS API 키 (재무제표)", style = MaterialTheme.typography.bodySmall)
                        Text("  4. AI API 키 (AI 분석)", style = MaterialTheme.typography.bodySmall)
                        Text("  5. DART API 키 (공시)", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    "설정 후 Schedule 탭에서 데이터 수집을 시작하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onGoToSettings) {
                Text("설정으로 이동")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("나중에")
            }
        }
    )
}
