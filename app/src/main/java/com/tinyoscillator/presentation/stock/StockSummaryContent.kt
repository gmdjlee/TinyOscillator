package com.tinyoscillator.presentation.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.core.ui.composable.EmptyStateContent
import com.tinyoscillator.presentation.quickanalysis.QuickAnalysisState
import com.tinyoscillator.presentation.quickanalysis.QuickAnalysisSummaryContent
import com.tinyoscillator.presentation.quickanalysis.QuickAnalysisViewModel
import com.tinyoscillator.ui.theme.Negative
import com.tinyoscillator.ui.theme.Positive
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 종목분석 "종합" 탭 — 퀵분석 확장판.
 * 기술 요약(수급 오실레이터·DeMark) + 재무 핵심(fundamental 캐시) +
 * 컨센서스 최신 리포트 + AI 확률분석 스냅샷을 한 화면에 요약.
 */
@Composable
fun StockSummaryContent(
    ticker: String?,
    stockName: String?,
    modifier: Modifier = Modifier,
    quickViewModel: QuickAnalysisViewModel = hiltViewModel(),
    summaryViewModel: StockSummaryViewModel = hiltViewModel()
) {
    if (ticker == null || stockName == null) {
        EmptyStateContent(
            message = "종목을 검색해 분석을 실행하면\n종합 요약이 표시됩니다.",
            modifier = modifier
        )
        return
    }

    LaunchedEffect(ticker) {
        quickViewModel.load(ticker, stockName)
        summaryViewModel.load(ticker)
    }
    val quickState by quickViewModel.state.collectAsStateWithLifecycle()
    val extras by summaryViewModel.extras.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 기술 요약
        SummarySectionCard(title = "기술 요약") {
            when (val s = quickState) {
                is QuickAnalysisState.Loading -> LoadingRow("수급·DeMark 데이터 수집 중...")
                is QuickAnalysisState.Error -> Text(
                    s.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                is QuickAnalysisState.Success -> QuickAnalysisSummaryContent(s.summary)
            }
        }

        // AI 확률분석 (야간 배치/수동 실행 스냅샷 재사용)
        SummarySectionCard(title = "AI 확률분석") {
            val ex = extras
            when {
                ex == null -> LoadingRow("스냅샷 조회 중...")
                ex.ensembleScore == null -> HintText("AI 확률분석 실행 이력이 없습니다. AI분석 탭에서 실행하면 표시됩니다.")
                else -> {
                    val score = ex.ensembleScore
                    val scoreColor = when {
                        score >= 0.65 -> Positive
                        score <= 0.35 -> Negative
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val scoreLabel = when {
                        score >= 0.65 -> "매수 우위"
                        score <= 0.35 -> "매도 우위"
                        else -> "중립"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "${(score * 100).toInt()}%",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = scoreColor
                            )
                            Text(
                                "앙상블 확률 · $scoreLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = scoreColor
                            )
                        }
                        ex.analyzedAt?.let {
                            Text(
                                remember(it) { SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date(it)) },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { score.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = scoreColor
                    )
                    ex.aiAnalysis?.let { ai ->
                        HorizontalDivider()
                        Text(
                            ai.summary.ifBlank { ai.overallAssessment },
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (ai.action.isNotBlank()) {
                            Text(
                                "제안: ${ai.action}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 재무 핵심 (KRX fundamental 캐시)
        SummarySectionCard(title = "재무 핵심") {
            val ex = extras
            when {
                ex == null -> LoadingRow("캐시 조회 중...")
                ex.fundamental == null -> HintText("재무 캐시가 없습니다. 재무 그룹 > 지표 탭에서 조회하면 표시됩니다.")
                else -> {
                    val f = ex.fundamental
                    val fmt = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
                    KeyValueRow("PER", if (f.per > 0) "%.2f배".format(f.per) else "-")
                    KeyValueRow("PBR", if (f.pbr > 0) "%.2f배".format(f.pbr) else "-")
                    KeyValueRow("EPS", if (f.eps != 0L) "${fmt.format(f.eps)}원" else "-")
                    KeyValueRow("BPS", if (f.bps != 0L) "${fmt.format(f.bps)}원" else "-")
                    KeyValueRow("배당수익률", if (f.dividendYield > 0) "%.2f%%".format(f.dividendYield) else "-")
                    Text(
                        "기준일 ${formatDate(f.date)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 컨센서스 최신 리포트
        SummarySectionCard(title = "컨센서스") {
            val ex = extras
            when {
                ex == null -> LoadingRow("캐시 조회 중...")
                ex.latestReport == null -> HintText("수집된 리포트가 없습니다. 시장의견 그룹에서 조회하면 표시됩니다.")
                else -> {
                    val r = ex.latestReport
                    val fmt = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
                    KeyValueRow("투자의견", r.opinion.ifBlank { "-" })
                    KeyValueRow("목표가", if (r.targetPrice > 0) "${fmt.format(r.targetPrice)}원" else "-")
                    KeyValueRow(
                        "괴리율",
                        "%+.1f%%".format(r.divergenceRate),
                        valueColor = when {
                            r.divergenceRate > 0 -> Positive
                            r.divergenceRate < 0 -> Negative
                            else -> null
                        }
                    )
                    Text(
                        "${r.institution} · ${formatDate(r.writeDate)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SummarySectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

@Composable
private fun LoadingRow(message: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun HintText(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun KeyValueRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color? = null
) {
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
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

/** "yyyyMMdd" 또는 "yyyy-MM-dd" → "yy.MM.dd" */
private fun formatDate(date: String): String {
    val digits = date.filter { it.isDigit() }
    if (digits.length < 8) return date
    return "${digits.substring(2, 4)}.${digits.substring(4, 6)}.${digits.substring(6, 8)}"
}
