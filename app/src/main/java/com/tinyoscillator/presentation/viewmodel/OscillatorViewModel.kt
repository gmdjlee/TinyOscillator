package com.tinyoscillator.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.data.repository.StockRepository
import com.tinyoscillator.data.repository.StockSearchResult
import com.tinyoscillator.domain.model.*
import com.tinyoscillator.domain.usecase.CalcOscillatorUseCase
import com.tinyoscillator.presentation.settings.loadKiwoomConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 수급 오실레이터 ViewModel
 *
 * 전체 파이프라인:
 * 1. API 키 설정 로드 (DataStore)
 * 2. 종목 검색 (Kiwoom API)
 * 3. 데이터 수집 (StockRepository → Kiwoom API)
 * 4. 오실레이터 계산 (CalcOscillatorUseCase)
 * 5. UI State 업데이트
 */
class OscillatorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StockRepository()
    private val calcOscillator = CalcOscillatorUseCase()
    private val config = OscillatorConfig()
    private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")

    private val _uiState = MutableStateFlow<OscillatorUiState>(OscillatorUiState.Idle)
    val uiState: StateFlow<OscillatorUiState> = _uiState.asStateFlow()

    private val _searchResults = MutableStateFlow<List<StockSearchResult>>(emptyList())
    val searchResults: StateFlow<List<StockSearchResult>> = _searchResults.asStateFlow()

    private var searchJob: Job? = null
    private var cachedApiConfig: KiwoomApiKeyConfig? = null

    /** API 키 설정 로드 */
    private suspend fun getApiConfig(): KiwoomApiKeyConfig {
        cachedApiConfig?.let { return it }
        val config = loadKiwoomConfig(getApplication())
        cachedApiConfig = config
        return config
    }

    /** API 키 캐시 초기화 (설정 변경 후) */
    fun invalidateApiConfig() {
        cachedApiConfig = null
    }

    /** 종목 검색 (300ms 디바운스) */
    fun searchStock(query: String) {
        if (query.isBlank()) {
            searchJob?.cancel()
            _searchResults.value = emptyList()
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            try {
                val apiConfig = getApiConfig()
                _searchResults.value = repository.searchStock(query, apiConfig)
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            }
        }
    }

    /** 오실레이터 분석 실행 */
    fun analyze(
        ticker: String,
        stockName: String,
        analysisDays: Int = OscillatorConfig.DEFAULT_ANALYSIS_DAYS,
        displayDays: Int = OscillatorConfig.DEFAULT_DISPLAY_DAYS
    ) {
        viewModelScope.launch {
            _uiState.value = OscillatorUiState.Loading("데이터 수집 중...")

            try {
                val apiConfig = getApiConfig()

                // 기간 설정
                val endDate = LocalDate.now()
                val startDate = endDate.minusDays(analysisDays.toLong())

                // Step 1: 데이터 수집 (Kiwoom API)
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
                val oscillatorRows = calcOscillator.execute(dailyData, warmupCount)

                // Step 3: 신호 분석
                val signals = calcOscillator.analyzeSignals(oscillatorRows)

                // Step 4: 결과 전달
                _uiState.value = OscillatorUiState.Success(
                    chartData = ChartData(
                        stockName = stockName,
                        ticker = ticker,
                        rows = oscillatorRows
                    ),
                    signals = signals,
                    latestSignal = signals.lastOrNull()
                )
            } catch (e: Exception) {
                _uiState.value = OscillatorUiState.Error(
                    "분석 실패: ${e.message ?: "알 수 없는 오류"}"
                )
            }
        }
    }
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
