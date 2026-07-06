package com.tinyoscillator.presentation.etf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mikephil.charting.data.Entry
import java.text.NumberFormat
import java.util.Locale

/** 단일 ETF 내 종목 비중/금액 추이 — 레이아웃은 [TrendScreenTemplate] 공용 */
@Composable
fun StockTrendScreen(
    onBack: () -> Unit,
    viewModel: StockTrendViewModel = hiltViewModel()
) {
    val stockName by viewModel.stockName.collectAsStateWithLifecycle()
    val etfName by viewModel.etfName.collectAsStateWithLifecycle()
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
        headerSubtitle = etfName,
        summaryItems = latest?.let {
            listOf(
                "최근 비중" to (it.weight?.let { w -> "%.2f%%".format(w) } ?: "-"),
                "최근 금액" to "${numberFormat.format(it.amount / 100_000_000)}억원",
                "데이터 수" to "${data.size}건"
            )
        } ?: emptyList(),
        selectedRange = selectedRange,
        onSelectRange = { viewModel.selectRange(it) },
        dateLabels = data.map { formatTrendDate(it.date) },
        charts = listOf(
            TrendChartSpec(
                title = "비중 추이",
                label = "비중(%)",
                colorHex = "#6750A4",
                entries = data.mapIndexedNotNull { index, d ->
                    d.weight?.let { Entry(index.toFloat(), it.toFloat()) }
                }
            ),
            TrendChartSpec(
                title = "금액 추이",
                label = "금액(억)",
                colorHex = "#0077B6",
                entries = data.mapIndexed { index, d ->
                    Entry(index.toFloat(), (d.amount / 100_000_000f))
                }
            )
        ),
        tableColumns = listOf(
            TrendTableColumn("날짜", 1f, TextAlign.Start),
            TrendTableColumn("비중(%)", 1f),
            TrendTableColumn("금액(억)", 1f)
        ),
        tableRows = data.takeLast(5).reversed().map { row ->
            listOf(
                formatTrendDate(row.date),
                row.weight?.let { "%.2f".format(it) } ?: "-",
                numberFormat.format(row.amount / 100_000_000)
            )
        }
    )
}
