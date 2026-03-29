package com.tinyoscillator.presentation.portfolio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.core.database.entity.PortfolioEntity
import com.tinyoscillator.core.database.entity.PortfolioHoldingEntity
import com.tinyoscillator.core.database.entity.PortfolioTransactionEntity
import com.tinyoscillator.data.repository.PortfolioRepository
import com.tinyoscillator.domain.model.PortfolioUiState
import com.tinyoscillator.domain.model.TransactionItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    application: Application,
    private val portfolioRepository: PortfolioRepository,
    private val stockMasterDao: StockMasterDao,
    private val apiConfigProvider: ApiConfigProvider
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<PortfolioUiState>(PortfolioUiState.Idle)
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    private val _portfolioId = MutableStateFlow<Long?>(null)
    val portfolioId: StateFlow<Long?> = _portfolioId.asStateFlow()

    private val _portfolio = MutableStateFlow<PortfolioEntity?>(null)
    val portfolio: StateFlow<PortfolioEntity?> = _portfolio.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Search
    private val _searchQuery = MutableStateFlow("")

    @Suppress("OPT_IN_USAGE")
    val searchResults = _searchQuery
        .debounce(200)
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList())
            else stockMasterDao.searchStocks(query)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Transaction detail
    private val _selectedHoldingId = MutableStateFlow<Long?>(null)
    val selectedHoldingId: StateFlow<Long?> = _selectedHoldingId.asStateFlow()

    private val _transactions = MutableStateFlow<List<TransactionItem>>(emptyList())
    val transactions: StateFlow<List<TransactionItem>> = _transactions.asStateFlow()

    private val _selectedHoldingName = MutableStateFlow("")
    val selectedHoldingName: StateFlow<String> = _selectedHoldingName.asStateFlow()

    private val _selectedHoldingCurrentPrice = MutableStateFlow(0L)
    val selectedHoldingCurrentPrice: StateFlow<Long> = _selectedHoldingCurrentPrice.asStateFlow()

    init {
        loadDefaultPortfolio()
    }

    private fun loadDefaultPortfolio() {
        viewModelScope.launch {
            try {
                val id = portfolioRepository.ensureDefaultPortfolio()
                _portfolioId.value = id
                _portfolio.value = portfolioRepository.getPortfolio(id)
                loadPortfolio()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "기본 포트폴리오 로딩 실패")
                _uiState.value = PortfolioUiState.Error("포트폴리오 로딩 실패: ${e.message}")
            }
        }
    }

    fun loadPortfolio() {
        val id = _portfolioId.value ?: return
        val maxWeight = _portfolio.value?.maxWeightPercent ?: 30
        val totalAssets = _portfolio.value?.totalAmountLimit

        viewModelScope.launch {
            _uiState.value = PortfolioUiState.Loading("포트폴리오 로딩 중...")
            try {
                val (summary, holdings) = portfolioRepository.loadPortfolioHoldings(id, maxWeight, totalAssets)
                _uiState.value = PortfolioUiState.Success(summary, holdings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "포트폴리오 로딩 실패")
                _uiState.value = PortfolioUiState.Error("로딩 실패: ${e.message}")
            }
        }
    }

    fun refreshPrices() {
        val id = _portfolioId.value ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val config = apiConfigProvider.getKiwoomConfig()
                if (!config.isValid()) {
                    _uiState.value = PortfolioUiState.Error("Kiwoom API 키가 설정되지 않았습니다. 설정에서 API 키를 입력해주세요.")
                    _isRefreshing.value = false
                    return@launch
                }
                portfolioRepository.refreshCurrentPrices(id, config)
                loadPortfolio()
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "가격 갱신 실패")
                _uiState.value = PortfolioUiState.Error("가격 갱신 실패: ${e.message}")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun searchStock(query: String) {
        _searchQuery.value = query
    }

    fun addHolding(
        ticker: String,
        stockName: String,
        market: String,
        sector: String,
        shares: Int,
        pricePerShare: Int,
        date: String,
        memo: String,
        targetPrice: Int = 0
    ) {
        val portfolioId = _portfolioId.value ?: return
        viewModelScope.launch {
            try {
                val holdingId = portfolioRepository.insertHolding(
                    PortfolioHoldingEntity(
                        portfolioId = portfolioId,
                        ticker = ticker,
                        stockName = stockName,
                        market = market,
                        sector = sector,
                        targetPrice = targetPrice
                    )
                )
                portfolioRepository.insertTransaction(
                    PortfolioTransactionEntity(
                        holdingId = holdingId,
                        date = date,
                        shares = shares,
                        pricePerShare = pricePerShare,
                        memo = memo
                    )
                )
                loadPortfolio()
                // Auto-fetch current price for the newly added holding
                try {
                    val config = apiConfigProvider.getKiwoomConfig()
                    if (config.isValid()) {
                        portfolioRepository.fetchAndUpdatePrice(holdingId, ticker, config)
                        loadPortfolio()
                    }
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w("신규 종목 현재가 조회 실패: $ticker - ${e.message}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "종목 추가 실패")
            }
        }
    }

    fun addTransaction(
        holdingId: Long,
        shares: Int,
        pricePerShare: Int,
        date: String,
        memo: String
    ) {
        viewModelScope.launch {
            try {
                portfolioRepository.insertTransaction(
                    PortfolioTransactionEntity(
                        holdingId = holdingId,
                        date = date,
                        shares = shares,
                        pricePerShare = pricePerShare,
                        memo = memo
                    )
                )
                loadPortfolio()
                // Refresh transaction list if viewing this holding
                if (_selectedHoldingId.value == holdingId) {
                    loadTransactions(holdingId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "거래 추가 실패")
            }
        }
    }

    fun updateHolding(
        holdingId: Long,
        stockName: String,
        market: String,
        sector: String,
        targetPrice: Int
    ) {
        viewModelScope.launch {
            try {
                portfolioRepository.updateHoldingInfo(holdingId, stockName, market, sector, targetPrice)
                loadPortfolio()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "종목 수정 실패")
            }
        }
    }

    fun deleteHolding(holdingId: Long) {
        viewModelScope.launch {
            try {
                portfolioRepository.deleteHoldingWithTransactions(holdingId)
                loadPortfolio()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "종목 삭제 실패")
            }
        }
    }

    fun updateTransaction(
        transactionId: Long,
        shares: Int,
        pricePerShare: Int,
        date: String,
        memo: String
    ) {
        viewModelScope.launch {
            try {
                portfolioRepository.updateTransaction(transactionId, date, shares, pricePerShare, memo)
                loadPortfolio()
                _selectedHoldingId.value?.let { loadTransactions(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "거래 수정 실패")
            }
        }
    }

    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            try {
                portfolioRepository.deleteTransaction(transactionId)
                loadPortfolio()
                _selectedHoldingId.value?.let { loadTransactions(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "거래 삭제 실패")
            }
        }
    }

    fun selectHolding(holdingId: Long, stockName: String, currentPrice: Long) {
        _selectedHoldingId.value = holdingId
        _selectedHoldingName.value = stockName
        _selectedHoldingCurrentPrice.value = currentPrice
        loadTransactions(holdingId)
    }

    fun clearSelectedHolding() {
        _selectedHoldingId.value = null
        _transactions.value = emptyList()
    }

    private fun loadTransactions(holdingId: Long) {
        viewModelScope.launch {
            try {
                val currentPrice = _selectedHoldingCurrentPrice.value
                _transactions.value = portfolioRepository.getTransactionItems(holdingId, currentPrice)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "거래 내역 로딩 실패")
            }
        }
    }

    fun updatePortfolioSettings(name: String, maxWeightPercent: Int, totalAmountLimit: Long?) {
        val current = _portfolio.value ?: return
        viewModelScope.launch {
            try {
                val updated = current.copy(
                    name = name,
                    maxWeightPercent = maxWeightPercent,
                    totalAmountLimit = totalAmountLimit
                )
                portfolioRepository.updatePortfolio(updated)
                _portfolio.value = updated
                loadPortfolio()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "설정 저장 실패")
            }
        }
    }

}
