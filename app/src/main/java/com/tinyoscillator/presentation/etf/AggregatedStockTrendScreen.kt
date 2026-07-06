package com.tinyoscillator.presentation.etf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mikephil.charting.data.Entry
import java.text.NumberFormat
import java.util.Locale

/** 전체 ETF 집계 종목 추이 — 레이아웃은 [TrendScreenTemplate] 공용 */
@Composable
fun AggregatedStockTrendScreen(
    onBack: () -> Unit,
    viewModel: AggregatedStockTrendViewModel = hiltViewModel()
) {
    val stockName by viewModel.stockName.collectAsStateWithLifecycle()
    val data by viewModel.filteredData.collectAsStateWithLifecycle()
    val selectedRange by viewModel.selectedRange.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    val displayName = stockName ?: viewModel.getStockTicker()
    val latest = data.lastOrNull()

    TrendScreenTemplate(
        title = displayName,
        onBack = onBack,
        isLoading = isLoading,
        headerTitle = "$displayName (${viewModel.getStockTicker()})",
        headerSubtitle = null,
        summaryItems = latest?.let {
            listOf(
                "총금액" to "${numberFormat.format(it.totalAmount / 100_000_000)}억원",
                "ETF수" to "${it.etfCount}개",
                "최대비중" to (it.maxWeight?.let { w -> "%.2f%%".format(w) } ?: "-"),
                "평균비중" to (it.avgWeight?.let { w -> "%.2f%%".format(w) } ?: "-")
            )
        } ?: emptyList(),
        selectedRange = selectedRange,
        onSelectRange = { viewModel.selectRange(it) },
        dateLabels = data.map { formatTrendDate(it.date) },
        charts = listOf(
            TrendChartSpec(
                title = "총금액 추이",
                label = "총금액(억)",
                colorHex = "#0077B6",
                entries = data.mapIndexed { index, d ->
                    Entry(index.toFloat(), (d.totalAmount / 100_000_000f))
                }
            ),
            TrendChartSpec(
                title = "최대비중 추이",
                label = "최대비중(%)",
                colorHex = "#6750A4",
                entries = data.mapIndexedNotNull { index, d ->
                    d.maxWeight?.let { Entry(index.toFloat(), it.toFloat()) }
                }
            ),
            TrendChartSpec(
                title = "평균비중 추이",
                label = "평균비중(%)",
                colorHex = "#2D6A4F",
                entries = data.mapIndexedNotNull { index, d ->
                    d.avgWeight?.let { Entry(index.toFloat(), it.toFloat()) }
                }
            )
        ),
        tableColumns = listOf(
            TrendTableColumn("날짜", 1f, TextAlign.Start),
            TrendTableColumn("총금액(억)", 1f),
            TrendTableColumn("ETF수", 0.6f, TextAlign.Center),
            TrendTableColumn("최대비중", 0.8f),
            TrendTableColumn("평균비중", 0.8f)
        ),
        tableRows = data.takeLast(5).reversed().map { row ->
            listOf(
                formatTrendDate(row.date),
                numberFormat.format(row.totalAmount / 100_000_000),
                "${row.etfCount}",
                row.maxWeight?.let { "%.2f".format(it) } ?: "-",
                row.avgWeight?.let { "%.2f".format(it) } ?: "-"
            )
        }
    )
}
