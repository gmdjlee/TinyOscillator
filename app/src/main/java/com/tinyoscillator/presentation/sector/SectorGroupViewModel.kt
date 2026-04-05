package com.tinyoscillator.presentation.sector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.data.repository.StockGroupRepository
import com.tinyoscillator.domain.model.StockGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SectorGroupViewModel @Inject constructor(
    private val repository: StockGroupRepository,
) : ViewModel() {

    val krxSectors: StateFlow<List<StockGroup>> = repository.observeKrxSectors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val userThemes: StateFlow<List<StockGroup>> = repository.observeUserThemes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repository.initDefaultThemes() }
    }

    fun addTheme(name: String, tickers: List<String>) {
        viewModelScope.launch { repository.addTheme(name, tickers) }
    }

    fun deleteTheme(id: Long) {
        viewModelScope.launch { repository.deleteTheme(id) }
    }
}
