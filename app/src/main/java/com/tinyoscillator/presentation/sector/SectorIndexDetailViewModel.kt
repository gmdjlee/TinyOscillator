package com.tinyoscillator.presentation.sector

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.data.repository.SectorIndexRepository
import com.tinyoscillator.domain.model.SectorChartPeriod
import com.tinyoscillator.domain.model.SectorIndexChart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SectorIndexDetailUiState(
    val code: String = "",
    val name: String = "",
    val period: SectorChartPeriod = SectorChartPeriod.DAILY,
    val chart: SectorIndexChart? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class SectorIndexDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SectorIndexRepository,
    private val apiConfigProvider: ApiConfigProvider,
) : ViewModel() {

    private val code: String = savedStateHandle["code"] ?: ""
    private val initialName: String = savedStateHandle["name"] ?: ""

    private val _state = MutableStateFlow(
        SectorIndexDetailUiState(code = code, name = initialName)
    )
    val uiState: StateFlow<SectorIndexDetailUiState> = _state.asStateFlow()

    init {
        if (code.isNotBlank()) {
            loadChart(SectorChartPeriod.default())
        } else {
            _state.value = _state.value.copy(errorMessage = "잘못된 업종 코드입니다.")
        }
    }

    fun selectPeriod(period: SectorChartPeriod) {
        if (_state.value.period == period && _state.value.chart != null) return
        loadChart(period)
    }

    fun reload() {
        loadChart(_state.value.period)
    }

    private fun loadChart(period: SectorChartPeriod) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                errorMessage = null,
                period = period,
            )
            val cfg = apiConfigProvider.getKisConfig()
            val result = repository.getSectorIndexChart(code, period, cfg)
            result.fold(
                onSuccess = { chart ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        chart = chart,
                        name = chart.name.ifBlank { _state.value.name },
                    )
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "차트 조회 실패",
                    )
                }
            )
        }
    }
}
