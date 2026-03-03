package com.tinyoscillator.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.core.database.dao.AnalysisHistoryDao
import com.tinyoscillator.core.database.entity.AnalysisHistoryEntity
import com.tinyoscillator.core.database.entity.StockMasterEntity
import com.tinyoscillator.core.network.NetworkUtils
import com.tinyoscillator.data.repository.FinancialRepository
import com.tinyoscillator.data.repository.StockMasterRepository
import com.tinyoscillator.data.repository.StockRepository
import com.tinyoscillator.domain.model.*
import com.tinyoscillator.domain.usecase.CalcOscillatorUseCase
import com.tinyoscillator.domain.usecase.SaveAnalysisHistoryUseCase
import com.tinyoscillator.domain.usecase.SearchStocksUseCase
import com.tinyoscillator.presentation.settings.loadKisConfig
import com.tinyoscillator.presentation.settings.loadKiwoomConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    private val financialRepository: FinancialRepository
) : AndroidViewModel(application) {

    private val config = OscillatorConfig()
    private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")

    private val _uiState = MutableStateFlow<OscillatorUiState>(OscillatorUiState.Idle)
    val uiState: StateFlow<OscillatorUiState> = _uiState.asStateFlow()

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

    @Volatile
    private var cachedApiConfig: KiwoomApiKeyConfig? = null
    private val configMutex = Mutex()

    init {
        // 앱 시작시 종목 마스터 DB 채우기
        viewModelScope.launch {
            try {
                val apiConfig = getApiConfig()
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

    /** API 키 설정 로드 (Mutex-protected to prevent race conditions) */
    private suspend fun getApiConfig(): KiwoomApiKeyConfig {
        cachedApiConfig?.let { return it }
        return configMutex.withLock {
            cachedApiConfig?.let { return@withLock it }
            val config = loadKiwoomConfig(getApplication())
            cachedApiConfig = config
            config
        }
    }

    /** API 키 캐시 초기화 (설정 변경 후) */
    fun invalidateApiConfig() {
        cachedApiConfig = null
        // 설정 변경 후 종목 마스터 재시도
        viewModelScope.launch {
            try {
                val apiConfig = getApiConfig()
                stockMasterRepository.populateIfEmpty(apiConfig)
                val count = stockMasterRepository.getCount()
                _stockMasterStatus.value = StockMasterStatus.Ready(count)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("설정 변경 후 종목 마스터 재시도 실패: %s", e.message)
            }
        }
    }

    /** 종목 마스터 DB 강제 새로고침 */
    fun refreshStockMaster() {
        viewModelScope.launch {
            _stockMasterStatus.value = StockMasterStatus.Loading
            try {
                val apiConfig = getApiConfig()
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
        viewModelScope.launch {
            _uiState.value = OscillatorUiState.Loading("데이터 수집 중...")

            try {
                if (!NetworkUtils.isNetworkAvailable(getApplication())) {
                    _uiState.value = OscillatorUiState.Error("네트워크에 연결되어 있지 않습니다. 인터넷 연결을 확인해주세요.")
                    return@launch
                }

                val apiConfig = getApiConfig()

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

                // Step 2: 오실레이터 계산
                _uiState.value = OscillatorUiState.Loading("오실레이터 계산 중...")
                val warmupCount = maxOf(0, dailyData.size - displayDays)
                Timber.d("━━━ ViewModel 파이프라인 ━━━")
                Timber.d("analysisDays=%d, displayDays=%d", analysisDays, displayDays)
                Timber.d("수집 기간: %s ~ %s", startDate.format(fmt), endDate.format(fmt))
                Timber.d("수집된 데이터: %d일, warmupCount=%d, 표시=%d일", dailyData.size, warmupCount, dailyData.size - warmupCount)
                val oscillatorRows = calcOscillator.execute(dailyData, warmupCount)

                // Step 3: 신호 분석
                val signals = calcOscillator.analyzeSignals(oscillatorRows)

                // Step 4: 분석 기록 저장
                saveAnalysisHistoryUseCase(ticker, stockName)

                // Step 5: 결과 전달
                _uiState.value = OscillatorUiState.Success(
                    chartData = ChartData(
                        stockName = stockName,
                        ticker = ticker,
                        rows = oscillatorRows
                    ),
                    signals = signals,
                    latestSignal = signals.lastOrNull()
                )

                // Step 6: 재무정보 비동기 수집 (오실레이터 결과와 독립)
                launch {
                    try {
                        val kisConfig = loadKisConfig(getApplication())
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
                val errorMsg = when (e) {
                    is ApiError.NoApiKeyError -> "API 키가 설정되지 않았습니다. 설정에서 API 키를 입력해주세요."
                    is ApiError.NetworkError -> "네트워크 연결을 확인해주세요."
                    is ApiError.TimeoutError -> "서버 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요."
                    is ApiError.AuthError -> "API 인증에 실패했습니다. API 키를 확인해주세요."
                    is kotlinx.coroutines.TimeoutCancellationException -> "분석 시간이 초과되었습니다. 잠시 후 다시 시도해주세요."
                    else -> "분석 실패: ${e.message ?: "알 수 없는 오류"}"
                }
                _uiState.value = OscillatorUiState.Error(errorMsg)
            }
        }
    }

    private fun isValidTicker(ticker: String): Boolean = ticker.matches(Regex("^\\d{6}$"))
}

/** UI 상태 */
sealed class OscillatorUiState {
    data object Idle : OscillatorUiState()
    data class Loading(val message: String) : OscillatorUiState()
    data class Success(
        val chartData: ChartData,
        val signals: List<SignalAnalysis>,
        val latestSignal: SignalAnalysis?
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
