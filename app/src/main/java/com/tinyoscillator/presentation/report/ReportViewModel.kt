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

    private val _pagedReports = MutableStateFlow<List<ConsensusReport>>(emptyList())
    val pagedReports: StateFlow<List<ConsensusReport>> = _pagedReports.asStateFlow()

    private val _filter = MutableStateFlow(ConsensusFilter())
    val filter: StateFlow<ConsensusFilter> = _filter.asStateFlow()

    private val _filterOptions = MutableStateFlow(ConsensusFilterOptions())
    val filterOptions: StateFlow<ConsensusFilterOptions> = _filterOptions.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _totalPages = MutableStateFlow(0)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private var allReports: List<ConsensusReport> = emptyList()
    private var pageSize: Int = DEFAULT_PAGE_SIZE

    init {
        viewModelScope.launch {
            _isLoading.value = true
            consensusRepository.seedFromJson(context)
            loadFilterOptions()
            loadReports()
            _isLoading.value = false
        }
    }

    fun setPageSize(size: Int) {
        val clamped = size.coerceAtLeast(1)
        if (clamped == pageSize) return
        pageSize = clamped
        recalculatePages()
    }

    fun updateFilter(newFilter: ConsensusFilter) {
        _filter.value = newFilter
        viewModelScope.launch {
            _isLoading.value = true
            _currentPage.value = 0
            loadReports()
            _isLoading.value = false
        }
    }

    fun clearFilter() {
        updateFilter(ConsensusFilter())
    }

    fun goToPage(page: Int) {
        val clamped = page.coerceIn(0, (_totalPages.value - 1).coerceAtLeast(0))
        _currentPage.value = clamped
        updatePagedReports()
    }

    fun nextPage() = goToPage(_currentPage.value + 1)
    fun prevPage() = goToPage(_currentPage.value - 1)

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            loadFilterOptions()
            loadReports()
            _isLoading.value = false
        }
    }

    private suspend fun loadReports() {
        allReports = consensusRepository.getReports(_filter.value)
        recalculatePages()
    }

    private fun recalculatePages() {
        _totalCount.value = allReports.size
        _totalPages.value = if (allReports.isEmpty()) 0
            else (allReports.size + pageSize - 1) / pageSize
        _currentPage.value = _currentPage.value.coerceIn(0, (_totalPages.value - 1).coerceAtLeast(0))
        updatePagedReports()
    }

    private fun updatePagedReports() {
        if (allReports.isEmpty()) {
            _pagedReports.value = emptyList()
            return
        }
        val start = _currentPage.value * pageSize
        val end = (start + pageSize).coerceAtMost(allReports.size)
        _pagedReports.value = allReports.subList(start, end)
    }

    private suspend fun loadFilterOptions() {
        _filterOptions.value = consensusRepository.getFilterOptions()
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 15
    }
}
