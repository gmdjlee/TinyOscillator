package com.tinyoscillator.presentation.progressive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.ui.UiState
import com.tinyoscillator.domain.model.ProgressiveAnalysisState
import com.tinyoscillator.domain.usecase.ProgressiveAnalysisUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressiveAnalysisViewModel @Inject constructor(
    private val progressiveUseCase: ProgressiveAnalysisUseCase,
) : ViewModel() {

    private val _ticker = MutableStateFlow("")

    val analysisState: StateFlow<UiState<ProgressiveAnalysisState>> = _ticker
        .filter { it.isNotBlank() }
        .flatMapLatest { ticker ->
            progressiveUseCase(ticker)
                .map<ProgressiveAnalysisState, UiState<ProgressiveAnalysisState>> {
                    UiState.Success(it)
                }
                .catch { e -> emit(UiState.Error(e.message ?: "분석 실패")) }
                .onStart { emit(UiState.Loading) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Idle)

    fun setTicker(ticker: String) {
        _ticker.value = ticker
    }

    fun retry() {
        val current = _ticker.value
        if (current.isNotBlank()) {
            _ticker.value = ""
            _ticker.value = current
        }
    }
}
