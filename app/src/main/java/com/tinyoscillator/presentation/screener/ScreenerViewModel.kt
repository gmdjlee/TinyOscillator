package com.tinyoscillator.presentation.screener

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.data.datasource.ScreenerDataSource
import com.tinyoscillator.data.preferences.ScreenerFilterPreferences
import com.tinyoscillator.domain.model.ScreenerFilter
import com.tinyoscillator.domain.model.ScreenerResultItem
import com.tinyoscillator.domain.model.ScreenerSortKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScreenerViewModel @Inject constructor(
    private val screenerDataSource: ScreenerDataSource,
    private val filterPrefs: ScreenerFilterPreferences,
    private val masterDao: StockMasterDao,
) : ViewModel() {

    val savedFilter: StateFlow<ScreenerFilter> = filterPrefs.filter
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenerFilter())

    private val _currentFilter = MutableStateFlow(ScreenerFilter())
    val currentFilter: StateFlow<ScreenerFilter> = _currentFilter.asStateFlow()

    private val _sortKey = MutableStateFlow(ScreenerSortKey.SIGNAL_SCORE)
    val sortKey: StateFlow<ScreenerSortKey> = _sortKey.asStateFlow()

    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val screenerState: StateFlow<ScreenerUiState> =
        combine(_currentFilter, _sortKey) { filter, sort -> filter to sort }
            .debounce(500)
            .flatMapLatest { (filter, sort) ->
                flow {
                    emit(ScreenerUiState.Loading)
                    try {
                        val results = screenerDataSource.runScreener(filter, sort)
                        emit(ScreenerUiState.Success(results, filter))
                    } catch (e: Exception) {
                        emit(ScreenerUiState.Error(e.message ?: "스크리너 실행 실패"))
                    }
                }.flowOn(Dispatchers.IO)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenerUiState.Idle)

    val sectors: StateFlow<List<String>> = flow {
        emit(masterDao.getAllSectors())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // 저장된 필터 복원
        viewModelScope.launch {
            filterPrefs.filter.collect { saved ->
                _currentFilter.value = saved
            }
        }
    }

    fun updateFilter(filter: ScreenerFilter) {
        _currentFilter.value = filter
    }

    fun updateSort(sort: ScreenerSortKey) {
        _sortKey.value = sort
    }

    fun saveFilter() = viewModelScope.launch {
        filterPrefs.save(_currentFilter.value)
    }

    fun resetFilter() {
        _currentFilter.value = ScreenerFilter()
        viewModelScope.launch { filterPrefs.reset() }
    }
}

sealed interface ScreenerUiState {
    data object Idle : ScreenerUiState
    data object Loading : ScreenerUiState
    data class Success(
        val items: List<ScreenerResultItem>,
        val appliedFilter: ScreenerFilter,
    ) : ScreenerUiState
    data class Error(val message: String) : ScreenerUiState
}
