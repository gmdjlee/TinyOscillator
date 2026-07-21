package com.tinyoscillator.presentation.keyword

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.worker.WorkManagerHelper
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.domain.model.KeywordGroup
import com.tinyoscillator.domain.model.KeywordSortMode
import com.tinyoscillator.domain.model.groupEtfsByKeyword
import com.tinyoscillator.presentation.settings.loadEtfKeywordFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 키워드 목록 화면 ViewModel.
 *
 * - 설정의 포함 키워드([includeKeywords]) + ETF 목록([EtfRepository.getAllEtfs])을
 *   검색어([query])·정렬 모드([sortMode])와 함께 결합해 [groupEtfsByKeyword]로 분류한다.
 * - 그룹핑은 순수함수(Phase 0)에 위임 — ViewModel은 상태 결합만 담당.
 * - [groupCount] / [lastUpdatedAt]는 [groups]에서 파생 (별도 DAO suspend 호출 race 회피).
 * - [refresh]는 기존 ETF 수집 워커([WorkManagerHelper.runEtfUpdateNow])에 위임 — UI는 캐시만 조회한다.
 */
@HiltViewModel
class KeywordViewModel @Inject constructor(
    private val etfRepository: EtfRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _includeKeywords = MutableStateFlow<List<String>>(emptyList())
    val includeKeywords: StateFlow<List<String>> = _includeKeywords.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sortMode = MutableStateFlow(KeywordSortMode.ETF_COUNT)
    val sortMode: StateFlow<KeywordSortMode> = _sortMode.asStateFlow()

    val groups: StateFlow<List<KeywordGroup>> = combine(
        etfRepository.getAllEtfs(),
        _includeKeywords,
        _query,
        _sortMode,
    ) { etfs, keywords, q, sort ->
        groupEtfsByKeyword(etfs, keywords, q, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groupCount: StateFlow<Int> =
        groups.map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val lastUpdatedAt: StateFlow<Long?> =
        groups.map { list -> list.maxOfOrNull { it.lastUpdated } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        loadKeywords()
    }

    private fun loadKeywords() {
        viewModelScope.launch {
            _includeKeywords.value = loadEtfKeywordFilter(context).includeKeywords
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onSortModeChange(mode: KeywordSortMode) {
        _sortMode.value = mode
    }

    /** 즉시 갱신 요청을 WorkManager에 enqueue (ETF 수집 워커 재사용). UI 데이터는 워커 완료 후 캐시 Flow로 반영된다. */
    fun refresh() {
        WorkManagerHelper.runEtfUpdateNow(context)
    }
}
