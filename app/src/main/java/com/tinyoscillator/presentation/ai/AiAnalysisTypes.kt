package com.tinyoscillator.presentation.ai

import com.tinyoscillator.domain.model.DemarkTDRow
import com.tinyoscillator.domain.model.FinancialData
import com.tinyoscillator.domain.model.OscillatorRow
import com.tinyoscillator.domain.model.SignalAnalysis
import com.tinyoscillator.domain.model.StatisticalResult
import com.tinyoscillator.domain.model.StockAggregatedTimePoint

enum class AiTab(val label: String) {
    MARKET("시장지표"),
    STOCK("종목"),
    PROBABILITY("확률분석")
}

data class SelectedStockInfo(
    val ticker: String,
    val name: String,
    val market: String?,
    val sector: String?
)

sealed class StockDataState {
    data object Idle : StockDataState()
    data object Loading : StockDataState()
    data class Loaded(
        val oscillatorRows: List<OscillatorRow>,
        val signals: List<SignalAnalysis>,
        val demarkRows: List<DemarkTDRow>,
        val financialData: FinancialData?,
        val etfAggregated: List<StockAggregatedTimePoint>
    ) : StockDataState()
    data class Error(val message: String) : StockDataState()
}

sealed class ProbabilityAnalysisState {
    data object Idle : ProbabilityAnalysisState()
    data class Computing(val message: String) : ProbabilityAnalysisState()
    data class Success(val result: StatisticalResult) : ProbabilityAnalysisState()
    data class Error(val message: String) : ProbabilityAnalysisState()
}

enum class InterpretationProvider(val label: String) {
    LOCAL("로컬 분석"),
    AI("AI 분석")
}

sealed class InterpretationState {
    data object Idle : InterpretationState()
    data object Loading : InterpretationState()
    data class Success(
        val summary: String,
        val engineInterpretations: Map<String, String>,
        val provider: InterpretationProvider
    ) : InterpretationState()
    data class Error(val message: String) : InterpretationState()
    data object NoApiKey : InterpretationState()
}
