package com.tinyoscillator.presentation.watchlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel = hiltViewModel(),
    onTickerClick: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val entries by viewModel.watchlistEntries.collectAsStateWithLifecycle()
    val sortKey by viewModel.sortKey.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("관심종목") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            WatchlistHeader(
                groups = groups,
                sortKey = sortKey,
                selectedGroupId = selectedGroupId,
                onSortChange = viewModel::setSortKey,
                onGroupSelect = viewModel::selectGroup,
                onAddGroup = viewModel::addGroup,
            )

            DraggableWatchlistColumn(
                items = entries,
                onReorder = viewModel::reorder,
                key = { it.id },
            ) { entry, isDragging ->
                SwipeToDeleteBox(
                    item = entry,
                    onDelete = { deleted ->
                        viewModel.deleteEntry(deleted.id)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "${deleted.name} 삭제됨",
                                actionLabel = "실행 취소",
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.undoDelete(deleted)
                            }
                        }
                    },
                ) { item ->
                    WatchlistItemRow(
                        entry = item,
                        isDragging = isDragging,
                        modifier = Modifier.clickable { onTickerClick(item.ticker) },
                    )
                }
            }
        }
    }
}
