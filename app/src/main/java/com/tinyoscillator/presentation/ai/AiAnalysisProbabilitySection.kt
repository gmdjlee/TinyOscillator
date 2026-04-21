package com.tinyoscillator.presentation.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tinyoscillator.domain.usecase.SignalConflictDetector
import com.tinyoscillator.presentation.common.AlgoAccuracyCard
import com.tinyoscillator.presentation.common.AlgoContributionView
import com.tinyoscillator.presentation.common.AlgorithmGuideContent
import com.tinyoscillator.presentation.common.ConflictWarningBanner
import com.tinyoscillator.presentation.common.GlassCard
import com.tinyoscillator.presentation.common.SignalRationaleCard
import com.tinyoscillator.ui.theme.LocalFinanceColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProbabilityTabContent(
    selectedStock: SelectedStockInfo?,
    probabilityState: ProbabilityAnalysisState,
    interpretationState: InterpretationState,
    metaLearnerStatus: com.tinyoscillator.domain.model.MetaLearnerStatus,
    ensembleProbability: Double?,
    algoAccuracy: Map<String, com.tinyoscillator.domain.model.AlgoAccuracyRow>,
    algoResults: Map<String, com.tinyoscillator.domain.model.AlgoResult>,
    snapshots: List<com.tinyoscillator.core.database.entity.AnalysisSnapshotEntity>,
    onAnalyze: () -> Unit,
    onDismiss: () -> Unit,
    onSelectStock: () -> Unit,
    onInterpretLocal: () -> Unit,
    onInterpretAi: () -> Unit,
    onDismissInterpretation: () -> Unit
) {
    var showAlgoGuide by remember { mutableStateOf(false) }

    if (showAlgoGuide) {
        ModalBottomSheet(onDismissRequest = { showAlgoGuide = false }) {
            AlgorithmGuideContent()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("확률적 기대값 분석", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                TextButton(onClick = { showAlgoGuide = true }) {
                    Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("알고리즘 해설")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("9개 통계 알고리즘을 병렬 실행하여 상승/하락 확률을 산출합니다. API 키 없이 로컬에서 실행됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (selectedStock == null) {
            Spacer(Modifier.height(24.dp))
            Text("종목을 먼저 선택하세요",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            OutlinedButton(
                onClick = onSelectStock,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text("종목 탭으로 이동") }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(selectedStock.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text("${selectedStock.ticker} | ${selectedStock.market ?: "-"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Button(
                onClick = onAnalyze,
                modifier = Modifier.fillMaxWidth(),
                enabled = probabilityState is ProbabilityAnalysisState.Idle ||
                        probabilityState is ProbabilityAnalysisState.Success ||
                        probabilityState is ProbabilityAnalysisState.Error
            ) {
                Icon(Icons.Default.Analytics, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("확률 분석 실행")
            }

            when (val state = probabilityState) {
                is ProbabilityAnalysisState.Idle -> {}

                is ProbabilityAnalysisState.Computing -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(state.message)
                        }
                    }
                }

                is ProbabilityAnalysisState.Error -> {
                    Card(modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(state.message, modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }

                is ProbabilityAnalysisState.Success -> {
                    InterpretationProviderSelector(
                        interpretationState = interpretationState,
                        onInterpretLocal = onInterpretLocal,
                        onInterpretAi = onInterpretAi
                    )

                    InterpretationResultCard(
                        interpretationState = interpretationState,
                        onDismiss = onDismissInterpretation,
                        onRetryLocal = onInterpretLocal,
                        onRetryAi = onInterpretAi
                    )

                    EnsembleProbabilityCard(
                        ensembleProbability = ensembleProbability,
                        metaLearnerStatus = metaLearnerStatus
                    )

                    if (algoResults.isNotEmpty()) {
                        val conflictResult = remember(algoResults) {
                            SignalConflictDetector.detect(algoResults)
                        }
                        ConflictWarningBanner(
                            conflictResult = conflictResult,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )

                        SignalRationaleCard(
                            algoResults = algoResults,
                            ensembleScore = (ensembleProbability ?: 0.5).toFloat()
                        )
                        AlgoContributionView(
                            algoResults = algoResults,
                            ensembleScore = (ensembleProbability ?: 0.5).toFloat()
                        )
                    }

                    AlgoAccuracyCard(accuracy = algoAccuracy)

                    state.result.positionRecommendation?.let { posRec ->
                        val multiplier = if (algoResults.isNotEmpty()) {
                            remember(algoResults) {
                                SignalConflictDetector.detect(algoResults)
                            }.positionMultiplier
                        } else 1f
                        PositionGuideCard(
                            positionRecommendation = posRec,
                            conflictMultiplier = multiplier,
                        )
                    }

                    ProbabilityResultContent(
                        result = state.result,
                        interpretationState = interpretationState
                    )

                    if (snapshots.size >= 2) {
                        SnapshotComparisonCard(snapshots = snapshots)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SnapshotComparisonCard(
    snapshots: List<com.tinyoscillator.core.database.entity.AnalysisSnapshotEntity>
) {
    var expanded by remember { mutableStateOf(false) }
    val dateFormat = remember { java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.KOREA) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Analytics, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("분석 히스토리 비교", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                }
                Text("${snapshots.size}회", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (snapshots.size >= 2) {
                val current = snapshots[0]
                val previous = snapshots[1]
                val currentScore = (current.ensembleScore * 100).toInt()
                val previousScore = (previous.ensembleScore * 100).toInt()
                val diff = currentScore - previousScore
                val diffColor = when {
                    diff > 0 -> MaterialTheme.colorScheme.tertiary
                    diff < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val diffText = if (diff >= 0) "+$diff" else "$diff"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("이전 $previousScore → 현재 $currentScore ($diffText)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = diffColor)
                    Text(dateFormat.format(java.util.Date(current.analyzedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider()

                    snapshots.reversed().forEach { snapshot ->
                        val score = (snapshot.ensembleScore * 100).toInt()
                        val direction = when {
                            score >= 65 -> "강세"
                            score <= 35 -> "약세"
                            else -> "중립"
                        }
                        val dirColor = when {
                            score >= 65 -> MaterialTheme.colorScheme.tertiary
                            score <= 35 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                dateFormat.format(java.util.Date(snapshot.analyzedAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(80.dp)
                            )
                            LinearProgressIndicator(
                                progress = { score / 100f },
                                modifier = Modifier.weight(1f).height(6.dp).padding(horizontal = 8.dp),
                                color = dirColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Text("$score $direction",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = dirColor,
                                modifier = Modifier.width(60.dp),
                                textAlign = TextAlign.End)
                        }
                    }

                    if (snapshots.size >= 2) {
                        HorizontalDivider()
                        Text("알고리즘별 변화", style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold)

                        val currentScores = parseAlgoScores(snapshots[0].algoScores)
                        val previousScores = parseAlgoScores(snapshots[1].algoScores)

                        val algoNameMap = mapOf(
                            "naiveBayes" to "나이브베이즈",
                            "logistic" to "로지스틱",
                            "hmm" to "HMM",
                            "pattern" to "패턴",
                            "signal" to "시그널",
                            "correlation" to "상관관계",
                            "bayesianUpdate" to "베이지안",
                            "orderFlow" to "수급",
                            "dartEvent" to "DART"
                        )

                        currentScores.forEach { (key, currentVal) ->
                            val prevVal = previousScores[key]
                            if (prevVal != null) {
                                val change = ((currentVal - prevVal) * 100).toInt()
                                val changeColor = when {
                                    change > 5 -> MaterialTheme.colorScheme.tertiary
                                    change < -5 -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                val changeText = if (change >= 0) "+$change" else "$change"

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(algoNameMap[key] ?: key,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.width(80.dp))
                                    Text("${(prevVal * 100).toInt()} → ${(currentVal * 100).toInt()}",
                                        style = MaterialTheme.typography.labelSmall)
                                    Text(changeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = changeColor,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(40.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 해석 제공자 선택 (로컬/AI) */
@Composable
internal fun InterpretationProviderSelector(
    interpretationState: InterpretationState,
    onInterpretLocal: () -> Unit,
    onInterpretAi: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("결과 해석", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onInterpretLocal,
                    modifier = Modifier.weight(1f),
                    enabled = interpretationState !is InterpretationState.Loading
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("로컬 분석", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onInterpretAi,
                    modifier = Modifier.weight(1f),
                    enabled = interpretationState !is InterpretationState.Loading
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("AI 분석", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** 해석 결과 카드 */
@Composable
internal fun InterpretationResultCard(
    interpretationState: InterpretationState,
    onDismiss: () -> Unit,
    onRetryLocal: () -> Unit,
    onRetryAi: () -> Unit
) {
    when (interpretationState) {
        is InterpretationState.Idle -> {}

        is InterpretationState.Loading -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("AI 해석 중...")
                }
            }
        }

        is InterpretationState.Success -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                if (interpretationState.provider == InterpretationProvider.AI)
                                    Icons.Default.AutoAwesome else Icons.Default.Lightbulb,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "${interpretationState.provider.label} 해석",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        TextButton(onClick = onDismiss) {
                            Text("닫기", color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        interpretationState.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        is InterpretationState.Error -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(interpretationState.message,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRetryAi) { Text("AI 재시도") }
                        OutlinedButton(onClick = onRetryLocal) { Text("로컬로 전환") }
                    }
                }
            }
        }

        is InterpretationState.NoApiKey -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("AI 분석을 사용하려면 설정에서 AI API 키를 입력해주세요.",
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRetryLocal) { Text("로컬 분석 사용") }
                }
            }
        }
    }
}

/** 앙상블 확률 + 메타 학습기 상태 카드 */
@Composable
internal fun EnsembleProbabilityCard(
    ensembleProbability: Double?,
    metaLearnerStatus: com.tinyoscillator.domain.model.MetaLearnerStatus
) {
    val financeColors = LocalFinanceColors.current

    ensembleProbability?.let { prob ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Text("앙상블 상승 확률", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)

                    val source = if (metaLearnerStatus.isFitted) "Meta-Learner" else "가중합"
                    SuggestionChip(
                        onClick = {},
                        label = { Text(source, style = MaterialTheme.typography.labelSmall) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (metaLearnerStatus.isFitted)
                                Color(0xFF4CAF50).copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = null
                    )
                }

                Spacer(Modifier.height(8.dp))

                val probPct = String.format("%.1f%%", prob * 100)
                val probColor = when {
                    prob >= 0.6 -> financeColors.positive
                    prob <= 0.4 -> financeColors.negative
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Text(probPct,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = probColor)

                if (metaLearnerStatus.isFitted) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "학습 샘플: ${metaLearnerStatus.nTrainingSamples} | " +
                        "최근 학습: ${metaLearnerStatus.lastFitDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (metaLearnerStatus.topAlgo.isNotEmpty()) {
                        Text(
                            "핵심 알고리즘: ${metaLearnerStatus.topAlgo} " +
                            "(${String.format("%.1f%%", metaLearnerStatus.topAlgoWeight * 100)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "학습 데이터 축적 중 (최소 60건 필요)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
