package com.tinyoscillator.presentation.quickanalysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.domain.model.CrossSignal
import com.tinyoscillator.domain.model.Trend
import com.tinyoscillator.ui.theme.LocalFinanceColors
import java.text.NumberFormat
import java.util.Locale

/**
 * 종목 퀵 분석 바텀시트.
 *
 * ETF 구성종목/리포트 등 종목 리스트에서 호출 — 종가·수급 오실레이터·DeMark TD
 * 요약을 보여주고 "종목분석에서 전체 보기"로 전체 분석 탭 딥링크를 제공.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockQuickAnalysisSheet(
    ticker: String,
    stockName: String,
    onDismiss: () -> Unit,
    onOpenFullAnalysis: (ticker: String, stockName: String) -> Unit,
    onOpenProbabilityAnalysis: ((ticker: String, stockName: String) -> Unit)? = null,
    viewModel: QuickAnalysisViewModel = hiltViewModel()
) {
    LaunchedEffect(ticker) {
        viewModel.load(ticker, stockName)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: 종목명 + 티커
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    stockName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    ticker,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 1.dp)
                )
            }

            HorizontalDivider()

            when (val s = state) {
                is QuickAnalysisState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            "분석 데이터 수집 중...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                is QuickAnalysisState.Error -> {
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                is QuickAnalysisState.Success -> {
                    QuickAnalysisSummaryContent(s.summary)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = { onOpenFullAnalysis(ticker, stockName) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("종목분석에서 전체 보기")
            }

            if (onOpenProbabilityAnalysis != null) {
                OutlinedButton(
                    onClick = { onOpenProbabilityAnalysis(ticker, stockName) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("AI 확률분석 실행")
                }
            }
        }
    }
}

/** 종가·수급 오실레이터·DeMark TD 요약 — 퀵분석 시트와 종목분석 "종합" 탭이 공유 */
@Composable
internal fun QuickAnalysisSummaryContent(summary: QuickAnalysisSummary) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 종가 + 등락률
        SummaryRow(label = "종가 (${formatShortDate(summary.date)})") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${numberFormat.format(summary.closePrice)}원",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                summary.changePct?.let { pct ->
                    val color = when {
                        pct > 0 -> LocalFinanceColors.current.positive
                        pct < 0 -> LocalFinanceColors.current.negative
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        "%+.2f%%".format(pct),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }

        // 수급 오실레이터 (차트와 동일하게 ×100 % 표시)
        SummaryRow(label = "수급 오실레이터") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "%.4f%%".format(summary.oscillator * 100),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TrendBadge(summary.trend)
                summary.crossSignal?.let { CrossSignalBadge(it) }
            }
        }

        // DeMark TD 카운트 (9+ 신호 강조)
        SummaryRow(label = "DeMark TD") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TdCountText(prefix = "Buy", count = summary.tdBuyCount, highlightColor = LocalFinanceColors.current.positive)
                Text(
                    " · ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TdCountText(prefix = "Sell", count = summary.tdSellCount, highlightColor = LocalFinanceColors.current.negative)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
private fun TrendBadge(trend: Trend) {
    val (label, color) = when (trend) {
        Trend.BULLISH -> "상승" to LocalFinanceColors.current.positive
        Trend.BEARISH -> "하락" to LocalFinanceColors.current.negative
        Trend.NEUTRAL -> "중립" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    LabelBadge(label = label, color = color, modifier = Modifier.padding(start = 6.dp))
}

@Composable
private fun CrossSignalBadge(cross: CrossSignal) {
    val (label, color) = when (cross) {
        CrossSignal.GOLDEN_CROSS -> "골든크로스" to LocalFinanceColors.current.positive
        CrossSignal.DEAD_CROSS -> "데드크로스" to LocalFinanceColors.current.negative
    }
    LabelBadge(label = label, color = color, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun LabelBadge(label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/** TD 카운트 텍스트 — 9 이상이면 신호로 간주해 강조 */
@Composable
private fun TdCountText(prefix: String, count: Int, highlightColor: Color) {
    val isSignal = count >= 9
    Text(
        "$prefix $count",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (isSignal) FontWeight.Bold else FontWeight.SemiBold,
        color = if (isSignal) highlightColor else MaterialTheme.colorScheme.onSurface
    )
}

private fun formatShortDate(yyyymmdd: String): String {
    if (yyyymmdd.length != 8) return yyyymmdd
    return "${yyyymmdd.substring(4, 6)}/${yyyymmdd.substring(6, 8)}"
}
