package com.tinyoscillator.presentation.theme

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.worker.WorkManagerHelper
import com.tinyoscillator.data.repository.ThemeRepository
import com.tinyoscillator.domain.model.ThemeGroup
import com.tinyoscillator.domain.model.ThemeSortMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 테마 목록 화면 ViewModel.
 *
 * - 검색어 [query] / 정렬 모드 [sortMode]를 결합해 [ThemeRepository.observeThemes]를 구독.
 * - DAO 쿼리는 빈 query → `observeAll`, 비어 있지 않은 query → `searchByName(LIKE)`로 분기되며,
 *   정렬은 Repository 단계에서 in-memory로 적용 (DAO 쿼리 다중화 회피).
 * - [refresh]는 [WorkManagerHelper.runThemeUpdateNow]에 위임 — UI는 캐시만 조회한다.
 * - [lastUpdatedAt] / [themeCount]는 themes Flow에서 파생 (별도 DAO suspend 호출 race 회피).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sortMode = MutableStateFlow(ThemeSortMode.TOP_RETURN)
    val sortMode: StateFlow<ThemeSortMode> = _sortMode.asStateFlow()

    val themes: StateFlow<List<ThemeGroup>> = combine(_query, _sortMode) { q, sort -> q to sort }
        .flatMapLatest { (q, sort) -> themeRepository.observeThemes(q, sort) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val themeCount: StateFlow<Int> =
        themes.map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val lastUpdatedAt: StateFlow<Long?> =
        themes.map { list -> list.maxOfOrNull { it.lastUpdated } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onSortModeChange(mode: ThemeSortMode) {
        _sortMode.value = mode
    }

    /** 즉시 갱신 요청을 WorkManager에 enqueue. UI에는 직접적인 데이터 변경이 없고, 워커 진행률은 별도 채널(Notification + worker_logs)로 표시된다. */
    fun refresh() {
        WorkManagerHelper.runThemeUpdateNow(context)
    }
}
