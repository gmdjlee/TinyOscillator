package com.tinyoscillator.presentation.marketanalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyoscillator.domain.model.FearGreedSummary
import com.tinyoscillator.domain.model.MarketOscillator
import com.tinyoscillator.domain.model.ThemeGroup
import com.tinyoscillator.presentation.common.FinanceCard
import com.tinyoscillator.ui.theme.signColor

/**
 * Fear & Greed 히어로 카드 — 홈(Fear&Greed 탭) 최상단에 오늘의 공포·탐욕 점수를
 * 대형 숫자로 강조 배치. 기존 MarketSummaryCard·FearGreedSummaryCard의 F&G 중복
 * 표시를 이 히어로 하나로 통합한다.
 *
 * summary가 null이면 아무것도 그리지 않는다. 신규 데이터 호출 없이 표시 계층만 담당.
 */
@Composable
fun FearGreedHeroCard(
    summary: FearGreedSummary?,
    modifier: Modifier = Modifier
) {
    if (summary == null) return

    val statusColor = fearGreedStatusColor(summary.currentValue)
    val score = (summary.currentValue * 100).toInt()

    FinanceCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp)
    ) {
        // 키커 행: 라벨 + 날짜
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "오늘의 시장 · FEAR & GREED",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = summary.date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(14.dp))

        // 점수 행: 대형 숫자 + 상태 라벨/부제
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "$score",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 66.sp,
                    lineHeight = 60.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = statusColor
            )
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                Text(
                    text = summary.status,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Text(
                    text = "2년 백분위 ${summary.percentile}% · 상위 ${100 - summary.percentile}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // 게이지 바 (커스텀 — M3 LinearProgressIndicator의 stop-indicator/gap 아티팩트 회피)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(summary.percentile / 100f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(statusColor)
            )
        }

        Spacer(Modifier.height(6.dp))

        // 스케일 라벨
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "극단적 공포",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "극단적 탐욕",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 세부 지표 (정보 손실 방지 — 있을 때만)
        if (summary.subIndicators.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                summary.subIndicators.forEach { indicator ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(indicator.value * 100).toInt()}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = indicator.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 히어로 하단으로 강등된 보조 지표 스트립 — 오실레이터·예탁금·상위테마를 compact 타일로.
 * 셋 다 null이면 아무것도 그리지 않으며, 각 타일은 데이터가 있을 때만 렌더된다.
 */
@Composable
fun MarketMetricStrip(
    latestOscillator: MarketOscillator?,
    depositChange: Double?,
    topTheme: ThemeGroup?,
    modifier: Modifier = Modifier
) {
    if (latestOscillator == null && depositChange == null && topTheme == null) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (latestOscillator != null) {
            MetricTile(
                value = String.format("%.0f", latestOscillator.oscillator),
                valueColor = signColor(latestOscillator.oscillator),
                caption = "${latestOscillator.market} 오실"
            )
        }
        if (depositChange != null) {
            MetricTile(
                value = String.format("%+,.0f", depositChange),
                valueColor = signColor(depositChange),
                caption = "예탁금 억"
            )
        }
        if (topTheme != null) {
            MetricTile(
                value = String.format("%+.1f", topTheme.fluRate),
                valueColor = signColor(topTheme.fluRate),
                caption = "상위테마 %"
            )
        }
    }
}

@Composable
private fun RowScope.MetricTile(
    value: String,
    valueColor: Color,
    caption: String
) {
    Surface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/** FearGreedSummaryCard·MarketSummaryCard와 동일한 구간 색상 규칙 (그대로 이식) */
@Composable
private fun fearGreedStatusColor(value: Double): Color = when {
    value >= 0.8 -> MaterialTheme.colorScheme.error
    value >= 0.6 -> MaterialTheme.colorScheme.tertiary
    value >= 0.4 -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.primary
}
