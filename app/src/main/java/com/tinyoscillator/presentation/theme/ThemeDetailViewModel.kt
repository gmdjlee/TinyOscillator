package com.tinyoscillator.presentation.theme

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.data.repository.ThemeRepository
import com.tinyoscillator.domain.model.ThemeStock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 테마 상세(구성 종목) 화면 ViewModel.
 *
 * SavedStateHandle에서 [themeCode]/[themeName]을 추출하고
 * [ThemeRepository.observeThemeStocks]를 구독해 캐시된 종목 리스트를 노출한다.
 * 갱신은 ThemeListScreen의 refresh를 통해서만 발생하므로 이 화면에서는 별도 트리거를 두지 않는다.
 *
 * Navigation route: `theme_detail/{themeCode}/{themeName}`
 */
@HiltViewModel
class ThemeDetailViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val themeCode: String = savedStateHandle["themeCode"] ?: ""
    val themeName: String = savedStateHandle["themeName"] ?: ""

    val stocks: StateFlow<List<ThemeStock>> = if (themeCode.isBlank()) {
        flowOf(emptyList<ThemeStock>())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    } else {
        themeRepository.observeThemeStocks(themeCode)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }
}
