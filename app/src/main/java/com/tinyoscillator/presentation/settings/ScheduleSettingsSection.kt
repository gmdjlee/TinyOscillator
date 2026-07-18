package com.tinyoscillator.presentation.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tinyoscillator.core.database.entity.WorkerLogEntity
import com.tinyoscillator.core.worker.STATUS_ERROR
import com.tinyoscillator.domain.model.ThemeExchange
import com.tinyoscillator.presentation.common.AccordionCard
import com.tinyoscillator.presentation.common.GlassCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ScheduleSection(
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
    lastResult: WorkerLogEntity? = null,
    // 수집 기간 + 데이터 초기화 (지원 데이터소스만 non-null) — 소스별 설정을 한 카드에 통합
    collectionDays: Int? = null,
    onCollectionDaysChange: (Int) -> Unit = {},
    onCollectionSave: () -> Unit = {},
    onResetData: (() -> Unit)? = null
) {
    // 제목은 아코디언 헤더가 담당 — 본문은 수집 기간부터 시작한다.
    if (collectionDays != null) {
        CollectionPeriodRow(
            daysBack = collectionDays,
            onDaysBackChange = onCollectionDaysChange,
            onSave = onCollectionSave
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "초기 수집 또는 전체 새로고침 시 수집할 기간입니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
    }
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

    onResetData?.let { reset ->
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = reset,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                Icons.Default.DeleteOutline,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("데이터 초기화")
        }
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
private fun BatteryOptimizationCard() {
    val context = LocalContext.current
    var isIgnoring by remember { mutableStateOf(isBatteryOptimizationIgnored(context)) }

    if (!isIgnoring) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("배터리 최적화 설정", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "배터리 최적화가 활성화되어 있으면 예약된 자동 업데이트가 지연되거나 실행되지 않을 수 있습니다. " +
                    "안정적인 자동 업데이트를 위해 이 앱을 배터리 최적화 예외로 설정해 주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    requestIgnoreBatteryOptimization(context)
                    // 사용자가 설정 화면에서 돌아오면 상태 갱신
                    isIgnoring = isBatteryOptimizationIgnored(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("배터리 최적화 예외 설정")
            }
        }
    }
}

private fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun requestIgnoreBatteryOptimization(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}

/**
 * 아코디언 헤더 부제 — 스케줄(매일 HH:mm / 자동 꺼짐)과 마지막 실행 결과(✓/✗ MM/dd HH:mm)를
 * 한 줄로 요약해, 펼치지 않아도 각 데이터소스 상태를 스캔할 수 있게 한다.
 */
private fun scheduleSummary(enabled: Boolean, hour: Int, minute: Int, lastLog: WorkerLogEntity?): String {
    val sched = if (enabled) "매일 %02d:%02d".format(hour, minute) else "자동 꺼짐"
    val last = lastLog?.let {
        val d = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(it.executedAt))
        if (it.status == STATUS_ERROR) " · ✗ $d" else " · ✓ $d"
    } ?: ""
    return sched + last
}

/** 데이터소스별 수집 기간·스케줄·수동 실행·초기화를 하나의 카드로 관리하는 통합 탭 (구 수집설정 + Schedule) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DataManagementTab(
    fearGreedCollectionDays: Int,
    onFearGreedCollectionDaysChange: (Int) -> Unit,
    etfCollectionDays: Int,
    onEtfCollectionDaysChange: (Int) -> Unit,
    marketOscCollectionDays: Int,
    onMarketOscCollectionDaysChange: (Int) -> Unit,
    marketDepositCollectionDays: Int,
    onMarketDepositCollectionDaysChange: (Int) -> Unit,
    consensusCollectionDays: Int,
    onConsensusCollectionDaysChange: (Int) -> Unit,
    onCollectionSave: () -> Unit,
    onResetData: (String) -> Unit,
    showResetConfirmDialog: String?,
    onShowResetConfirm: (String) -> Unit,
    onDismissResetConfirm: () -> Unit,
    fgScheduleEnabled: Boolean = false,
    onFgScheduleEnabledChange: (Boolean) -> Unit = {},
    fgScheduleHour: Int = 4,
    onFgScheduleHourChange: (Int) -> Unit = {},
    fgScheduleMinute: Int = 0,
    onFgScheduleMinuteChange: (Int) -> Unit = {},
    fgManualMessage: String? = null,
    isFgCollecting: Boolean = false,
    onFgManualCollect: () -> Unit = {},
    lastFearGreedLog: WorkerLogEntity? = null,
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
    consensusScheduleEnabled: Boolean = false,
    onConsensusScheduleEnabledChange: (Boolean) -> Unit = {},
    consensusScheduleHour: Int = 3,
    onConsensusScheduleHourChange: (Int) -> Unit = {},
    consensusScheduleMinute: Int = 0,
    onConsensusScheduleMinuteChange: (Int) -> Unit = {},
    consensusManualMessage: String? = null,
    isConsensusCollecting: Boolean = false,
    onConsensusManualCollect: () -> Unit = {},
    themeScheduleEnabled: Boolean = false,
    onThemeScheduleEnabledChange: (Boolean) -> Unit = {},
    themeScheduleHour: Int = 2,
    onThemeScheduleHourChange: (Int) -> Unit = {},
    themeScheduleMinute: Int = 30,
    onThemeScheduleMinuteChange: (Int) -> Unit = {},
    themeExchange: ThemeExchange = ThemeExchange.KRX,
    onThemeExchangeChange: (ThemeExchange) -> Unit = {},
    themeManualMessage: String? = null,
    isThemeCollecting: Boolean = false,
    onThemeManualCollect: () -> Unit = {},
    lastThemeLog: WorkerLogEntity? = null,
    integrityCheckMessage: String?,
    integrityCheckProgress: Float? = null,
    isIntegrityChecking: Boolean = false,
    onIntegrityCheck: () -> Unit = {},
    lastEtfLog: WorkerLogEntity? = null,
    lastOscLog: WorkerLogEntity? = null,
    lastDepositLog: WorkerLogEntity? = null,
    lastMarketCloseLog: WorkerLogEntity? = null,
    lastConsensusLog: WorkerLogEntity? = null,
    lastIntegrityLog: WorkerLogEntity? = null,
    saveMessage: String?,
    onSave: () -> Unit
) {
    // 펼침 상태(로컬) — 기본 전부 접힘. 화면 회전에도 유지되도록 rememberSaveable + listSaver.
    var expandedSources by rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })
    ) { mutableStateOf(emptySet<String>()) }
    val toggle: (String) -> Unit = { key ->
        expandedSources = if (key in expandedSources) expandedSources - key else expandedSources + key
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BatteryOptimizationCard()

        AccordionCard(
            title = "Fear & Greed",
            subtitle = scheduleSummary(fgScheduleEnabled, fgScheduleHour, fgScheduleMinute, lastFearGreedLog),
            expanded = "feargreed" in expandedSources,
            onToggle = { toggle("feargreed") }
        ) {
            ScheduleSection(
                enabled = fgScheduleEnabled,
                onEnabledChange = onFgScheduleEnabledChange,
                hour = fgScheduleHour,
                onHourChange = onFgScheduleHourChange,
                minute = fgScheduleMinute,
                onMinuteChange = onFgScheduleMinuteChange,
                manualButtonText = "지금 Fear & Greed 업데이트",
                onManualCollect = onFgManualCollect,
                message = fgManualMessage,
                isCollecting = isFgCollecting,
                lastResult = lastFearGreedLog,
                collectionDays = fearGreedCollectionDays,
                onCollectionDaysChange = onFearGreedCollectionDaysChange,
                onCollectionSave = onCollectionSave,
                onResetData = { onShowResetConfirm("feargreed") }
            )
        }

        AccordionCard(
            title = "ETF",
            subtitle = scheduleSummary(etfScheduleEnabled, scheduleHour, scheduleMinute, lastEtfLog),
            expanded = "etf" in expandedSources,
            onToggle = { toggle("etf") }
        ) {
            ScheduleSection(
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
                lastResult = lastEtfLog,
                collectionDays = etfCollectionDays,
                onCollectionDaysChange = onEtfCollectionDaysChange,
                onCollectionSave = onCollectionSave,
                onResetData = { onShowResetConfirm("etf") }
            )
        }

        AccordionCard(
            title = "과매수/과매도",
            subtitle = scheduleSummary(oscScheduleEnabled, oscScheduleHour, oscScheduleMinute, lastOscLog),
            expanded = "oscillator" in expandedSources,
            onToggle = { toggle("oscillator") }
        ) {
            ScheduleSection(
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
                lastResult = lastOscLog,
                collectionDays = marketOscCollectionDays,
                onCollectionDaysChange = onMarketOscCollectionDaysChange,
                onCollectionSave = onCollectionSave,
                onResetData = { onShowResetConfirm("oscillator") }
            )
        }

        AccordionCard(
            title = "자금 동향",
            subtitle = scheduleSummary(depositScheduleEnabled, depositScheduleHour, depositScheduleMinute, lastDepositLog),
            expanded = "deposit" in expandedSources,
            onToggle = { toggle("deposit") }
        ) {
            ScheduleSection(
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
                lastResult = lastDepositLog,
                collectionDays = marketDepositCollectionDays,
                onCollectionDaysChange = onMarketDepositCollectionDaysChange,
                onCollectionSave = onCollectionSave,
                onResetData = { onShowResetConfirm("deposit") }
            )
        }

        AccordionCard(
            title = "장 마감 데이터 교체",
            subtitle = scheduleSummary(marketCloseRefreshEnabled, marketCloseRefreshHour, marketCloseRefreshMinute, lastMarketCloseLog),
            expanded = "market_close" in expandedSources,
            onToggle = { toggle("market_close") }
        ) {
            ScheduleSection(
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

        AccordionCard(
            title = "리포트",
            subtitle = scheduleSummary(consensusScheduleEnabled, consensusScheduleHour, consensusScheduleMinute, lastConsensusLog),
            expanded = "consensus" in expandedSources,
            onToggle = { toggle("consensus") }
        ) {
            ScheduleSection(
                enabled = consensusScheduleEnabled,
                onEnabledChange = onConsensusScheduleEnabledChange,
                hour = consensusScheduleHour,
                onHourChange = onConsensusScheduleHourChange,
                minute = consensusScheduleMinute,
                onMinuteChange = onConsensusScheduleMinuteChange,
                manualButtonText = "지금 리포트 데이터 수집",
                onManualCollect = onConsensusManualCollect,
                message = consensusManualMessage,
                isCollecting = isConsensusCollecting,
                lastResult = lastConsensusLog,
                collectionDays = consensusCollectionDays,
                onCollectionDaysChange = onConsensusCollectionDaysChange,
                onCollectionSave = onCollectionSave,
                onResetData = { onShowResetConfirm("consensus") }
            )
        }

        AccordionCard(
            title = "테마 자동 업데이트",
            subtitle = scheduleSummary(themeScheduleEnabled, themeScheduleHour, themeScheduleMinute, lastThemeLog),
            expanded = "theme" in expandedSources,
            onToggle = { toggle("theme") }
        ) {
            ScheduleSection(
                enabled = themeScheduleEnabled,
                onEnabledChange = onThemeScheduleEnabledChange,
                hour = themeScheduleHour,
                onHourChange = onThemeScheduleHourChange,
                minute = themeScheduleMinute,
                onMinuteChange = onThemeScheduleMinuteChange,
                manualButtonText = "지금 테마 데이터 수집",
                onManualCollect = onThemeManualCollect,
                message = themeManualMessage,
                isCollecting = isThemeCollecting,
                lastResult = lastThemeLog
            )
            Spacer(Modifier.height(8.dp))
            Text("테마 거래소", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeExchange.entries.forEachIndexed { index, ex ->
                    SegmentedButton(
                        selected = themeExchange == ex,
                        onClick = { onThemeExchangeChange(ex) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ThemeExchange.entries.size
                        )
                    ) {
                        Text(ex.displayName)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "거래소 변경은 다음 갱신부터 반영됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val integritySummary = lastIntegrityLog?.let {
            val d = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(it.executedAt))
            if (it.status == STATUS_ERROR) "✗ $d" else "✓ $d"
        } ?: "수동 실행 · 데이터 정합 검사"
        AccordionCard(
            title = "데이터 무결성 검사",
            subtitle = integritySummary,
            expanded = "integrity" in expandedSources,
            onToggle = { toggle("integrity") }
        ) {
            Text(
                "Fear & Greed, ETF, 과매수/과매도, 자금 동향 데이터를 최신 데이터와 비교하여 불일치 항목을 수정합니다.",
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

    // 데이터 초기화 확인 다이얼로그
    showResetConfirmDialog?.let { dataType ->
        val label = when (dataType) {
            "feargreed" -> "Fear & Greed"
            "etf" -> "ETF"
            "oscillator" -> "과매수/과매도"
            "deposit" -> "자금 동향"
            "consensus" -> "리포트"
            else -> dataType
        }
        AlertDialog(
            onDismissRequest = onDismissResetConfirm,
            title = { Text("$label 데이터 초기화") },
            text = {
                Text("$label 데이터를 모두 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.\n다음 수집 시 설정된 기간만큼 다시 수집됩니다.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onResetData(dataType)
                    onDismissResetConfirm()
                }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissResetConfirm) {
                    Text("취소")
                }
            }
        )
    }
}
