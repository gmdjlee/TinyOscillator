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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

data class ReportDetailUiState(
    val isLoading: Boolean = true,
    val report: ConsensusReport? = null,
    val currentPrice: Int = 0,
    val marketCap: Long = 0,
    val divergenceRate: Double = 0.0,
    val chartData: ChartData? = null,
    val financialSummary: FinancialSummary? = null,
    val latestStability: StabilityRatios? = null,
    val etfHoldings: List<StockInEtfRow> = emptyList(),
    val error: String? = null
) {
    val etfHoldingCount: Int get() = etfHoldings.size
}

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

    private val ticker: String = savedStateHandle["ticker"] ?: ""
    private val writeDate: String = savedStateHandle["writeDate"] ?: ""
    private val fmt = DateFormats.yyyyMMdd

    private val _uiState = MutableStateFlow(ReportDetailUiState())
    val uiState: StateFlow<ReportDetailUiState> = _uiState.asStateFlow()

    init {
        if (ticker.isNotEmpty() && writeDate.isNotEmpty()) {
            loadData()
        } else {
            _uiState.value = ReportDetailUiState(isLoading = false, error = "종목 정보가 없습니다.")
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = ReportDetailUiState(isLoading = true)
            try {
                coroutineScope {
                    val reportDeferred = async { loadReport() }
                    val priceDeferred = async { loadPriceData() }
                    val chartDeferred = async { loadChartData() }
                    val financialDeferred = async { loadFinancialSummary() }
                    val etfDeferred = async { loadEtfHoldings() }

                    val report = reportDeferred.await()
                    val (cachedPrice, cachedMarketCap) = priceDeferred.await()
                    val chartData = chartDeferred.await()
                    val (financialSummary, latestStability) = financialDeferred.await()
                    val etfHoldings = etfDeferred.await()

                    // 캐시 가격 우선, 없으면 리포트의 현재가 사용
                    val currentPrice = if (cachedPrice > 0) cachedPrice
                        else report?.currentPrice?.toInt() ?: 0
                    val targetPrice = report?.targetPrice ?: 0L
                    val divergenceRate = if (currentPrice > 0 && targetPrice > 0) {
                        (targetPrice - currentPrice).toDouble() / currentPrice * 100.0
                    } else {
                        report?.divergenceRate ?: 0.0
                    }

                    // 시가총액: 캐시 → 차트 데이터(최신 오실레이터 행)
                    val marketCap = if (cachedMarketCap > 0) cachedMarketCap
                        else chartData?.rows?.lastOrNull()?.marketCap ?: 0L

                    _uiState.value = ReportDetailUiState(
                        isLoading = false,
                        report = report,
                        currentPrice = currentPrice,
                        marketCap = marketCap,
                        divergenceRate = divergenceRate,
                        chartData = chartData,
                        financialSummary = financialSummary,
                        latestStability = latestStability,
                        etfHoldings = etfHoldings
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "리포트 상세 로딩 실패: $ticker")
                _uiState.value = ReportDetailUiState(
                    isLoading = false,
                    error = "데이터 로딩 실패: ${e.message}"
                )
            }
        }
    }

    private suspend fun loadReport(): ConsensusReport? {
        val reports = consensusRepository.getReportsByTicker(ticker)
        return reports.find { it.writeDate == writeDate } ?: reports.firstOrNull()
    }

    private suspend fun loadPriceData(): Pair<Int, Long> {
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

    private suspend fun loadChartData(): ChartData? {
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

    private suspend fun loadFinancialSummary(): Pair<FinancialSummary?, StabilityRatios?> {
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

    private suspend fun loadEtfHoldings(): List<StockInEtfRow> {
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
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    val financeColors = LocalFinanceColors.current

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
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        state.error?.let { errorMessage ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 리포트 헤더
            state.report?.let { report ->
                item {
                    ReportHeaderCard(report)
                }
            }

            // 가격 정보
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

            // 수급오실레이터 차트
            item {
                SectionTitle("수급오실레이터")
                val chartData = state.chartData
                if (chartData != null) {
                    OscillatorChart(
                        chartData = chartData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )
                } else {
                    EmptyDataCard("오실레이터 데이터가 없습니다.")
                }
            }

            // 수익성/안정성 요약
            item {
                FinancialSummarySection(
                    summary = state.financialSummary,
                    latestStability = state.latestStability
                )
            }

            // ETF 보유
            item {
                EtfHoldingSection(holdings = state.etfHoldings)
            }

            // 하단 여백
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
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
