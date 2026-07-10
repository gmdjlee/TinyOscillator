package com.tinyoscillator.feature.bearsignal.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.feature.bearsignal.domain.model.BearPhase
import com.tinyoscillator.feature.bearsignal.presentation.BearSignalViewModel

/**
 * 신규 메뉴 진입점 카드 (TASK.md §5.1 "권장안") — 시장분석 탭(Fear & Greed) 상단에 배치해
 * `BearSignalScreen`으로 내비게이트한다(하단바 혼잡 회피). 현재 국면을 실시간(Room 캐시 기반)으로
 * 미리보기해 탭 전환 없이도 위험도를 가늠할 수 있다.
 */
@Composable
fun BearSignalEntryCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: BearSignalViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val color = phaseColor(uiState.result.phase)
    val meta = phaseMeta(uiState.result.phase)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("시장 국면 · 리스크", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "주도주 붕괴 판단 계기판 — 현재 ${meta.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LevelChip(
                level = when (uiState.result.phase) {
                    BearPhase.GREEN -> 0
                    BearPhase.AMBER -> 1
                    BearPhase.ORANGE -> 2
                    BearPhase.RED -> 3
                },
                labels = listOf("안정", "주의", "임박", "격발")
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "자세히 보기",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
