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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tinyoscillator.presentation.common.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EtfTab(
    includeKeywords: List<String>,
    excludeKeywords: List<String>,
    etfCollectionDays: Int,
    onEtfCollectionDaysChange: (Int) -> Unit,
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
    etfCollectProgress: Float?,
    isEtfCollecting: Boolean,
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

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("데이터 수집 기간", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            var isWeekUnit by remember { mutableStateOf(etfCollectionDays % 7 == 0 && etfCollectionDays / 7 <= 4) }
        var periodValue by remember {
            mutableStateOf(
                if (etfCollectionDays % 7 == 0 && etfCollectionDays / 7 <= 4)
                    (etfCollectionDays / 7).toString()
                else
                    (etfCollectionDays / 30).coerceAtLeast(1).toString()
            )
        }
        val unitOptions = listOf("주" to true, "월" to false)
        var unitExpanded by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = periodValue,
                onValueChange = { v ->
                    val filtered = v.filter { it.isDigit() }.take(2)
                    periodValue = filtered
                    filtered.toIntOrNull()?.let { num ->
                        if (num > 0) {
                            val days = if (isWeekUnit) num * 7 else num * 30
                            onEtfCollectionDaysChange(days)
                        }
                    }
                },
                label = { Text("기간") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.weight(1f)
            )

            ExposedDropdownMenuBox(
                expanded = unitExpanded,
                onExpandedChange = { unitExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = if (isWeekUnit) "주" else "월",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("단위") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = unitExpanded,
                    onDismissRequest = { unitExpanded = false }
                ) {
                    unitOptions.forEach { (label, isWeek) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                isWeekUnit = isWeek
                                unitExpanded = false
                                periodValue.toIntOrNull()?.let { num ->
                                    if (num > 0) onEtfCollectionDaysChange(if (isWeek) num * 7 else num * 30)
                                }
                            }
                        )
                    }
                }
            }

            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                enabled = !isEtfCollecting
            ) {
                Text("저장")
            }
        }

            Spacer(Modifier.height(8.dp))
            Text(
                "앱 첫 실행 시 또는 전체 새로고침 시 수집할 기간입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            etfCollectProgress?.let { progress ->
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
