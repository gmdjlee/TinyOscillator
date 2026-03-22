package com.tinyoscillator.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tinyoscillator.presentation.common.GlassCard

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
    isCollecting: Boolean = false
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
                isCollecting = isEtfCollecting
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
                isCollecting = isOscCollecting
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
                isCollecting = isDepositCollecting
            )
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
