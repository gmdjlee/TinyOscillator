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
import androidx.compose.ui.unit.dp

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

/** 시장 구분 텍스트 색 — 배경 없는 일반 텍스트용 (primary/tertiary 강조 톤) */
@Composable
private fun marketTextColor(market: String?): Color = when (normalizeMarketCode(market)) {
    "KOSPI" -> MaterialTheme.colorScheme.primary
    "KOSDAQ" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun MarketLabel(market: String?, modifier: Modifier) {
    val name = marketDisplayName(market)
    if (name != null) {
        Text(
            name,
            modifier = modifier,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = marketTextColor(market)
        )
    } else {
        Spacer(modifier = modifier)
    }
}

@Composable
fun SectorLabel(sector: String?, modifier: Modifier) {
    if (!sector.isNullOrBlank()) {
        Text(
            sector,
            modifier = modifier,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    } else {
        Spacer(modifier = modifier)
    }
}

@Composable
fun MarketBadge(market: String?, modifier: Modifier = Modifier) {
    val name = marketDisplayName(market) ?: return
    val isKospi = normalizeMarketCode(market) == "KOSPI"
    val containerColor = if (isKospi) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = if (isKospi) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = containerColor
    ) {
        Text(
            name,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
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
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            sector,
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 1.dp)
                .widthIn(max = 80.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
