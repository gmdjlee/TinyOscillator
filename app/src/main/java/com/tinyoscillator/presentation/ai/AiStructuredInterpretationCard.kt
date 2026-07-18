package com.tinyoscillator.presentation.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tinyoscillator.domain.model.AiAnalysisResult
import com.tinyoscillator.domain.model.StockAnalysis

/**
 * AI 구조화 해석 카드 — 종합 판단을 최상단에 고정 배치.
 * 판단/신뢰도/행동 권고를 먼저 보여주고, 인사이트는 중요도(높음→낮음) 순으로 정렬한다.
 */
@Composable
internal fun AiStructuredInterpretationCard(
    structured: StockAnalysis,
    aiResult: AiAnalysisResult?,
    onDismiss: () -> Unit,
    fromCache: Boolean = false,
    onReanalyze: (() -> Unit)? = null,
    stockLabel: String? = null
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        "AI 종합 판단",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    if (fromCache) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("저장된 해석", style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = null
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (fromCache && onReanalyze != null) {
                        TextButton(onClick = onReanalyze) {
                            Text("새로 분석", color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                    IconButton(onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, buildShareText(structured, stockLabel))
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "분석 공유"))
                    }) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "공유",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text("닫기", color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                structured.overallAssessment,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "신뢰도",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { structured.confidence.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).height(8.dp),
                    color = confidenceColor(structured.confidence),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${(structured.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            if (structured.action.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "→ ${structured.action}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            if (structured.conflicts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                structured.conflicts.forEach { conflict ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            conflict,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (structured.risks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "리스크",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                structured.risks.forEach { risk ->
                    Text(
                        "· $risk",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            if (structured.insights.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))

                val sortedInsights = remember(structured.insights) {
                    structured.insights.sortedBy { significanceRank(it.significance) }
                }
                sortedInsights.forEach { insight ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    insight.significance,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = significanceColor(insight.significance).copy(alpha = 0.25f)
                            ),
                            border = null
                        )
                        Column {
                            Text(
                                insight.algorithmName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                insight.interpretation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "분석 참고용 자료이며 투자 조언이 아닙니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
            )
        }
    }
}

/** 공유용 텍스트 — 종합 판단 + 행동 + 충돌/리스크 요약 */
private fun buildShareText(structured: StockAnalysis, stockLabel: String?): String = buildString {
    appendLine("[AI 종합 판단]${stockLabel?.let { " $it" } ?: ""}")
    appendLine(structured.overallAssessment)
    appendLine("신뢰도: ${(structured.confidence * 100).toInt()}%")
    if (structured.action.isNotBlank()) appendLine("행동 권고: ${structured.action}")
    if (structured.conflicts.isNotEmpty()) {
        appendLine("상충 신호:")
        structured.conflicts.forEach { appendLine("· $it") }
    }
    if (structured.risks.isNotEmpty()) {
        appendLine("리스크:")
        structured.risks.forEach { appendLine("· $it") }
    }
    append("※ 분석 참고용 자료이며 투자 조언이 아닙니다.")
}

@Composable
private fun confidenceColor(confidence: Double): Color = when {
    confidence >= 0.7 -> Color(0xFF4CAF50)
    confidence >= 0.5 -> Color(0xFFFF9800)
    else -> MaterialTheme.colorScheme.error
}

private fun significanceRank(significance: String): Int = when (significance) {
    "높음" -> 0
    "보통" -> 1
    else -> 2
}

private fun significanceColor(significance: String): Color = when (significance) {
    "높음" -> Color(0xFFEF5350)
    "보통" -> Color(0xFFFF9800)
    else -> Color(0xFF9E9E9E)
}

internal fun formatTokens(tokens: Int): String =
    if (tokens >= 1000) String.format("%.1fk", tokens / 1000.0) else tokens.toString()

/**
 * 대표 모델 단가 기반 추정 비용 (USD). 모델을 모르면 null.
 * 단가는 변동 가능 — 참고용 추정치로만 표시한다.
 */
internal fun estimateCostUsd(result: AiAnalysisResult): Double? {
    val id = result.modelId.lowercase()
    // (입력, 출력) USD per 1M tokens
    val (inPrice, outPrice) = when {
        id.contains("haiku") -> 1.00 to 5.00
        id.contains("sonnet") -> 3.00 to 15.00
        id.contains("opus") -> 15.00 to 75.00
        id.contains("flash-lite") -> 0.075 to 0.30
        id.contains("flash") -> 0.30 to 2.50
        id.contains("gemini") && id.contains("pro") -> 1.25 to 10.00
        else -> return null
    }
    val cacheReadPrice = inPrice * 0.1
    return (result.inputTokens * inPrice +
        result.outputTokens * outPrice +
        result.cacheReadTokens * cacheReadPrice) / 1_000_000.0
}

internal fun formatCostUsd(cost: Double): String = when {
    cost < 0.0001 -> "≈$%.5f".format(cost)
    cost < 0.01 -> "≈$%.4f".format(cost)
    else -> "≈$%.3f".format(cost)
}
