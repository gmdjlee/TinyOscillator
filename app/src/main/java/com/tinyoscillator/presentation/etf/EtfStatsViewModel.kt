package com.tinyoscillator.presentation.etf

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.domain.model.AmountRankingItem
import com.tinyoscillator.domain.model.CashDepositRow
import com.tinyoscillator.domain.model.ChangeType
import com.tinyoscillator.domain.model.StockChange
import com.tinyoscillator.domain.model.StockInEtfRow
import com.tinyoscillator.domain.model.StockSearchResult
import com.tinyoscillator.domain.model.WeightTrend
import com.tinyoscillator.presentation.etf.stats.normalizeMarketCode
import com.tinyoscillator.presentation.settings.loadEtfKeywordFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import com.tinyoscillator.core.util.DateFormats
import java.time.temporal.WeekFields
import javax.inject.Inject

enum class ComparisonMode { DAILY, WEEKLY }

data class WeekInfo(
    val year: Int,
    val weekNumber: Int,
    val representativeDate: String,
    val dateRange: Pair<String, String>,
    val label: String
)

@HiltViewModel
class EtfStatsViewModel @Inject constructor(
    private val etfRepository: EtfRepository,
    private val stockMasterDao: StockMasterDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _dates = MutableStateFlow<List<String>>(emptyList())
    val dates: StateFlow<List<String>> = _dates.asStateFlow()

    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()

    private val _comparisonDate = MutableStateFlow<String?>(null)
    val comparisonDate: StateFlow<String?> = _comparisonDate.asStateFlow()

    private val _comparisonMode = MutableStateFlow(ComparisonMode.DAILY)
    val comparisonMode: StateFlow<ComparisonMode> = _comparisonMode.asStateFlow()

    private val _weeks = MutableStateFlow<List<WeekInfo>>(emptyList())
    val weeks: StateFlow<List<WeekInfo>> = _weeks.asStateFlow()

    private val _selectedWeek = MutableStateFlow<WeekInfo?>(null)
    val selectedWeek: StateFlow<WeekInfo?> = _selectedWeek.asStateFlow()

    private val _selectedComparisonWeek = MutableStateFlow<WeekInfo?>(null)
    val selectedComparisonWeek: StateFlow<WeekInfo?> = _selectedComparisonWeek.asStateFlow()

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

    private val _selectedStockMarket = MutableStateFlow<String?>(null)
    val selectedStockMarket: StateFlow<String?> = _selectedStockMarket.asStateFlow()

    private val _selectedStockSector = MutableStateFlow<String?>(null)
    val selectedStockSector: StateFlow<String?> = _selectedStockSector.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 금액 순위 탭 정렬 상태 (인코딩: "COLUMN:ORDER,..." 또는 빈 문자열) */
    private val _amountRankingSortEncoded = MutableStateFlow("")
    val amountRankingSortEncoded: StateFlow<String> = _amountRankingSortEncoded.asStateFlow()

    fun updateAmountRankingSort(encoded: String) {
        _amountRankingSortEncoded.value = encoded
    }

    /** 금액 순위 시장 필터 (null = 전체) */
    private val _selectedMarketFilter = MutableStateFlow<String?>(null)
    val selectedMarketFilter: StateFlow<String?> = _selectedMarketFilter.asStateFlow()

    /** 금액 순위 업종 필터 (null = 전체) */
    private val _selectedSectorFilter = MutableStateFlow<String?>(null)
    val selectedSectorFilter: StateFlow<String?> = _selectedSectorFilter.asStateFlow()

    /** 금액 순위 비중추이 필터 (null = 전체, NONE = 비중없음) */
    private val _selectedWeightTrendFilter = MutableStateFlow<WeightTrend?>(null)
    val selectedWeightTrendFilter: StateFlow<WeightTrend?> = _selectedWeightTrendFilter.asStateFlow()

    /** 필터 적용된 금액 순위 */
    val filteredAmountRanking: StateFlow<List<AmountRankingItem>> = combine(
        _amountRanking, _selectedMarketFilter, _selectedSectorFilter, _selectedWeightTrendFilter
    ) { items, market, sector, weightTrend ->
        items.filter { item ->
            (market == null || item.market == market) &&
                (sector == null || item.sector == sector) &&
                (weightTrend == null || item.maxWeightTrend == weightTrend)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 현재 시장 필터 적용 후 사용 가능한 업종 목록 */
    val availableSectors: StateFlow<List<String>> = combine(
        _amountRanking, _selectedMarketFilter
    ) { items, market ->
        items.filter { market == null || it.market == market }
            .mapNotNull { it.sector }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setMarketFilter(market: String?) {
        _selectedMarketFilter.value = market
        _selectedSectorFilter.value = null
    }

    fun setSectorFilter(sector: String?) {
        _selectedSectorFilter.value = sector
    }

    fun setWeightTrendFilter(trend: WeightTrend?) {
        _selectedWeightTrendFilter.value = trend
    }

    fun setComparisonMode(mode: ComparisonMode) {
        _comparisonMode.value = mode
        if (mode == ComparisonMode.WEEKLY) {
            val currentDate = _selectedDate.value
            val matchingWeek = _weeks.value.find { week ->
                currentDate != null && currentDate >= week.dateRange.first && currentDate <= week.dateRange.second
            }
            _selectedWeek.value = matchingWeek ?: _weeks.value.firstOrNull()
            _selectedDate.value = _selectedWeek.value?.representativeDate ?: _selectedDate.value
        }
        autoSelectComparisonIfNeeded()
        loadAllStats()
    }

    fun selectWeek(weekInfo: WeekInfo) {
        _selectedWeek.value = weekInfo
        _selectedDate.value = weekInfo.representativeDate
        autoSelectComparisonIfNeeded()
        loadAllStats()
    }

    /** 비교일 수동 선택 (일간 모드) */
    fun selectComparisonDate(date: String) {
        _comparisonDate.value = date
        loadAllStats()
    }

    /** 비교주 수동 선택 (주간 모드) */
    fun selectComparisonWeek(weekInfo: WeekInfo) {
        _selectedComparisonWeek.value = weekInfo
        _comparisonDate.value = weekInfo.representativeDate
        loadAllStats()
    }

    /** 초기 로드 또는 기준일 변경 시 비교일 자동 설정 (다음 날짜/주차) */
    private fun autoSelectComparisonIfNeeded() {
        when (_comparisonMode.value) {
            ComparisonMode.DAILY -> {
                val dates = _dates.value
                val selected = _selectedDate.value ?: return
                val idx = dates.indexOf(selected)
                if (idx < 0) return
                _comparisonDate.value = dates.getOrNull(idx + 1)
            }
            ComparisonMode.WEEKLY -> {
                val weeks = _weeks.value
                val selected = _selectedWeek.value ?: return
                val idx = weeks.indexOf(selected)
                if (idx < 0) return
                val compWeek = weeks.getOrNull(idx + 1)
                _selectedComparisonWeek.value = compWeek
                _comparisonDate.value = compWeek?.representativeDate
            }
        }
    }

    private var loadStatsJob: Job? = null
    private var excludedTickers: List<String> = emptyList()
    private var marketMap: Map<String, String> = emptyMap()
    private var sectorMap: Map<String, String> = emptyMap()

    init {
        loadExcludedTickersAndDates()
    }

    private fun loadExcludedTickersAndDates() {
        viewModelScope.launch {
            try {
                val keywords = loadEtfKeywordFilter(context)
                excludedTickers = etfRepository.getExcludedTickers(keywords.excludeKeywords)
                marketMap = stockMasterDao.getTickerMarketMap()
                    .associate { it.ticker to (normalizeMarketCode(it.market) ?: it.market) }
                sectorMap = stockMasterDao.getTickerSectorMap()
                    .associate { it.ticker to it.sector }
            } catch (e: CancellationException) {
                throw e
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
                _weeks.value = groupDatesByWeek(allDates)
                if (allDates.isNotEmpty()) {
                    _selectedDate.value = allDates.first() // most recent
                    _selectedWeek.value = _weeks.value.firstOrNull()
                    autoSelectComparisonIfNeeded()
                    loadAllStats()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load dates")
            }
        }
    }

    fun selectDate(date: String) {
        _selectedDate.value = date
        autoSelectComparisonIfNeeded()
        loadAllStats()
    }

    private fun loadAllStats() {
        val date = _selectedDate.value ?: return
        val compDate = _comparisonDate.value

        loadStatsJob?.cancel()
        loadStatsJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                // Load amount ranking
                _amountRanking.value = etfRepository.getEnrichedAmountRanking(date, compDate, excludedTickers)
                    .map { it.copy(market = marketMap[it.stockTicker], sector = sectorMap[it.stockTicker]) }

                // Load stock changes
                if (compDate != null) {
                    val changes = etfRepository.computeStockChanges(compDate, date, excludedTickers)
                        .map { it.copy(market = marketMap[it.stockTicker], sector = sectorMap[it.stockTicker]) }
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
                    .map { it.copy(market = marketMap[it.stock_ticker], sector = sectorMap[it.stock_ticker]) }
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
                _selectedStockMarket.value = marketMap[ticker]
                _selectedStockSector.value = sectorMap[ticker]
                _stockSearchResults.value = emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to analyze stock")
            }
        }
    }
}

private val DATE_FMT = DateFormats.yyyyMMdd

private fun buildWeekLabel(weekNumber: Int, dateRange: Pair<String, String>): String {
    fun shortDate(d: String) = "${d.substring(4, 6)}.${d.substring(6, 8)}"
    return "${weekNumber}주차 (${shortDate(dateRange.first)}~${shortDate(dateRange.second)})"
}

internal fun groupDatesByWeek(dates: List<String>): List<WeekInfo> {
    if (dates.isEmpty()) return emptyList()
    val wf = WeekFields.of(java.util.Locale.KOREA)
    return dates
        .groupBy { d ->
            val ld = LocalDate.parse(d, DATE_FMT)
            ld.get(wf.weekBasedYear()) to ld.get(wf.weekOfWeekBasedYear())
        }
        .map { (yw, datesInWeek) ->
            val range = datesInWeek.last() to datesInWeek.first()
            WeekInfo(yw.first, yw.second, datesInWeek.first(), range, buildWeekLabel(yw.second, range))
        }
        .sortedByDescending { it.year * 100 + it.weekNumber }
}
