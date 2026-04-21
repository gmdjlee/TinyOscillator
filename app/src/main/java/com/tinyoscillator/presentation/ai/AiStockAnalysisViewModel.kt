package com.tinyoscillator.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.api.AiApiClient
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.core.database.entity.StockMasterEntity
import com.tinyoscillator.core.util.DateFormats
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.data.repository.FinancialRepository
import com.tinyoscillator.data.repository.StockRepository
import com.tinyoscillator.domain.model.AiAnalysisState
import com.tinyoscillator.domain.model.AiAnalysisType
import com.tinyoscillator.domain.model.ChatMessage
import com.tinyoscillator.domain.model.ChatRole
import com.tinyoscillator.domain.model.DemarkPeriodType
import com.tinyoscillator.domain.usecase.AiAnalysisPreparer
import com.tinyoscillator.domain.usecase.CalcDemarkTDUseCase
import com.tinyoscillator.domain.usecase.CalcOscillatorUseCase
import com.tinyoscillator.domain.usecase.SearchStocksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

/**
 * 종목 탭 전담 ViewModel.
 *
 * 책임: 종목 검색/선택, 일별 매매·재무·ETF 추이 병렬 수집, 종목 AI 분석,
 * 종목 채팅 컨텍스트 관리. 확률 분석은 [AiProbabilityAnalysisViewModel]이 담당하지만
 * 대상 종목은 본 VM의 [selectedStock]에서 읽는다.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class AiStockAnalysisViewModel @Inject constructor(
    private val stockRepository: StockRepository,
    private val financialRepository: FinancialRepository,
    private val etfRepository: EtfRepository,
    private val calcOscillator: CalcOscillatorUseCase,
    private val calcDemarkTD: CalcDemarkTDUseCase,
    private val searchStocksUseCase: SearchStocksUseCase,
    private val aiApiClient: AiApiClient,
    private val aiPreparer: AiAnalysisPreparer,
    private val apiConfigProvider: ApiConfigProvider
) : ViewModel() {

    private val fmt = DateFormats.yyyyMMdd

    private val _searchQuery = MutableStateFlow("")
    val searchResults: StateFlow<List<StockMasterEntity>> = _searchQuery
        .debounce(200)
        .flatMapLatest { query ->
            flow {
                if (query.isBlank()) emit(emptyList())
                else emit(searchStocksUseCase.searchWithChosung(query))
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedStock = MutableStateFlow<SelectedStockInfo?>(null)
    val selectedStock: StateFlow<SelectedStockInfo?> = _selectedStock.asStateFlow()

    private val _stockDataState = MutableStateFlow<StockDataState>(StockDataState.Idle)
    val stockDataState: StateFlow<StockDataState> = _stockDataState.asStateFlow()

    private val _stockAiState = MutableStateFlow<AiAnalysisState>(AiAnalysisState.Idle)
    val stockAiState: StateFlow<AiAnalysisState> = _stockAiState.asStateFlow()

    private var stockSystemPrompt: String = ""
    private var stockDataContext: String = ""

    private val _stockChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val stockChatMessages: StateFlow<List<ChatMessage>> = _stockChatMessages.asStateFlow()

    private val _stockChatLoading = MutableStateFlow(false)
    val stockChatLoading: StateFlow<Boolean> = _stockChatLoading.asStateFlow()

    fun searchStock(query: String) {
        _searchQuery.value = query
    }

    fun selectStock(ticker: String, name: String, market: String?, sector: String?) {
        _selectedStock.value = SelectedStockInfo(ticker, name, market, sector)
        _stockAiState.value = AiAnalysisState.Idle
        _stockChatMessages.value = emptyList()
        stockDataContext = ""
        stockSystemPrompt = ""
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

                        val oscillatorRows = if (dailyData.isNotEmpty()) {
                            calcOscillator.execute(dailyData, 0)
                        } else emptyList()

                        val signals = if (oscillatorRows.isNotEmpty()) {
                            calcOscillator.analyzeSignals(oscillatorRows)
                        } else emptyList()

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

                        val stock = _selectedStock.value
                        if (stock != null && oscillatorRows.isNotEmpty()) {
                            stockDataContext = aiPreparer.prepareComprehensiveStockAnalysis(
                                stockName = stock.name,
                                ticker = stock.ticker,
                                oscillatorRows = oscillatorRows,
                                signals = signals,
                                demarkRows = demarkRows,
                                financialData = financialData,
                                etfAggregated = etfAggregated,
                                market = stock.market,
                                sector = stock.sector
                            )
                            stockSystemPrompt = aiPreparer.getChatSystemPrompt(AiAnalysisType.COMPREHENSIVE_STOCK, stockDataContext)
                        }
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

    fun dismissStockAi() {
        _stockAiState.value = AiAnalysisState.Idle
    }

    fun sendStockChat(userMessage: String) {
        if (userMessage.isBlank() || stockDataContext.isBlank()) return

        viewModelScope.launch {
            val aiConfig = apiConfigProvider.getAiConfig()
            if (!aiConfig.isValid()) {
                _stockChatMessages.value = _stockChatMessages.value +
                    ChatMessage(ChatRole.ASSISTANT, "AI API 키가 설정되지 않았습니다. 설정에서 API 키를 입력해주세요.")
                return@launch
            }

            val userMsg = ChatMessage(ChatRole.USER, userMessage)
            _stockChatMessages.value = _stockChatMessages.value + userMsg
            _stockChatLoading.value = true

            try {
                val result = aiApiClient.chat(
                    config = aiConfig,
                    systemPrompt = stockSystemPrompt,
                    messages = _stockChatMessages.value,
                    maxTokens = 1024
                )
                result.fold(
                    onSuccess = { response ->
                        _stockChatMessages.value = _stockChatMessages.value +
                            ChatMessage(ChatRole.ASSISTANT, response)
                    },
                    onFailure = { e ->
                        _stockChatMessages.value = _stockChatMessages.value +
                            ChatMessage(ChatRole.ASSISTANT, "오류: ${e.message}")
                    }
                )
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _stockChatMessages.value = _stockChatMessages.value +
                    ChatMessage(ChatRole.ASSISTANT, "오류: ${e.message}")
            } finally {
                _stockChatLoading.value = false
            }
        }
    }

    fun clearStockChat() {
        _stockChatMessages.value = emptyList()
    }
}
