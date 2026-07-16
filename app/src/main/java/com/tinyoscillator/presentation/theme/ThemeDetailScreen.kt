package com.tinyoscillator.presentation.theme

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.domain.model.ThemeStock
import com.tinyoscillator.ui.theme.LocalFinanceColors
import com.tinyoscillator.ui.theme.signColor
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeDetailScreen(
    viewModel: ThemeDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onStockClick: (stockCode: String) -> Unit = {},
) {
    val stocks by viewModel.stocks.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            selected.name.ifBlank { "테마 상세" },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "코드 ${selected.code}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        ThemeDetailContent(
            stocks = stocks,
            onStockClick = onStockClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

/**
 * 탐색 탭 2-Pane 우측 패널용 테마 상세 — 자체 ViewModel을 갖고
 * [ThemeDetailViewModel.selectTheme]으로 목록 선택을 따라간다.
 */
@Composable
fun ThemeDetailPane(
    themeCode: String,
    themeName: String,
    onStockClick: (stockCode: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ThemeDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(themeCode, themeName) {
        viewModel.selectTheme(themeCode, themeName)
    }
    val stocks by viewModel.stocks.collectAsStateWithLifecycle()

    Column(modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                themeName.ifBlank { "테마 상세" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "코드 $themeCode",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ThemeDetailContent(
            stocks = stocks,
            onStockClick = onStockClick,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 테마 구성 종목 본문 — 전체 화면([ThemeDetailScreen])과 2-Pane 패널이 공유 */
@Composable
fun ThemeDetailContent(
    stocks: List<ThemeStock>,
    onStockClick: (stockCode: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HeaderCard(
            stocks = stocks,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (stocks.isEmpty()) {
            EmptyView(modifier = Modifier
                .fillMaxSize()
                .padding(24.dp))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(stocks, key = { it.stockCode }) { stock ->
                    StockCard(
                        stock = stock,
                        onClick = { onStockClick(stock.stockCode) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(stocks: List<ThemeStock>, modifier: Modifier = Modifier) {
    val count = stocks.size
    val avgFlu = if (stocks.isEmpty()) 0.0 else stocks.map { it.fluRate }.average()
    val avgReturn = if (stocks.isEmpty()) 0.0 else stocks.map { it.periodReturnRate }.average()
    val rise = stocks.count { it.priorDiff > 0 }
    val fall = stocks.count { it.priorDiff < 0 }
    val lastUpdated = stocks.maxOfOrNull { it.lastUpdated }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatBlock("종목수", "${count}개")
                StatBlock("평균 등락률", formatSignedPercent(avgFlu), color = signColor(avgFlu))
                StatBlock("평균 수익률", formatSignedPercent(avgReturn), color = signColor(avgReturn))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "상승 ${rise}",
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalFinanceColors.current.positive,
                )
                Text(
                    "하락 ${fall}",
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalFinanceColors.current.negative,
                )
            }
            lastUpdated?.let { ts ->
                Text(
                    "마지막 갱신: ${DATE_FMT.format(Date(ts))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

@Composable
private fun StockCard(stock: ThemeStock, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stock.stockName.ifBlank { "(이름 없음)" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stock.stockCode,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    PRICE_FMT.format(stock.currentPrice),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${formatSignedNumber(stock.priorDiff)} (${formatSignedPercent(stock.fluRate)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = signColor(stock.priorDiff),
                )
                if (stock.volume > 0) {
                    Text(
                        "거래량 ${VOLUME_FMT.format(stock.volume)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyView(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "구성 종목 데이터가 없습니다",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "테마 목록 화면에서 새로고침해 주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatSignedNumber(value: Double): String {
    val sign = if (value > 0) "+" else ""
    return "$sign${PRICE_FMT.format(value)}"
}

private fun formatSignedPercent(value: Double): String {
    val sign = if (value > 0) "+" else ""
    return "$sign${"%.2f".format(value)}%"
}

private val PRICE_FMT = DecimalFormat("#,##0.##")
private val VOLUME_FMT = DecimalFormat("#,###")
private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
