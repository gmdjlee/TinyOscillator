package com.tinyoscillator.feature.bearsignal.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tinyoscillator.core.ui.composable.LastUpdatedText
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalStaticContent

/**
 * 섹션 7 · 지표↔리포트 매핑 + 면책 + 전체 최신 갱신일 (TASK.md §5.2-7, 부록 B #9).
 */
@Composable
fun BearSignalFooterSection(lastUpdatedAt: Long?, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "지표 ↔ 리포트 매핑",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                BearSignalStaticContent.INDICATOR_MAPPING,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(
                BearSignalStaticContent.DISCLAIMER,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LastUpdatedText(epochMillis = lastUpdatedAt, modifier = Modifier.padding(start = 0.dp))
        }
    }
}
