package com.tinyoscillator.feature.bearsignal.presentation.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tinyoscillator.feature.bearsignal.domain.model.SnapshotUpdateSuggestion

/**
 * §6.1 "state:latest 로드" 신선도 제안 배너 — 최신 저장 스냅샷의 `as_of`가 오늘보다 오래됐을 때만
 * (즉 [com.tinyoscillator.feature.bearsignal.presentation.BearSignalUiState.updateSuggestion]이
 * non-null일 때만) 노출된다.
 *
 * 기존 [com.tinyoscillator.core.ui.composable.StaleBanner] 시각 문법(Surface + Row + 아이콘 +
 * TextButton)을 참고하되, 이 배너는 "오류"가 아니라 "제안"이므로 errorContainer 대신
 * tertiaryContainer를 사용해 시각적으로 구분한다.
 *
 * **승인 원칙(§4.5/§7)**: 배너가 노출된다는 사실 자체는 어떤 상태도 바꾸지 않는다. 사용자가
 * [onAccept]를 직접 탭했을 때만 (ViewModel의 `acceptUpdateSuggestion()` → `refresh()`) 갱신이
 * 트리거된다 — 자동 반영 없음.
 */
@Composable
fun SnapshotUpdateSuggestionBanner(
    suggestion: SnapshotUpdateSuggestion,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "마지막 저장 이력이 ${suggestion.latestAsOf}입니다. " +
                    "오늘(${suggestion.today}) 기준으로 갱신을 제안합니다."
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "마지막 이력 ${suggestion.latestAsOf} · 오늘(${suggestion.today}) 갱신을 제안합니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onAccept) {
                Text(
                    "수락",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}
