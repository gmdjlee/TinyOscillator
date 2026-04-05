package com.tinyoscillator.presentation.comparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.core.database.entity.StockMasterEntity
import com.tinyoscillator.core.util.KoreanUtils
import com.tinyoscillator.domain.model.ComparisonData
import com.tinyoscillator.domain.model.ComparisonPeriod
import com.tinyoscillator.domain.usecase.BuildComparisonUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ComparisonUiState {
    data object Idle : ComparisonUiState
    data object Loading : ComparisonUiState
    data class Success(val data: ComparisonData) : ComparisonUiState
    data class Error(val message: String) : ComparisonUiState
}

@HiltViewModel
class ComparisonViewModel @Inject constructor(
    private val buildComparison: BuildComparisonUseCase,
    private val masterDao: StockMasterDao,
) : ViewModel() {

    private val _ticker = MutableStateFlow("")
    private val _selectedPeriod = MutableStateFlow(ComparisonPeriod.THREE_MONTHS)
    val selectedPeriod: StateFlow<ComparisonPeriod> = _selectedPeriod.asStateFlow()

    // 검색
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<StockMasterEntity>> =
        _searchQuery
            .debounce(300)
            .flatMapLatest { query ->
                flow {
                    if (query.isBlank()) {
                        emit(emptyList())
                    } else if (KoreanUtils.isChosungOnly(query)) {
                        emit(masterDao.searchByChosung(query))
                    } else {
                        emit(masterDao.searchByText(query))
                    }
                }.flowOn(Dispatchers.IO)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val comparisonState: StateFlow<ComparisonUiState> =
        kotlinx.coroutines.flow.combine(_ticker, _selectedPeriod) { t, p -> t to p }
            .debounce(200)
            .flatMapLatest { (ticker, period) ->
                flow {
                    if (ticker.isBlank()) {
                        emit(ComparisonUiState.Idle)
                        return@flow
                    }
                    emit(ComparisonUiState.Loading)
                    try {
                        val data = buildComparison(ticker, period)
                        if (data.targetSeries.returns.isEmpty()) {
                            emit(ComparisonUiState.Error("가격 데이터가 없습니다. 먼저 종목분석에서 데이터를 수집하세요."))
                        } else {
                            emit(ComparisonUiState.Success(data))
                        }
                    } catch (e: Exception) {
                        emit(ComparisonUiState.Error(e.message ?: "비교 데이터 로드 실패"))
                    }
                }.flowOn(Dispatchers.IO)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ComparisonUiState.Idle)

    fun setTicker(ticker: String) {
        _ticker.value = ticker
        _searchQuery.value = ""
    }

    fun setPeriod(period: ComparisonPeriod) {
        _selectedPeriod.value = period
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun retry() {
        val current = _ticker.value
        _ticker.value = ""
        viewModelScope.launch {
            kotlinx.coroutines.delay(100)
            _ticker.value = current
        }
    }
}
