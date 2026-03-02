package com.tinyoscillator.presentation.financial

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.data.repository.FinancialRepository
import com.tinyoscillator.domain.model.FinancialState
import com.tinyoscillator.domain.model.FinancialSummary
import com.tinyoscillator.domain.model.FinancialTab
import com.tinyoscillator.domain.model.toSummary
import com.tinyoscillator.presentation.settings.loadKisConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FinancialInfoViewModel @Inject constructor(
    application: Application,
    private val financialRepository: FinancialRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<FinancialState>(FinancialState.NoStock)
    val state: StateFlow<FinancialState> = _state.asStateFlow()

    private val _selectedTab = MutableStateFlow(FinancialTab.PROFITABILITY)
    val selectedTab: StateFlow<FinancialTab> = _selectedTab.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var currentTicker: String? = null
    private var currentName: String? = null

    fun selectTab(tab: FinancialTab) {
        _selectedTab.value = tab
    }

    fun loadForStock(ticker: String, name: String) {
        if (ticker == currentTicker && _state.value is FinancialState.Success) return
        currentTicker = ticker
        currentName = name
        loadFinancialData(ticker, name, forceRefresh = false)
    }

    fun refresh() {
        val ticker = currentTicker ?: return
        val name = currentName ?: return
        _isRefreshing.value = true
        viewModelScope.launch {
            val kisConfig = loadKisConfig(getApplication())
            val result = financialRepository.refreshFinancialData(ticker, name, kisConfig)
            handleResult(result.map { it.toSummary() })
            _isRefreshing.value = false
        }
    }

    fun retry() {
        val ticker = currentTicker ?: return
        val name = currentName ?: return
        loadFinancialData(ticker, name, forceRefresh = true)
    }

    fun clearStock() {
        currentTicker = null
        currentName = null
        _state.value = FinancialState.NoStock
    }

    private fun loadFinancialData(ticker: String, name: String, forceRefresh: Boolean) {
        viewModelScope.launch {
            _state.value = FinancialState.Loading
            val kisConfig = loadKisConfig(getApplication())
            val result = if (forceRefresh) {
                financialRepository.refreshFinancialData(ticker, name, kisConfig)
            } else {
                financialRepository.getFinancialData(ticker, name, kisConfig)
            }
            handleResult(result.map { it.toSummary() })
        }
    }

    private fun handleResult(result: Result<FinancialSummary>) {
        _state.value = result.fold(
            onSuccess = { summary ->
                if (summary.periods.isEmpty()) {
                    FinancialState.Error("재무정보를 찾을 수 없습니다.")
                } else {
                    FinancialState.Success(summary)
                }
            },
            onFailure = { error ->
                when {
                    error.message?.contains("API key") == true -> FinancialState.NoApiKey
                    error.message?.contains("network", ignoreCase = true) == true ->
                        FinancialState.Error("네트워크 연결을 확인해주세요.")
                    else -> FinancialState.Error(
                        error.message ?: "알 수 없는 오류가 발생했습니다."
                    )
                }
            }
        )
    }
}
