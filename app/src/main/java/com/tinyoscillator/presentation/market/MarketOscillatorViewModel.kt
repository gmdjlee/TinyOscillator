package com.tinyoscillator.presentation.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.data.repository.MarketIndicatorRepository
import com.tinyoscillator.domain.model.OscillatorRangeOption
import com.tinyoscillator.domain.model.MarketOscillator
import com.tinyoscillator.domain.model.MarketOscillatorState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MarketOscillatorViewModel @Inject constructor(
    private val repository: MarketIndicatorRepository
) : ViewModel() {

    companion object {
        private const val KRX_RATE_LIMIT_COOLDOWN_MS = 5000L
    }

    private val _state = MutableStateFlow<MarketOscillatorState>(MarketOscillatorState.Loading)
    val state: StateFlow<MarketOscillatorState> = _state.asStateFlow()

    private val _selectedMarket = MutableStateFlow("KOSPI")
    val selectedMarket: StateFlow<String> = _selectedMarket.asStateFlow()

    private val _selectedRange = MutableStateFlow(OscillatorRangeOption.DEFAULT)
    val selectedRange: StateFlow<OscillatorRangeOption> = _selectedRange.asStateFlow()

    private val _marketData = MutableStateFlow<List<MarketOscillator>>(emptyList())
    val marketData: StateFlow<List<MarketOscillator>> = _marketData.asStateFlow()

    private val _overboughtThreshold = MutableStateFlow(80.0)
    val overboughtThreshold: StateFlow<Double> = _overboughtThreshold.asStateFlow()

    private val _oversoldThreshold = MutableStateFlow(-80.0)
    val oversoldThreshold: StateFlow<Double> = _oversoldThreshold.asStateFlow()

    init {
        checkData()
        observeDateRangeChanges()
    }

    private fun observeDateRangeChanges() {
        viewModelScope.launch {
            combine(_selectedMarket, _selectedRange) { market, range ->
                Pair(market, range)
            }.collectLatest { (market, range) ->
                loadDataByRange(market, range)
            }
        }
    }

    private suspend fun loadDataByRange(market: String, range: OscillatorRangeOption) {
        val (startDate, endDate) = OscillatorRangeOption.calculateDateRange(range)
        repository.getDataByDateRange(market, startDate, endDate)
            .collect { data ->
                _marketData.value = data
            }
    }

    private fun checkData() {
        viewModelScope.launch {
            val kospiCount = repository.getDataCount("KOSPI")
            val kosdaqCount = repository.getDataCount("KOSDAQ")
            val hasData = kospiCount > 0 || kosdaqCount > 0
            val latestData = repository.getLatestData(_selectedMarket.value)
            _state.value = MarketOscillatorState.Idle(hasData, latestData?.date)
        }
    }

    fun onSelectedMarketChanged(market: String) {
        _selectedMarket.value = market
        checkData()
    }

    fun updateDateRange(option: OscillatorRangeOption) {
        _selectedRange.value = option
    }

    fun onOverboughtThresholdChanged(threshold: Double) {
        _overboughtThreshold.value = threshold
    }

    fun onOversoldThresholdChanged(threshold: Double) {
        _oversoldThreshold.value = threshold
    }

    /**
     * 초기 데이터 수집 (KOSPI → KOSDAQ 순차)
     */
    fun initialize(days: Int = 30) {
        viewModelScope.launch {
            _state.value = MarketOscillatorState.Initializing("시장 데이터 수집 중...", 0)

            val (kospiResult, kosdaqResult) = withContext(NonCancellable) {
                _state.value = MarketOscillatorState.Initializing("KOSPI 데이터 수집 중...", 25)
                val kospi = repository.initializeMarketData("KOSPI", days)

                _state.value = MarketOscillatorState.Initializing("KRX 서버 대기 중...", 45)
                delay(KRX_RATE_LIMIT_COOLDOWN_MS)

                _state.value = MarketOscillatorState.Initializing("KOSDAQ 데이터 수집 중...", 50)
                val kosdaq = repository.initializeMarketData("KOSDAQ", days)
                Pair(kospi, kosdaq)
            }

            if (kospiResult.isSuccess && kosdaqResult.isSuccess) {
                val kospiCount = kospiResult.getOrNull() ?: 0
                val kosdaqCount = kosdaqResult.getOrNull() ?: 0
                _state.value = MarketOscillatorState.Success(
                    "KOSPI: $kospiCount, KOSDAQ: $kosdaqCount 개의 데이터를 수집했습니다"
                )
                checkData()
            } else {
                val error = kospiResult.exceptionOrNull() ?: kosdaqResult.exceptionOrNull()
                _state.value = MarketOscillatorState.Error("데이터 수집 실패: ${error?.message}")
            }
        }
    }

    /**
     * 데이터 업데이트 (최근 30일)
     */
    fun update() {
        viewModelScope.launch {
            _state.value = MarketOscillatorState.Updating("시장 데이터 업데이트 중...")

            val (kospiResult, kosdaqResult) = withContext(NonCancellable) {
                val kospi = repository.updateMarketData("KOSPI")
                _state.value = MarketOscillatorState.Updating("KRX 서버 대기 중...")
                delay(KRX_RATE_LIMIT_COOLDOWN_MS)
                val kosdaq = repository.updateMarketData("KOSDAQ")
                Pair(kospi, kosdaq)
            }

            if (kospiResult.isSuccess && kosdaqResult.isSuccess) {
                val kospiCount = kospiResult.getOrNull() ?: 0
                val kosdaqCount = kosdaqResult.getOrNull() ?: 0
                _state.value = MarketOscillatorState.Success(
                    "KOSPI: $kospiCount, KOSDAQ: $kosdaqCount 개의 데이터를 업데이트했습니다"
                )
                checkData()
            } else {
                val error = kospiResult.exceptionOrNull() ?: kosdaqResult.exceptionOrNull()
                _state.value = MarketOscillatorState.Error("업데이트 실패: ${error?.message}")
            }
        }
    }

    fun clearMessage() {
        if (_state.value is MarketOscillatorState.Success || _state.value is MarketOscillatorState.Error) {
            checkData()
        }
    }
}
