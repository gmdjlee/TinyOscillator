package com.tinyoscillator.presentation.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.data.repository.MarketIndicatorRepository
import com.tinyoscillator.domain.model.OscillatorRangeOption
import com.tinyoscillator.domain.model.MarketOscillator
import com.tinyoscillator.domain.model.MarketOscillatorState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MarketOscillatorViewModel @Inject constructor(
    private val repository: MarketIndicatorRepository
) : ViewModel() {

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

    private val _lastUpdatedAt = MutableStateFlow<Long?>(null)
    val lastUpdatedAt: StateFlow<Long?> = _lastUpdatedAt.asStateFlow()

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
        try {
            val (startDate, endDate) = OscillatorRangeOption.calculateDateRange(range)
            repository.getDataByDateRange(market, startDate, endDate)
                .collect { data ->
                    _marketData.value = data
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = MarketOscillatorState.Error("데이터 로드 실패: ${e.message}")
        }
    }

    private fun checkData() {
        viewModelScope.launch {
            val kospiCount = repository.getDataCount("KOSPI")
            val kosdaqCount = repository.getDataCount("KOSDAQ")
            val hasData = kospiCount > 0 || kosdaqCount > 0
            val latestData = repository.getLatestData(_selectedMarket.value)
            _state.value = MarketOscillatorState.Idle(hasData, latestData?.date)
            _lastUpdatedAt.value = repository.getOscillatorLastUpdateTime()
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

    fun clearMessage() {
        if (_state.value is MarketOscillatorState.Success || _state.value is MarketOscillatorState.Error) {
            checkData()
        }
    }
}
