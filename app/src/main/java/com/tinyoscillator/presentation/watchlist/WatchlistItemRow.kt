package com.tinyoscillator.presentation.watchlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tinyoscillator.domain.model.WatchlistEntry
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WatchlistItemRow(
    entry: WatchlistEntry,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = if (isDragging) 4.dp else 0.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 드래그 핸들
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "순서 변경",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))

                // 종목명 + 티커
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        entry.ticker,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 신호 강도 미니 바
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.width(56.dp),
                ) {
                    Text(
                        "${(entry.signalScore * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = scoreColor(entry.signalScore),
                        fontWeight = FontWeight.Bold,
                    )
                    LinearProgressIndicator(
                        progress = { entry.signalScore },
                        color = scoreColor(entry.signalScore),
                        trackColor = Color(0x22888780),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp)),
                    )
                }

                Spacer(Modifier.width(12.dp))

                // 현재가 + 등락률
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatPrice(entry.currentPrice),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    val changePct = entry.priceChange * 100
                    Text(
                        "${if (changePct >= 0) "+" else ""}${"%.1f".format(changePct)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (changePct >= 0) Color(0xFFD85A30) else Color(0xFF378ADD),
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp)
        }
    }
}

internal fun scoreColor(score: Float): Color = when {
    score >= 0.7f -> Color(0xFF2E7D32)
    score >= 0.4f -> Color(0xFFF9A825)
    else -> Color(0xFFD32F2F)
}

private fun formatPrice(price: Long): String {
    if (price == 0L) return "-"
    val formatter = NumberFormat.getNumberInstance(Locale.KOREA)
    return "${formatter.format(price)}원"
}
