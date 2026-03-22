package com.tinyoscillator.presentation.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tinyoscillator.core.database.entity.WorkerLogEntity
import com.tinyoscillator.core.worker.STATUS_ERROR
import com.tinyoscillator.core.worker.STATUS_SUCCESS
import com.tinyoscillator.presentation.common.GlassCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class LogFilter(val label: String) {
    ALL("전체"),
    SUCCESS("성공"),
    ERROR("에러")
}

@Composable
internal fun LogTab(
    logs: List<WorkerLogEntity>,
    logFilter: LogFilter,
    onFilterChange: (LogFilter) -> Unit,
    onExport: () -> Unit,
    onClearLogs: () -> Unit,
    onRefresh: () -> Unit
) {
    var selectedLog by remember { mutableStateOf<WorkerLogEntity?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val filteredLogs = remember(logs, logFilter) {
        when (logFilter) {
            LogFilter.ALL -> logs
            LogFilter.SUCCESS -> logs.filter { it.status == STATUS_SUCCESS }
            LogFilter.ERROR -> logs.filter { it.status == STATUS_ERROR }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Filter bar
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            LogFilterBar(
                selectedFilter = logFilter,
                onFilterChange = onFilterChange
            )
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onExport,
                modifier = Modifier.weight(1f),
                enabled = logs.isNotEmpty()
            ) {
                Text("로그 내보내기")
            }
            OutlinedButton(
                onClick = { showClearConfirmDialog = true },
                modifier = Modifier.weight(1f),
                enabled = logs.isNotEmpty(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("로그 삭제")
            }
        }

        // Log count
        Text(
            text = "총 ${filteredLogs.size}건",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Log list
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (logs.isEmpty()) "로그 기록이 없습니다." else "해당 필터에 맞는 로그가 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredLogs, key = { it.id }) { log ->
                    LogListItem(
                        log = log,
                        onClick = { selectedLog = log }
                    )
                }
            }
        }
    }

    // Log detail dialog
    selectedLog?.let { log ->
        LogDetailDialog(
            log = log,
            onDismiss = { selectedLog = null }
        )
    }

    // Clear confirmation dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("로그 삭제") },
            text = { Text("모든 로그를 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearLogs()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
private fun LogFilterBar(
    selectedFilter: LogFilter,
    onFilterChange: (LogFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LogFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterChange(filter) },
                label = { Text(filter.label) }
            )
        }
    }
}

@Composable
private fun LogListItem(
    log: WorkerLogEntity,
    onClick: () -> Unit
) {
    val isError = log.status == STATUS_ERROR
    val isSuccess = log.status == STATUS_SUCCESS
    val dateStr = remember(log.executedAt) {
        SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()).format(Date(log.executedAt))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            isError -> "✗"
                            isSuccess -> "✓"
                            else -> "⋯"
                        },
                        color = when {
                            isError -> MaterialTheme.colorScheme.error
                            isSuccess -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = log.workerName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun LogDetailDialog(
    log: WorkerLogEntity,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("로그 상세 — ${log.workerName}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "상태: ${
                        when (log.status) {
                            STATUS_SUCCESS -> "성공"
                            STATUS_ERROR -> "에러"
                            else -> log.status
                        }
                    }",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "시간: ${dateFormat.format(Date(log.executedAt))}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Text("메시지: ${log.message}", style = MaterialTheme.typography.bodySmall)
                if (!log.errorDetail.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "상세:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(log.errorDetail, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    )
}

internal fun exportLogs(context: Context, logs: List<WorkerLogEntity>) {
    val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
    val exportDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    val text = buildString {
        appendLine("TinyOscillator 앱 로그")
        appendLine("내보내기 시간: ${dateFormat.format(Date())}")
        appendLine("총 ${logs.size}건")
        appendLine("─".repeat(40))
        appendLine()
        logs.forEach { log ->
            val status = when (log.status) {
                STATUS_SUCCESS -> "성공"
                STATUS_ERROR -> "에러"
                else -> log.status
            }
            appendLine("[${dateFormat.format(Date(log.executedAt))}] ${log.workerName} — $status")
            appendLine("  ${log.message}")
            if (!log.errorDetail.isNullOrBlank()) {
                appendLine("  상세: ${log.errorDetail}")
            }
            appendLine()
        }
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "TinyOscillator_Log_${exportDateFormat.format(Date())}")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "로그 내보내기"))
}
