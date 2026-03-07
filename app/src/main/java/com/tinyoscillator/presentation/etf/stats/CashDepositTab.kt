package com.tinyoscillator.presentation.etf.stats

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.tinyoscillator.domain.model.CashDepositRow
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CashDepositTab(
    data: List<CashDepositRow>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "원화예금 데이터가 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    val isDarkTheme = isSystemInDarkTheme()
    val latest = data.last()
    val previous = data.getOrNull(data.size - 2)
    val changeAmount = if (previous != null) latest.totalAmount - previous.totalAmount else 0L

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SummaryItem("현재 총액", "${numberFormat.format(latest.totalAmount / 100_000_000)}억원")
                    SummaryItem(
                        "변동액",
                        "${if (changeAmount >= 0) "+" else ""}${numberFormat.format(changeAmount / 100_000_000)}억원"
                    )
                    SummaryItem("ETF수", "${latest.etfCount}개")
                }
            }
        }

        // Chart
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "원화예금 추이",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val entries = data.mapIndexed { index, row ->
                        Entry(index.toFloat(), (row.totalAmount / 100_000_000f))
                    }
                    val labels = data.map { formatShortDate(it.date) }

                    AndroidView(
                        factory = { context ->
                            LineChart(context).apply {
                                description.isEnabled = false
                                legend.isEnabled = false
                                setTouchEnabled(true)
                                setScaleEnabled(false)

                                val textColor = if (isDarkTheme) AndroidColor.WHITE else AndroidColor.DKGRAY

                                xAxis.apply {
                                    position = XAxis.XAxisPosition.BOTTOM
                                    valueFormatter = IndexAxisValueFormatter(labels)
                                    granularity = 1f
                                    setDrawGridLines(false)
                                    this.textColor = textColor
                                    labelRotationAngle = -45f
                                }
                                axisLeft.apply {
                                    this.textColor = textColor
                                    setDrawGridLines(true)
                                    gridColor = if (isDarkTheme) AndroidColor.GRAY else AndroidColor.LTGRAY
                                }
                                axisRight.isEnabled = false

                                val dataSet = LineDataSet(entries, "원화예금(억)").apply {
                                    color = AndroidColor.parseColor("#6750A4")
                                    setCircleColor(AndroidColor.parseColor("#6750A4"))
                                    lineWidth = 2f
                                    circleRadius = 3f
                                    setDrawValues(false)
                                    setDrawFilled(true)
                                    fillColor = AndroidColor.parseColor("#6750A4")
                                    fillAlpha = 30
                                    mode = LineDataSet.Mode.CUBIC_BEZIER
                                }

                                this.data = LineData(dataSet)
                                invalidate()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            }
        }

        // Recent entries table
        item {
            Text(
                "최근 데이터",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        val recentData = data.takeLast(5).reversed()
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TableHeader("날짜", Modifier.weight(1f))
                TableHeader("총액(억)", Modifier.weight(1f))
                TableHeader("ETF수", Modifier.weight(0.5f))
            }
            HorizontalDivider()
        }

        recentData.forEach { row ->
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatShortDate(row.date),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        numberFormat.format(row.totalAmount / 100_000_000),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End
                    )
                    Text(
                        "${row.etfCount}",
                        modifier = Modifier.weight(0.5f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun TableHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun formatShortDate(yyyyMMdd: String): String {
    if (yyyyMMdd.length != 8) return yyyyMMdd
    return "${yyyyMMdd.substring(4, 6)}/${yyyyMMdd.substring(6, 8)}"
}
