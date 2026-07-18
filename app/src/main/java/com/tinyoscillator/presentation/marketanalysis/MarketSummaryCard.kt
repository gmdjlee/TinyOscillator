package com.tinyoscillator.presentation.marketanalysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tinyoscillator.domain.model.FearGreedSummary
import com.tinyoscillator.domain.model.MarketOscillator
import com.tinyoscillator.domain.model.ThemeGroup
import com.tinyoscillator.presentation.common.FinanceCard
import com.tinyoscillator.ui.theme.signColor

/**
 * "오늘의 시장" 요약 카드 — Fear&Greed 탭 상단에 시장 핵심 지표를 한눈에 표시.
 *
 * 전부 기존 수집 데이터만 사용(신규 API 호출 없음). 각 섹션은 데이터가 있을 때만
 * 렌더되며, 모든 데이터가 비어 있으면 카드 자체를 그리지 않는다.
 */
@Composable
fun MarketSummaryCard(
    fearGreedSummary: FearGreedSummary?,
    latestOscillator: MarketOscillator?,
    depositChange: Double?,
    topThemes: List<ThemeGroup>,
    modifier: Modifier = Modifier
) {
    val hasAny = fearGreedSummary != null || latestOscillator != null ||
        depositChange != null || topThemes.isNotEmpty()
    if (!hasAny) return

    FinanceCard(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "오늘의 시장",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            val date = fearGreedSummary?.date ?: latestOscillator?.date
            if (date != null) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (fearGreedSummary != null) {
                val score = (fearGreedSummary.currentValue * 100).toInt()
                MetricColumn(
                    value = "$score",
                    valueColor = fearGreedColor(fearGreedSummary.currentValue),
                    label = fearGreedSummary.status,
                    caption = "공포·탐욕"
                )
            }
            if (latestOscillator != null) {
                MetricColumn(
                    value = String.format("%.0f", latestOscillator.oscillator),
                    valueColor = signColor(latestOscillator.oscillator),
                    label = latestOscillator.getStatusKorean(),
                    caption = "${latestOscillator.market} 오실레이터"
                )
            }
            if (depositChange != null) {
                MetricColumn(
                    value = String.format("%+.0f억", depositChange),
                    valueColor = signColor(depositChange),
                    label = "전일 대비",
                    caption = "예탁금"
                )
            }
        }

        if (topThemes.isNotEmpty()) {
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "상위 테마",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                topThemes.forEach { theme ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = theme.themeName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                        Text(
                            text = String.format("%+.2f%%", theme.fluRate),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = signColor(theme.fluRate)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(
    value: String,
    valueColor: Color,
    label: String,
    caption: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** FearGreedSummaryCard와 동일한 구간 색상 규칙 */
@Composable
private fun fearGreedColor(value: Double): Color = when {
    value >= 0.8 -> MaterialTheme.colorScheme.error
    value >= 0.6 -> MaterialTheme.colorScheme.tertiary
    value >= 0.4 -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.primary
}
