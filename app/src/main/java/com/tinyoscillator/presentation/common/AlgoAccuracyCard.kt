package com.tinyoscillator.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tinyoscillator.domain.model.AlgoAccuracyRow
import kotlin.math.roundToInt

@Composable
fun AlgoAccuracyCard(
    accuracy: Map<String, AlgoAccuracyRow>,
    windowDays: Int = 60,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "알고리즘 적중률 (최근 ${windowDays}일)",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(10.dp))

            if (accuracy.isEmpty()) {
                Text(
                    "신호 이력이 아직 없습니다. 분석 후 ${windowDays}일이 지나면 표시됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                accuracy.entries
                    .sortedByDescending { it.value.accuracy }
                    .forEach { (name, row) ->
                        AccuracyRow(
                            algoName = ALGO_DISPLAY_NAMES[name] ?: name,
                            accuracy = row.accuracy,
                            sampleCount = row.total,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
            }
        }
    }
}

@Composable
fun AccuracyRow(algoName: String, accuracy: Float, sampleCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            algoName,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(80.dp),
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        LinearProgressIndicator(
            progress = { accuracy },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = when {
                accuracy >= 0.6f -> Color(0xFFD85A30)
                accuracy <= 0.4f -> Color(0xFF378ADD)
                else -> Color(0xFF888780)
            },
            trackColor = Color(0x22888780),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${(accuracy * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End,
            color = if (accuracy >= 0.6f) Color(0xFFD85A30) else Color(0xFF888780),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "(${sampleCount}건)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
