package com.tinyoscillator.presentation.etf

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.domain.model.DateRange
import com.tinyoscillator.domain.model.HoldingTimeSeries
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class StockTrendViewModel @Inject constructor(
    private val etfRepository: EtfRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val etfTicker: String = savedStateHandle["etfTicker"] ?: ""
    private val stockTicker: String = savedStateHandle["stockTicker"] ?: ""

    private val _stockName = MutableStateFlow<String?>(null)
    val stockName: StateFlow<String?> = _stockName.asStateFlow()

    private val _etfName = MutableStateFlow<String?>(null)
    val etfName: StateFlow<String?> = _etfName.asStateFlow()

    private val _allData = MutableStateFlow<List<HoldingTimeSeries>>(emptyList())

    private val _filteredData = MutableStateFlow<List<HoldingTimeSeries>>(emptyList())
    val filteredData: StateFlow<List<HoldingTimeSeries>> = _filteredData.asStateFlow()

    private val _selectedRange = MutableStateFlow(DateRange.ALL)
    val selectedRange: StateFlow<DateRange> = _selectedRange.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                _stockName.value = etfRepository.getStockName(stockTicker)
                _etfName.value = etfRepository.getEtf(etfTicker)?.name
                val data = etfRepository.getStockTrendInEtf(etfTicker, stockTicker)
                _allData.value = data
                applyFilter()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load stock trend")
            }
        }
    }

    fun selectRange(range: DateRange) {
        _selectedRange.value = range
        applyFilter()
    }

    private fun applyFilter() {
        val all = _allData.value
        val range = _selectedRange.value
        if (range == DateRange.ALL) {
            _filteredData.value = all
        } else {
            val cutoff = range.getCutoffDate()
            _filteredData.value = all.filter { it.date >= cutoff }
        }
    }

    fun getStockTicker(): String = stockTicker
    fun getEtfTicker(): String = etfTicker
}
