package com.tinyoscillator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 상승/하락 부호 색상 단일 규칙 — 한국식: 상승=적([Positive]), 하락=청([Negative]).
 * 화면별로 error/primary/tertiary를 혼용하던 것을 이 헬퍼로 통일한다.
 */
@Composable
fun signColor(value: Double): Color = when {
    value > 0 -> Positive
    value < 0 -> Negative
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
