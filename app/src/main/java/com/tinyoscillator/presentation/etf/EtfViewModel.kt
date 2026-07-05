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

enum class EtfSortMode { NAME, RETURN }

@HiltViewModel
class EtfViewModel @Inject constructor(
    private val etfRepository: EtfRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _excludeKeywords = MutableStateFlow<List<String>>(emptyList())

    private val _includeKeywords = MutableStateFlow<List<String>>(emptyList())
    val includeKeywords: StateFlow<List<String>> = _includeKeywords.asStateFlow()

    private val _sortMode = MutableStateFlow(EtfSortMode.NAME)
    val sortMode: StateFlow<EtfSortMode> = _sortMode.asStateFlow()

    fun setSortMode(mode: EtfSortMode) {
        _sortMode.value = mode
    }

    val etfList: StateFlow<List<EtfEntity>> = combine(
        etfRepository.getAllEtfs(),
        _excludeKeywords,
        _sortMode
    ) { etfs, excludeKws, sort ->
        val filtered = if (excludeKws.isEmpty()) etfs
            else etfs.filter { etf -> excludeKws.none { kw -> etf.name.contains(kw) } }
        when (sort) {
            EtfSortMode.NAME -> filtered  // getAllEtfs가 이미 name ASC
            EtfSortMode.RETURN -> filtered.sortedByDescending { it.changeRate ?: -Double.MAX_VALUE } // null 최하위
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _needsCredentials = MutableStateFlow(false)
    val needsCredentials: StateFlow<Boolean> = _needsCredentials.asStateFlow()

    init {
        loadKeywords()
        checkCredentials()
    }

    private fun loadKeywords() {
        viewModelScope.launch {
            val keywords = loadEtfKeywordFilter(context)
            _excludeKeywords.value = keywords.excludeKeywords
            _includeKeywords.value = keywords.includeKeywords
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
