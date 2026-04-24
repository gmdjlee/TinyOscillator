package com.tinyoscillator.presentation.sector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val selectedLevel: SectorLevel = SectorLevel.INDEX,
    val sectors: List<SectorIndex> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lastUpdatedAt: Long? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class SectorIndexViewModel @Inject constructor(
    private val repository: SectorIndexRepository,
) : ViewModel() {

    private val _selectedLevel = MutableStateFlow(SectorLevel.INDEX)
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
            sectors = all.filter { it.level == level }.sortedBy { it.code },
            isLoading = loading,
            errorMessage = err,
            infoMessage = info,
            lastUpdatedAt = _lastUpdated.value,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SectorIndexUiState())

    init {
        viewModelScope.launch {
            repository.ensureSeeded(force = false)
                .onSuccess { _lastUpdated.value = repository.lastMasterUpdate() }
                .onFailure { err -> _error.value = err.message ?: "업종 시드 적용 실패" }
        }
    }

    fun selectLevel(level: SectorLevel) {
        _selectedLevel.value = level
    }

    /** 씨드 강제 재적용. 네트워크 호출 없음 — 상수 테이블로 즉시 갱신된다. */
    fun refresh(force: Boolean = true) {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _info.value = null
            try {
                repository.ensureSeeded(force = force).fold(
                    onSuccess = { count ->
                        _lastUpdated.value = repository.lastMasterUpdate()
                        _info.value = "KIS 업종분류코드 ${count}건 적용"
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
