package com.tinyoscillator.presentation.marketanalysis

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.data.repository.FearGreedRepository
import com.tinyoscillator.domain.model.DemarkPeriodType
import com.tinyoscillator.domain.model.MarketDemarkChartData
import com.tinyoscillator.presentation.settings.loadKrxCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class MarketDemarkState {
    data object Idle : MarketDemarkState()
    data object Loading : MarketDemarkState()
    data class Success(val chartData: MarketDemarkChartData) : MarketDemarkState()
    data class Error(val message: String) : MarketDemarkState()
}

@HiltViewModel
class MarketDemarkViewModel @Inject constructor(
    application: Application,
    private val repository: FearGreedRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<MarketDemarkState>(MarketDemarkState.Idle)
    val state: StateFlow<MarketDemarkState> = _state.asStateFlow()

    private val _selectedMarket = MutableStateFlow("KOSPI")
    val selectedMarket: StateFlow<String> = _selectedMarket.asStateFlow()

    private val _selectedPeriod = MutableStateFlow(DemarkPeriodType.DAILY)
    val selectedPeriod: StateFlow<DemarkPeriodType> = _selectedPeriod.asStateFlow()

    fun selectMarket(market: String) {
        _selectedMarket.value = market
        loadData()
    }

    fun selectPeriod(periodType: DemarkPeriodType) {
        _selectedPeriod.value = periodType
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _state.value = MarketDemarkState.Loading

            val context = getApplication<Application>()
            val creds = loadKrxCredentials(context)
            if (creds.id.isBlank() || creds.password.isBlank()) {
                _state.value = MarketDemarkState.Error("KRX 자격증명이 설정되지 않았습니다. 설정에서 입력해주세요.")
                return@launch
            }

            val result = repository.getMarketDemarkData(
                market = _selectedMarket.value,
                days = 365,
                periodType = _selectedPeriod.value,
                krxId = creds.id,
                krxPassword = creds.password
            )

            result.fold(
                onSuccess = { chartData ->
                    _state.value = MarketDemarkState.Success(chartData)
                },
                onFailure = { e ->
                    _state.value = MarketDemarkState.Error("데이터 로드 실패: ${e.message}")
                }
            )
        }
    }
}
