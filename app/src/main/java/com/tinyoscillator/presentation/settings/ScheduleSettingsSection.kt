package com.tinyoscillator.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tinyoscillator.core.database.entity.WorkerLogEntity
import com.tinyoscillator.core.worker.STATUS_ERROR
import com.tinyoscillator.presentation.common.GlassCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ScheduleSection(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    hour: Int,
    onHourChange: (Int) -> Unit,
    minute: Int,
    onMinuteChange: (Int) -> Unit,
    manualButtonText: String,
    onManualCollect: () -> Unit,
    message: String?,
    progress: Float? = null,
    isCollecting: Boolean = false,
    lastResult: WorkerLogEntity? = null
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("자동 업데이트 활성화", style = MaterialTheme.typography.bodyMedium)
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }

    if (enabled) {
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = "%02d".format(hour),
                onValueChange = { v ->
                    v.filter { it.isDigit() }.take(2).toIntOrNull()?.let {
                        if (it in 0..23) onHourChange(it)
                    }
                },
                label = { Text("시") },
                singleLine = true,
                modifier = Modifier.width(80.dp)
            )
            Text(":", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = "%02d".format(minute),
                onValueChange = { v ->
                    v.filter { it.isDigit() }.take(2).toIntOrNull()?.let {
                        if (it in 0..59) onMinuteChange(it)
                    }
                },
                label = { Text("분") },
                singleLine = true,
                modifier = Modifier.width(80.dp)
            )
            Text(
                "매일 자동 업데이트",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = onManualCollect,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isCollecting
    ) {
        Text(manualButtonText)
    }

    progress?.let { p ->
        LinearProgressIndicator(
            progress = { p },
            modifier = Modifier.fillMaxWidth()
        )
    }

    message?.let { msg ->
        Text(
            text = msg,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall
        )
    }

    // 마지막 실행 결과 영구 표시
    lastResult?.let { log ->
        LastResultDisplay(log)
    }
}

@Composable
private fun LastResultDisplay(log: WorkerLogEntity) {
    val isError = log.status == STATUS_ERROR
    val dateStr = remember(log.executedAt) {
        SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(log.executedAt))
    }
    var showDetail by remember { mutableStateOf(false) }

    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isError) "✗ 실패 ($dateStr)" else "✓ 완료 ($dateStr)",
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = log.message.take(60),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (isError && !log.errorDetail.isNullOrBlank()) {
            TextButton(onClick = { showDetail = true }) {
                Text("상세", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    if (showDetail && !log.errorDetail.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { showDetail = false },
            title = { Text("에러 상세 — ${log.workerName}") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("시간: $dateStr", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text("메시지: ${log.message}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(log.errorDetail, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetail = false }) { Text("닫기") }
            }
        )
    }
}

@Composable
internal fun ScheduleTab(
    etfScheduleEnabled: Boolean,
    onEtfScheduleEnabledChange: (Boolean) -> Unit,
    scheduleHour: Int,
    onScheduleHourChange: (Int) -> Unit,
    scheduleMinute: Int,
    onScheduleMinuteChange: (Int) -> Unit,
    manualCollectMessage: String?,
    etfCollectProgress: Float?,
    isEtfCollecting: Boolean,
    onManualCollect: () -> Unit,
    oscScheduleEnabled: Boolean,
    onOscScheduleEnabledChange: (Boolean) -> Unit,
    oscScheduleHour: Int,
    onOscScheduleHourChange: (Int) -> Unit,
    oscScheduleMinute: Int,
    onOscScheduleMinuteChange: (Int) -> Unit,
    oscManualMessage: String?,
    isOscCollecting: Boolean,
    onOscManualCollect: () -> Unit,
    depositScheduleEnabled: Boolean,
    onDepositScheduleEnabledChange: (Boolean) -> Unit,
    depositScheduleHour: Int,
    onDepositScheduleHourChange: (Int) -> Unit,
    depositScheduleMinute: Int,
    onDepositScheduleMinuteChange: (Int) -> Unit,
    depositManualMessage: String?,
    isDepositCollecting: Boolean,
    onDepositManualCollect: () -> Unit,
    marketCloseRefreshEnabled: Boolean = false,
    onMarketCloseRefreshEnabledChange: (Boolean) -> Unit = {},
    marketCloseRefreshHour: Int = 19,
    onMarketCloseRefreshHourChange: (Int) -> Unit = {},
    marketCloseRefreshMinute: Int = 0,
    onMarketCloseRefreshMinuteChange: (Int) -> Unit = {},
    marketCloseRefreshMessage: String? = null,
    marketCloseRefreshProgress: Float? = null,
    isMarketCloseRefreshing: Boolean = false,
    onMarketCloseRefreshManual: () -> Unit = {},
    integrityCheckMessage: String?,
    integrityCheckProgress: Float? = null,
    isIntegrityChecking: Boolean = false,
    onIntegrityCheck: () -> Unit = {},
    lastEtfLog: WorkerLogEntity? = null,
    lastOscLog: WorkerLogEntity? = null,
    lastDepositLog: WorkerLogEntity? = null,
    lastMarketCloseLog: WorkerLogEntity? = null,
    lastIntegrityLog: WorkerLogEntity? = null,
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
            ScheduleSection(
                title = "ETF 자동 업데이트",
                enabled = etfScheduleEnabled,
                onEnabledChange = onEtfScheduleEnabledChange,
                hour = scheduleHour,
                onHourChange = onScheduleHourChange,
                minute = scheduleMinute,
                onMinuteChange = onScheduleMinuteChange,
                manualButtonText = "지금 ETF 데이터 수집",
                onManualCollect = onManualCollect,
                message = manualCollectMessage,
                progress = etfCollectProgress,
                isCollecting = isEtfCollecting,
                lastResult = lastEtfLog
            )
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            ScheduleSection(
                title = "과매수/과매도 자동 업데이트",
                enabled = oscScheduleEnabled,
                onEnabledChange = onOscScheduleEnabledChange,
                hour = oscScheduleHour,
                onHourChange = onOscScheduleHourChange,
                minute = oscScheduleMinute,
                onMinuteChange = onOscScheduleMinuteChange,
                manualButtonText = "지금 과매수/과매도 업데이트",
                onManualCollect = onOscManualCollect,
                message = oscManualMessage,
                isCollecting = isOscCollecting,
                lastResult = lastOscLog
            )
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            ScheduleSection(
                title = "자금 동향 자동 업데이트",
                enabled = depositScheduleEnabled,
                onEnabledChange = onDepositScheduleEnabledChange,
                hour = depositScheduleHour,
                onHourChange = onDepositScheduleHourChange,
                minute = depositScheduleMinute,
                onMinuteChange = onDepositScheduleMinuteChange,
                manualButtonText = "지금 자금 동향 업데이트",
                onManualCollect = onDepositManualCollect,
                message = depositManualMessage,
                isCollecting = isDepositCollecting,
                lastResult = lastDepositLog
            )
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            ScheduleSection(
                title = "장 마감 데이터 교체",
                enabled = marketCloseRefreshEnabled,
                onEnabledChange = onMarketCloseRefreshEnabledChange,
                hour = marketCloseRefreshHour,
                onHourChange = onMarketCloseRefreshHourChange,
                minute = marketCloseRefreshMinute,
                onMinuteChange = onMarketCloseRefreshMinuteChange,
                manualButtonText = "지금 장 마감 데이터 교체",
                onManualCollect = onMarketCloseRefreshManual,
                message = marketCloseRefreshMessage,
                progress = marketCloseRefreshProgress,
                isCollecting = isMarketCloseRefreshing,
                lastResult = lastMarketCloseLog
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "장중 수집된 데이터를 장 마감 확정 데이터로 교체합니다. (종목분석, ETF, 시장지표)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("데이터 무결성 검사", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "ETF, 과매수/과매도, 자금 동향 데이터를 최신 데이터와 비교하여 불일치 항목을 수정합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onIntegrityCheck,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isIntegrityChecking
            ) {
                Text("무결성 검사 실행")
            }

            integrityCheckProgress?.let { p ->
                LinearProgressIndicator(
                    progress = { p },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            integrityCheckMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            lastIntegrityLog?.let { log ->
                LastResultDisplay(log)
            }
        }

        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
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
