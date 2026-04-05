package com.tinyoscillator.presentation.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.database.dao.WatchlistDao
import com.tinyoscillator.core.database.entity.WatchlistGroupEntity
import com.tinyoscillator.core.database.entity.WatchlistItemEntity
import com.tinyoscillator.domain.model.WatchlistEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class WatchlistSortKey(val label: String) {
    SIGNAL_DESC("신호↓"),
    SIGNAL_ASC("신호↑"),
    CUSTOM_ORDER("사용자순"),
    CHANGE_DESC("등락↓"),
}

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val dao: WatchlistDao,
) : ViewModel() {

    private val _sortKey = MutableStateFlow(WatchlistSortKey.SIGNAL_DESC)
    val sortKey: StateFlow<WatchlistSortKey> = _sortKey.asStateFlow()

    private val _selectedGroupId = MutableStateFlow<Long?>(null)
    val selectedGroupId: StateFlow<Long?> = _selectedGroupId.asStateFlow()

    private val _deletedEntry = MutableStateFlow<WatchlistItemEntity?>(null)

    val groups: StateFlow<List<WatchlistGroupEntity>> =
        dao.observeGroups()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val watchlistEntries: StateFlow<List<WatchlistEntry>> =
        combine(
            _selectedGroupId.flatMapLatest { groupId ->
                if (groupId != null) dao.observeByGroup(groupId) else dao.observeAll()
            },
            _sortKey,
        ) { items, sort ->
            val sorted = when (sort) {
                WatchlistSortKey.SIGNAL_DESC ->
                    items.sortedByDescending { it.cachedSignal ?: 0.5f }
                WatchlistSortKey.SIGNAL_ASC ->
                    items.sortedBy { it.cachedSignal ?: 0.5f }
                WatchlistSortKey.CUSTOM_ORDER ->
                    items.sortedBy { it.sortOrder }
                WatchlistSortKey.CHANGE_DESC ->
                    items.sortedByDescending { it.cachedChange ?: 0f }
            }
            sorted.map { it.toDomain() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSortKey(key: WatchlistSortKey) {
        _sortKey.value = key
    }

    fun selectGroup(groupId: Long?) {
        _selectedGroupId.value = groupId
    }

    fun deleteEntry(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@launch
        _deletedEntry.value = entity
        dao.delete(entity)
    }

    fun undoDelete(entry: WatchlistEntry) = viewModelScope.launch(Dispatchers.IO) {
        _deletedEntry.value?.let { dao.insert(it) }
        _deletedEntry.value = null
    }

    fun reorder(from: Int, to: Int) = viewModelScope.launch(Dispatchers.IO) {
        val items = dao.getAllSortedByOrder()
        val mutable = items.toMutableList()
        if (from !in mutable.indices || to !in mutable.indices) return@launch
        val moved = mutable.removeAt(from)
        mutable.add(to, moved)
        mutable.forEachIndexed { index, item ->
            dao.updateSortOrder(item.id, index)
        }
        _sortKey.value = WatchlistSortKey.CUSTOM_ORDER
    }

    fun addGroup(name: String) = viewModelScope.launch(Dispatchers.IO) {
        if (name.isBlank()) return@launch
        dao.insertGroup(WatchlistGroupEntity(name = name))
    }
}

private fun WatchlistItemEntity.toDomain() = WatchlistEntry(
    id = id,
    ticker = ticker,
    name = name,
    groupId = groupId,
    sortOrder = sortOrder,
    signalScore = cachedSignal ?: 0f,
    currentPrice = cachedPrice ?: 0L,
    priceChange = cachedChange ?: 0f,
)
