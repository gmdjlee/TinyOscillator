package com.tinyoscillator.presentation.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.data.repository.MarketIndicatorRepository
import com.tinyoscillator.domain.model.DateRangeOption
import com.tinyoscillator.domain.model.MarketDepositChartData
import com.tinyoscillator.domain.model.MarketDepositState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MarketDepositViewModel @Inject constructor(
    private val repository: MarketIndicatorRepository
) : ViewModel() {

    private val _state = MutableStateFlow<MarketDepositState>(MarketDepositState.Idle)
    val state: StateFlow<MarketDepositState> = _state.asStateFlow()

    private val _selectedRange = MutableStateFlow(DateRangeOption.DEFAULT)
    val selectedRange: StateFlow<DateRangeOption> = _selectedRange.asStateFlow()

    private val _depositData = MutableStateFlow(MarketDepositChartData.empty())
    val depositData: StateFlow<MarketDepositChartData> = _depositData.asStateFlow()

    init {
        observeDateRangeChanges()
    }

    private fun observeDateRangeChanges() {
        viewModelScope.launch {
            _selectedRange.collectLatest { range ->
                loadDataByRange(range)
            }
        }
    }

    private suspend fun loadDataByRange(range: DateRangeOption) {
        try {
            val (startDate, endDate) = DateRangeOption.calculateDateRange(range)

            repository.getDepositsByDateRange(startDate, endDate)
                .collectLatest { deposits ->
                    if (deposits.isEmpty()) {
                        _state.value = MarketDepositState.Error("저장된 데이터가 없습니다.")
                        return@collectLatest
                    }

                    val marketData = MarketDepositChartData(
                        dates = deposits.map { it.date },
                        depositAmounts = deposits.map { it.depositAmount },
                        depositChanges = deposits.map { it.depositChange },
                        creditAmounts = deposits.map { it.creditAmount },
                        creditChanges = deposits.map { it.creditChange }
                    )

                    _depositData.value = marketData
                    _state.value = MarketDepositState.Success("데이터 로드 완료")
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = MarketDepositState.Error("데이터 로드 실패: ${e.message}")
        }
    }

    fun updateDateRange(option: DateRangeOption) {
        _selectedRange.value = option
    }

    fun refreshData() {
        val currentRange = _selectedRange.value
        _selectedRange.value = currentRange
    }

    fun clearMessage() {
        if (_state.value is MarketDepositState.Success) {
            _state.value = MarketDepositState.Idle
        }
    }
}
