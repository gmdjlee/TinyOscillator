package com.tinyoscillator.presentation.progressive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.core.ui.composable.UiStateContent
import com.tinyoscillator.core.ui.modifier.ShimmerBox
import com.tinyoscillator.domain.model.AnalysisStep
import com.tinyoscillator.domain.model.ProgressiveAnalysisState
import com.tinyoscillator.presentation.common.skeleton.AnalysisScreenSkeleton
import kotlin.math.abs

@Composable
fun ProgressiveAnalysisScreen(
    viewModel: ProgressiveAnalysisViewModel = hiltViewModel(),
) {
    val state by viewModel.analysisState.collectAsStateWithLifecycle()

    UiStateContent(
        state = state,
        loadingContent = { AnalysisScreenSkeleton() },
        onRetry = viewModel::retry,
        successContent = { analysisState: ProgressiveAnalysisState ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 진행 도트 ────────────────────────────────────────
            item {
                AnalysisProgressIndicator(state = analysisState)
            }

            // ── 1단계: 현재가 헤더 ──────────────────────────────
            item {
                val price = analysisState.priceData
                if (price?.isComplete == true) {
                    PriceHeaderCard(priceStep = price)
                } else {
                    ShimmerBox(height = 64.dp)
                }
            }

            // ── 2단계: 기술 지표 ─────────────────────────────────
            item {
                val tech = analysisState.technicalData
                AnimatedVisibility(
                    visible = tech?.isComplete == true,
                    enter = fadeIn() + expandVertically(),
                ) {
                    tech?.let { TechnicalIndicatorsCard(techStep = it) }
                }
                if (tech == null) ShimmerBox(height = 80.dp)
            }

            // ── 3단계: 앙상블 점수 ──────────────────────────────
            item {
                val ensemble = analysisState.ensembleData
                AnimatedVisibility(
                    visible = ensemble?.isComplete == true,
                    enter = fadeIn(animationSpec = tween(400)) +
                            expandVertically(animationSpec = tween(400)),
                ) {
                    ensemble?.let { EnsembleSignalCard(ensembleStep = it) }
                }
                if (ensemble == null) ShimmerBox(height = 120.dp)
            }

            // ── 4단계: 외부 데이터 (소프트 표시) ────────────────
            item {
                val ext = analysisState.externalData
                AnimatedVisibility(visible = ext?.isComplete == true) {
                    ext?.let { ExternalDataCard(externalStep = it) }
                }
            }
        }
        },
    )
}

@Composable
fun PriceHeaderCard(priceStep: AnalysisStep.PriceData) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    priceStep.ticker,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "거래량 ${formatVolume(priceStep.volume)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatPrice(priceStep.currentPrice),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                val changePct = priceStep.priceChange * 100f
                Text(
                    "${if (changePct >= 0) "▲" else "▼"}${"%.2f".format(abs(changePct))}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (changePct >= 0) Color(0xFFD05540) else Color(0xFF4088CC),
                )
            }
        }
    }
}

@Composable
fun TechnicalIndicatorsCard(techStep: AnalysisStep.TechnicalIndicators) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "기술 지표",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            IndicatorRow("EMA 5/20/60", "%.0f / %.0f / %.0f".format(
                techStep.ema5, techStep.ema20, techStep.ema60
            ))
            IndicatorRow("MACD 히스토그램", "%.2f".format(techStep.macdHistogram))
            IndicatorRow("RSI(14)", "%.1f".format(techStep.rsi))
            IndicatorRow("볼린저 %B", "%.2f".format(techStep.bollingerPct))
        }
    }
}

@Composable
fun EnsembleSignalCard(ensembleStep: AnalysisStep.EnsembleSignal) {
    val score = ensembleStep.ensembleScore
    val scoreColor = when {
        score >= 0.6f -> Color(0xFFD05540)   // 강세 (적색)
        score <= 0.4f -> Color(0xFF4088CC)   // 약세 (청색)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "앙상블 신호",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "%.1f%%".format(score * 100f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor,
                )
            }
            if (ensembleStep.algoResults.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                ensembleStep.algoResults.entries.take(5).forEach { (algo, algoScore) ->
                    IndicatorRow(algo, "%.1f%%".format(algoScore * 100f))
                }
                if (ensembleStep.algoResults.size > 5) {
                    Text(
                        "외 ${ensembleStep.algoResults.size - 5}개 알고리즘",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun ExternalDataCard(externalStep: AnalysisStep.ExternalData) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "외부 데이터",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            if (externalStep.dartEvents.isNotEmpty()) {
                Text(
                    "DART 공시",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                externalStep.dartEvents.forEach { event ->
                    Text(
                        "• $event",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            IndicatorRow(
                "기관 평균 순매수",
                formatInstitutionalFlow(externalStep.institutionalFlow),
            )
            externalStep.consensusTarget?.let { target ->
                IndicatorRow("컨센서스 목표가", formatPrice(target))
            }
        }
    }
}

@Composable
private fun IndicatorRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatPrice(price: Long): String {
    return when {
        price >= 10_000L -> "%,d원".format(price)
        else -> "${price}원"
    }
}

private fun formatVolume(volume: Long): String {
    return when {
        volume >= 100_000_000L -> "%.1f억주".format(volume / 100_000_000.0)
        volume >= 10_000L -> "%.1f만주".format(volume / 10_000.0)
        else -> "${volume}주"
    }
}

private fun formatInstitutionalFlow(flow: Float): String {
    return when {
        flow >= 100_000_000f -> "+%.1f억".format(flow / 100_000_000f)
        flow <= -100_000_000f -> "%.1f억".format(flow / 100_000_000f)
        flow >= 10_000f -> "+%.1f만".format(flow / 10_000f)
        flow <= -10_000f -> "%.1f만".format(flow / 10_000f)
        else -> "%.0f".format(flow)
    }
}
