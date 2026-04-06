package com.tinyoscillator.presentation.chart.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tinyoscillator.domain.model.VolumeProfile
import com.tinyoscillator.presentation.chart.bridge.ChartAxisRange

@Composable
fun VolumeProfileOverlay(
    profile: VolumeProfile?,
    axisRange: ChartAxisRange,
    modifier: Modifier = Modifier,
    barMaxWidthFraction: Float = 0.15f,
    bullColor: Color = Color(0x55D05540),
    bearColor: Color = Color(0x554088CC),
    pocColor: Color = Color(0xFFE8C36A),
    vaColor: Color = Color(0x224088CC),
) {
    if (profile == null || profile.buckets.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val (yMin, yMax, chartH) = axisRange
        val priceRange = yMax - yMin
        if (priceRange <= 0f) return@Canvas

        val barMaxWidth = size.width * barMaxWidthFraction
        val maxVol = profile.buckets.maxOf { it.totalVolume }.toFloat().coerceAtLeast(1f)

        fun priceToY(price: Float): Float =
            chartH * (1f - (price - yMin) / priceRange)

        // Value Area background
        val vaTopY = priceToY(profile.valueAreaHigh)
        val vaBottomY = priceToY(profile.valueAreaLow)
        drawRect(
            color = vaColor,
            topLeft = Offset(size.width - barMaxWidth, vaTopY),
            size = Size(barMaxWidth, vaBottomY - vaTopY),
        )

        // Bucket bars
        val bucketHeight = (profile.priceStep / priceRange * chartH).coerceAtLeast(1f)
        profile.buckets.forEach { bucket ->
            val y = priceToY(bucket.priceLevel)
            val totalW = (bucket.totalVolume / maxVol) * barMaxWidth
            val bullW = if (bucket.totalVolume > 0L)
                (bucket.bullVolume.toFloat() / bucket.totalVolume) * totalW
            else 0f
            val top = y - bucketHeight / 2f
            val barLeft = size.width - totalW

            // Bear volume (left portion)
            if (totalW - bullW > 0f)
                drawRect(bearColor, Offset(barLeft, top), Size(totalW - bullW, bucketHeight))
            // Bull volume (right portion)
            if (bullW > 0f)
                drawRect(bullColor, Offset(size.width - bullW, top), Size(bullW, bucketHeight))
        }

        // POC horizontal line
        val pocY = priceToY(profile.pocPrice)
        drawLine(
            color = pocColor,
            start = Offset(size.width - barMaxWidth, pocY),
            end = Offset(size.width, pocY),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}
