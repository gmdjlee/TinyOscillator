package com.tinyoscillator.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.domain.model.HeatmapData
import com.tinyoscillator.domain.usecase.BuildHeatmapUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class HeatmapUiState(
    val windowDays: Int = 20,
    val data: HeatmapData? = null,
    val isLoading: Boolean = false,
)

@HiltViewModel
class HeatmapViewModel @Inject constructor(
    private val buildHeatmapUseCase: BuildHeatmapUseCase,
) : ViewModel() {

    private val _heatmapState = MutableStateFlow(HeatmapUiState())
    val heatmapState: StateFlow<HeatmapUiState> = _heatmapState.asStateFlow()

    init {
        loadHeatmap()
    }

    fun setWindowDays(days: Int) {
        _heatmapState.update { it.copy(windowDays = days) }
        loadHeatmap()
    }

    private fun loadHeatmap() {
        viewModelScope.launch {
            _heatmapState.update { it.copy(isLoading = true) }
            try {
                val data = buildHeatmapUseCase(_heatmapState.value.windowDays)
                _heatmapState.update { it.copy(data = data, isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "히트맵 데이터 로드 실패")
                _heatmapState.update { it.copy(isLoading = false) }
            }
        }
    }
}
