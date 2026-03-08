package com.tinyoscillator.presentation.etf

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.database.entity.EtfEntity
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.presentation.settings.loadEtfKeywordFilter
import com.tinyoscillator.presentation.settings.loadKrxCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EtfViewModel @Inject constructor(
    private val etfRepository: EtfRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _excludeKeywords = MutableStateFlow<List<String>>(emptyList())

    val etfList: StateFlow<List<EtfEntity>> = combine(
        etfRepository.getAllEtfs(),
        _excludeKeywords
    ) { etfs, excludeKws ->
        if (excludeKws.isEmpty()) etfs
        else etfs.filter { etf -> excludeKws.none { kw -> etf.name.contains(kw) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _needsCredentials = MutableStateFlow(false)
    val needsCredentials: StateFlow<Boolean> = _needsCredentials.asStateFlow()

    init {
        loadExcludeKeywords()
        checkCredentials()
    }

    private fun loadExcludeKeywords() {
        viewModelScope.launch {
            val keywords = loadEtfKeywordFilter(context)
            _excludeKeywords.value = keywords.excludeKeywords
        }
    }

    private fun checkCredentials() {
        viewModelScope.launch {
            val creds = loadKrxCredentials(context)
            if (creds.id.isBlank() || creds.password.isBlank()) {
                _needsCredentials.value = true
            }
        }
    }

    fun onCredentialsSaved() {
        _needsCredentials.value = false
    }
}
