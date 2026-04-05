package com.tinyoscillator.presentation.progressive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tinyoscillator.core.ui.modifier.shimmerEffect
import com.tinyoscillator.domain.model.ProgressiveAnalysisState

/**
 * 상단 진행 도트 — 1단계~4단계 완료 여부 시각화
 */
@Composable
fun AnalysisProgressIndicator(
    state: ProgressiveAnalysisState,
    modifier: Modifier = Modifier,
) {
    val steps = listOf(
        "가격" to (state.priceData?.isComplete == true),
        "지표" to (state.technicalData?.isComplete == true),
        "신호" to (state.ensembleData?.isComplete == true),
        "외부" to (state.externalData?.isComplete == true),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { i, (label, complete) ->
            val color = if (complete)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (!complete) Modifier.shimmerEffect()
                        else Modifier
                    ),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (complete)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )

            if (i < steps.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 0.5.dp,
                    color = if (complete)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                )
            }
        }
    }
}
