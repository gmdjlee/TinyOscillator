package com.tinyoscillator.presentation.etf

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.domain.model.DateRange
import com.tinyoscillator.domain.model.StockAggregatedTimePoint
import com.tinyoscillator.presentation.settings.loadEtfKeywordFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AggregatedStockTrendViewModel @Inject constructor(
    private val etfRepository: EtfRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val stockTicker: String = savedStateHandle["stockTicker"] ?: ""

    private val _stockName = MutableStateFlow<String?>(null)
    val stockName: StateFlow<String?> = _stockName.asStateFlow()

    private val _allData = MutableStateFlow<List<StockAggregatedTimePoint>>(emptyList())

    private val _filteredData = MutableStateFlow<List<StockAggregatedTimePoint>>(emptyList())
    val filteredData: StateFlow<List<StockAggregatedTimePoint>> = _filteredData.asStateFlow()

    private val _selectedRange = MutableStateFlow(DateRange.ALL)
    val selectedRange: StateFlow<DateRange> = _selectedRange.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                _stockName.value = etfRepository.getStockName(stockTicker)

                val keywords = loadEtfKeywordFilter(context)
                val excludedTickers = etfRepository.getExcludedTickers(keywords.excludeKeywords)
                val data = etfRepository.getStockAggregatedTrend(stockTicker, excludedTickers)
                _allData.value = data
                applyFilter()
            } catch (e: Exception) {
                Timber.e(e, "Failed to load aggregated stock trend")
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
            val cutoff = getCutoffDate(range.days)
            _filteredData.value = all.filter { it.date >= cutoff }
        }
    }

    private fun getCutoffDate(daysBack: Int): String {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.KOREA)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysBack)
        return sdf.format(cal.time)
    }

    fun getStockTicker(): String = stockTicker
}
