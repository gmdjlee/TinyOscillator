package com.tinyoscillator.presentation.quickanalysis

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.api.toUserMessage
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.core.network.NetworkUtils
import com.tinyoscillator.core.util.DateFormats
import com.tinyoscillator.data.repository.StockRepository
import com.tinyoscillator.domain.model.CrossSignal
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.DemarkPeriodType
import com.tinyoscillator.domain.model.OscillatorConfig
import com.tinyoscillator.domain.model.Trend
import com.tinyoscillator.domain.usecase.CalcDemarkTDUseCase
import com.tinyoscillator.domain.usecase.CalcOscillatorUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

/** 퀵 분석 요약 — 종목 리스트에서 바텀시트로 보여줄 핵심 지표 */
data class QuickAnalysisSummary(
    val ticker: String,
    val stockName: String,
    val date: String,           // 기준일 "yyyyMMdd" (최근 거래일)
    val closePrice: Int,        // 종가 (원)
    val changePct: Double?,     // 전일 대비 등락률 (%), 계산 불가 시 null
    val oscillator: Double,     // 수급 오실레이터 (차트 표시값은 ×100 %)
    val trend: Trend,
    val crossSignal: CrossSignal?,
    val tdBuyCount: Int,        // DeMark TD Buy 카운트 (9+ 신호)
    val tdSellCount: Int        // DeMark TD Sell 카운트 (9+ 신호)
)

sealed class QuickAnalysisState {
    data object Loading : QuickAnalysisState()
    data class Success(val summary: QuickAnalysisSummary) : QuickAnalysisState()
    data class Error(val message: String) : QuickAnalysisState()
}

/**
 * 종목 퀵 분석 ViewModel.
 *
 * ETF 구성종목/리포트 리스트에서 종목 탭 시 바텀시트에 오실레이터·DeMark TD·종가
 * 요약을 제공. 데이터 범위는 종목분석 탭과 동일(DEFAULT_ANALYSIS_DAYS)하게 수집해
 * incremental cache를 공유 — 이후 전체 분석 이동 시 API 재호출 최소화.
 */
@HiltViewModel
class QuickAnalysisViewModel @Inject constructor(
    application: Application,
    private val repository: StockRepository,
    private val calcOscillator: CalcOscillatorUseCase,
    private val calcDemarkTD: CalcDemarkTDUseCase,
    private val apiConfigProvider: ApiConfigProvider
) : AndroidViewModel(application) {

    private val fmt = DateFormats.yyyyMMdd

    private val _state = MutableStateFlow<QuickAnalysisState>(QuickAnalysisState.Loading)
    val state: StateFlow<QuickAnalysisState> = _state.asStateFlow()

    private var currentTicker: String? = null

    /** 같은 종목 재요청(시트 재오픈)은 기존 결과 유지, 실패 상태면 재시도 */
    fun load(ticker: String, stockName: String) {
        if (ticker == currentTicker && _state.value !is QuickAnalysisState.Error) return
        currentTicker = ticker

        viewModelScope.launch {
            _state.value = QuickAnalysisState.Loading
            try {
                if (!NetworkUtils.isNetworkAvailable(getApplication())) {
                    _state.value = QuickAnalysisState.Error("네트워크에 연결되어 있지 않습니다.")
                    return@launch
                }

                val apiConfig = apiConfigProvider.getKiwoomConfig()
                val endDate = LocalDate.now()
                val startDate = endDate.minusDays(OscillatorConfig.DEFAULT_ANALYSIS_DAYS.toLong())

                val dailyData = repository.getDailyTradingData(
                    ticker = ticker,
                    startDate = startDate.format(fmt),
                    endDate = endDate.format(fmt),
                    config = apiConfig
                )

                if (dailyData.isEmpty()) {
                    _state.value = QuickAnalysisState.Error(
                        "해당 기간에 거래 데이터가 없습니다. " +
                        "신규 상장 종목이거나 휴장일인 경우 데이터가 제공되지 않을 수 있습니다."
                    )
                    return@launch
                }

                _state.value = QuickAnalysisState.Success(buildSummary(ticker, stockName, dailyData))
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _state.value = QuickAnalysisState.Error(e.message ?: "계산 오류가 발생했습니다.")
            } catch (e: Exception) {
                Timber.w(e, "퀵 분석 실패: %s (%s)", ticker, stockName)
                _state.value = QuickAnalysisState.Error(e.toUserMessage())
            }
        }
    }

    private fun buildSummary(
        ticker: String,
        stockName: String,
        dailyData: List<DailyTrading>
    ): QuickAnalysisSummary {
        val warmupCount = maxOf(0, dailyData.size - OscillatorConfig.DEFAULT_DISPLAY_DAYS)
        val oscillatorRows = calcOscillator.execute(dailyData, warmupCount)
        val latestSignal = calcOscillator.analyzeSignals(oscillatorRows).lastOrNull()

        val latestTd = calcDemarkTD.execute(dailyData, DemarkPeriodType.DAILY).lastOrNull()

        val last = dailyData.last()
        val prevClose = dailyData.getOrNull(dailyData.size - 2)?.closePrice
        val changePct = if (prevClose != null && prevClose > 0 && last.closePrice > 0) {
            (last.closePrice - prevClose) * 100.0 / prevClose
        } else null

        return QuickAnalysisSummary(
            ticker = ticker,
            stockName = stockName,
            date = last.date,
            closePrice = last.closePrice,
            changePct = changePct,
            oscillator = latestSignal?.oscillator ?: oscillatorRows.last().oscillator,
            trend = latestSignal?.trend ?: Trend.NEUTRAL,
            crossSignal = latestSignal?.crossSignal,
            tdBuyCount = latestTd?.tdBuyCount ?: 0,
            tdSellCount = latestTd?.tdSellCount ?: 0
        )
    }
}
