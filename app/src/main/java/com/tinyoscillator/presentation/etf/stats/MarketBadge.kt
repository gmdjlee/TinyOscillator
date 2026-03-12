package com.tinyoscillator.presentation.etf.stats

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val KospiColor = Color(0xFF1565C0)
private val KosdaqColor = Color(0xFFD32F2F)
private val SectorColor = Color(0xFF616161)

/** 시장 코드를 표시명으로 변환. Kiwoom API는 "거래소"/"코스닥"을 반환함 */
fun marketDisplayName(market: String?): String? = when (market) {
    "KOSPI", "거래소" -> "코스피"
    "KOSDAQ", "코스닥" -> "코스닥"
    else -> null
}

/** 시장명을 정규화된 코드로 변환 (필터 비교용) */
fun normalizeMarketCode(market: String?): String? = when (market) {
    "KOSPI", "거래소" -> "KOSPI"
    "KOSDAQ", "코스닥" -> "KOSDAQ"
    else -> market
}

@Composable
fun MarketLabel(market: String?, width: Dp) {
    val name = marketDisplayName(market)
    if (name != null) {
        val color = if (normalizeMarketCode(market) == "KOSPI") KospiColor else KosdaqColor
        Text(
            name,
            modifier = Modifier.width(width),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = color
        )
    } else {
        Spacer(modifier = Modifier.width(width))
    }
}

@Composable
fun SectorLabel(sector: String?, width: Dp) {
    if (!sector.isNullOrBlank()) {
        Text(
            sector,
            modifier = Modifier.width(width),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = SectorColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    } else {
        Spacer(modifier = Modifier.width(width))
    }
}

@Composable
fun MarketBadge(market: String?, modifier: Modifier = Modifier) {
    val name = marketDisplayName(market) ?: return
    val color = if (normalizeMarketCode(market) == "KOSPI") KospiColor else KosdaqColor
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            name,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SectorBadge(sector: String?, modifier: Modifier = Modifier) {
    if (sector.isNullOrBlank()) return
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = SectorColor.copy(alpha = 0.10f)
    ) {
        Text(
            sector,
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 1.dp)
                .widthIn(max = 80.dp),
            style = MaterialTheme.typography.labelSmall,
            color = SectorColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
