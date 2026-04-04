package com.tinyoscillator.presentation.chart.composable

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyoscillator.domain.model.PatternResult
import com.tinyoscillator.domain.model.PatternSentiment

@Composable
fun PatternSummaryCard(
    patterns: List<PatternResult>,
    dateLabels: Map<Int, String>,
    modifier: Modifier = Modifier,
) {
    if (patterns.isEmpty()) return
    Card(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "최근 패턴",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            patterns.sortedByDescending { it.index }.take(5).forEach { result ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val (icon, color) = when (result.type.sentiment) {
                        PatternSentiment.BULLISH -> "▲" to Color(0xFFD85A30)
                        PatternSentiment.BEARISH -> "▽" to Color(0xFF378ADD)
                        PatternSentiment.NEUTRAL -> "◇" to Color(0xFF888780)
                    }
                    Text(
                        dateLabels[result.index] ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(icon, color = color, fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        result.type.labelKo,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${(result.strength * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
