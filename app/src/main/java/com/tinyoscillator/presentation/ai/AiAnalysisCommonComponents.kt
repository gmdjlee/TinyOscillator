package com.tinyoscillator.presentation.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun DataChip(label: String, available: Boolean) {
    SuggestionChip(
        onClick = {},
        label = {
            Text(
                if (available) "$label ✓" else "$label ✗",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (available) FontWeight.Bold else FontWeight.Normal,
                color = if (available) Color(0xFF1B5E20) else Color(0xFF9E9E9E)
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = if (available) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)
        ),
        border = if (available) BorderStroke(1.dp, Color(0xFF66BB6A)) else null
    )
}

@Composable
internal fun ProbChip(label: String, probability: Double, color: Color) {
    SuggestionChip(
        onClick = {},
        label = {
            Text(
                "$label ${pctFmt(probability)}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = color.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.7f))
    )
}

@Composable
internal fun ProbExpandableCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) { content() }
            }
        }
    }
}

/** JSON {"key": score, ...} 파싱 (경량 — kotlinx.serialization 사용하지 않음) */
internal fun parseAlgoScores(json: String): Map<String, Float> {
    val result = mutableMapOf<String, Float>()
    val content = json.trim().removePrefix("{").removeSuffix("}")
    if (content.isBlank()) return result

    content.split(",").forEach { pair ->
        val parts = pair.split(":")
        if (parts.size == 2) {
            val key = parts[0].trim().removeSurrounding("\"")
            val value = parts[1].trim().toFloatOrNull()
            if (value != null) result[key] = value
        }
    }
    return result
}

internal fun pctFmt(value: Double): String = "${String.format("%.1f", value * 100)}%"
