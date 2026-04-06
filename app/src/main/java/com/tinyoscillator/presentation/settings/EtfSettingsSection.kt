package com.tinyoscillator.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tinyoscillator.presentation.common.GlassCard

@Composable
internal fun EtfTab(
    includeKeywords: List<String>,
    excludeKeywords: List<String>,
    showAddIncludeDialog: Boolean,
    showAddExcludeDialog: Boolean,
    onIncludeRemove: (String) -> Unit,
    onExcludeRemove: (String) -> Unit,
    onShowAddInclude: () -> Unit,
    onShowAddExclude: () -> Unit,
    onAddInclude: (String) -> Unit,
    onDismissInclude: () -> Unit,
    onAddExclude: (String) -> Unit,
    onDismissExclude: () -> Unit,
    saveMessage: String?,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("ETF 키워드 필터", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Text("포함 키워드", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            KeywordChipRow(
                keywords = includeKeywords,
                onRemove = onIncludeRemove,
                onAdd = onShowAddInclude
            )
            Spacer(Modifier.height(12.dp))
            Text("제외 키워드", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            KeywordChipRow(
                keywords = excludeKeywords,
                onRemove = onExcludeRemove,
                onAdd = onShowAddExclude
            )
        }

        if (showAddIncludeDialog) {
            AddKeywordDialog(
                title = "포함 키워드 추가",
                onDismiss = onDismissInclude,
                onConfirm = onAddInclude
            )
        }

        if (showAddExcludeDialog) {
            AddKeywordDialog(
                title = "제외 키워드 추가",
                onDismiss = onDismissExclude,
                onConfirm = onAddExclude
            )
        }

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("저장")
        }

        saveMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KeywordChipRow(
    keywords: List<String>,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        keywords.forEach { keyword ->
            FilterChip(
                selected = true,
                onClick = { onRemove(keyword) },
                label = { Text(keyword) },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "삭제",
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Add, contentDescription = "추가")
        }
    }
}

@Composable
internal fun AddKeywordDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("키워드") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(text) }),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("추가")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
