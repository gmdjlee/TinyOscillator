package com.tinyoscillator.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.data.preferences.IndicatorPreferencesRepository
import com.tinyoscillator.domain.indicator.IndicatorCalculator
import com.tinyoscillator.domain.model.Indicator
import com.tinyoscillator.domain.model.IndicatorParams
import com.tinyoscillator.domain.model.OhlcvPoint
import com.tinyoscillator.domain.model.OverlayType
import com.tinyoscillator.domain.model.PatternResult
import com.tinyoscillator.domain.model.VolumeProfile
import com.tinyoscillator.domain.usecase.BuildVolumeProfileUseCase
import com.tinyoscillator.domain.usecase.ParkSignalDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class StockChartViewModel @Inject constructor(
    private val indicatorPrefsRepo: IndicatorPreferencesRepository,
) : ViewModel() {

    private val _candleData = MutableStateFlow<List<OhlcvPoint>>(emptyList())
    val candleData: StateFlow<List<OhlcvPoint>> = _candleData.asStateFlow()

    val selectedIndicators: StateFlow<Set<Indicator>> = indicatorPrefsRepo.selectedIndicators
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val indicatorParams: StateFlow<Map<Indicator, IndicatorParams>> = indicatorPrefsRepo.indicatorParams
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val indicatorData: StateFlow<IndicatorCalculator.IndicatorData?> =
        combine(_candleData, selectedIndicators, indicatorParams) { candles, selected, params ->
            if (candles.isEmpty()) null
            else withContext(Dispatchers.Default) {
                IndicatorCalculator.build(candles, selected, params)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val buildVolumeProfile = BuildVolumeProfileUseCase()

    val volumeProfile: StateFlow<VolumeProfile?> =
        combine(_candleData, selectedIndicators) { candles, selected ->
            if (Indicator.VOLUME_PROFILE in selected && candles.isNotEmpty())
                withContext(Dispatchers.Default) { buildVolumeProfile(candles) }
            else null
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activeOscillator: StateFlow<Indicator?> =
        selectedIndicators.map { set ->
            set.firstOrNull { it.overlayType == OverlayType.OSCILLATOR }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val detectedPatterns: StateFlow<List<PatternResult>> =
        _candleData
            .map { candles ->
                if (candles.isEmpty()) emptyList()
                else withContext(Dispatchers.Default) {
                    ParkSignalDetector.detect(candles)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val patternSummary: StateFlow<List<PatternResult>> =
        detectedPatterns
            .map { it.sortedByDescending { p -> p.index }.take(5) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val showIndicatorSheet = MutableStateFlow(false)

    fun setCandleData(candles: List<OhlcvPoint>) {
        _candleData.value = candles
    }

    fun toggleIndicator(ind: Indicator) {
        viewModelScope.launch { indicatorPrefsRepo.toggle(ind) }
    }

    fun updateParams(ind: Indicator, params: IndicatorParams) {
        viewModelScope.launch { indicatorPrefsRepo.updateParams(ind, params) }
    }
}
