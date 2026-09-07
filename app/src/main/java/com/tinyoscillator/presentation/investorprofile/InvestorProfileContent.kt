package com.tinyoscillator.presentation.investorprofile

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.core.ui.composable.EmptyStateContent
import com.tinyoscillator.domain.model.InvestorPriceBin
import com.tinyoscillator.domain.model.InvestorProfileMetrics
import com.tinyoscillator.domain.model.InvestorProfileQuery
import com.tinyoscillator.domain.model.InvestorType
import com.tinyoscillator.domain.model.InvestorVolumeProfile
import com.tinyoscillator.domain.model.ProfileBasis
import com.tinyoscillator.domain.model.ProfileDisplayMode
import com.tinyoscillator.domain.model.ProfilePeriod
import com.tinyoscillator.presentation.chart.ext.formatKRW
import com.tinyoscillator.presentation.common.PillTabRow
import com.tinyoscillator.presentation.common.ScrollablePillTabRow
import com.tinyoscillator.ui.theme.TinyOscillatorTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

/**
 * 주체별 매물대 세부 탭. 명세: `docs/TASK_investor_volume_profile.md` §7.2, §7.4.
 * 상태·질의 4개 StateFlow를 수집해 상태 없는 [InvestorProfileBody]에 위임한다(`@Preview`·smoke test 용이).
 */
@Composable
fun InvestorProfileContent(
    ticker: String?,
    stockName: String?,
    modifier: Modifier = Modifier,
    viewModel: InvestorProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedInvestors by viewModel.selectedInvestors.collectAsStateWithLifecycle()
    val displayMode by viewModel.displayMode.collectAsStateWithLifecycle()

    LaunchedEffect(ticker, stockName) {
        if (ticker != null && stockName != null) {
            viewModel.loadForStock(ticker, stockName)
        } else {
            viewModel.clearStock()
        }
    }

    InvestorProfileBody(
        state = state,
        query = query,
        selectedInvestors = selectedInvestors,
        displayMode = displayMode,
        onToggleInvestor = viewModel::toggleInvestor,
        onPeriodSelected = viewModel::setPeriod,
        onCustomRangeConfirmed = viewModel::setCustomRange,
        onBasisSelected = viewModel::setBasis,
        onBinCountSelected = viewModel::setBinCount,
        onDisplayModeSelected = viewModel::setDisplayMode,
        onRetry = viewModel::retry,
        modifier = modifier
    )
}

/** 상태 없는 본문. [InvestorProfileContent]와 smoke test·`@Preview`가 공유한다. */
@Composable
fun InvestorProfileBody(
    state: InvestorProfileState,
    query: InvestorProfileQuery,
    selectedInvestors: Set<InvestorType>,
    displayMode: ProfileDisplayMode,
    onToggleInvestor: (InvestorType) -> Unit,
    onPeriodSelected: (ProfilePeriod) -> Unit,
    onCustomRangeConfirmed: (LocalDate, LocalDate) -> Unit,
    onBasisSelected: (ProfileBasis?) -> Unit,
    onBinCountSelected: (Int) -> Unit,
    onDisplayModeSelected: (ProfileDisplayMode) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state is InvestorProfileState.NoStock) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "종목을 선택해주세요.\n검색 화면에서 종목을 검색하고 선택하세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    var showRangeDialog by rememberSaveable { mutableStateOf(false) }
    val successProfile = (state as? InvestorProfileState.Success)?.profile

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InvestorChipsRow(
            selected = selectedInvestors,
            metrics = successProfile?.metrics,
            onToggle = onToggleInvestor
        )
        Text(
            text = "최소 1개 주체는 선택되어야 합니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ScrollablePillTabRow(
            tabs = ProfilePeriod.entries.toList(),
            selectedTab = query.period,
            onTabSelected = { period ->
                if (period == ProfilePeriod.CUSTOM) showRangeDialog = true else onPeriodSelected(period)
            },
            tabLabel = { period -> periodTabLabel(period, query) }
        )

        if (showRangeDialog) {
            val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
            InvestorProfileDateRangeDialog(
                initialStart = query.customStart,
                initialEnd = query.customEnd,
                minDate = today.minusYears(3),
                maxDate = today,
                onConfirm = { start, end ->
                    onCustomRangeConfirmed(start, end)
                    showRangeDialog = false
                },
                onDismiss = { showRangeDialog = false }
            )
        }

        PillTabRow(
            tabs = listOf<ProfileBasis?>(null, ProfileBasis.CUMULATIVE_BUY, ProfileBasis.RESIDUAL),
            selectedTab = query.basis,
            onTabSelected = onBasisSelected,
            tabLabel = { it?.label ?: "자동" },
            contentPadding = PaddingValues(0.dp)
        )
        PillTabRow(
            tabs = ProfileDisplayMode.entries.toList(),
            selectedTab = displayMode,
            onTabSelected = onDisplayModeSelected,
            tabLabel = { it.label },
            contentPadding = PaddingValues(0.dp)
        )
        PillTabRow(
            tabs = listOf(10, 15, 20, 25),
            selectedTab = query.binCount,
            onTabSelected = onBinCountSelected,
            tabLabel = { "${it}구간" },
            contentPadding = PaddingValues(0.dp)
        )

        when (state) {
            InvestorProfileState.NoStock -> Unit // 위에서 이미 처리(도달 불가) — when 완전성용
            is InvestorProfileState.Loading -> LoadingBody(state)
            is InvestorProfileState.Success -> SuccessBody(state.profile, selectedInvestors, displayMode)
            is InvestorProfileState.Empty -> EmptyStateContent(message = state.message)
            is InvestorProfileState.Error -> ErrorBody(state.message, onRetry)
            is InvestorProfileState.NoApiKey -> {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingBody(state: InvestorProfileState.Loading) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        if (state.maxPages > 0) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(
                    progress = { state.page.toFloat() / state.maxPages },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "수집 중 ${state.page}/${state.maxPages}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(
                    "매물대를 계산하는 중...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ErrorBody(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "[ERROR]", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onRetry) { Text("다시 시도") }
        }
    }
}

@Composable
private fun SuccessBody(
    profile: InvestorVolumeProfile,
    selected: Set<InvestorType>,
    displayMode: ProfileDisplayMode
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val noticeStyle = MaterialTheme.typography.labelSmall
        val noticeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        Text("주체별 매물대는 일별 매매대금에서 산출한 추정치입니다.", style = noticeStyle, color = noticeColor)
        Text(
            "산출 기준: ${profile.basis.label}(${if (profile.autoBasis) "자동" else "수동"})",
            style = noticeStyle,
            color = noticeColor
        )
        Text(
            "구간 폭 ${profile.binWidth.roundToLong().formatKRW()} · " +
                "${profile.startDate}~${profile.endDate} · ${profile.tradingDays}거래일 · 최신 ${profile.endDate}",
            style = noticeStyle,
            color = noticeColor
        )
    }

    InvestorProfileChart(profile = profile, selected = selected, displayMode = displayMode)

    InvestorProfileMetricsCard(profile)

    if (profile.truncatedAt != null) {
        NoticeCard("${profile.truncatedAt} 가격 불연속(액면분할 등)을 감지해 그 이후 구간만 표시합니다.")
    }
    if (profile.swingFallback) {
        NoticeCard("스윙 전환점을 찾지 못해 최근 1년 창으로 표시합니다.")
    }
}

@Composable
private fun NoticeCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun InvestorChipsRow(
    selected: Set<InvestorType>,
    metrics: InvestorProfileMetrics?,
    onToggle: (InvestorType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InvestorType.entries.forEach { type ->
            val isSelected = type in selected
            val supplyText = metrics?.upperSupply?.get(type)?.toKoreanShares()
            val label = if (supplyText != null) "${type.label} · 상방 $supplyText" else type.label
            val selectedText = if (isSelected) "선택됨" else "선택 안 됨"
            val description = if (supplyText != null) {
                "${type.label}, $selectedText, 상방 $supplyText"
            } else {
                "${type.label}, $selectedText"
            }
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(type) },
                label = { Text(label) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(investorColor(type), CircleShape)
                    )
                },
                modifier = Modifier.semantics { contentDescription = description }
            )
        }
    }
}

@Composable
private fun investorColor(type: InvestorType): Color = when (type) {
    InvestorType.INDIVIDUAL -> MaterialTheme.colorScheme.primary
    InvestorType.FOREIGN -> MaterialTheme.colorScheme.secondary
    InvestorType.INSTITUTION -> MaterialTheme.colorScheme.tertiary
}

private val CustomRangeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")

private fun periodTabLabel(period: ProfilePeriod, query: InvestorProfileQuery): String {
    if (period == ProfilePeriod.CUSTOM && query.period == ProfilePeriod.CUSTOM) {
        val start = query.customStart
        val end = query.customEnd
        if (start != null && end != null) {
            return "${start.format(CustomRangeFormatter)}~${end.format(CustomRangeFormatter)}"
        }
    }
    return period.label
}

/**
 * 사용자 지정 기간 다이얼로그. `ReportDatePickerDialog`(`ReportScreen.kt:331-395`) 구조를 따르되
 * `DateRangePicker`(material3 1.2.0)를 쓴다. UTC epoch millis ↔ [LocalDate] 변환도 동일 관례.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvestorProfileDateRangeDialog(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    minDate: LocalDate,
    maxDate: LocalDate,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val minMillis = remember(minDate) { minDate.toUtcMillis() }
    val maxMillis = remember(maxDate) { maxDate.toUtcMillis() }
    val selectableDates = remember(minMillis, maxMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in minMillis..maxMillis
            override fun isSelectableYear(year: Int): Boolean = true
        }
    }
    val rangeState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart?.toUtcMillis(),
        initialSelectedEndDateMillis = initialEnd?.toUtcMillis(),
        selectableDates = selectableDates
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val start = rangeState.selectedStartDateMillis
                val end = rangeState.selectedEndDateMillis
                if (start != null && end != null) {
                    onConfirm(start.toLocalDateUtc(), end.toLocalDateUtc())
                } else {
                    onDismiss()
                }
            }) { Text("확인") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    ) {
        DateRangePicker(
            state = rangeState,
            showModeToggle = false,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
private fun Long.toLocalDateUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.of("UTC")).toLocalDate()

@Composable
private fun InvestorProfileMetricsCard(profile: InvestorVolumeProfile) {
    val bins = profile.bins
    val metrics = profile.metrics

    val anchorDate = profile.anchorDate
    val anchorPrice = profile.anchorPrice
    val baseValue = if (anchorDate != null && anchorPrice != null) {
        val kind = if (anchorPrice >= profile.basePrice) "고점" else "저점"
        "${profile.basePrice.formatKRW()} · 스윙 $kind ${anchorPrice.formatKRW()} (${anchorDate.format(CustomRangeFormatter)})"
    } else {
        profile.basePrice.formatKRW()
    }

    val capValue = metrics.capZone?.let { zone ->
        val lower = bins[zone.first].lower.roundToLong()
        val upper = bins[zone.last].upper.roundToLong()
        val days = "%.1f".format(metrics.capZoneVolume / metrics.averageDailyVolume)
        "${rangeText(lower, upper)} · ${metrics.capZoneVolume.toKoreanShares()} · 일평균 ${days}일분"
    } ?: "상방 매물 없음"

    val supportValue = metrics.supportZone?.let { zone ->
        val lower = bins[zone.first].lower.roundToLong()
        val upper = bins[zone.last].upper.roundToLong()
        val days = "%.1f".format(metrics.supportZoneVolume / metrics.averageDailyVolume)
        "${rangeText(lower, upper)} · ${metrics.supportZoneVolume.toKoreanShares()} · ${days}일분"
    } ?: "하방 매물 없음"

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricRow("기준가", baseValue)
            MetricRow("상방 캡", capValue)
            MetricRow("하방 지지", supportValue)
            InvestorType.entries.forEach { type ->
                val upper = (metrics.upperSupply[type] ?: 0.0).toKoreanShares()
                val lower = (metrics.lowerSupply[type] ?: 0.0).toKoreanShares()
                MetricRow(type.label, "상방 $upper · 하방 $lower")
            }
            MetricRow("POC", metrics.poc.roundToLong().formatKRW())
            MetricRow("밸류 에어리어", rangeText(metrics.valueAreaLow.roundToLong(), metrics.valueAreaHigh.roundToLong()))
            MetricRow("흡수 일수", "%.1f일".format(metrics.absorptionDays))
            MetricRow("매물 공백 구간", "${metrics.lowVolumeNodeCount}개")
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun rangeText(lower: Long, upper: Long): String = "${lower.formatKRW().dropLast(1)}~${upper.formatKRW()}"

/** 물량 표기(≥1억: 억주, ≥1만: 만주, 그 외: 주). 축 포맷터([com.tinyoscillator.presentation.chart.formatter.KoreanVolumeFormatter])는 정수 단위만 내므로 카드 전용. */
private fun Double.toKoreanShares(): String = when {
    this >= 1e8 -> "%.1f억주".format(this / 1e8)
    this >= 1e4 -> "%,d만주".format((this / 1e4).roundToLong())
    else -> "%,d주".format(this.roundToLong())
}

/** `@Preview`·androidTest smoke test 공용 합성 프로필(10구간·3주체). */
internal fun previewProfile(): InvestorVolumeProfile {
    val lowerStart = 240_000.0
    val binWidth = 2_000.0
    val bins = (0 until 10).map { i -> InvestorPriceBin(lowerStart + i * binWidth, lowerStart + (i + 1) * binWidth) }
    val individual = doubleArrayOf(8.0, 12.0, 20.0, 26.0, 30.0, 28.0, 24.0, 18.0, 11.0, 7.0).map { it * 10_000 }.toDoubleArray()
    val foreign = doubleArrayOf(4.0, 6.0, 9.0, 14.0, 18.0, 22.0, 26.0, 20.0, 12.0, 6.0).map { it * 10_000 }.toDoubleArray()
    val institution = doubleArrayOf(3.0, 5.0, 7.0, 10.0, 13.0, 16.0, 19.0, 15.0, 9.0, 4.0).map { it * 10_000 }.toDoubleArray()
    val byInvestor = mapOf(
        InvestorType.INDIVIDUAL to individual,
        InvestorType.FOREIGN to foreign,
        InvestorType.INSTITUTION to institution
    )
    val upperSupply = byInvestor.mapValues { (_, values) -> values.slice(5..9).sum() }
    val lowerSupply = byInvestor.mapValues { (_, values) -> values.slice(0..4).sum() }
    val averageDailyVolume = 900_000.0
    return InvestorVolumeProfile(
        bins = bins,
        byInvestor = byInvestor,
        basePrice = 250_000L,
        basis = ProfileBasis.RESIDUAL,
        autoBasis = true,
        startDate = LocalDate.of(2026, 6, 1),
        endDate = LocalDate.of(2026, 9, 4),
        tradingDays = 65,
        binWidth = binWidth,
        metrics = InvestorProfileMetrics(
            poc = bins[4].center,
            valueAreaHigh = bins[7].upper,
            valueAreaLow = bins[2].lower,
            upperSupply = upperSupply,
            lowerSupply = lowerSupply,
            capZone = 5..6,
            capZoneVolume = byInvestor.values.sumOf { it[5] + it[6] },
            supportZone = 2..3,
            supportZoneVolume = byInvestor.values.sumOf { it[2] + it[3] },
            lowVolumeNodeCount = 2,
            absorptionDays = 1.8,
            averageDailyVolume = averageDailyVolume
        ),
        truncatedAt = null,
        swingFallback = false,
        anchorDate = null,
        anchorPrice = null
    )
}

@Preview(showBackground = true)
@Composable
private fun InvestorProfileBodyPreview() {
    TinyOscillatorTheme {
        InvestorProfileBody(
            state = InvestorProfileState.Success(previewProfile()),
            query = InvestorProfileQuery(period = ProfilePeriod.M6, binCount = 10),
            selectedInvestors = InvestorType.entries.toSet(),
            displayMode = ProfileDisplayMode.GROUPED,
            onToggleInvestor = {},
            onPeriodSelected = {},
            onCustomRangeConfirmed = { _, _ -> },
            onBasisSelected = {},
            onBinCountSelected = {},
            onDisplayModeSelected = {},
            onRetry = {}
        )
    }
}
