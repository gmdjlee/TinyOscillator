package com.tinyoscillator.core.ui.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tinyoscillator.core.ui.UiState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Schedule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UiState에 따라 로딩/성공/오류/아이들 콘텐츠를 전환하는 범용 컴포저블.
 * 오류 시 staleData가 있으면 Stale 배너 + 기존 데이터 함께 표시.
 */
@Composable
fun <T> UiStateContent(
    state:          UiState<T>,
    loadingContent: @Composable () -> Unit,
    successContent: @Composable (T) -> Unit,
    errorContent:   @Composable (message: String, staleData: T?, onRetry: () -> Unit) -> Unit
                    = { msg, _, retry -> DefaultErrorContent(msg, retry) },
    idleContent:    @Composable () -> Unit = {},
    onRetry:        () -> Unit = {},
    modifier:       Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when (state) {
            is UiState.Loading -> loadingContent()
            is UiState.Success -> successContent(state.data)
            is UiState.Error   -> {
                if (state.staleData != null) {
                    Column {
                        StaleBanner(message = state.message, onRetry = onRetry)
                        successContent(state.staleData)
                    }
                } else {
                    errorContent(state.message, null, onRetry)
                }
            }
            is UiState.Idle    -> idleContent()
        }
    }
}

/** 상단 Amber 배너 — 네트워크 오류 시 캐시 데이터 표시 중임을 안내 */
@Composable
fun StaleBanner(message: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.WifiOff,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text  = "오프라인 · 마지막 저장 데이터를 표시 중입니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text(
                    "새로고침",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
fun DefaultErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.ErrorOutline, null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text("다시 시도") }
    }
}

/**
 * 빈 상태 공통 컴포넌트.
 * 데이터가 없거나 검색 결과가 없을 때 통일된 UI를 제공.
 */
@Composable
fun EmptyStateContent(
    message: String,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = {
        Icon(
            Icons.Default.Inbox, null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    },
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon?.invoke()
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

/**
 * 검색 결과 없음 상태. EmptyStateContent의 특수화.
 */
@Composable
fun NoSearchResultContent(
    query: String,
    modifier: Modifier = Modifier,
) {
    EmptyStateContent(
        message = "'$query' 검색 결과가 없습니다.",
        modifier = modifier,
        icon = {
            Icon(
                Icons.Default.SearchOff, null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        },
    )
}

/**
 * 데이터 수집 필요 상태. Schedule 안내 포함.
 */
@Composable
fun NeedDataCollectionContent(
    modifier: Modifier = Modifier,
    onSettingsClick: (() -> Unit)? = null,
) {
    EmptyStateContent(
        message = "설정 > Schedule에서 데이터를 수집해주세요.",
        modifier = modifier,
        action = onSettingsClick?.let {
            {
                TextButton(onClick = it) { Text("설정으로 이동") }
            }
        },
    )
}

/**
 * 마지막 갱신 시간 표시 컴포넌트.
 * epochMillis가 null이면 아무것도 표시하지 않음.
 */
@Composable
fun LastUpdatedText(
    epochMillis: Long?,
    modifier: Modifier = Modifier,
) {
    if (epochMillis == null || epochMillis == 0L) return

    val formatted = remember(epochMillis) {
        val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA)
        sdf.format(Date(epochMillis))
    }

    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Default.Schedule, null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Text(
            text = "마지막 갱신: $formatted",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}
