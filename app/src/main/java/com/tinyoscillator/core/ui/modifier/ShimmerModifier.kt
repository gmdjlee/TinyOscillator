package com.tinyoscillator.core.ui.modifier

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Compose 네이티브 Shimmer 애니메이션.
 * 외부 라이브러리 없이 Canvas + InfiniteTransition으로 구현.
 * 다크모드 자동 대응 (MaterialTheme 색상 토큰).
 */
@Composable
fun Modifier.shimmerEffect(
    durationMillis: Int = 1_200,
    shimmerWidthRatio: Float = 0.4f,
): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate by transition.animateFloat(
        initialValue = -shimmerWidthRatio,
        targetValue  = 1f + shimmerWidthRatio,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_translate",
    )

    val baseColor    = MaterialTheme.colorScheme.surfaceVariant
    val shimmerColor = MaterialTheme.colorScheme.surface

    return this.drawWithContent {
        drawContent()
        val gradient = Brush.linearGradient(
            colorStops = arrayOf(
                0.0f to baseColor,
                (shimmerTranslate - shimmerWidthRatio / 2).coerceIn(0f, 1f) to baseColor,
                shimmerTranslate.coerceIn(0f, 1f) to shimmerColor,
                (shimmerTranslate + shimmerWidthRatio / 2).coerceIn(0f, 1f) to baseColor,
                1.0f to baseColor,
            ),
            start = Offset(0f, 0f),
            end   = Offset(size.width, size.height),
        )
        drawRect(brush = gradient)
    }
}

/** Shimmer 텍스트 라인 대체용 */
@Composable
fun ShimmerLine(
    modifier: Modifier = Modifier,
    width: Dp = Dp.Infinity,
    height: Dp = 14.dp,
    cornerRadius: Dp = 4.dp,
) {
    Box(
        modifier = modifier
            .then(
                if (width == Dp.Infinity) Modifier.fillMaxWidth()
                else Modifier.width(width)
            )
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .shimmerEffect(),
    )
}

/** Shimmer 박스(카드) 대체용 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    cornerRadius: Dp = 12.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .shimmerEffect(),
    )
}
