package com.tinyoscillator.presentation.estimatedearnings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.data.repository.EstimatedEarningsRepository
import com.tinyoscillator.domain.model.EstimatedEarningsSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class EstimatedEarningsViewModel @Inject constructor(
    private val repository: EstimatedEarningsRepository,
    private val apiConfigProvider: ApiConfigProvider
) : ViewModel() {

    private val _summary = MutableStateFlow<EstimatedEarningsSummary?>(null)
    val summary: StateFlow<EstimatedEarningsSummary?> = _summary.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var currentTicker: String? = null

    fun loadData(ticker: String?) {
        if (ticker == null || ticker == currentTicker) return
        currentTicker = ticker
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val kisConfig = apiConfigProvider.getKisConfig()
                val result = repository.getEstimatedEarnings(ticker, kisConfig)
                result.onSuccess {
                    _summary.value = it
                }.onFailure {
                    Timber.w(it, "추정실적 로드 실패")
                    _error.value = it.message
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "추정실적 예외")
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        val ticker = currentTicker ?: return
        currentTicker = null
        loadData(ticker)
    }
}
