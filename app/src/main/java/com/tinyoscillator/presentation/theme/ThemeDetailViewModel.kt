package com.tinyoscillator.presentation.theme

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.data.repository.ThemeRepository
import com.tinyoscillator.domain.model.ThemeStock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 테마 상세(구성 종목) ViewModel.
 *
 * 초기 테마는 SavedStateHandle(`theme_detail/{themeCode}/{themeName}` 라우트)에서 오고,
 * 태블릿 2-Pane 임베드에서는 [selectTheme]로 테마를 동적으로 전환한다.
 * [ThemeRepository.observeThemeStocks] 구독 — 캐시 조회 전용, 갱신은 테마 목록 refresh 경유.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ThemeDetailViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _selected = MutableStateFlow(
        ThemeSelection(
            code = savedStateHandle["themeCode"] ?: "",
            name = savedStateHandle["themeName"] ?: "",
        )
    )
    val selected: StateFlow<ThemeSelection> = _selected.asStateFlow()

    val stocks: StateFlow<List<ThemeStock>> = _selected
        .flatMapLatest { sel ->
            if (sel.code.isBlank()) flowOf(emptyList())
            else themeRepository.observeThemeStocks(sel.code)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 2-Pane 임베드에서 목록 탭으로 테마 전환 */
    fun selectTheme(code: String, name: String) {
        _selected.value = ThemeSelection(code, name)
    }

    data class ThemeSelection(val code: String, val name: String)
}
