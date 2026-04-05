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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tinyoscillator.core.ui.UiState
import androidx.compose.foundation.layout.Arrangement

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
