package com.tinyoscillator.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HeatmapScreen(
    viewModel: HeatmapViewModel = hiltViewModel(),
    onTickerClick: (String) -> Unit = {},
) {
    val state by viewModel.heatmapState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "신호 강도 히트맵",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            WindowSelector(
                selected = state.windowDays,
                onSelect = { viewModel.setWindowDays(it) },
            )
        }

        Spacer(Modifier.height(8.dp))

        if (state.isLoading) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val data = state.data
            if (data == null || data.tickers.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "분석 이력이 없습니다.\n종목을 먼저 분석해 주세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SignalHeatmap(
                    data = data,
                    onCellClick = { ticker, _ -> onTickerClick(ticker) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        HeatmapLegend()
    }
}

@Composable
fun WindowSelector(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row {
        listOf(7, 14, 20).forEach { days ->
            FilterChip(
                selected = selected == days,
                onClick = { onSelect(days) },
                label = { Text("${days}일", fontSize = 12.sp) },
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

@Composable
fun HeatmapLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            "강한 강세" to Color(0xFFD05540),
            "강세" to Color(0xFFE88A66),
            "중립" to Color(0xFF8A8580),
            "약세" to Color(0xFF6699CC),
            "강한 약세" to Color(0xFF4088CC),
        ).forEach { (label, color) ->
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(2.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
            Spacer(Modifier.width(8.dp))
        }
    }
}
