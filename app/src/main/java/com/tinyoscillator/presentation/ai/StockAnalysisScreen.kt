package com.tinyoscillator.presentation.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tinyoscillator.domain.model.*
import com.tinyoscillator.presentation.viewmodel.AnalysisUiState
import com.tinyoscillator.presentation.viewmodel.StockAnalysisViewModel

/**
 * 확률적 주식 분석 리포트 화면
 *
 * - "AI 분석" 버튼
 * - 단계별 로딩 상태 표시
 * - 스트리밍 텍스트 표시
 * - 최종 리포트 카드 (각 알고리즘 결과 expandable)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockAnalysisScreen(
    ticker: String,
    stockName: String,
    viewModel: StockAnalysisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 분석 버튼
        item {
            Button(
                onClick = { viewModel.analyzeStock(ticker) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState is AnalysisUiState.Idle || uiState is AnalysisUiState.Success || uiState is AnalysisUiState.Error
            ) {
                Icon(Icons.Default.Analytics, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("AI 확률 분석")
            }
        }

        // 상태 표시
        item {
            when (val state = uiState) {
                is AnalysisUiState.Idle -> {}
                is AnalysisUiState.Loading -> LoadingCard("분석 준비 중...")
                is AnalysisUiState.Computing -> ComputingCard(state.message, state.progress)
                is AnalysisUiState.LlmProcessing -> LoadingCard(state.message)
                is AnalysisUiState.Streaming -> StreamingCard(streamingText)
                is AnalysisUiState.Success -> {}
                is AnalysisUiState.Error -> ErrorCard(state.message)
            }
        }

        // 성공 시 결과 표시
        val successState = uiState as? AnalysisUiState.Success
        if (successState != null) {
            // 종합 평가
            item { OverallAssessmentCard(successState.analysis) }

            // 통계 결과 섹션들
            item { StatisticalResultCards(successState.statisticalResult) }
        }
    }
}

@Composable
private fun LoadingCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(message)
        }
    }
}

@Composable
private fun ComputingCard(message: String, progress: Float) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(message)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StreamingCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("AI 분석 진행 중...", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun OverallAssessmentCard(analysis: StockAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("종합 평가", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(analysis.overallAssessment, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text("신뢰도: ${(analysis.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text("행동: ${analysis.action}", style = MaterialTheme.typography.bodyMedium)

            if (analysis.risks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("리스크:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                analysis.risks.forEach { risk ->
                    Text("  · $risk", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun StatisticalResultCards(result: StatisticalResult) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Bayes
        result.bayesResult?.let { bayes ->
            ExpandableSection("나이브 베이즈") {
                Text("상승: ${pct(bayes.upProbability)} | 하락: ${pct(bayes.downProbability)} | 횡보: ${pct(bayes.sidewaysProbability)}")
                Text("샘플: ${bayes.sampleCount}건")
            }
        }

        // Logistic
        result.logisticResult?.let { logistic ->
            ExpandableSection("로지스틱 회귀") {
                Text("상승확률: ${pct(logistic.probability)} (${logistic.score0to100}/100)")
            }
        }

        // HMM
        result.hmmResult?.let { hmm ->
            ExpandableSection("HMM 레짐") {
                Text("현재: ${hmm.regimeDescription}")
            }
        }

        // Pattern
        result.patternAnalysis?.let { patterns ->
            ExpandableSection("패턴 분석 (${patterns.activePatterns.size} 활성)") {
                patterns.activePatterns.take(5).forEach { p ->
                    Text("${p.patternDescription}: 승률 ${pct(p.winRate20d)}")
                }
            }
        }

        // Signal Score
        result.signalScoringResult?.let { signal ->
            ExpandableSection("신호 점수: ${signal.totalScore}/100") {
                Text("방향: ${signal.dominantDirection}")
                signal.conflictingSignals.forEach { c ->
                    Text("충돌: ${c.description}", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Bayesian Update
        result.bayesianUpdateResult?.let { bu ->
            ExpandableSection("베이지안 갱신") {
                Text("사전: ${pct(bu.priorProbability)} → 사후: ${pct(bu.finalPosterior)}")
            }
        }

        // Execution metadata
        Text(
            "실행 시간: ${result.executionMetadata.totalTimeMs}ms" +
                    if (result.executionMetadata.failedEngines.isNotEmpty())
                        " (실패: ${result.executionMetadata.failedEngines.joinToString()})" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExpandableSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    content()
                }
            }
        }
    }
}

private fun pct(value: Double): String = "${String.format("%.1f", value * 100)}%"
