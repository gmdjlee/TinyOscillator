package com.tinyoscillator.presentation.keyword

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tinyoscillator.domain.model.KeywordGroup
import com.tinyoscillator.presentation.etf.EtfListItem
import com.tinyoscillator.ui.theme.signColor

/**
 * 키워드 상세 pane ([ThemeDetailPane] 미러) — 선택된 키워드 그룹의 멤버 ETF 목록을 보여준다.
 * 그룹핑은 [KeywordViewModel.groups]에서 이미 계산됨 → 선택된 [group]을 그대로 받는다(별도 VM 불필요).
 *
 * @param group 선택된 키워드 그룹. null이면 미선택 안내를 표시(2-Pane 초기 상태).
 * @param includeKeywords ETF 카드 배지용 포함 키워드.
 * @param onEtfClick 멤버 ETF 탭 → 기존 ETF 상세 네비게이션.
 */
@Composable
fun KeywordDetailPane(
    group: KeywordGroup?,
    includeKeywords: List<String>,
    onEtfClick: (ticker: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (group == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "키워드를 선택해주세요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                group.keyword,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        HeaderCard(
            group = group,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(group.members, key = { it.ticker }) { etf ->
                EtfListItem(
                    etf = etf,
                    includeKeywords = includeKeywords,
                    onClick = { onEtfClick(etf.ticker) },
                )
            }
        }
    }
}

@Composable
private fun HeaderCard(group: KeywordGroup, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatBlock("ETF 수", "${group.etfCount}개")
            StatBlock(
                "평균 등락률",
                formatSignedPercent(group.avgChangeRate),
                color = signColor(group.avgChangeRate),
            )
        }
    }
}

@Composable
private fun StatBlock(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

private fun formatSignedPercent(value: Double): String {
    val sign = if (value > 0) "+" else ""
    return "$sign${"%.2f".format(value)}%"
}
