package com.tinyoscillator.presentation.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.api.AiApiClient
import com.tinyoscillator.core.database.entity.StockMasterEntity
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.data.repository.FinancialRepository
import com.tinyoscillator.data.repository.MarketIndicatorRepository
import com.tinyoscillator.data.repository.StockRepository
import com.tinyoscillator.domain.model.*
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.core.util.DateFormats
import com.tinyoscillator.data.engine.StatisticalAnalysisEngine
import com.tinyoscillator.domain.usecase.AiAnalysisPreparer
import com.tinyoscillator.domain.usecase.CalcDemarkTDUseCase
import com.tinyoscillator.domain.usecase.CalcOscillatorUseCase
import com.tinyoscillator.domain.usecase.SearchStocksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

enum class AiTab(val label: String) {
    MARKET("시장지표"),
    STOCK("종목"),
    PROBABILITY("확률분석")
}

data class SelectedStockInfo(
    val ticker: String,
    val name: String,
    val market: String?,
    val sector: String?
)

sealed class StockDataState {
    data object Idle : StockDataState()
    data object Loading : StockDataState()
    data class Loaded(
        val oscillatorRows: List<OscillatorRow>,
        val signals: List<SignalAnalysis>,
        val demarkRows: List<DemarkTDRow>,
        val financialData: FinancialData?,
        val etfAggregated: List<StockAggregatedTimePoint>
    ) : StockDataState()
    data class Error(val message: String) : StockDataState()
}

/** 확률 분석 상태 */
sealed class ProbabilityAnalysisState {
    data object Idle : ProbabilityAnalysisState()
    data class Computing(val message: String) : ProbabilityAnalysisState()
    data class Success(val result: StatisticalResult) : ProbabilityAnalysisState()
    data class Error(val message: String) : ProbabilityAnalysisState()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class AiAnalysisViewModel @Inject constructor(
    application: Application,
    private val stockRepository: StockRepository,
    private val financialRepository: FinancialRepository,
    private val etfRepository: EtfRepository,
    private val marketIndicatorRepository: MarketIndicatorRepository,
    private val calcOscillator: CalcOscillatorUseCase,
    private val calcDemarkTD: CalcDemarkTDUseCase,
    private val searchStocksUseCase: SearchStocksUseCase,
    private val aiApiClient: AiApiClient,
    private val aiPreparer: AiAnalysisPreparer,
    private val apiConfigProvider: ApiConfigProvider,
    private val statisticalAnalysisEngine: StatisticalAnalysisEngine
) : AndroidViewModel(application) {

    private val fmt = DateFormats.yyyyMMdd

    private val _selectedTab = MutableStateFlow(AiTab.MARKET)
    val selectedTab: StateFlow<AiTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchResults: StateFlow<List<StockMasterEntity>> = _searchQuery
        .debounce(200)
        .flatMapLatest { query -> searchStocksUseCase(query) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedStock = MutableStateFlow<SelectedStockInfo?>(null)
    val selectedStock: StateFlow<SelectedStockInfo?> = _selectedStock.asStateFlow()

    private val _stockDataState = MutableStateFlow<StockDataState>(StockDataState.Idle)
    val stockDataState: StateFlow<StockDataState> = _stockDataState.asStateFlow()

    private val _marketAiState = MutableStateFlow<AiAnalysisState>(AiAnalysisState.Idle)
    val marketAiState: StateFlow<AiAnalysisState> = _marketAiState.asStateFlow()

    private val _stockAiState = MutableStateFlow<AiAnalysisState>(AiAnalysisState.Idle)
    val stockAiState: StateFlow<AiAnalysisState> = _stockAiState.asStateFlow()

    // 확률 분석 상태
    private val _probabilityState = MutableStateFlow<ProbabilityAnalysisState>(ProbabilityAnalysisState.Idle)
    val probabilityState: StateFlow<ProbabilityAnalysisState> = _probabilityState.asStateFlow()

    fun selectTab(tab: AiTab) {
        _selectedTab.value = tab
    }

    fun searchStock(query: String) {
        _searchQuery.value = query
    }

    fun selectStock(ticker: String, name: String, market: String?, sector: String?) {
        _selectedStock.value = SelectedStockInfo(ticker, name, market, sector)
        _stockAiState.value = AiAnalysisState.Idle
        loadStockData(ticker, name)
    }

    private fun loadStockData(ticker: String, name: String) {
        viewModelScope.launch {
            _stockDataState.value = StockDataState.Loading
            try {
                withTimeout(90_000) {
                    supervisorScope {
                        val endDate = LocalDate.now()
                        val startDate = endDate.minusDays(365)

                        // 1. Daily trading data (needed for oscillator + demark)
                        val kiwoomConfig = try {
                            apiConfigProvider.getKiwoomConfig()
                        } catch (e: Exception) {
                            null
                        }

                        val dailyDataDeferred = async {
                            if (kiwoomConfig == null) return@async emptyList()
                            try {
                                stockRepository.getDailyTradingData(
                                    ticker = ticker,
                                    startDate = startDate.format(fmt),
                                    endDate = endDate.format(fmt),
                                    config = kiwoomConfig
                                )
                            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.w("오실레이터 데이터 수집 실패: %s", e.message)
                                emptyList()
                            }
                        }

                        // 2. Financial data
                        val financialDeferred = async {
                            try {
                                val kisConfig = apiConfigProvider.getKisConfig()
                                if (kisConfig.isValid()) {
                                    financialRepository.getFinancialData(ticker, name, kisConfig).getOrNull()
                                } else null
                            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.w("재무정보 수집 실패: %s", e.message)
                                null
                            }
                        }

                        // 3. ETF aggregated trend
                        val etfDeferred = async {
                            try {
                                etfRepository.getStockAggregatedTrend(ticker)
                            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.w("ETF 추이 수집 실패: %s", e.message)
                                emptyList()
                            }
                        }

                        val dailyData = dailyDataDeferred.await()
                        val financialData = financialDeferred.await()
                        val etfAggregated = etfDeferred.await()

                        // Calculate oscillator
                        val oscillatorRows = if (dailyData.isNotEmpty()) {
                            calcOscillator.execute(dailyData, 0)
                        } else emptyList()

                        val signals = if (oscillatorRows.isNotEmpty()) {
                            calcOscillator.analyzeSignals(oscillatorRows)
                        } else emptyList()

                        // Calculate DeMark
                        val demarkRows = if (dailyData.isNotEmpty()) {
                            try {
                                calcDemarkTD.execute(dailyData, DemarkPeriodType.DAILY)
                            } catch (e: Exception) {
                                Timber.w("DeMark 계산 실패: %s", e.message)
                                emptyList()
                            }
                        } else emptyList()

                        _stockDataState.value = StockDataState.Loaded(
                            oscillatorRows = oscillatorRows,
                            signals = signals,
                            demarkRows = demarkRows,
                            financialData = financialData,
                            etfAggregated = etfAggregated
                        )
                    }
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _stockDataState.value = StockDataState.Error(e.message ?: "데이터 수집 실패")
            }
        }
    }

    fun analyzeStockWithAi() {
        val loaded = _stockDataState.value as? StockDataState.Loaded ?: return
        val stock = _selectedStock.value ?: return
        if (loaded.oscillatorRows.isEmpty()) return

        viewModelScope.launch {
            val aiConfig = apiConfigProvider.getAiConfig()
            if (!aiConfig.isValid()) {
                _stockAiState.value = AiAnalysisState.NoApiKey
                return@launch
            }
            _stockAiState.value = AiAnalysisState.Loading
            try {
                val userMessage = aiPreparer.prepareComprehensiveStockAnalysis(
                    stockName = stock.name,
                    ticker = stock.ticker,
                    oscillatorRows = loaded.oscillatorRows,
                    signals = loaded.signals,
                    demarkRows = loaded.demarkRows,
                    financialData = loaded.financialData,
                    etfAggregated = loaded.etfAggregated,
                    market = stock.market,
                    sector = stock.sector
                )
                val systemPrompt = aiPreparer.getSystemPrompt(AiAnalysisType.COMPREHENSIVE_STOCK)
                val result = aiApiClient.analyze(
                    config = aiConfig,
                    systemPrompt = systemPrompt,
                    userMessage = userMessage,
                    analysisType = AiAnalysisType.COMPREHENSIVE_STOCK
                )
                result.fold(
                    onSuccess = { _stockAiState.value = AiAnalysisState.Success(it) },
                    onFailure = { _stockAiState.value = AiAnalysisState.Error(it.message ?: "AI 분석 실패") }
                )
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _stockAiState.value = AiAnalysisState.Error(e.message ?: "AI 분석 실패")
            }
        }
    }

    fun analyzeMarketWithAi() {
        viewModelScope.launch {
            val aiConfig = apiConfigProvider.getAiConfig()
            if (!aiConfig.isValid()) {
                _marketAiState.value = AiAnalysisState.NoApiKey
                return@launch
            }
            _marketAiState.value = AiAnalysisState.Loading
            try {
                val kospiData = marketIndicatorRepository.getRecentData("KOSPI", 14)
                val kosdaqData = marketIndicatorRepository.getRecentData("KOSDAQ", 14)
                val deposits = marketIndicatorRepository.getRecentDeposits(10)

                val userMessage = aiPreparer.prepareMarketAnalysis(
                    kospiData = kospiData,
                    kosdaqData = kosdaqData,
                    deposits = deposits
                )
                val systemPrompt = aiPreparer.getSystemPrompt(AiAnalysisType.MARKET_OVERVIEW)
                val result = aiApiClient.analyze(
                    config = aiConfig,
                    systemPrompt = systemPrompt,
                    userMessage = userMessage,
                    analysisType = AiAnalysisType.MARKET_OVERVIEW
                )
                result.fold(
                    onSuccess = { _marketAiState.value = AiAnalysisState.Success(it) },
                    onFailure = { _marketAiState.value = AiAnalysisState.Error(it.message ?: "AI 분석 실패") }
                )
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _marketAiState.value = AiAnalysisState.Error(e.message ?: "AI 분석 실패")
            }
        }
    }

    /** 확률 분석 실행 — 7개 통계 엔진 병렬 실행 (API 키 불필요) */
    fun analyzeProbability() {
        val stock = _selectedStock.value ?: return

        viewModelScope.launch {
            _probabilityState.value = ProbabilityAnalysisState.Computing("7개 통계 알고리즘 실행 중...")
            try {
                val result = statisticalAnalysisEngine.analyze(stock.ticker)
                _probabilityState.value = ProbabilityAnalysisState.Success(result)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _probabilityState.value = ProbabilityAnalysisState.Error(e.message ?: "확률 분석 실패")
            }
        }
    }

    fun dismissProbability() {
        _probabilityState.value = ProbabilityAnalysisState.Idle
    }

    fun dismissMarketAi() {
        _marketAiState.value = AiAnalysisState.Idle
    }

    fun dismissStockAi() {
        _stockAiState.value = AiAnalysisState.Idle
    }
}
