package com.tinyoscillator.presentation.investopinion

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.data.repository.InvestOpinionRepository
import com.tinyoscillator.domain.model.InvestOpinionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InvestOpinionViewModel @Inject constructor(
    private val repository: InvestOpinionRepository,
    private val apiConfigProvider: ApiConfigProvider
) : ViewModel() {

    private val _summary = MutableStateFlow<InvestOpinionSummary?>(null)
    val summary: StateFlow<InvestOpinionSummary?> = _summary.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var currentTicker: String? = null

    fun loadData(ticker: String?, stockName: String?) {
        Log.w("InvestOpinionVM", "loadData called: ticker=$ticker, current=$currentTicker")
        if (ticker == null || ticker == currentTicker) return
        currentTicker = ticker
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val kisConfig = apiConfigProvider.getKisConfig()
                Log.w("InvestOpinionVM", "kisConfig valid=${kisConfig.isValid()}")
                val result = repository.getInvestOpinions(ticker, stockName ?: ticker, kisConfig)
                result.onSuccess {
                    Log.w("InvestOpinionVM", "success: ${it.opinions.size} opinions")
                    _summary.value = it
                }.onFailure {
                    Log.w("InvestOpinionVM", "failure: ${it.message}")
                    _error.value = it.message
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("InvestOpinionVM", "exception", e)
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        val ticker = currentTicker ?: return
        currentTicker = null
        loadData(ticker, _summary.value?.stockName)
    }
}
