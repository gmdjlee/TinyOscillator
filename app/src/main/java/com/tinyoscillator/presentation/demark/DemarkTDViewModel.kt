package com.tinyoscillator.presentation.demark

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.core.network.NetworkUtils
import com.tinyoscillator.data.repository.StockRepository
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.DemarkPeriodType
import com.tinyoscillator.domain.model.DemarkTDChartData
import com.tinyoscillator.domain.model.OscillatorConfig
import com.tinyoscillator.domain.usecase.CalcDemarkTDUseCase
import com.tinyoscillator.presentation.settings.loadKiwoomConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

sealed class DemarkTDState {
    data object NoStock : DemarkTDState()
    data object Loading : DemarkTDState()
    data class Success(val chartData: DemarkTDChartData) : DemarkTDState()
    data class Error(val message: String) : DemarkTDState()
}

@HiltViewModel
class DemarkTDViewModel @Inject constructor(
    application: Application,
    private val repository: StockRepository,
    private val calcDemarkTD: CalcDemarkTDUseCase
) : AndroidViewModel(application) {

    private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")

    private val _state = MutableStateFlow<DemarkTDState>(DemarkTDState.NoStock)
    val state: StateFlow<DemarkTDState> = _state.asStateFlow()

    private val _selectedPeriod = MutableStateFlow(DemarkPeriodType.DAILY)
    val selectedPeriod: StateFlow<DemarkPeriodType> = _selectedPeriod.asStateFlow()

    @Volatile
    private var currentTicker: String? = null
    @Volatile
    private var currentName: String? = null
    @Volatile
    private var cachedDailyData: List<DailyTrading>? = null
    @Volatile
    private var cachedApiConfig: KiwoomApiKeyConfig? = null
    private val configMutex = Mutex()

    fun loadForStock(ticker: String, stockName: String) {
        if (ticker == currentTicker && _state.value is DemarkTDState.Loading) return
        currentTicker = ticker
        currentName = stockName
        cachedDailyData = null
        loadData(ticker, stockName)
    }

    fun selectPeriod(periodType: DemarkPeriodType) {
        if (periodType == _selectedPeriod.value) return
        _selectedPeriod.value = periodType

        val dailyData = cachedDailyData
        val ticker = currentTicker
        val name = currentName
        if (dailyData != null && ticker != null && name != null) {
            recalculate(ticker, name, dailyData, periodType)
        }
    }

    fun clearStock() {
        currentTicker = null
        currentName = null
        cachedDailyData = null
        _state.value = DemarkTDState.NoStock
    }

    private suspend fun getApiConfig(): KiwoomApiKeyConfig {
        cachedApiConfig?.let { return it }
        return configMutex.withLock {
            cachedApiConfig?.let { return@withLock it }
            val config = loadKiwoomConfig(getApplication())
            cachedApiConfig = config
            config
        }
    }

    private fun loadData(ticker: String, stockName: String) {
        viewModelScope.launch {
            try {
                _state.value = DemarkTDState.Loading

                if (!NetworkUtils.isNetworkAvailable(getApplication())) {
                    _state.value = DemarkTDState.Error("네트워크에 연결되어 있지 않습니다.")
                    return@launch
                }

                val apiConfig = getApiConfig()
                val endDate = LocalDate.now()
                val startDate = endDate.minusDays(OscillatorConfig.DEFAULT_ANALYSIS_DAYS.toLong())

                val dailyData = repository.getDailyTradingData(
                    ticker = ticker,
                    startDate = startDate.format(fmt),
                    endDate = endDate.format(fmt),
                    config = apiConfig
                )

                if (dailyData.isEmpty()) {
                    _state.value = DemarkTDState.Error("데이터가 없습니다. 종목코드를 확인해주세요.")
                    return@launch
                }

                cachedDailyData = dailyData
                val periodType = _selectedPeriod.value
                val rows = calcDemarkTD.execute(dailyData, periodType)

                _state.value = DemarkTDState.Success(
                    DemarkTDChartData(
                        stockName = stockName,
                        ticker = ticker,
                        rows = rows,
                        periodType = periodType
                    )
                )
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _state.value = DemarkTDState.Error(e.message ?: "계산 오류가 발생했습니다.")
            } catch (e: Exception) {
                val errorMsg = when (e) {
                    is ApiError.NoApiKeyError -> "API 키가 설정되지 않았습니다."
                    is ApiError.NetworkError -> "네트워크 연결을 확인해주세요."
                    is ApiError.TimeoutError -> "서버 응답 시간이 초과되었습니다."
                    else -> "분석 실패: ${e.message ?: "알 수 없는 오류"}"
                }
                _state.value = DemarkTDState.Error(errorMsg)
            }
        }
    }

    private fun recalculate(
        ticker: String,
        stockName: String,
        dailyData: List<DailyTrading>,
        periodType: DemarkPeriodType
    ) {
        viewModelScope.launch {
            try {
                val rows = calcDemarkTD.execute(dailyData, periodType)
                _state.value = DemarkTDState.Success(
                    DemarkTDChartData(
                        stockName = stockName,
                        ticker = ticker,
                        rows = rows,
                        periodType = periodType
                    )
                )
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _state.value = DemarkTDState.Error(e.message ?: "계산 오류가 발생했습니다.")
            }
        }
    }
}
