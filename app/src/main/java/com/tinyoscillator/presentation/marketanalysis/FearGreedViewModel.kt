package com.tinyoscillator.presentation.marketanalysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.data.repository.FearGreedRepository
import com.tinyoscillator.domain.model.FearGreedChartData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

sealed class FearGreedState {
    data object Idle : FearGreedState()
    data object Loading : FearGreedState()
    data class Success(val chartData: FearGreedChartData) : FearGreedState()
    data class Error(val message: String) : FearGreedState()
}

enum class FearGreedDateRange(val label: String, val days: Long) {
    ONE_MONTH("1M", 30),
    THREE_MONTHS("3M", 90),
    SIX_MONTHS("6M", 180),
    ONE_YEAR("1Y", 365),
    TWO_YEARS("2Y", 730),
    ALL("전체", 0)
}

@HiltViewModel
class FearGreedViewModel @Inject constructor(
    private val repository: FearGreedRepository
) : ViewModel() {

    private val _state = MutableStateFlow<FearGreedState>(FearGreedState.Loading)
    val state: StateFlow<FearGreedState> = _state.asStateFlow()

    private val _selectedMarket = MutableStateFlow("KOSPI")
    val selectedMarket: StateFlow<String> = _selectedMarket.asStateFlow()

    private val _selectedRange = MutableStateFlow(FearGreedDateRange.ONE_YEAR)
    val selectedRange: StateFlow<FearGreedDateRange> = _selectedRange.asStateFlow()

    private val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE

    init {
        checkData()
    }

    private fun checkData() {
        viewModelScope.launch {
            val count = repository.getCountByMarket("KOSPI") + repository.getCountByMarket("KOSDAQ")
            if (count == 0) {
                _state.value = FearGreedState.Idle
            } else {
                loadData()
            }
        }
    }

    fun selectMarket(market: String) {
        _selectedMarket.value = market
        loadData()
    }

    fun selectDateRange(range: FearGreedDateRange) {
        _selectedRange.value = range
        loadData()
    }

    fun update() {
        checkData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _state.value = FearGreedState.Loading
            val market = _selectedMarket.value
            val range = _selectedRange.value

            val endDate = LocalDate.now().format(isoFmt)
            val startDate = if (range.days > 0) {
                LocalDate.now().minusDays(range.days).format(isoFmt)
            } else {
                "2000-01-01"
            }

            repository.getChartData(market, startDate, endDate)
                .collect { chartData ->
                    if (chartData.rows.isEmpty()) {
                        val count = repository.getCountByMarket(market)
                        if (count == 0) {
                            _state.value = FearGreedState.Idle
                        } else {
                            _state.value = FearGreedState.Error("선택한 기간에 데이터가 없습니다")
                        }
                    } else {
                        _state.value = FearGreedState.Success(chartData)
                    }
                }
        }
    }
}
