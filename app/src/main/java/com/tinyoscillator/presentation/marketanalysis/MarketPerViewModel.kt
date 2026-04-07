package com.tinyoscillator.presentation.marketanalysis

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.data.repository.MarketPerRepository
import com.tinyoscillator.domain.model.MarketPerChartData
import com.tinyoscillator.domain.model.MarketPerDateRange
import com.tinyoscillator.presentation.settings.loadKrxCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

sealed class MarketPerState {
    data object Idle : MarketPerState()
    data object Loading : MarketPerState()
    data class Success(val chartData: MarketPerChartData) : MarketPerState()
    data class Error(val message: String) : MarketPerState()
}

@HiltViewModel
class MarketPerViewModel @Inject constructor(
    application: Application,
    private val repository: MarketPerRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<MarketPerState>(MarketPerState.Idle)
    val state: StateFlow<MarketPerState> = _state.asStateFlow()

    private val _selectedMarket = MutableStateFlow("KOSPI")
    val selectedMarket: StateFlow<String> = _selectedMarket.asStateFlow()

    private val _selectedRange = MutableStateFlow(MarketPerDateRange.ONE_YEAR)
    val selectedRange: StateFlow<MarketPerDateRange> = _selectedRange.asStateFlow()

    private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")
    private var loadJob: Job? = null

    init {
        loadData()
    }

    fun selectMarket(market: String) {
        _selectedMarket.value = market
        loadData()
    }

    fun selectDateRange(range: MarketPerDateRange) {
        _selectedRange.value = range
        loadData()
    }

    fun refresh() {
        loadData()
    }

    private fun loadData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = MarketPerState.Loading

            val context = getApplication<Application>()
            val creds = loadKrxCredentials(context)
            if (creds.id.isBlank() || creds.password.isBlank()) {
                _state.value = MarketPerState.Error("KRX 자격증명이 설정되지 않았습니다.\n설정에서 입력해주세요.")
                return@launch
            }

            try {
                val market = _selectedMarket.value
                val range = _selectedRange.value

                val endDate = LocalDate.now().format(fmt)
                val startDate = LocalDate.now().minusDays(range.days).format(fmt)

                val chartData = repository.getMarketPerHistory(
                    market = market,
                    startDate = startDate,
                    endDate = endDate,
                    krxId = creds.id,
                    krxPassword = creds.password
                )
                if (chartData.rows.isEmpty()) {
                    _state.value = MarketPerState.Idle
                } else {
                    _state.value = MarketPerState.Success(chartData)
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = MarketPerState.Error(e.message ?: "데이터 로드 실패")
            }
        }
    }
}
