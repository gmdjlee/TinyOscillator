package com.tinyoscillator.presentation.report

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.domain.model.ConsensusFilter
import com.tinyoscillator.domain.model.ConsensusFilterOptions
import com.tinyoscillator.domain.model.ConsensusReport
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
            ReportFilterSection(
                filter = filter,
                filterOptions = filterOptions,
                onFilterChanged = { viewModel.updateFilter(it) },
                onClearFilter = { viewModel.clearFilter() }
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (reports.isEmpty()) {
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
                ReportTable(reports = reports)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ReportFilterSection(
    filter: ConsensusFilter,
    filterOptions: ConsensusFilterOptions,
    onFilterChanged: (ConsensusFilter) -> Unit,
    onClearFilter: () -> Unit
) {
    val hasActiveFilter = filter.category != null || filter.prevOpinion != null ||
        filter.opinion != null || filter.stockTicker != null ||
        filter.author != null || filter.institution != null ||
        filter.dateRange != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterDropdownChip(
                label = "분류",
                selectedValue = filter.category,
                options = filterOptions.categories,
                onSelected = { onFilterChanged(filter.copy(category = it)) }
            )
            FilterDropdownChip(
                label = "이전의견",
                selectedValue = filter.prevOpinion,
                options = filterOptions.prevOpinions,
                onSelected = { onFilterChanged(filter.copy(prevOpinion = it)) }
            )
            FilterDropdownChip(
                label = "투자의견",
                selectedValue = filter.opinion,
                options = filterOptions.opinions,
                onSelected = { onFilterChanged(filter.copy(opinion = it)) }
            )
            FilterDropdownChip(
                label = "작성자",
                selectedValue = filter.author,
                options = filterOptions.authors,
                onSelected = { onFilterChanged(filter.copy(author = it)) }
            )
            FilterDropdownChip(
                label = "작성기관",
                selectedValue = filter.institution,
                options = filterOptions.institutions,
                onSelected = { onFilterChanged(filter.copy(institution = it)) }
            )

            if (hasActiveFilter) {
                FilterChip(
                    selected = true,
                    onClick = onClearFilter,
                    label = { Text("초기화") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "필터 초기화",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdownChip(
    label: String,
    selectedValue: String?,
    options: List<String>,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        FilterChip(
            selected = selectedValue != null,
            onClick = { expanded = true },
            label = { Text(selectedValue ?: label) }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("전체") },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ReportTable(reports: List<ConsensusReport>) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    val financeColors = LocalFinanceColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            HeaderCell("작성일", 88.dp)
            HeaderCell("종목코드", 80.dp)
            HeaderCell("제목", 180.dp)
            HeaderCell("투자의견", 72.dp)
            HeaderCell("목표가", 88.dp)
            HeaderCell("현재가", 88.dp)
            HeaderCell("괴리율(%)", 80.dp)
            HeaderCell("작성기관", 88.dp)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(reports, key = { "${it.writeDate}_${it.stockTicker}_${it.title}" }) { report ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DataCell(report.writeDate, 88.dp)
                        DataCell(report.stockTicker, 80.dp)
                        DataCell(report.title, 180.dp, maxLines = 2)
                        DataCell(report.opinion, 72.dp)
                        DataCell(
                            text = if (report.targetPrice > 0) numberFormat.format(report.targetPrice) else "-",
                            width = 88.dp,
                            textAlign = TextAlign.End
                        )
                        DataCell(
                            text = if (report.currentPrice > 0) numberFormat.format(report.currentPrice) else "-",
                            width = 88.dp,
                            textAlign = TextAlign.End
                        )

                        val divergenceColor = when {
                            report.divergenceRate > 0 -> financeColors.positive
                            report.divergenceRate < 0 -> financeColors.negative
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Text(
                            text = if (report.targetPrice > 0) String.format("%.1f", report.divergenceRate) else "-",
                            modifier = Modifier.width(80.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = divergenceColor,
                            textAlign = TextAlign.End,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )

                        DataCell(report.institution, 88.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
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
    width: androidx.compose.ui.unit.Dp,
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
