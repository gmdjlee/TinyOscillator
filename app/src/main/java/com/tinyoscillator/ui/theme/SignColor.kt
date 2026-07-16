package com.tinyoscillator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 상승/하락 부호 색상 단일 규칙 — 한국식: 상승=적, 하락=청.
 * 화면별로 error/primary/tertiary를 혼용하던 것을 이 헬퍼로 통일한다.
 * [LocalFinanceColors]를 통해 다크 모드에서는 [PositiveDark]/[NegativeDark] 변형이 적용된다.
 */
@Composable
fun signColor(value: Double): Color {
    val finance = LocalFinanceColors.current
    return when {
        value > 0 -> finance.positive
        value < 0 -> finance.negative
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
