package com.tinyoscillator.presentation.consensus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.data.repository.ConsensusRepository
import com.tinyoscillator.domain.model.ConsensusChartData
import com.tinyoscillator.domain.model.ConsensusReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConsensusViewModel @Inject constructor(
    private val consensusRepository: ConsensusRepository
) : ViewModel() {

    private val _chartData = MutableStateFlow<ConsensusChartData?>(null)
    val chartData: StateFlow<ConsensusChartData?> = _chartData.asStateFlow()

    private val _reports = MutableStateFlow<List<ConsensusReport>>(emptyList())
    val reports: StateFlow<List<ConsensusReport>> = _reports.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentTicker: String? = null

    fun loadData(ticker: String?, stockName: String?) {
        if (ticker == null || ticker == currentTicker) return
        currentTicker = ticker
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _reports.value = consensusRepository.getReportsByTicker(ticker)
                _chartData.value = consensusRepository.getConsensusChartData(ticker, stockName ?: ticker)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _reports.value = emptyList()
                _chartData.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }
}
