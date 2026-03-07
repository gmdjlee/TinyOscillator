package com.tinyoscillator.presentation.etf

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.database.entity.EtfEntity
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.domain.model.EtfDataProgress
import com.tinyoscillator.domain.model.EtfUiState
import com.tinyoscillator.presentation.settings.KrxCredentials
import com.tinyoscillator.presentation.settings.loadEtfCollectionPeriod
import com.tinyoscillator.presentation.settings.loadEtfKeywordFilter
import com.tinyoscillator.presentation.settings.loadKrxCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EtfViewModel @Inject constructor(
    private val etfRepository: EtfRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<EtfUiState>(EtfUiState.Idle)
    val uiState: StateFlow<EtfUiState> = _uiState.asStateFlow()

    val etfList: StateFlow<List<EtfEntity>> = etfRepository.getAllEtfs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _needsCredentials = MutableStateFlow(false)
    val needsCredentials: StateFlow<Boolean> = _needsCredentials.asStateFlow()

    init {
        checkCredentials()
    }

    private fun checkCredentials() {
        viewModelScope.launch {
            val creds = loadKrxCredentials(context)
            if (creds.id.isBlank() || creds.password.isBlank()) {
                _needsCredentials.value = true
            } else {
                // Check if we have any data
                val latestDate = etfRepository.getLatestDate()
                if (latestDate == null) {
                    collectInitialData(creds)
                }
            }
        }
    }

    fun onCredentialsSaved() {
        _needsCredentials.value = false
        viewModelScope.launch {
            val creds = loadKrxCredentials(context)
            collectInitialData(creds)
        }
    }

    private fun collectInitialData(creds: KrxCredentials) {
        viewModelScope.launch {
            val keywords = loadEtfKeywordFilter(context)
            val period = loadEtfCollectionPeriod(context)
            etfRepository.updateData(creds, keywords, daysBack = period.daysBack).collect { progress ->
                _uiState.value = when (progress) {
                    is EtfDataProgress.Loading -> EtfUiState.Loading(progress.message, progress.progress)
                    is EtfDataProgress.Success -> EtfUiState.Success(progress.etfCount)
                    is EtfDataProgress.Error -> EtfUiState.Error(progress.message)
                }
            }
        }
    }

    fun refreshData(daysBack: Int = 1) {
        viewModelScope.launch {
            val creds = loadKrxCredentials(context)
            if (creds.id.isBlank() || creds.password.isBlank()) {
                _needsCredentials.value = true
                return@launch
            }
            val keywords = loadEtfKeywordFilter(context)
            etfRepository.updateData(creds, keywords, daysBack).collect { progress ->
                _uiState.value = when (progress) {
                    is EtfDataProgress.Loading -> EtfUiState.Loading(progress.message, progress.progress)
                    is EtfDataProgress.Success -> EtfUiState.Success(progress.etfCount)
                    is EtfDataProgress.Error -> EtfUiState.Error(progress.message)
                }
            }
        }
    }
}
