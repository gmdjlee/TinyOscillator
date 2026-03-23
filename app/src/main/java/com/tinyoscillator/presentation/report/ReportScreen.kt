package com.tinyoscillator.presentation.report

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import com.tinyoscillator.presentation.common.ThemeToggleIcon
import com.tinyoscillator.ui.theme.LocalFinanceColors
import com.tinyoscillator.ui.theme.LocalThemeModeState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.domain.model.ConsensusFilter
import com.tinyoscillator.domain.model.ConsensusFilterOptions
import com.tinyoscillator.domain.model.ConsensusReport
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    onSettingsClick: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel()
) {
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val filterOptions by viewModel.filterOptions.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val reportCount by viewModel.reportCount.collectAsStateWithLifecycle()
    val themeModeState = LocalThemeModeState.current

    val hasActiveFilter = filter.dateRange != null || filter.opinion != null ||
        filter.institution != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("리포트") },
                actions = {
                    if (reportCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text("$reportCount")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (hasActiveFilter) {
                        IconButton(onClick = { viewModel.clearFilter() }) {
                            Icon(Icons.Default.Clear, contentDescription = "필터 초기화")
                        }
                    }
                    ThemeToggleIcon(themeModeState)
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (reports.isEmpty()) {
                // Show header even when empty so user can clear filters
                ReportTable(
                    reports = emptyList(),
                    filter = filter,
                    filterOptions = filterOptions,
                    onFilterChanged = { viewModel.updateFilter(it) }
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "리포트가 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                ReportTable(
                    reports = reports,
                    filter = filter,
                    filterOptions = filterOptions,
                    onFilterChanged = { viewModel.updateFilter(it) }
                )
            }
        }
    }
}

@Composable
private fun ReportTable(
    reports: List<ConsensusReport>,
    filter: ConsensusFilter,
    filterOptions: ConsensusFilterOptions,
    onFilterChanged: (ConsensusFilter) -> Unit
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    val financeColors = LocalFinanceColors.current

    // 컬럼 너비 정의 (폴드 오픈 ~690dp에 맞춤)
    val colDate = 60.dp
    val colTicker = 54.dp
    val colName = 68.dp
    val colTitle = 148.dp
    val colOpinion = 52.dp
    val colTarget = 68.dp
    val colCurrent = 68.dp
    val colDiv = 52.dp
    val colInst = 68.dp

    // 작성일 필터용 표시값 변환
    val selectedDateDisplay = filter.dateRange?.first?.let { formatShortDate(it) }

    val headerScrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header row with filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(headerScrollState)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterableHeaderCell(
                label = "작성일",
                width = colDate,
                selectedValue = selectedDateDisplay,
                options = filterOptions.dates.map { formatShortDate(it) },
                onSelected = { displayDate ->
                    if (displayDate == null) {
                        onFilterChanged(filter.copy(dateRange = null))
                    } else {
                        // "26/03/23" → "2026-03-23"로 역변환하여 필터에 저장
                        val idx = filterOptions.dates.indexOfFirst { formatShortDate(it) == displayDate }
                        if (idx >= 0) {
                            val isoDate = filterOptions.dates[idx]
                            onFilterChanged(filter.copy(dateRange = Pair(isoDate, isoDate)))
                        }
                    }
                }
            )
            HeaderCell("종목코드", colTicker)
            HeaderCell("종목명", colName)
            HeaderCell("제목", colTitle)
            FilterableHeaderCell(
                label = "의견",
                width = colOpinion,
                selectedValue = filter.opinion,
                options = filterOptions.opinions,
                onSelected = { onFilterChanged(filter.copy(opinion = it)) }
            )
            HeaderCell("목표가", colTarget)
            HeaderCell("현재가", colCurrent)
            HeaderCell("괴리율", colDiv)
            FilterableHeaderCell(
                label = "작성기관",
                width = colInst,
                selectedValue = filter.institution,
                options = filterOptions.institutions,
                onSelected = { onFilterChanged(filter.copy(institution = it)) }
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(reports, key = { "${it.writeDate}_${it.stockTicker}_${it.title}" }) { report ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DataCell(formatShortDate(report.writeDate), colDate)
                        DataCell(report.stockTicker, colTicker)
                        DataCell(report.stockName, colName)
                        DataCell(report.title, colTitle, maxLines = 2)
                        DataCell(report.opinion, colOpinion)
                        DataCell(
                            text = if (report.targetPrice > 0) numberFormat.format(report.targetPrice) else "-",
                            width = colTarget,
                            textAlign = TextAlign.End
                        )
                        DataCell(
                            text = if (report.currentPrice > 0) numberFormat.format(report.currentPrice) else "-",
                            width = colCurrent,
                            textAlign = TextAlign.End
                        )

                        val divergenceColor = when {
                            report.divergenceRate > 0 -> financeColors.positive
                            report.divergenceRate < 0 -> financeColors.negative
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Text(
                            text = if (report.targetPrice > 0) String.format("%.1f", report.divergenceRate) else "-",
                            modifier = Modifier.width(colDiv),
                            style = MaterialTheme.typography.bodySmall,
                            color = divergenceColor,
                            textAlign = TextAlign.End,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )

                        DataCell(report.institution, colInst)
                    }
                }
            }
        }
    }
}

/**
 * 필터 가능한 컬럼 헤더. 클릭하면 드롭다운 메뉴를 표시.
 */
@Composable
private fun FilterableHeaderCell(
    label: String,
    width: Dp,
    selectedValue: String?,
    options: List<String>,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isFiltered = selectedValue != null
    val headerColor = if (isFiltered) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(modifier = Modifier.width(width)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedValue ?: label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = headerColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = headerColor
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        "전체",
                        fontWeight = if (!isFiltered) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option,
                            fontWeight = if (option == selectedValue) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * "2026-03-23" → "26/03/23"
 */
private fun formatShortDate(date: String): String {
    if (date.length != 10) return date
    return "${date.substring(2, 4)}/${date.substring(5, 7)}/${date.substring(8, 10)}"
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

@Composable
private fun DataCell(
    text: String,
    width: Dp,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = 1
) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}
