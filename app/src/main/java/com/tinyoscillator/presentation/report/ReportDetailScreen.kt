package com.tinyoscillator.presentation.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.util.DateFormats
import com.tinyoscillator.data.repository.ConsensusRepository
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.data.repository.FinancialRepository
import com.tinyoscillator.data.repository.StockRepository
import com.tinyoscillator.domain.model.*
import com.tinyoscillator.domain.usecase.CalcOscillatorUseCase
import com.tinyoscillator.presentation.chart.OscillatorChart
import com.tinyoscillator.ui.theme.LocalFinanceColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

/**
 * 5개 소스(리포트·가격·차트·재무·ETF)를 섹션별로 점진 렌더 — all-or-nothing
 * 단일 로딩 대신 각 소스 도착 즉시 해당 섹션만 갱신한다.
 */
data class ReportDetailUiState(
    val report: ConsensusReport? = null,
    val currentPrice: Int = 0,
    val marketCap: Long = 0,
    val divergenceRate: Double = 0.0,
    val headerLoaded: Boolean = false,
    val chartData: ChartData? = null,
    val chartLoaded: Boolean = false,
    val financialSummary: FinancialSummary? = null,
    val latestStability: StabilityRatios? = null,
    val financialLoaded: Boolean = false,
    val etfHoldings: List<StockInEtfRow> = emptyList(),
    val etfLoaded: Boolean = false,
    // 현재 설정 경로 없음(블랭크 selection은 로드 스킵) — 향후 치명 오류 표시용으로 분기와 함께 보존
    val error: String? = null
) {
    val etfHoldingCount: Int get() = etfHoldings.size
}

/** 리포트 선택 키 — ticker/writeDate 쌍. 하나라도 비어있으면 [isBlank]. */
private data class ReportSelection(val ticker: String, val writeDate: String) {
    val isBlank: Boolean get() = ticker.isBlank() || writeDate.isBlank()
}

/**
 * 리포트 상세(헤더·가격·차트·재무·ETF) ViewModel.
 *
 * 초기 선택은 SavedStateHandle(`report_detail/{ticker}/{writeDate}` 라우트)에서 오고,
 * 태블릿 2-Pane 임베드에서는 [selectReport]로 리포트를 동적으로 전환한다.
 * 선택이 바뀌면 [collectLatest]가 이전 선택의 로드(4개 섹션 코루틴)를 전부 취소하고
 * 상태를 리셋한 뒤 재로드한다.
 *
 * 블랭크 selection(ticker 또는 writeDate 비어있음)은 로드를 스킵하고 현재 상태를 유지한다 —
 * 2-Pane 페인의 초기 selection은 SavedStateHandle에 값이 없어 블랭크이며, `LaunchedEffect`가
 * [selectReport]를 보내기 전 첫 프레임에 에러가 노출되는 것을 막기 위함이다. 전체화면 라우트는
 * 경로 세그먼트 2개(`{ticker}`, `{writeDate}`)가 항상 채워져 있어 블랭크에 도달하지 않는다.
 */
@HiltViewModel
class ReportDetailViewModel @Inject constructor(
    private val consensusRepository: ConsensusRepository,
    private val analysisCacheDao: AnalysisCacheDao,
    private val stockRepository: StockRepository,
    private val calcOscillator: CalcOscillatorUseCase,
    private val financialRepository: FinancialRepository,
    private val etfRepository: EtfRepository,
    private val apiConfigProvider: ApiConfigProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val fmt = DateFormats.yyyyMMdd

    private val _selection = MutableStateFlow(
        ReportSelection(
            ticker = savedStateHandle["ticker"] ?: "",
            writeDate = savedStateHandle["writeDate"] ?: ""
        )
    )

    private val _uiState = MutableStateFlow(ReportDetailUiState())
    val uiState: StateFlow<ReportDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _selection.collectLatest { sel ->
                if (sel.isBlank) return@collectLatest
                _uiState.value = ReportDetailUiState()
                loadData(sel.ticker, sel.writeDate)
            }
        }
    }

    /**
     * 2-Pane 임베드에서 목록 탭으로 리포트 전환. 동일 선택이면 no-op(StateFlow 동등성으로 재방출 없음).
     * 블랭크 인자는 무시 — 진행 중인 로드를 취소하고 로딩 스켈레톤에 갇히는 것을 방지.
     */
    fun selectReport(ticker: String, writeDate: String) {
        if (ticker.isBlank() || writeDate.isBlank()) return
        _selection.value = ReportSelection(ticker, writeDate)
    }

    private suspend fun loadData(ticker: String, writeDate: String) = coroutineScope {
        // 헤더/가격 (로컬 DB — 즉시 도착)
        launch {
            val report = loadReport(ticker, writeDate)
            val (cachedPrice, cachedMarketCap) = loadPriceData(ticker)

            // 캐시 가격 우선, 없으면 리포트의 현재가 사용
            val currentPrice = if (cachedPrice > 0) cachedPrice
                else report?.currentPrice?.toInt() ?: 0
            val targetPrice = report?.targetPrice ?: 0L
            val divergenceRate = if (currentPrice > 0 && targetPrice > 0) {
                (targetPrice - currentPrice).toDouble() / currentPrice * 100.0
            } else {
                report?.divergenceRate ?: 0.0
            }

            _uiState.update {
                it.copy(
                    report = report,
                    currentPrice = currentPrice,
                    divergenceRate = divergenceRate,
                    // 시가총액 우선순위: 캐시 > 차트 (차트가 먼저 채웠어도 캐시가 이김)
                    marketCap = if (cachedMarketCap > 0) cachedMarketCap else it.marketCap,
                    headerLoaded = true
                )
            }
        }

        // 수급오실레이터 차트 (Kiwoom API — 최대 수십 초)
        launch {
            val chartData = loadChartData(ticker)
            _uiState.update {
                it.copy(
                    chartData = chartData,
                    chartLoaded = true,
                    marketCap = if (it.marketCap > 0) it.marketCap
                        else chartData?.rows?.lastOrNull()?.marketCap ?: 0L
                )
            }
        }

        // 재무 요약 (KIS API/캐시)
        launch {
            val (financialSummary, latestStability) = loadFinancialSummary(ticker)
            _uiState.update {
                it.copy(
                    financialSummary = financialSummary,
                    latestStability = latestStability,
                    financialLoaded = true
                )
            }
        }

        // ETF 보유 (로컬 DB)
        launch {
            val etfHoldings = loadEtfHoldings(ticker)
            _uiState.update { it.copy(etfHoldings = etfHoldings, etfLoaded = true) }
        }
    }

    private suspend fun loadReport(ticker: String, writeDate: String): ConsensusReport? {
        return try {
            val reports = consensusRepository.getReportsByTicker(ticker)
            reports.find { it.writeDate == writeDate } ?: reports.firstOrNull()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "리포트 로딩 실패: $ticker")
            null
        }
    }

    private suspend fun loadPriceData(ticker: String): Pair<Int, Long> {
        return try {
            val latestDate = analysisCacheDao.getLatestDate(ticker)
            if (latestDate != null) {
                val entries = analysisCacheDao.getByTickerDateRange(ticker, latestDate, latestDate)
                val entry = entries.firstOrNull()
                Pair(entry?.closePrice ?: 0, entry?.marketCap ?: 0L)
            } else {
                Pair(0, 0L)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "가격 데이터 로딩 실패: $ticker")
            Pair(0, 0L)
        }
    }

    private suspend fun loadChartData(ticker: String): ChartData? {
        return try {
            val kiwoomConfig = apiConfigProvider.getKiwoomConfig()
            if (!kiwoomConfig.isValid()) return null

            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(OscillatorConfig.DEFAULT_ANALYSIS_DAYS.toLong())
            val dailyData = stockRepository.getDailyTradingData(
                ticker = ticker,
                startDate = startDate.format(fmt),
                endDate = endDate.format(fmt),
                config = kiwoomConfig
            )
            if (dailyData.isEmpty()) return null

            val displayDays = OscillatorConfig.DEFAULT_DISPLAY_DAYS
            val warmupCount = maxOf(0, dailyData.size - displayDays)
            val rows = calcOscillator.execute(dailyData, warmupCount)

            val report = _uiState.value.report
            ChartData(
                stockName = report?.stockName ?: "",
                ticker = ticker,
                rows = rows
            )
        } catch (e: ApiError.NoApiKeyError) {
            null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "오실레이터 차트 로딩 실패: $ticker")
            null
        }
    }

    private suspend fun loadFinancialSummary(ticker: String): Pair<FinancialSummary?, StabilityRatios?> {
        return try {
            val kisConfig = apiConfigProvider.getKisConfig()
            if (!kisConfig.isValid()) return Pair(null, null)

            val report = consensusRepository.getReportsByTicker(ticker).firstOrNull()
            val stockName = report?.stockName ?: ""

            val result = financialRepository.getFinancialData(ticker, stockName, kisConfig)
            val data = result.getOrNull() ?: return Pair(null, null)
            val summary = data.toSummary()
            // 이자보상배율 등은 FinancialSummary에 없으므로 원본 StabilityRatios도 함께 반환
            val latestPeriod = data.periods.sorted().lastOrNull()
            val latestStability = latestPeriod?.let { data.stabilityRatios[it] }
            if (summary.periods.isEmpty()) Pair(null, null)
            else Pair(summary, latestStability)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "재무 데이터 로딩 실패: $ticker")
            Pair(null, null)
        }
    }

    private suspend fun loadEtfHoldings(ticker: String): List<StockInEtfRow> {
        return try {
            val latestDate = etfRepository.getLatestDate() ?: return emptyList()
            etfRepository.getEtfsHoldingStock(ticker, latestDate)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "ETF 보유 목록 로딩 실패: $ticker")
            emptyList()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    onBack: () -> Unit,
    viewModel: ReportDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.report?.stockName ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        ReportDetailContent(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

/**
 * 탐색 탭 2-Pane 우측 패널용 리포트 상세 — 자체 ViewModel을 갖고
 * [ReportDetailViewModel.selectReport]으로 목록 선택을 따라간다.
 */
@Composable
fun ReportDetailPane(
    ticker: String,
    writeDate: String,
    modifier: Modifier = Modifier,
    viewModel: ReportDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(ticker, writeDate) {
        viewModel.selectReport(ticker, writeDate)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 리포트 전환 시 이전 리포트의 스크롤 오프셋이 남지 않도록 key로 콘텐츠 재생성
    key(ticker, writeDate) {
        ReportDetailContent(
            state = state,
            modifier = modifier.fillMaxSize()
        )
    }
}

/** 리포트 상세 본문 — 전체 화면([ReportDetailScreen])과 2-Pane 패널([ReportDetailPane])이 공유 */
@Composable
fun ReportDetailContent(
    state: ReportDetailUiState,
    modifier: Modifier = Modifier
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    val financeColors = LocalFinanceColors.current

    if (state.error != null) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                state.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            // 리포트 헤더 + 가격 정보 (로컬 DB — 가장 먼저 도착)
            if (!state.headerLoaded) {
                item { SectionLoadingCard("리포트 정보 로딩 중...") }
            } else {
                state.report?.let { report ->
                    item {
                        ReportHeaderCard(report)
                    }
                }
                item {
                    PriceInfoCard(
                        currentPrice = state.currentPrice,
                        targetPrice = state.report?.targetPrice ?: 0L,
                        divergenceRate = state.divergenceRate,
                        marketCap = state.marketCap,
                        numberFormat = numberFormat,
                        financeColors = financeColors
                    )
                }
            }

            // 수급오실레이터 차트
            item {
                SectionTitle("수급오실레이터")
                val chartData = state.chartData
                when {
                    !state.chartLoaded -> SectionLoadingCard("수급 데이터 수집 중...")
                    chartData != null -> OscillatorChart(
                        chartData = chartData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )
                    else -> EmptyDataCard("오실레이터 데이터가 없습니다.")
                }
            }

            // 수익성/안정성 요약
            item {
                if (!state.financialLoaded) {
                    SectionTitle("수익성 / 안정성")
                    SectionLoadingCard("재무 데이터 로딩 중...")
                } else {
                    FinancialSummarySection(
                        summary = state.financialSummary,
                        latestStability = state.latestStability
                    )
                }
            }

            // ETF 보유
            item {
                if (!state.etfLoaded) {
                    SectionLoadingCard("ETF 보유 조회 중...")
                } else {
                    EtfHoldingSection(holdings = state.etfHoldings)
                }
            }

            // 하단 여백
            item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun ReportHeaderCard(report: ConsensusReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                report.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    report.writeDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    report.institution,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (report.author.isNotBlank()) {
                Text(
                    "작성자: ${report.author}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (report.opinion.isNotBlank()) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        report.opinion,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceInfoCard(
    currentPrice: Int,
    targetPrice: Long,
    divergenceRate: Double,
    marketCap: Long,
    numberFormat: NumberFormat,
    financeColors: com.tinyoscillator.ui.theme.FinanceColors
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PriceItem("현재가", if (currentPrice > 0) "${numberFormat.format(currentPrice)}원" else "-")
                PriceItem("목표가", if (targetPrice > 0) "${numberFormat.format(targetPrice)}원" else "-")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val divergenceColor = when {
                    divergenceRate > 0 -> financeColors.positive
                    divergenceRate < 0 -> financeColors.negative
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Column {
                    Text(
                        "괴리율",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (currentPrice > 0 && targetPrice > 0) String.format("%.1f%%", divergenceRate) else "-",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = divergenceColor
                    )
                }
                PriceItem("시가총액", formatMarketCap(marketCap))
            }
        }
    }
}

@Composable
private fun PriceItem(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun FinancialSummarySection(
    summary: FinancialSummary?,
    latestStability: StabilityRatios? = null
) {
    SectionTitle("수익성 / 안정성")

    if (summary == null || summary.periods.isEmpty()) {
        EmptyDataCard("재무 데이터가 없습니다.")
        return
    }

    val latestPeriod = summary.displayPeriods.lastOrNull() ?: ""
    val hasProfitability = summary.hasProfitabilityData || summary.hasDuPontData
    val hasStability = summary.hasStabilityData || latestStability != null

    if (!hasProfitability && !hasStability) {
        EmptyDataCard("재무 데이터가 없습니다.")
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (hasProfitability) {
                Text(
                    "수익성 ($latestPeriod)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RatioItem("ROE", summary.roes.lastOrNull())
                    // ROA: NPM × Asset Turnover (DuPont)
                    val roa = summary.netProfitMargins.lastOrNull()?.let { npm ->
                        summary.assetTurnovers.lastOrNull()?.let { at -> npm * at }
                    }
                    RatioItem("ROA", roa)
                    RatioItem("영업이익률", computeOperatingMargin(summary))
                    RatioItem("순이익률", summary.netProfitMargins.lastOrNull())
                }
            }

            if (hasProfitability && hasStability) {
                HorizontalDivider()
            }

            if (hasStability) {
                Text(
                    "안정성 ($latestPeriod)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RatioItem("부채비율", summary.debtRatios.lastOrNull().takeIf { it != 0.0 }
                        ?: latestStability?.debtRatio)
                    RatioItem("유동비율", summary.currentRatios.lastOrNull().takeIf { it != 0.0 }
                        ?: latestStability?.currentRatio)
                    RatioItem("당좌비율", latestStability?.quickRatio)
                    RatioItem("이자보상", latestStability?.interestCoverageRatio)
                }
            }
        }
    }
}

/** 영업이익률: operatingProfit / revenue * 100 (최신 분기) */
private fun computeOperatingMargin(summary: FinancialSummary): Double? {
    val revenue = summary.revenues.lastOrNull() ?: return null
    val operatingProfit = summary.operatingProfits.lastOrNull() ?: return null
    if (revenue == 0L) return null
    return operatingProfit.toDouble() / revenue * 100.0
}

@Composable
private fun RatioItem(label: String, value: Double?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            if (value != null) String.format("%.1f%%", value) else "-",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EtfHoldingSection(holdings: List<StockInEtfRow>) {
    val count = holdings.size
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ETF 보유",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Badge(
                    containerColor = if (count > 0) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (count > 0) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Text(
                        "${count}개 ETF에 보유됨",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            if (holdings.isNotEmpty()) {
                HorizontalDivider()
                holdings.forEach { etf ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            etf.etfName,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        if (etf.weight != null) {
                            Text(
                                String.format("%.2f%%", etf.weight),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 섹션 단위 로딩 표시 — 소스별 점진 렌더에서 미도착 섹션에 사용 */
@Composable
private fun SectionLoadingCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun EmptyDataCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatMarketCap(marketCap: Long): String {
    if (marketCap <= 0) return "-"
    val tril = marketCap / 1_000_000_000_000.0
    return if (tril >= 1.0) {
        String.format("%.1f조원", tril)
    } else {
        val billion = marketCap / 100_000_000
        "${billion}억원"
    }
}
