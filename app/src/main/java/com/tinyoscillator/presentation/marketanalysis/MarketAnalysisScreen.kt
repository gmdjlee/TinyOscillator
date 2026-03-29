package com.tinyoscillator.presentation.marketanalysis

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.utils.MPPointF
import com.github.mikephil.charting.utils.Utils
import com.tinyoscillator.R
import com.tinyoscillator.core.worker.FearGreedUpdateWorker
import com.tinyoscillator.domain.model.DemarkPeriodType
import com.tinyoscillator.domain.model.MarketDemarkChartData
import com.tinyoscillator.domain.model.MarketDemarkRow
import com.tinyoscillator.presentation.common.CollectionProgressBar
import com.tinyoscillator.presentation.common.GlassCard
import com.tinyoscillator.presentation.common.PillTabRow
import com.tinyoscillator.presentation.common.ThemeToggleIcon
import com.tinyoscillator.ui.theme.LocalThemeModeState

private enum class MarketAnalysisTab(val label: String) {
    FEAR_GREED("Fear & Greed"),
    DEMARK("DeMark")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketAnalysisScreen(
    onSettingsClick: () -> Unit
) {
    val fearGreedViewModel: FearGreedViewModel = hiltViewModel()
    val demarkViewModel: MarketDemarkViewModel = hiltViewModel()
    var selectedTab by rememberSaveable { mutableStateOf(MarketAnalysisTab.FEAR_GREED) }
    val themeModeState = LocalThemeModeState.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("시장분석") },
                actions = {
                    if (selectedTab == MarketAnalysisTab.FEAR_GREED) {
                        IconButton(onClick = { fearGreedViewModel.update() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                        }
                    } else {
                        IconButton(onClick = { demarkViewModel.loadData() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "새로고침")
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            CollectionProgressBar(tag = FearGreedUpdateWorker.TAG)

            PillTabRow(
                tabs = MarketAnalysisTab.entries.toList(),
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                tabLabel = { it.label }
            )

            when (selectedTab) {
                MarketAnalysisTab.FEAR_GREED -> FearGreedTab(viewModel = fearGreedViewModel)
                MarketAnalysisTab.DEMARK -> MarketDemarkTab(viewModel = demarkViewModel)
            }
        }
    }
}

// ===== Fear & Greed Tab =====

@Composable
private fun FearGreedTab(viewModel: FearGreedViewModel) {
    val state by viewModel.state.collectAsState()
    val selectedMarket by viewModel.selectedMarket.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 시장 선택
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("시장 선택", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedMarket == "KOSPI",
                        onClick = { viewModel.selectMarket("KOSPI") },
                        label = { Text("코스피") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedMarket == "KOSDAQ",
                        onClick = { viewModel.selectMarket("KOSDAQ") },
                        label = { Text("코스닥") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 기간 선택
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FearGreedDateRange.entries.forEach { range ->
                FilterChip(
                    selected = selectedRange == range,
                    onClick = { viewModel.selectDateRange(range) },
                    label = { Text(range.label) }
                )
            }
        }

        // 상태 표시
        when (val currentState = state) {
            is FearGreedState.Loading -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            is FearGreedState.Success -> {
                FearGreedChart(
                    chartData = currentState.chartData,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is FearGreedState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        currentState.message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            is FearGreedState.Idle -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "설정 > Schedule에서 데이터를 수집해주세요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ===== DeMark Tab =====

@Composable
private fun MarketDemarkTab(viewModel: MarketDemarkViewModel) {
    val state by viewModel.state.collectAsState()
    val selectedMarket by viewModel.selectedMarket.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 시장 선택
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("시장 선택", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedMarket == "KOSPI",
                        onClick = { viewModel.selectMarket("KOSPI") },
                        label = { Text("코스피") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedMarket == "KOSDAQ",
                        onClick = { viewModel.selectMarket("KOSDAQ") },
                        label = { Text("코스닥") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 기간 선택 (일봉/주봉)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DemarkPeriodType.entries.forEach { period ->
                FilterChip(
                    selected = selectedPeriod == period,
                    onClick = { viewModel.selectPeriod(period) },
                    label = { Text(period.label) }
                )
            }
        }

        // 상태 표시
        when (val currentState = state) {
            is MarketDemarkState.Loading -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            is MarketDemarkState.Success -> {
                MarketDemarkChart(
                    chartData = currentState.chartData,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is MarketDemarkState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        currentState.message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            is MarketDemarkState.Idle -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "시장을 선택하고 데이터를 불러와주세요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.loadData() }) {
                            Text("데이터 조회")
                        }
                    }
                }
            }
        }
    }
}

// ===== Market DeMark Chart =====

/**
 * 시장 DeMark TD Sequential 차트 Composable
 *
 * - 좌측 Y축: 시장 지수 — 파란 라인
 * - 우측 Y축: TD 카운트 범위
 *   - TD Sell 라인 (빨강, 양수 영역)
 *   - TD Buy 라인 (파랑, 음수 반전 표시 = -count)
 * - 9+ 카운트 시 circleRadius 확대
 */
@Composable
private fun MarketDemarkChart(
    chartData: MarketDemarkChartData,
    modifier: Modifier = Modifier
) {
    val lastBound = remember { arrayOfNulls<MarketDemarkChartData>(1) }
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val chartTextColor = if (isDarkTheme) Color.WHITE else Color.DKGRAY

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${chartData.market} DeMark TD (${chartData.periodType.label})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))

            AndroidView(
                factory = { context ->
                    CombinedChart(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setupMarketDemarkChart(this, chartTextColor)
                    }
                },
                update = { chart ->
                    chart.xAxis.textColor = chartTextColor
                    chart.legend.textColor = chartTextColor
                    chart.axisLeft.textColor = chartTextColor
                    chart.axisRight.textColor = chartTextColor
                    val gridColor = if (isDarkTheme) Color.parseColor("#444444") else Color.parseColor("#CCCCCC")
                    chart.axisLeft.gridColor = gridColor
                    if (chartData != lastBound[0]) {
                        bindMarketDemarkData(chart, chartData, isDarkTheme)
                        lastBound[0] = chartData
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )
        }
    }
}

private fun setupMarketDemarkChart(chart: CombinedChart, chartTextColor: Int) {
    val isDark = chartTextColor == Color.WHITE
    chart.apply {
        description.isEnabled = false
        setDrawGridBackground(false)
        setDrawBarShadow(false)
        isHighlightFullBarEnabled = false
        setDrawOrder(arrayOf(CombinedChart.DrawOrder.LINE))

        val gColor = if (isDark) Color.parseColor("#444444") else Color.parseColor("#CCCCCC")
        val dashLen = Utils.convertDpToPixel(4f)
        val dashGap = Utils.convertDpToPixel(4f)
        val labelCount = 6

        setExtraOffsets(8f, 8f, 8f, 8f)

        axisLeft.apply {
            setDrawGridLines(true)
            gridColor = gColor
            gridLineWidth = 0.5f
            enableGridDashedLine(dashLen, dashGap, 0f)
            textColor = chartTextColor
            setLabelCount(labelCount, true)
        }

        axisRight.apply {
            setDrawGridLines(false)
            textColor = chartTextColor
            setLabelCount(labelCount, true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val absVal = kotlin.math.abs(value.toInt())
                    return if (value >= 0) "S$absVal" else "B$absVal"
                }
            }
        }

        xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            setDrawGridLines(false)
            textColor = chartTextColor
        }

        legend.apply {
            isEnabled = true
            verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
            setDrawInside(false)
            textColor = chartTextColor
        }

        setTouchEnabled(true)
        isDragEnabled = true
        setScaleEnabled(true)
        setPinchZoom(true)
    }
}

private fun bindMarketDemarkData(
    chart: CombinedChart,
    chartData: MarketDemarkChartData,
    isDarkTheme: Boolean = false
) {
    val rows = chartData.rows
    if (rows.isEmpty()) return

    val labels = rows.map { row ->
        if (row.date.length >= 10) {
            "${row.date.substring(5, 7)}/${row.date.substring(8, 10)}"
        } else if (row.date.length >= 8) {
            "${row.date.substring(4, 6)}/${row.date.substring(6, 8)}"
        } else row.date
    }
    chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)

    // 시장 지수 — 좌측 Y축
    val indexEntries = rows.mapIndexed { i, row ->
        Entry(i.toFloat(), row.indexValue.toFloat())
    }
    val indexDataSet = LineDataSet(indexEntries, "${chartData.market} 지수").apply {
        color = Color.parseColor("#1976D2")
        lineWidth = 2f
        setDrawCircles(false)
        setDrawValues(false)
        axisDependency = YAxis.AxisDependency.LEFT
        isHighlightEnabled = true
        highLightColor = Color.parseColor("#1976D2")
    }

    // 좌측 Y축 범위
    val indexValues = rows.map { it.indexValue.toFloat() }
    val indexMin = indexValues.min()
    val indexMax = indexValues.max()
    val indexPadding = (indexMax - indexMin) * 0.05f
    chart.axisLeft.axisMinimum = indexMin - indexPadding
    chart.axisLeft.axisMaximum = indexMax + indexPadding

    // TD Sell — 우측 Y축 (양수 영역, 빨강)
    val sellEntries = rows.mapIndexed { i, row ->
        Entry(i.toFloat(), row.tdSellCount.toFloat())
    }
    val sellDataSet = LineDataSet(sellEntries, "TD Sell").apply {
        color = Color.parseColor("#F44336")
        lineWidth = 1.5f
        setDrawCircles(true)
        setDrawValues(false)
        axisDependency = YAxis.AxisDependency.RIGHT
        isHighlightEnabled = true
        highLightColor = Color.parseColor("#F44336")
        circleColors = rows.map { Color.parseColor("#F44336") }
        circleRadius = 3f
        setCircleHoleColor(if (isDarkTheme) Color.parseColor("#1C1B1F") else Color.WHITE)
        circleHoleRadius = 1.5f
    }

    // TD Buy — 우측 Y축 (음수 반전 표시, 파랑)
    val buyEntries = rows.mapIndexed { i, row ->
        Entry(i.toFloat(), -row.tdBuyCount.toFloat())
    }
    val buyDataSet = LineDataSet(buyEntries, "TD Buy").apply {
        color = Color.parseColor("#2196F3")
        lineWidth = 1.5f
        setDrawCircles(true)
        setDrawValues(false)
        axisDependency = YAxis.AxisDependency.RIGHT
        isHighlightEnabled = true
        highLightColor = Color.parseColor("#2196F3")
        circleColors = rows.map { Color.parseColor("#2196F3") }
        circleRadius = 3f
        setCircleHoleColor(if (isDarkTheme) Color.parseColor("#1C1B1F") else Color.WHITE)
        circleHoleRadius = 1.5f
    }

    // 9+ 카운트 강조 scatter 데이터셋
    val sellHighlightEntries = mutableListOf<Entry>()
    val buyHighlightEntries = mutableListOf<Entry>()
    rows.forEachIndexed { i, row ->
        if (row.tdSellCount >= 9) {
            sellHighlightEntries.add(Entry(i.toFloat(), row.tdSellCount.toFloat()))
        }
        if (row.tdBuyCount >= 9) {
            buyHighlightEntries.add(Entry(i.toFloat(), -row.tdBuyCount.toFloat()))
        }
    }

    val dataSets = mutableListOf<ILineDataSet>(indexDataSet, sellDataSet, buyDataSet)

    if (sellHighlightEntries.isNotEmpty()) {
        val sellHighlight = LineDataSet(sellHighlightEntries, "Sell 9+").apply {
            color = Color.TRANSPARENT
            lineWidth = 0f
            setDrawCircles(true)
            circleRadius = 6f
            circleColors = listOf(Color.parseColor("#F44336"))
            setCircleHoleColor(Color.parseColor("#F44336"))
            circleHoleRadius = 3f
            setDrawValues(false)
            axisDependency = YAxis.AxisDependency.RIGHT
            isHighlightEnabled = false
        }
        dataSets.add(sellHighlight)
    }

    if (buyHighlightEntries.isNotEmpty()) {
        val buyHighlight = LineDataSet(buyHighlightEntries, "Buy 9+").apply {
            color = Color.TRANSPARENT
            lineWidth = 0f
            setDrawCircles(true)
            circleRadius = 6f
            circleColors = listOf(Color.parseColor("#2196F3"))
            setCircleHoleColor(Color.parseColor("#2196F3"))
            circleHoleRadius = 3f
            setDrawValues(false)
            axisDependency = YAxis.AxisDependency.RIGHT
            isHighlightEnabled = false
        }
        dataSets.add(buyHighlight)
    }

    // 우측 Y축 범위 설정
    val maxSell = rows.maxOf { it.tdSellCount }
    val maxBuy = rows.maxOf { it.tdBuyCount }
    val maxCount = maxOf(maxSell, maxBuy, 9)  // 최소 9까지 표시
    chart.axisRight.axisMinimum = -(maxCount + 1).toFloat()
    chart.axisRight.axisMaximum = (maxCount + 1).toFloat()

    chart.data = CombinedData().apply {
        setData(LineData(dataSets))
    }

    // 마커 설정
    val marker = MarketDemarkMarkerView(chart.context, labels, chartData)
    marker.chartView = chart
    chart.marker = marker

    chart.invalidate()
}

/** 시장 DeMark 차트 데이터 포인트 선택 시 값을 표시하는 MarkerView */
private class MarketDemarkMarkerView(
    context: Context,
    private val labels: List<String>,
    private val chartData: MarketDemarkChartData
) : MarkerView(context, R.layout.chart_marker_view) {

    private val tvContent: TextView = findViewById(R.id.marker_text)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e == null || highlight == null) {
            super.refreshContent(e, highlight)
            return
        }

        val xIndex = e.x.toInt()
        val date = labels.getOrElse(xIndex) { "" }
        val row = chartData.rows.getOrNull(xIndex)

        val valueText = when (highlight.dataSetIndex) {
            0 -> "${chartData.market} 지수: ${String.format("%.2f", e.y)}"
            1 -> "TD Sell: ${row?.tdSellCount ?: 0}"
            2 -> "TD Buy: ${row?.tdBuyCount ?: 0}"
            else -> ""
        }

        tvContent.text = "$date\n$valueText"
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat())
    }
}
