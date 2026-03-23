package com.tinyoscillator.presentation.report

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.data.repository.ConsensusRepository
import com.tinyoscillator.domain.model.ConsensusFilter
import com.tinyoscillator.domain.model.ConsensusFilterOptions
import com.tinyoscillator.domain.model.ConsensusReport
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val consensusRepository: ConsensusRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _reports = MutableStateFlow<List<ConsensusReport>>(emptyList())
    val reports: StateFlow<List<ConsensusReport>> = _reports.asStateFlow()

    private val _filter = MutableStateFlow(ConsensusFilter())
    val filter: StateFlow<ConsensusFilter> = _filter.asStateFlow()

    private val _filterOptions = MutableStateFlow(ConsensusFilterOptions())
    val filterOptions: StateFlow<ConsensusFilterOptions> = _filterOptions.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _reportCount = MutableStateFlow(0)
    val reportCount: StateFlow<Int> = _reportCount.asStateFlow()

    init {
        viewModelScope.launch {
            _isLoading.value = true
            consensusRepository.seedFromJson(context)
            loadFilterOptions()
            loadReports()
            _isLoading.value = false
        }
    }

    fun updateFilter(newFilter: ConsensusFilter) {
        _filter.value = newFilter
        viewModelScope.launch {
            _isLoading.value = true
            loadReports()
            _isLoading.value = false
        }
    }

    fun clearFilter() {
        updateFilter(ConsensusFilter())
    }

    private suspend fun loadReports() {
        val result = consensusRepository.getReports(_filter.value)
        _reports.value = result
        _reportCount.value = result.size
    }

    private suspend fun loadFilterOptions() {
        _filterOptions.value = consensusRepository.getFilterOptions()
    }
}
