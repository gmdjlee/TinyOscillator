package com.tinyoscillator.presentation.chart

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.tinyoscillator.ui.theme.LocalFinanceColors
import com.tinyoscillator.ui.theme.Neutral
import com.tinyoscillator.ui.theme.NeutralDark

/** MPAndroidChart용 ARGB Int 팔레트. Compose 테마 토큰에서 파생 — hex 재하드코딩 금지. */
data class ChartTheme(
    val neutralLine: Int,   // secondary — 방향성 없는 지수/평균선
    val emphasisLine: Int,  // primary — 오실레이터 등 주 시리즈(후속 차트용, 이번엔 미사용이어도 정의)
    val positive: Int,      // finance.positive — 상승/과매수/TD Sell (한국식 적)
    val negative: Int,      // finance.negative — 하락/TD Buy (한국식 청)
    val grid: Int,          // surfaceVariant — 격자선
    val axisText: Int,      // onSurfaceVariant — 축/범례 텍스트
    val holeFill: Int,      // surface — 서클 홀 채움
    val neutral: Int,       // Neutral/NeutralDark — 캔들 심지 등 무채색
    val isDark: Boolean,
)

@Composable
fun rememberChartTheme(): ChartTheme {
    val scheme = MaterialTheme.colorScheme
    val finance = LocalFinanceColors.current
    return remember(scheme, finance) {
        val isDark = scheme.surface.luminance() < 0.5f
        ChartTheme(
            neutralLine = scheme.secondary.toArgb(),
            emphasisLine = scheme.primary.toArgb(),
            positive = finance.positive.toArgb(),
            negative = finance.negative.toArgb(),
            grid = scheme.surfaceVariant.toArgb(),
            axisText = scheme.onSurfaceVariant.toArgb(),
            holeFill = scheme.surface.toArgb(),
            neutral = if (isDark) NeutralDark.toArgb() else Neutral.toArgb(),
            isDark = isDark,
        )
    }
}
