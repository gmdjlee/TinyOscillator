package com.tinyoscillator.presentation.etf

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.domain.model.AmountRankingItem
import com.tinyoscillator.domain.model.CashDepositRow
import com.tinyoscillator.domain.model.ChangeType
import com.tinyoscillator.domain.model.StockChange
import com.tinyoscillator.domain.model.StockInEtfRow
import com.tinyoscillator.domain.model.StockSearchResult
import com.tinyoscillator.presentation.settings.loadEtfKeywordFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class EtfStatsViewModel @Inject constructor(
    private val etfRepository: EtfRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _dates = MutableStateFlow<List<String>>(emptyList())
    val dates: StateFlow<List<String>> = _dates.asStateFlow()

    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()

    private val _comparisonDate = MutableStateFlow<String?>(null)
    val comparisonDate: StateFlow<String?> = _comparisonDate.asStateFlow()

    private val _amountRanking = MutableStateFlow<List<AmountRankingItem>>(emptyList())
    val amountRanking: StateFlow<List<AmountRankingItem>> = _amountRanking.asStateFlow()

    private val _newStocks = MutableStateFlow<List<StockChange>>(emptyList())
    val newStocks: StateFlow<List<StockChange>> = _newStocks.asStateFlow()

    private val _removedStocks = MutableStateFlow<List<StockChange>>(emptyList())
    val removedStocks: StateFlow<List<StockChange>> = _removedStocks.asStateFlow()

    private val _increasedStocks = MutableStateFlow<List<StockChange>>(emptyList())
    val increasedStocks: StateFlow<List<StockChange>> = _increasedStocks.asStateFlow()

    private val _decreasedStocks = MutableStateFlow<List<StockChange>>(emptyList())
    val decreasedStocks: StateFlow<List<StockChange>> = _decreasedStocks.asStateFlow()

    private val _cashDeposit = MutableStateFlow<List<CashDepositRow>>(emptyList())
    val cashDeposit: StateFlow<List<CashDepositRow>> = _cashDeposit.asStateFlow()

    private val _stockSearchResults = MutableStateFlow<List<StockSearchResult>>(emptyList())
    val stockSearchResults: StateFlow<List<StockSearchResult>> = _stockSearchResults.asStateFlow()

    private val _stockAnalysis = MutableStateFlow<List<StockInEtfRow>>(emptyList())
    val stockAnalysis: StateFlow<List<StockInEtfRow>> = _stockAnalysis.asStateFlow()

    private val _selectedStockName = MutableStateFlow<String?>(null)
    val selectedStockName: StateFlow<String?> = _selectedStockName.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var excludedTickers: List<String> = emptyList()

    init {
        loadExcludedTickersAndDates()
    }

    private fun loadExcludedTickersAndDates() {
        viewModelScope.launch {
            try {
                val keywords = loadEtfKeywordFilter(context)
                excludedTickers = etfRepository.getExcludedTickers(keywords.excludeKeywords)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load excluded tickers")
            }
            loadDates()
        }
    }

    private fun loadDates() {
        viewModelScope.launch {
            try {
                val allDates = etfRepository.getAllDates()
                _dates.value = allDates
                if (allDates.isNotEmpty()) {
                    _selectedDate.value = allDates.first() // most recent
                    _comparisonDate.value = allDates.getOrNull(1) // second most recent
                    loadAllStats()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load dates")
            }
        }
    }

    fun selectDate(date: String) {
        val dates = _dates.value
        val idx = dates.indexOf(date)
        _selectedDate.value = date
        _comparisonDate.value = dates.getOrNull(idx + 1)
        loadAllStats()
    }

    private fun loadAllStats() {
        val date = _selectedDate.value ?: return
        val compDate = _comparisonDate.value

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Load amount ranking
                _amountRanking.value = etfRepository.getEnrichedAmountRanking(date, compDate, excludedTickers)

                // Load stock changes
                if (compDate != null) {
                    val changes = etfRepository.computeStockChanges(compDate, date, excludedTickers)
                    _newStocks.value = changes.filter { it.changeType == ChangeType.NEW }
                        .sortedByDescending { it.currentAmount }
                    _removedStocks.value = changes.filter { it.changeType == ChangeType.REMOVED }
                        .sortedByDescending { it.previousAmount }
                    _increasedStocks.value = changes.filter { it.changeType == ChangeType.INCREASED }
                        .sortedByDescending { (it.currentWeight ?: 0.0) - (it.previousWeight ?: 0.0) }
                    _decreasedStocks.value = changes.filter { it.changeType == ChangeType.DECREASED }
                        .sortedBy { (it.currentWeight ?: 0.0) - (it.previousWeight ?: 0.0) }
                } else {
                    _newStocks.value = emptyList()
                    _removedStocks.value = emptyList()
                    _increasedStocks.value = emptyList()
                    _decreasedStocks.value = emptyList()
                }

                // Load cash deposit trend
                _cashDeposit.value = etfRepository.getCashDepositTrend(excludedTickers)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load stats")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchStock(query: String) {
        val date = _selectedDate.value ?: return
        if (query.length < 2) {
            _stockSearchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                _stockSearchResults.value = etfRepository.searchStocksInHoldings(date, query, excludedTickers)
            } catch (e: Exception) {
                Timber.e(e, "Failed to search stocks")
            }
        }
    }

    fun analyzeStock(ticker: String) {
        val date = _selectedDate.value ?: return
        viewModelScope.launch {
            try {
                val results = etfRepository.getEtfsHoldingStock(ticker, date, excludedTickers)
                _stockAnalysis.value = results
                _selectedStockName.value = results.firstOrNull()?.stock_name
                _stockSearchResults.value = emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to analyze stock")
            }
        }
    }
}
