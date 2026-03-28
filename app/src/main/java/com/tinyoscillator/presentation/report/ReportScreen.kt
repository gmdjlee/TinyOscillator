package com.tinyoscillator.presentation.report

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.domain.model.ConsensusFilter
import com.tinyoscillator.domain.model.ConsensusFilterOptions
import com.tinyoscillator.domain.model.ConsensusReport
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
        filter.institution != null || filter.stockName != null

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

// 컬럼 weight 비율 정의
private object ColWeights {
    const val DATE = 0.8f
    const val NAME = 1.0f
    const val TITLE = 2.5f
    const val OPINION = 0.6f
    const val TARGET = 0.9f
    const val CURRENT = 0.9f
    const val DIV = 0.6f
    const val INST = 0.9f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportTable(
    reports: List<ConsensusReport>,
    filter: ConsensusFilter,
    filterOptions: ConsensusFilterOptions,
    onFilterChanged: (ConsensusFilter) -> Unit
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    val financeColors = LocalFinanceColors.current

    // 날짜 필터 — DatePicker 다이얼로그
    var showDatePicker by remember { mutableStateOf(false) }
    var showStockNameSearch by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DateHeaderCell(
                label = "작성일",
                selectedDate = filter.dateRange?.first,
                modifier = Modifier.weight(ColWeights.DATE),
                onClick = { showDatePicker = true }
            )
            SearchableHeaderCell(
                label = "종목명",
                modifier = Modifier.weight(ColWeights.NAME),
                selectedValue = filter.stockName,
                onClick = { showStockNameSearch = true }
            )
            HeaderCell("제목", Modifier.weight(ColWeights.TITLE))
            FilterableHeaderCell(
                label = "의견",
                modifier = Modifier.weight(ColWeights.OPINION),
                selectedValue = filter.opinion,
                options = filterOptions.opinions,
                onSelected = { onFilterChanged(filter.copy(opinion = it)) }
            )
            HeaderCell("목표가", Modifier.weight(ColWeights.TARGET))
            HeaderCell("현재가", Modifier.weight(ColWeights.CURRENT))
            HeaderCell("괴리율", Modifier.weight(ColWeights.DIV))
            FilterableHeaderCell(
                label = "작성기관",
                modifier = Modifier.weight(ColWeights.INST),
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
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DataCell(formatShortDate(report.writeDate), Modifier.weight(ColWeights.DATE))
                        DataCell(report.stockName, Modifier.weight(ColWeights.NAME))
                        DataCell(report.title, Modifier.weight(ColWeights.TITLE), maxLines = 2)
                        DataCell(report.opinion, Modifier.weight(ColWeights.OPINION))
                        DataCell(
                            text = if (report.targetPrice > 0) numberFormat.format(report.targetPrice) else "-",
                            modifier = Modifier.weight(ColWeights.TARGET),
                            textAlign = TextAlign.End
                        )
                        DataCell(
                            text = if (report.currentPrice > 0) numberFormat.format(report.currentPrice) else "-",
                            modifier = Modifier.weight(ColWeights.CURRENT),
                            textAlign = TextAlign.End
                        )

                        val divergenceColor = when {
                            report.divergenceRate > 0 -> financeColors.positive
                            report.divergenceRate < 0 -> financeColors.negative
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Text(
                            text = if (report.targetPrice > 0) String.format("%.1f", report.divergenceRate) else "-",
                            modifier = Modifier.weight(ColWeights.DIV),
                            style = MaterialTheme.typography.bodySmall,
                            color = divergenceColor,
                            textAlign = TextAlign.End,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )

                        DataCell(report.institution, Modifier.weight(ColWeights.INST))
                    }
                }
            }
        }
    }

    // DatePicker 다이얼로그
    if (showDatePicker) {
        ReportDatePickerDialog(
            selectedDate = filter.dateRange?.first,
            availableDates = filterOptions.dates,
            onDateSelected = { isoDate ->
                if (isoDate == null) {
                    onFilterChanged(filter.copy(dateRange = null))
                } else {
                    onFilterChanged(filter.copy(dateRange = Pair(isoDate, isoDate)))
                }
            },
            onDismiss = { showDatePicker = false }
        )
    }

    // 종목명 검색 다이얼로그
    if (showStockNameSearch) {
        StockNameSearchDialog(
            selectedValue = filter.stockName,
            stockNames = filterOptions.stockNames,
            onSelected = { name ->
                onFilterChanged(filter.copy(stockName = name))
            },
            onDismiss = { showStockNameSearch = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportDatePickerDialog(
    selectedDate: String?,
    availableDates: List<String>,
    onDateSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val isoFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val initialMillis = remember(selectedDate) {
        selectedDate?.let {
            try {
                LocalDate.parse(it, isoFormatter)
                    .atStartOfDay(ZoneId.of("UTC"))
                    .toInstant()
                    .toEpochMilli()
            } catch (_: Exception) { null }
        }
    }

    // 날짜 범위 제한: availableDates의 min ~ max
    val (minMillis, maxMillis) = remember(availableDates) {
        if (availableDates.isEmpty()) Pair(null, null)
        else {
            val sorted = availableDates.sorted()
            val min = LocalDate.parse(sorted.first(), isoFormatter)
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
            val max = LocalDate.parse(sorted.last(), isoFormatter)
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
            Pair(min, max)
        }
    }

    // 데이터 있는 날짜만 선택 가능
    val availableMillisSet = remember(availableDates) {
        availableDates.mapNotNull { dateStr ->
            try {
                LocalDate.parse(dateStr, isoFormatter)
                    .atStartOfDay(ZoneId.of("UTC"))
                    .toInstant()
                    .toEpochMilli()
            } catch (_: Exception) { null }
        }.toSet()
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return availableMillisSet.contains(utcTimeMillis)
            }

            override fun isSelectableYear(year: Int): Boolean {
                return true
            }
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = datePickerState.selectedDateMillis
                if (millis != null) {
                    val date = Instant.ofEpochMilli(millis)
                        .atZone(ZoneId.of("UTC"))
                        .toLocalDate()
                        .format(isoFormatter)
                    onDateSelected(date)
                }
                onDismiss()
            }) {
                Text("확인")
            }
        },
        dismissButton = {
            Row {
                if (selectedDate != null) {
                    TextButton(onClick = {
                        onDateSelected(null)
                        onDismiss()
                    }) {
                        Text("전체")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("취소")
                }
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun DateHeaderCell(
    label: String,
    selectedDate: String?,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val isFiltered = selectedDate != null
    val headerColor = if (isFiltered) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .clickable { onClick() }
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (selectedDate != null) formatShortDate(selectedDate) else label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = headerColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            Icons.Default.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = headerColor
        )
    }
}

/**
 * 검색 가능한 컬럼 헤더. 클릭하면 검색 다이얼로그를 표시.
 */
@Composable
private fun SearchableHeaderCell(
    label: String,
    modifier: Modifier,
    selectedValue: String?,
    onClick: () -> Unit
) {
    val isFiltered = selectedValue != null
    val headerColor = if (isFiltered) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .clickable { onClick() }
            .padding(horizontal = 2.dp),
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
            Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = headerColor
        )
    }
}

/**
 * 종목명 검색 다이얼로그. 검색창 + 필터링된 목록.
 */
@Composable
private fun StockNameSearchDialog(
    selectedValue: String?,
    stockNames: List<String>,
    onSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredNames = remember(searchQuery, stockNames) {
        if (searchQuery.isBlank()) stockNames
        else stockNames.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = 360.dp)
            .heightIn(max = 480.dp),
        title = { Text("종목명 검색") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("종목명 입력") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "지우기")
                            }
                        }
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // "전체" 선택 항목
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelected(null)
                            onDismiss()
                        },
                    color = if (selectedValue == null) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ) {
                    Text(
                        text = "전체",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        fontWeight = if (selectedValue == null) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                HorizontalDivider()

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(filteredNames) { name ->
                        val isSelected = name == selectedValue
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelected(name)
                                    onDismiss()
                                },
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ) {
                            Text(
                                text = name,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    if (filteredNames.isEmpty()) {
                        item {
                            Text(
                                text = "검색 결과 없음",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

/**
 * 필터 가능한 컬럼 헤더. 클릭하면 드롭다운 메뉴를 표시.
 */
@Composable
private fun FilterableHeaderCell(
    label: String,
    modifier: Modifier,
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

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 2.dp),
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
 * "2026-03-23" -> "26/03/23"
 */
private fun formatShortDate(date: String): String {
    if (date.length != 10) return date
    return "${date.substring(2, 4)}/${date.substring(5, 7)}/${date.substring(8, 10)}"
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 2.dp),
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
    modifier: Modifier,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = 1
) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 2.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}
