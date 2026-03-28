package com.tinyoscillator.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.data.local.llm.CachedModel
import com.tinyoscillator.data.local.llm.ModelManager
import com.tinyoscillator.domain.model.AnalysisState
import com.tinyoscillator.domain.model.StatisticalResult
import com.tinyoscillator.domain.model.StockAnalysis
import com.tinyoscillator.domain.repository.LlmRepository
import com.tinyoscillator.domain.usecase.AnalyzeStockProbabilityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 확률적 주식 분석 ViewModel
 *
 * UI 상태 관리:
 * - 분석 파이프라인 상태 (Loading → Computing → LLM → Streaming → Success/Error)
 * - 스트리밍 텍스트
 * - 모델 로드 상태
 */
@HiltViewModel
class StockAnalysisViewModel @Inject constructor(
    private val analyzeUseCase: AnalyzeStockProbabilityUseCase,
    private val llmRepository: LlmRepository,
    private val modelManager: ModelManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    init {
        viewModelScope.launch {
            llmRepository.isModelLoaded.collect { loaded ->
                _isModelLoaded.value = loaded
            }
        }
    }

    /**
     * 주식 분석 실행
     */
    fun analyzeStock(stockCode: String) {
        viewModelScope.launch {
            _uiState.value = AnalysisUiState.Loading
            _streamingText.value = ""

            analyzeUseCase.execute(stockCode).collect { state ->
                when (state) {
                    is AnalysisState.Loading ->
                        _uiState.value = AnalysisUiState.Loading

                    is AnalysisState.Computing ->
                        _uiState.value = AnalysisUiState.Computing(state.message, state.progress)

                    is AnalysisState.LlmProcessing ->
                        _uiState.value = AnalysisUiState.LlmProcessing(state.message)

                    is AnalysisState.Streaming -> {
                        _streamingText.value = state.partialText
                        _uiState.value = AnalysisUiState.Streaming
                    }

                    is AnalysisState.Success ->
                        _uiState.value = AnalysisUiState.Success(
                            analysis = state.result,
                            statisticalResult = state.statisticalResult
                        )

                    is AnalysisState.Error ->
                        _uiState.value = AnalysisUiState.Error(state.message)
                }
            }
        }
    }

    /**
     * 모델 로드
     */
    fun loadModel(modelPath: String) {
        viewModelScope.launch {
            llmRepository.loadModel(modelPath)
        }
    }

    /**
     * 캐시된 모델 목록 조회
     */
    suspend fun getCachedModels(): List<CachedModel> {
        return modelManager.getCachedModels()
    }
}

/** 분석 UI 상태 */
sealed class AnalysisUiState {
    data object Idle : AnalysisUiState()
    data object Loading : AnalysisUiState()
    data class Computing(val message: String, val progress: Float) : AnalysisUiState()
    data class LlmProcessing(val message: String) : AnalysisUiState()
    data object Streaming : AnalysisUiState()
    data class Success(
        val analysis: StockAnalysis,
        val statisticalResult: StatisticalResult
    ) : AnalysisUiState()
    data class Error(val message: String) : AnalysisUiState()
}
