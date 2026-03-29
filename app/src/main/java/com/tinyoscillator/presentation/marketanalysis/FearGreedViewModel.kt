package com.tinyoscillator.presentation.marketanalysis

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.data.repository.FearGreedRepository
import com.tinyoscillator.domain.model.FearGreedChartData
import com.tinyoscillator.presentation.settings.loadKrxCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

sealed class FearGreedState {
    data object Idle : FearGreedState()
    data object Loading : FearGreedState()
    data class Initializing(val message: String, val progress: Int) : FearGreedState()
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
    application: Application,
    private val repository: FearGreedRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<FearGreedState>(FearGreedState.Loading)
    val state: StateFlow<FearGreedState> = _state.asStateFlow()

    private val _selectedMarket = MutableStateFlow("KOSPI")
    val selectedMarket: StateFlow<String> = _selectedMarket.asStateFlow()

    private val _selectedRange = MutableStateFlow(FearGreedDateRange.ONE_YEAR)
    val selectedRange: StateFlow<FearGreedDateRange> = _selectedRange.asStateFlow()

    private val _showInitDialog = MutableStateFlow(false)
    val showInitDialog: StateFlow<Boolean> = _showInitDialog.asStateFlow()

    private val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE

    init {
        checkData()
    }

    private fun checkData() {
        viewModelScope.launch {
            val count = repository.getCountByMarket("KOSPI") + repository.getCountByMarket("KOSDAQ")
            if (count == 0) {
                _showInitDialog.value = true
                _state.value = FearGreedState.Idle
            } else {
                _showInitDialog.value = false
                loadData()
            }
        }
    }

    fun dismissInitDialog() {
        _showInitDialog.value = false
        _state.value = FearGreedState.Idle
    }

    fun selectMarket(market: String) {
        _selectedMarket.value = market
        loadData()
    }

    fun selectDateRange(range: FearGreedDateRange) {
        _selectedRange.value = range
        loadData()
    }

    fun initialize(days: Int = 365) {
        viewModelScope.launch {
            _showInitDialog.value = false
            _state.value = FearGreedState.Initializing("초기화 준비 중...", 0)

            val context = getApplication<Application>()
            val creds = loadKrxCredentials(context)
            if (creds.id.isBlank() || creds.password.isBlank()) {
                _state.value = FearGreedState.Error("KRX 자격증명이 설정되지 않았습니다. 설정에서 입력해주세요.")
                return@launch
            }

            val result = repository.initializeFearGreed(
                days = days,
                krxId = creds.id,
                krxPassword = creds.password,
                onProgress = { msg, pct ->
                    _state.value = FearGreedState.Initializing(msg, pct)
                }
            )

            result.fold(
                onSuccess = { count ->
                    Timber.i("Fear & Greed 초기화 완료: ${count}건")
                    loadData()
                },
                onFailure = { e ->
                    _state.value = FearGreedState.Error("초기화 실패: ${e.message}")
                }
            )
        }
    }

    fun update() {
        viewModelScope.launch {
            _state.value = FearGreedState.Loading

            val context = getApplication<Application>()
            val creds = loadKrxCredentials(context)
            if (creds.id.isBlank() || creds.password.isBlank()) {
                _state.value = FearGreedState.Error("KRX 자격증명이 설정되지 않았습니다.")
                return@launch
            }

            val result = repository.updateFearGreed(creds.id, creds.password)
            result.fold(
                onSuccess = { loadData() },
                onFailure = { e -> _state.value = FearGreedState.Error("업데이트 실패: ${e.message}") }
            )
        }
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
                            _showInitDialog.value = true
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
