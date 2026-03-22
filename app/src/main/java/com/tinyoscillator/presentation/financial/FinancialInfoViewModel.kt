package com.tinyoscillator.presentation.financial

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.core.network.NetworkUtils
import com.tinyoscillator.data.repository.FinancialRepository
import com.tinyoscillator.domain.model.FinancialState
import com.tinyoscillator.domain.model.FinancialSummary
import com.tinyoscillator.domain.model.FinancialTab
import com.tinyoscillator.domain.model.toSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FinancialInfoViewModel @Inject constructor(
    application: Application,
    private val financialRepository: FinancialRepository,
    private val apiConfigProvider: ApiConfigProvider
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<FinancialState>(FinancialState.NoStock)
    val state: StateFlow<FinancialState> = _state.asStateFlow()

    private val _selectedTab = MutableStateFlow(FinancialTab.PROFITABILITY)
    val selectedTab: StateFlow<FinancialTab> = _selectedTab.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    @Volatile
    private var currentTicker: String? = null
    @Volatile
    private var currentName: String? = null

    fun selectTab(tab: FinancialTab) {
        _selectedTab.value = tab
    }

    fun loadForStock(ticker: String, name: String) {
        // Skip only if already loading the same ticker (prevents duplicate requests)
        if (ticker == currentTicker && _state.value is FinancialState.Loading) return
        currentTicker = ticker
        currentName = name
        // Repository's TTL cache handles freshness — always call to let it decide
        loadFinancialData(ticker, name, forceRefresh = false)
    }

    fun refresh() {
        val ticker = currentTicker ?: return
        val name = currentName ?: return
        _isRefreshing.value = true
        val previousState = _state.value
        viewModelScope.launch {
            try {
                val kisConfig = apiConfigProvider.getKisConfig()
                val result = financialRepository.refreshFinancialData(ticker, name, kisConfig)
                handleResult(result.map { it.toSummary() })
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Preserve previous Success state if refresh fails
                if (previousState is FinancialState.Success) {
                    _state.value = previousState
                } else {
                    _state.value = FinancialState.Error(
                        e.message ?: "알 수 없는 오류가 발생했습니다."
                    )
                }
            } finally {
                _isRefreshing.value = false
            }
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
            try {
                _state.value = FinancialState.Loading
                if (!NetworkUtils.isNetworkAvailable(getApplication())) {
                    _state.value = FinancialState.Error("네트워크에 연결되어 있지 않습니다. 인터넷 연결을 확인해주세요.")
                    return@launch
                }
                val kisConfig = apiConfigProvider.getKisConfig()
                val result = if (forceRefresh) {
                    financialRepository.refreshFinancialData(ticker, name, kisConfig)
                } else {
                    financialRepository.getFinancialData(ticker, name, kisConfig)
                }
                handleResult(result.map { it.toSummary() })
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = FinancialState.Error(
                    e.message ?: "알 수 없는 오류가 발생했습니다."
                )
            }
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
                when (error) {
                    is ApiError.NoApiKeyError, is ApiError.AuthError -> FinancialState.NoApiKey
                    is ApiError.NetworkError -> FinancialState.Error("네트워크 연결을 확인해주세요.")
                    is ApiError.TimeoutError -> FinancialState.Error("서버 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.")
                    is IllegalStateException -> if (error.message?.contains("API key") == true) {
                        FinancialState.NoApiKey
                    } else {
                        FinancialState.Error(error.message ?: "알 수 없는 오류가 발생했습니다.")
                    }
                    else -> FinancialState.Error(
                        error.message ?: "알 수 없는 오류가 발생했습니다."
                    )
                }
            }
        )
    }
}
