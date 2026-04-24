package com.tinyoscillator.presentation.sector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.data.repository.SectorIndexRepository
import com.tinyoscillator.domain.model.SectorIndex
import com.tinyoscillator.domain.model.SectorLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SectorIndexUiState(
    val selectedLevel: SectorLevel = SectorLevel.SMALL,
    val sectors: List<SectorIndex> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lastUpdatedAt: Long? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class SectorIndexViewModel @Inject constructor(
    private val repository: SectorIndexRepository,
    private val apiConfigProvider: ApiConfigProvider,
) : ViewModel() {

    private val _selectedLevel = MutableStateFlow(SectorLevel.SMALL)
    private val _loading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _info = MutableStateFlow<String?>(null)
    private val _lastUpdated = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<SectorIndexUiState> = combine(
        _selectedLevel.asStateFlow(),
        repository.observeSectors(),
        _loading.asStateFlow(),
        _error.asStateFlow(),
        _info.asStateFlow(),
    ) { level, all, loading, err, info ->
        SectorIndexUiState(
            selectedLevel = level,
            sectors = all.filter { it.level == level }.sortedBy { it.name },
            isLoading = loading,
            errorMessage = err,
            infoMessage = info,
            lastUpdatedAt = _lastUpdated.value,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SectorIndexUiState())

    init {
        viewModelScope.launch {
            _lastUpdated.value = repository.lastMasterUpdate()
            val count = repository.sectorCount()
            if (count == 0) {
                _info.value = "업종 목록이 비어 있습니다. 상단 새로고침 버튼으로 KIS에서 수집해 주세요."
            }
        }
    }

    fun selectLevel(level: SectorLevel) {
        _selectedLevel.value = level
    }

    fun refresh(force: Boolean = false) {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _info.value = null
            try {
                val cfg = apiConfigProvider.getKisConfig()
                val result = repository.refreshSectorMaster(cfg, force = force)
                result.fold(
                    onSuccess = { count ->
                        _lastUpdated.value = repository.lastMasterUpdate()
                        _info.value = "업종 ${count}건 수집 완료"
                    },
                    onFailure = { err ->
                        _error.value = err.message ?: "업종 갱신 실패"
                    }
                )
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearMessages() {
        _error.value = null
        _info.value = null
    }
}
