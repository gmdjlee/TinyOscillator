package com.tinyoscillator.presentation.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun <T : Any> DraggableWatchlistColumn(
    items: List<T>,
    onReorder: (from: Int, to: Int) -> Unit,
    key: (T) -> Any,
    itemContent: @Composable (T, Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var draggedOffset by remember { mutableFloatStateOf(0f) }

    LazyColumn(state = listState) {
        itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
            val isDragging = index == draggedIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedIndex = index
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                draggedOffset += dragAmount.y
                                val cellHeight = 60.dp.toPx()
                                val targetIndex = (draggedIndex + (draggedOffset / cellHeight).toInt())
                                    .coerceIn(0, items.lastIndex)
                                if (targetIndex != draggedIndex) {
                                    onReorder(draggedIndex, targetIndex)
                                    draggedIndex = targetIndex
                                    draggedOffset = 0f
                                }
                            },
                            onDragEnd = {
                                draggedIndex = -1
                                draggedOffset = 0f
                            },
                            onDragCancel = {
                                draggedIndex = -1
                                draggedOffset = 0f
                            },
                        )
                    }
                    .then(
                        if (isDragging)
                            Modifier
                                .shadow(4.dp)
                                .background(MaterialTheme.colorScheme.surface)
                        else Modifier
                    ),
            ) {
                itemContent(item, isDragging)
            }
        }
    }
}
