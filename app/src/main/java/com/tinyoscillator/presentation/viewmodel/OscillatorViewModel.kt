package com.tinyoscillator.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.core.api.toUserMessage
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.core.database.dao.AnalysisHistoryDao
import com.tinyoscillator.core.database.entity.AnalysisHistoryEntity
import com.tinyoscillator.core.database.entity.StockMasterEntity
import com.tinyoscillator.core.network.NetworkUtils
import com.tinyoscillator.core.util.DateFormats
import com.tinyoscillator.data.repository.FinancialRepository
import com.tinyoscillator.data.repository.StockMasterRepository
import com.tinyoscillator.data.repository.StockRepository
import com.tinyoscillator.domain.model.*
import com.tinyoscillator.domain.usecase.CalcOscillatorUseCase
import com.tinyoscillator.domain.usecase.IntradayDataMerger
import com.tinyoscillator.domain.usecase.SaveAnalysisHistoryUseCase
import com.tinyoscillator.domain.usecase.SearchStocksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import timber.log.Timber
import javax.inject.Inject

/**
 * 수급 오실레이터 ViewModel
 *
 * 전체 파이프라인:
 * 1. API 키 설정 로드 (DataStore)
 * 2. 종목 검색 (로컬 DB → autocomplete)
 * 3. 데이터 수집 (StockRepository → Kiwoom API + 캐시)
 * 4. 오실레이터 계산 (CalcOscillatorUseCase)
 * 5. UI State 업데이트
 * 6. 장중 실시간 수급 데이터 60초 폴링 (ka10063)
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class OscillatorViewModel @Inject constructor(
    application: Application,
    private val repository: StockRepository,
    private val stockMasterRepository: StockMasterRepository,
    private val searchStocksUseCase: SearchStocksUseCase,
    private val saveAnalysisHistoryUseCase: SaveAnalysisHistoryUseCase,
    private val calcOscillator: CalcOscillatorUseCase,
    private val analysisHistoryDao: AnalysisHistoryDao,
    private val financialRepository: FinancialRepository,
    private val apiConfigProvider: ApiConfigProvider
) : AndroidViewModel(application) {

    private val config = OscillatorConfig()
    private val fmt = DateFormats.yyyyMMdd

    private val _uiState = MutableStateFlow<OscillatorUiState>(OscillatorUiState.Idle)
    val uiState: StateFlow<OscillatorUiState> = _uiState.asStateFlow()

    // 실시간 수급 상태
    private val _isIntradayMerged = MutableStateFlow(false)
    val isIntradayMerged: StateFlow<Boolean> = _isIntradayMerged.asStateFlow()

    private val _autoRefreshEnabled = MutableStateFlow(true)
    val autoRefreshEnabled: StateFlow<Boolean> = _autoRefreshEnabled.asStateFlow()

    // 로컬 DB 검색 결과
    private val _searchQuery = MutableStateFlow("")
    val searchResults: StateFlow<List<StockMasterEntity>> = _searchQuery
        .debounce(200)
        .flatMapLatest { query -> searchStocksUseCase(query) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 분석 기록
    val analysisHistory: StateFlow<List<AnalysisHistoryEntity>> = analysisHistoryDao
        .getRecent(30)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 종목 마스터 상태
    private val _stockMasterStatus = MutableStateFlow<StockMasterStatus>(StockMasterStatus.Unknown)
    val stockMasterStatus: StateFlow<StockMasterStatus> = _stockMasterStatus.asStateFlow()

    // 실시간 폴링 관련
    private var autoRefreshJob: Job? = null
    private var currentAnalysisTicker: String? = null
    private var currentAnalysisName: String? = null
    private var currentDailyData: List<DailyTrading>? = null
    private var currentDisplayDays: Int = OscillatorConfig.DEFAULT_DISPLAY_DAYS

    init {
        // 앱 시작시 종목 마스터 DB 채우기
        viewModelScope.launch {
            try {
                val apiConfig = apiConfigProvider.getKiwoomConfig()
                _stockMasterStatus.value = StockMasterStatus.Loading
                stockMasterRepository.populateIfEmpty(apiConfig)
                val count = stockMasterRepository.getCount()
                _stockMasterStatus.value = StockMasterStatus.Ready(count)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("종목 마스터 초기화 실패: %s", e.message)
                _stockMasterStatus.value = StockMasterStatus.Error(e.message ?: "알 수 없는 오류")
            }
        }
    }

    /** API 키 캐시 초기화 (설정 변경 후) */
    fun invalidateApiConfig() {
        apiConfigProvider.invalidateKiwoom()
        // 설정 변경 후 종목 마스터 재시도
        viewModelScope.launch {
            try {
                _stockMasterStatus.value = StockMasterStatus.Loading
                val apiConfig = apiConfigProvider.getKiwoomConfig()
                stockMasterRepository.populateIfEmpty(apiConfig)
                val count = stockMasterRepository.getCount()
                _stockMasterStatus.value = StockMasterStatus.Ready(count)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("설정 변경 후 종목 마스터 재시도 실패: %s", e.message)
                _stockMasterStatus.value = StockMasterStatus.Error(e.message ?: "알 수 없는 오류")
            }
        }
    }

    /** 종목 마스터 DB 강제 새로고침 */
    fun refreshStockMaster() {
        viewModelScope.launch {
            _stockMasterStatus.value = StockMasterStatus.Loading
            try {
                val apiConfig = apiConfigProvider.getKiwoomConfig()
                stockMasterRepository.forceRefresh(apiConfig)
                val count = stockMasterRepository.getCount()
                _stockMasterStatus.value = StockMasterStatus.Ready(count)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("종목 마스터 새로고침 실패: %s", e.message)
                _stockMasterStatus.value = StockMasterStatus.Error(e.message ?: "알 수 없는 오류")
            }
        }
    }

    /** 종목 검색 (로컬 DB, 200ms 디바운스) */
    fun searchStock(query: String) {
        _searchQuery.value = query
    }

    /** 자동 갱신 토글 */
    fun toggleAutoRefresh() {
        _autoRefreshEnabled.value = !_autoRefreshEnabled.value
    }

    /** 오실레이터 분석 실행 */
    fun analyze(
        ticker: String,
        stockName: String,
        analysisDays: Int = OscillatorConfig.DEFAULT_ANALYSIS_DAYS,
        displayDays: Int = OscillatorConfig.DEFAULT_DISPLAY_DAYS
    ) {
        if (!isValidTicker(ticker)) {
            _uiState.value = OscillatorUiState.Error("유효하지 않은 종목코드입니다: $ticker")
            return
        }
        if (analysisDays < 1 || displayDays < 1) {
            _uiState.value = OscillatorUiState.Error("분석일수와 표시일수는 1 이상이어야 합니다.")
            return
        }

        // 기존 폴링 중지
        stopAutoRefresh()

        viewModelScope.launch {
            _uiState.value = OscillatorUiState.Loading("데이터 수집 중...")
            _isIntradayMerged.value = false

            try {
                if (!NetworkUtils.isNetworkAvailable(getApplication())) {
                    _uiState.value = OscillatorUiState.Error("네트워크에 연결되어 있지 않습니다. 인터넷 연결을 확인해주세요.")
                    return@launch
                }

                val apiConfig = apiConfigProvider.getKiwoomConfig()

                // 기간 설정
                val endDate = LocalDate.now()
                val startDate = endDate.minusDays(analysisDays.toLong())

                // Step 1: 데이터 수집 (incremental cache)
                _uiState.value = OscillatorUiState.Loading("Kiwoom API 데이터 수집 중...")
                val dailyData = repository.getDailyTradingData(
                    ticker = ticker,
                    startDate = startDate.format(fmt),
                    endDate = endDate.format(fmt),
                    config = apiConfig
                )

                if (dailyData.isEmpty()) {
                    _uiState.value = OscillatorUiState.Error("데이터가 없습니다. 종목코드를 확인해주세요.")
                    return@launch
                }

                // Step 2: 장중이면 실시간 수급 병합
                val mergedData = mergeIntradayIfAvailable(dailyData, ticker, apiConfig)

                // Step 3: 오실레이터 계산
                _uiState.value = OscillatorUiState.Loading("오실레이터 계산 중...")
                val result = calculateOscillator(mergedData, stockName, ticker, displayDays)

                // Step 4: 분석 기록 저장
                saveAnalysisHistoryUseCase(ticker, stockName)

                // Step 5: 결과 전달
                _uiState.value = result

                // Step 6: 현재 분석 정보 저장 (폴링용)
                currentAnalysisTicker = ticker
                currentAnalysisName = stockName
                currentDailyData = dailyData
                currentDisplayDays = displayDays

                // Step 7: 장중이면 자동 갱신 시작
                startAutoRefresh(ticker)

                // Step 8: 재무정보 비동기 수집 (오실레이터 결과와 독립)
                launch {
                    try {
                        val kisConfig = apiConfigProvider.getKisConfig()
                        if (kisConfig.isValid()) {
                            financialRepository.getFinancialData(ticker, stockName, kisConfig)
                            Timber.d("재무정보 수집 완료: %s", ticker)
                        }
                    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w("재무정보 수집 실패 (무시): %s", e.message)
                    }
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = OscillatorUiState.Error(e.toUserMessage())
            }
        }
    }

    /**
     * 장중이면 실시간 수급 데이터를 병합.
     * 장 마감 후에는 기존 데이터를 그대로 반환.
     */
    private suspend fun mergeIntradayIfAvailable(
        dailyData: List<DailyTrading>,
        ticker: String,
        apiConfig: KiwoomApiKeyConfig
    ): List<DailyTrading> {
        if (!TradingHours.isTradingHours()) {
            _isIntradayMerged.value = false
            return dailyData
        }

        return try {
            val result = repository.fetchRealtimeSupply(ticker, apiConfig, useCache = false)
            result.getOrNull()?.let { supplyData ->
                _isIntradayMerged.value = true
                Timber.d("장중 수급 병합: ticker=$ticker, netBuy=${supplyData.netBuyAmount}M₩")
                IntradayDataMerger.merge(dailyData, supplyData)
            } ?: dailyData
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("실시간 수급 병합 실패 (무시): ${e.message}")
            dailyData
        }
    }

    /**
     * 오실레이터 계산 공통 메서드.
     */
    private fun calculateOscillator(
        data: List<DailyTrading>,
        stockName: String,
        ticker: String,
        displayDays: Int
    ): OscillatorUiState.Success {
        val warmupCount = maxOf(0, data.size - displayDays)
        val oscillatorRows = calcOscillator.execute(data, warmupCount)
        val signals = calcOscillator.analyzeSignals(oscillatorRows)

        return OscillatorUiState.Success(
            chartData = ChartData(
                stockName = stockName,
                ticker = ticker,
                rows = oscillatorRows
            ),
            signals = signals,
            latestSignal = signals.lastOrNull(),
            isIntradayMerged = _isIntradayMerged.value
        )
    }

    /**
     * 60초 간격 자동 갱신 시작.
     * 장중(09:00~15:30)에만 실제 API 호출.
     */
    private fun startAutoRefresh(ticker: String) {
        stopAutoRefresh()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                if (!_autoRefreshEnabled.value) continue
                if (!TradingHours.isTradingHours()) {
                    if (_isIntradayMerged.value) {
                        // 장 마감 → 기존 일봉 데이터로 복원
                        _isIntradayMerged.value = false
                        currentDailyData?.let { data ->
                            val name = currentAnalysisName ?: return@let
                            val result = calculateOscillator(data, name, ticker, currentDisplayDays)
                            _uiState.value = result
                            Timber.d("장 마감 → 종가 데이터로 복원: $ticker")
                        }
                    }
                    continue
                }

                try {
                    val apiConfig = apiConfigProvider.getKiwoomConfig()
                    val dailyData = currentDailyData ?: continue
                    val mergedData = mergeIntradayIfAvailable(dailyData, ticker, apiConfig)
                    val name = currentAnalysisName ?: continue

                    val result = calculateOscillator(mergedData, name, ticker, currentDisplayDays)
                    _uiState.value = result
                    Timber.d("실시간 갱신 완료: $ticker")
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w("자동 갱신 실패 (무시): ${e.message}")
                }
            }
        }
    }

    private fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoRefresh()
    }

    private fun isValidTicker(ticker: String): Boolean = ticker.matches(Regex("^\\d{6}$"))

    companion object {
        private const val AUTO_REFRESH_INTERVAL_MS = 60_000L // 60초
    }
}

/** UI 상태 */
sealed class OscillatorUiState {
    data object Idle : OscillatorUiState()
    data class Loading(val message: String) : OscillatorUiState()
    data class Success(
        val chartData: ChartData,
        val signals: List<SignalAnalysis>,
        val latestSignal: SignalAnalysis?,
        val isIntradayMerged: Boolean = false
    ) : OscillatorUiState()
    data class Error(val message: String) : OscillatorUiState()
}

/** 종목 마스터 DB 상태 */
sealed class StockMasterStatus {
    data object Unknown : StockMasterStatus()
    data object Loading : StockMasterStatus()
    data class Ready(val count: Int) : StockMasterStatus()
    data class Error(val message: String) : StockMasterStatus()
}
