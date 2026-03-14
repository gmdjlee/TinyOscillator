package com.tinyoscillator.presentation.portfolio

import android.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.tinyoscillator.domain.model.PortfolioHoldingItem

private val CHART_COLORS = listOf(
    Color.rgb(66, 133, 244),   // Blue
    Color.rgb(234, 67, 53),    // Red
    Color.rgb(251, 188, 4),    // Yellow
    Color.rgb(52, 168, 83),    // Green
    Color.rgb(155, 89, 182),   // Purple
    Color.rgb(230, 126, 34),   // Orange
    Color.rgb(26, 188, 156),   // Teal
    Color.rgb(241, 196, 15),   // Gold
    Color.rgb(231, 76, 60),    // Coral
    Color.rgb(52, 73, 94)      // Dark Blue
)

private val OVERWEIGHT_COLOR = Color.rgb(211, 47, 47)  // Red for overweight

@Composable
fun PortfolioPieChart(
    holdings: List<PortfolioHoldingItem>,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.WHITE else Color.DKGRAY

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "비중 분포",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            AndroidView(
                factory = { context ->
                    PieChart(context).apply {
                        description.isEnabled = false
                        isDrawHoleEnabled = true
                        holeRadius = 40f
                        transparentCircleRadius = 45f
                        setUsePercentValues(true)
                        setEntryLabelTextSize(10f)
                        setEntryLabelColor(textColor)
                        legend.isEnabled = true
                        legend.textColor = textColor
                        legend.textSize = 10f
                        setDrawEntryLabels(holdings.size <= 5)
                    }
                },
                update = { chart ->
                    val entries = mutableListOf<PieEntry>()
                    val colors = mutableListOf<Int>()

                    // Sort by weight descending
                    val sorted = holdings.sortedByDescending { it.weightPercent }

                    if (sorted.size <= 7) {
                        sorted.forEach { h ->
                            entries.add(PieEntry(h.weightPercent.toFloat(), h.stockName))
                            colors.add(
                                if (h.isOverWeight) OVERWEIGHT_COLOR
                                else CHART_COLORS[entries.size % CHART_COLORS.size]
                            )
                        }
                    } else {
                        // Show top 6 individually, rest as "기타"
                        sorted.take(6).forEach { h ->
                            entries.add(PieEntry(h.weightPercent.toFloat(), h.stockName))
                            colors.add(
                                if (h.isOverWeight) OVERWEIGHT_COLOR
                                else CHART_COLORS[entries.size % CHART_COLORS.size]
                            )
                        }
                        val othersWeight = sorted.drop(6).sumOf { it.weightPercent }
                        if (othersWeight > 0) {
                            entries.add(PieEntry(othersWeight.toFloat(), "기타 (${sorted.size - 6})"))
                            colors.add(Color.GRAY)
                        }
                    }

                    val dataSet = PieDataSet(entries, "").apply {
                        this.colors = colors
                        sliceSpace = 2f
                        valueTextSize = 11f
                        valueTextColor = textColor
                        valueFormatter = PercentFormatter(chart)
                    }

                    chart.data = PieData(dataSet)
                    chart.invalidate()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
        }
    }
}
