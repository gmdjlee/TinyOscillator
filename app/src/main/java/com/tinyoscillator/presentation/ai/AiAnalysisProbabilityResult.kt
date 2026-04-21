package com.tinyoscillator.presentation.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tinyoscillator.domain.model.DartEventType
import com.tinyoscillator.domain.model.PositionRecommendation
import com.tinyoscillator.domain.model.SizeReasonCode
import com.tinyoscillator.domain.model.StatisticalResult
import com.tinyoscillator.ui.theme.LocalFinanceColors

@Composable
internal fun ProbabilityResultContent(
    result: StatisticalResult,
    interpretationState: InterpretationState
) {
    val engineInterpretations = (interpretationState as? InterpretationState.Success)
        ?.engineInterpretations ?: emptyMap()

    // 종합 요약 카드
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("분석 완료", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                val isCached = result.executionMetadata.totalTimeMs < 50
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            if (isCached) "Cached" else "Live",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (isCached)
                            MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                    border = null
                )
            }
            Spacer(Modifier.height(8.dp))

            result.marketRegimeResult?.let { regime ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val regimeColor = when (regime.regimeName) {
                        "BULL_LOW_VOL" -> Color(0xFF4CAF50)
                        "BEAR_HIGH_VOL" -> Color(0xFFFF9800)
                        "SIDEWAYS" -> Color(0xFF9E9E9E)
                        "CRISIS" -> Color(0xFFF44336)
                        else -> Color(0xFF9E9E9E)
                    }
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                "${regime.regimeDescription} (${String.format("%.0f", regime.confidence * 100)}%)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = regimeColor.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, regimeColor.copy(alpha = 0.7f))
                    )
                    Text("${regime.regimeDurationDays}일 지속",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
            }

            result.macroSignalResult?.let { macro ->
                if (macro.unavailableReason == null) {
                    val macroColor = when (macro.macroEnv) {
                        "EASING" -> Color(0xFF4CAF50)
                        "TIGHTENING" -> Color(0xFFF44336)
                        "STAGFLATION" -> Color(0xFFFF9800)
                        else -> Color(0xFF9E9E9E)
                    }
                    val macroLabel = when (macro.macroEnv) {
                        "EASING" -> "완화"
                        "TIGHTENING" -> "긴축"
                        "STAGFLATION" -> "스태그플레이션"
                        else -> "중립"
                    }
                    var showMacroSheet by remember { mutableStateOf(false) }
                    SuggestionChip(
                        onClick = { showMacroSheet = !showMacroSheet },
                        label = {
                            Text(
                                "매크로: $macroLabel",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = macroColor.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, macroColor.copy(alpha = 0.7f))
                    )
                    AnimatedVisibility(visible = showMacroSheet) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("매크로 YoY 변화율", fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.height(4.dp))
                                Text("기준금리: ${String.format("%+.2f", macro.baseRateYoy)}pp",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("M2 통화량: ${String.format("%+.1f", macro.m2Yoy)}%",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("산업생산: ${String.format("%+.1f", macro.iipYoy)}%",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("USD/KRW: ${String.format("%+.1f", macro.usdKrwYoy)}%",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("소비자물가: ${String.format("%+.1f", macro.cpiYoy)}%",
                                    style = MaterialTheme.typography.bodySmall)
                                if (macro.referenceMonth.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("기준월: ${macro.referenceMonth}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Bayes 확률 요약 (한국 주식 관례: 상승=빨강, 하락=파랑, 횡보=회색)
            result.bayesResult?.let { bayes ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProbChip("상승", bayes.upProbability, Color(0xFFEF5350))
                    ProbChip("하락", bayes.downProbability, Color(0xFF42A5F5))
                    ProbChip("횡보", bayes.sidewaysProbability, Color(0xFF9E9E9E))
                }
            }

            result.signalScoringResult?.let { signal ->
                Spacer(Modifier.height(8.dp))
                Text("신호 점수: ${signal.totalScore}/100 (${signal.dominantDirection})",
                    style = MaterialTheme.typography.bodyMedium)
            }

            result.bayesianUpdateResult?.let { bu ->
                Text("베이지안 확률: ${pctFmt(bu.priorProbability)} → ${pctFmt(bu.finalPosterior)}",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    result.bayesResult?.let { bayes ->
        ProbExpandableCard("나이브 베이즈 (샘플 ${bayes.sampleCount}건)") {
            Text("상승: ${pctFmt(bayes.upProbability)} | 하락: ${pctFmt(bayes.downProbability)} | 횡보: ${pctFmt(bayes.sidewaysProbability)}")
            if (bayes.dominantFeatures.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("주요 피처:", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold)
                bayes.dominantFeatures.take(5).forEach { f ->
                    Text("  · ${f.featureName}: ${String.format("%.2f", f.likelihoodRatio)}x",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            EngineInterpretationBlock(engineInterpretations["bayes"])
        }
    }

    result.logisticResult?.let { lr ->
        ProbExpandableCard("로지스틱 회귀: ${lr.score0to100}/100") {
            Text("상승 확률: ${pctFmt(lr.probability)}")
            Spacer(Modifier.height(4.dp))
            lr.featureValues.forEach { (name, value) ->
                Text("  · $name: ${String.format("%.3f", value)}",
                    style = MaterialTheme.typography.bodySmall)
            }
            EngineInterpretationBlock(engineInterpretations["logistic"])
        }
    }

    result.hmmResult?.let { hmm ->
        ProbExpandableCard("HMM 레짐: ${hmm.regimeDescription}") {
            Text("R0(저변동↑): ${pctFmt(hmm.regimeProbabilities[0])}")
            Text("R1(저변동→): ${pctFmt(hmm.regimeProbabilities[1])}")
            Text("R2(고변동↑): ${pctFmt(hmm.regimeProbabilities[2])}")
            Text("R3(고변동↓): ${pctFmt(hmm.regimeProbabilities[3])}")
            if (hmm.recentRegimePath.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("최근 경로: ${hmm.recentRegimePath.takeLast(10).joinToString("→")}",
                    style = MaterialTheme.typography.bodySmall)
            }
            EngineInterpretationBlock(engineInterpretations["hmm"])
        }
    }

    result.marketRegimeResult?.let { regime ->
        ProbExpandableCard("시장 레짐: ${regime.regimeDescription}") {
            val probLabels = listOf("안정적 상승장", "변동성 하락장", "박스권 횡보", "위기 구간")
            regime.probaVec.forEachIndexed { i, p ->
                if (i < probLabels.size) {
                    Text("${probLabels[i]}: ${pctFmt(p)}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("신뢰도: ${pctFmt(regime.confidence)} | 지속: ${regime.regimeDurationDays}일",
                style = MaterialTheme.typography.bodySmall)
            EngineInterpretationBlock(engineInterpretations["regime"])
        }
    }

    result.patternAnalysis?.let { pa ->
        ProbExpandableCard("패턴 분석 (활성 ${pa.activePatterns.size}/${pa.allPatterns.size})") {
            if (pa.activePatterns.isEmpty()) {
                Text("현재 활성 패턴 없음", style = MaterialTheme.typography.bodySmall)
            } else {
                pa.activePatterns.forEach { p ->
                    Text("${p.patternDescription}", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall)
                    Text("  20일 승률: ${pctFmt(p.winRate20d)} | 평균수익: ${pctFmt(p.avgReturn20d)} | 발생: ${p.totalOccurrences}회",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            EngineInterpretationBlock(engineInterpretations["pattern"])
        }
    }

    result.signalScoringResult?.let { ss ->
        ProbExpandableCard("신호 점수: ${ss.totalScore}/100") {
            ss.contributions.filter { it.signal > 0 }.forEach { c ->
                val dir = if (c.direction > 0) "매수" else if (c.direction < 0) "매도" else "중립"
                Text("  · ${c.name}: $dir (기여 ${String.format("%.1f", c.contributionPercent)}%)",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (ss.conflictingSignals.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                ss.conflictingSignals.forEach { c ->
                    Text("⚠ ${c.description}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            EngineInterpretationBlock(engineInterpretations["signal"])
        }
    }

    result.correlationAnalysis?.let { ca ->
        if (ca.correlations.isNotEmpty()) {
            ProbExpandableCard("상관 분석 (${ca.correlations.size}쌍)") {
                ca.correlations.forEach { c ->
                    Text("${c.indicator1} ↔ ${c.indicator2}: r=${String.format("%.2f", c.pearsonR)} (${c.strength.label})",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (ca.leadLagResults.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("선행-후행:", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium)
                    ca.leadLagResults.forEach { ll ->
                        Text("  · ${ll.interpretation}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                EngineInterpretationBlock(engineInterpretations["correlation"])
            }
        }
    }

    result.bayesianUpdateResult?.let { bu ->
        ProbExpandableCard("베이지안 갱신 (${bu.updateHistory.size}단계)") {
            Text("사전: ${pctFmt(bu.priorProbability)} → 사후: ${pctFmt(bu.finalPosterior)}")
            Spacer(Modifier.height(4.dp))
            bu.updateHistory.forEach { u ->
                val arrow = if (u.deltaProb > 0) "↑" else "↓"
                Text("  · ${u.signalName} $arrow${pctFmt(kotlin.math.abs(u.deltaProb))}",
                    style = MaterialTheme.typography.bodySmall)
            }
            EngineInterpretationBlock(engineInterpretations["bayesian"])
        }
    }

    result.orderFlowResult?.let { of ->
        val dirLabel = when (of.flowDirection) {
            "BUY" -> "매수 우위"
            "SELL" -> "매도 우위"
            else -> "중립"
        }
        ProbExpandableCard("투자자 자금흐름: $dirLabel (${of.flowStrength})") {
            Text("종합 점수: ${pctFmt(of.buyerDominanceScore)}")
            Spacer(Modifier.height(4.dp))
            Text("OFI(5일): ${String.format("%.3f", of.ofi5d)} | OFI(20일): ${String.format("%.3f", of.ofi20d)}",
                style = MaterialTheme.typography.bodySmall)
            Text("외국인 매수 압력: ${String.format("%.3f", of.foreignBuyPressure)}",
                style = MaterialTheme.typography.bodySmall)
            Text("기관-외국인 괴리: ${pctFmt(of.institutionalDivergence)}",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text("추세 정렬: ${pctFmt(of.trendAlignment)} | 평균회귀: ${pctFmt(of.meanReversionSignal)}",
                style = MaterialTheme.typography.bodySmall)
            EngineInterpretationBlock(engineInterpretations["orderflow"])
        }
    }

    result.dartEventResult?.let { de ->
        if (de.nEvents > 0 || de.unavailableReason == null) {
            val title = if (de.nEvents > 0) {
                "DART 공시 이벤트: ${DartEventType.toKorean(de.dominantEventType)} (${de.nEvents}건)"
            } else {
                "DART 공시 이벤트: 최근 공시 없음"
            }
            ProbExpandableCard(title) {
                if (de.nEvents > 0) {
                    Text("신호 점수: ${pctFmt(de.signalScore)}")
                    Text("최근 CAR: ${String.format("%+.2f%%", de.latestCar * 100)}",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    de.eventStudies.forEach { es ->
                        Text("${es.eventDate} ${DartEventType.toKorean(es.eventType)}: " +
                                "CAR=${String.format("%+.2f%%", es.carFinal * 100)} " +
                                "(t=${String.format("%.2f", es.tStat)}${if (es.significant) " ★" else ""})",
                            style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text("최근 30일 내 주요 공시가 없습니다.",
                        style = MaterialTheme.typography.bodySmall)
                }
                EngineInterpretationBlock(engineInterpretations["dartevent"])
            }
        }
    }

    result.korea5FactorResult?.let { k5 ->
        if (k5.unavailableReason == null) {
            val alphaDir = when {
                k5.alphaZscore > 1.0 -> "강한 양(+)"
                k5.alphaZscore > 0.3 -> "양(+)"
                k5.alphaZscore < -1.0 -> "강한 음(-)"
                k5.alphaZscore < -0.3 -> "음(-)"
                else -> "중립"
            }
            ProbExpandableCard("5팩터 알파: $alphaDir (z=${String.format("%+.2f", k5.alphaZscore)})") {
                Text("신호 점수: ${pctFmt(k5.signalScore)}")
                Text("alpha: ${String.format("%+.4f", k5.alphaRaw)} (${k5.nObs}개월 윈도우)",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Text("팩터 베타:", style = MaterialTheme.typography.bodySmall)
                Text("  MKT=${String.format("%.2f", k5.betas.mkt)}, SMB=${String.format("%+.3f", k5.betas.smb)}, HML=${String.format("%+.3f", k5.betas.hml)}",
                    style = MaterialTheme.typography.bodySmall)
                Text("  RMW=${String.format("%+.3f", k5.betas.rmw)}, CMA=${String.format("%+.3f", k5.betas.cma)}",
                    style = MaterialTheme.typography.bodySmall)
                Text("R²: ${String.format("%.1f%%", k5.rSquared * 100)}",
                    style = MaterialTheme.typography.bodySmall)
                EngineInterpretationBlock(engineInterpretations["korea5factor"])
            }
        }
    }

    result.sectorCorrelationResult?.let { sc ->
        if (sc.unavailableReason == null) {
            val statusLabel = if (sc.isOutlier) "이상치" else "정상"
            ProbExpandableCard("섹터 상관: $statusLabel (${sc.sectorName}, 상관=${String.format("%.2f", sc.meanNeighborCorr)})") {
                Text("신호 점수: ${pctFmt(sc.signalScore)}")
                Text("이웃 수: ${sc.nNeighbors}개, 피어 수: ${sc.nPeers}개",
                    style = MaterialTheme.typography.bodySmall)
                Text("평균 |상관|: ${String.format("%.3f", sc.avgAbsCorr)}, 축소 강도: ${String.format("%.3f", sc.shrinkageIntensity)}",
                    style = MaterialTheme.typography.bodySmall)
                Text("상관 순위: ${sc.corrRank}/${sc.nPeers} (낮을수록 독립적)",
                    style = MaterialTheme.typography.bodySmall)
                EngineInterpretationBlock(engineInterpretations["sectorcorr"])
            }
        }
    }

    result.macroSignalResult?.let { macro ->
        if (macro.unavailableReason == null) {
            val envLabel = when (macro.macroEnv) {
                "EASING" -> "완화"
                "TIGHTENING" -> "긴축"
                "STAGFLATION" -> "스태그플레이션"
                else -> "중립"
            }
            ProbExpandableCard("매크로 환경: $envLabel") {
                Text("기준금리 YoY: ${String.format("%+.2f", macro.baseRateYoy)}pp",
                    style = MaterialTheme.typography.bodySmall)
                Text("M2 통화량 YoY: ${String.format("%+.1f", macro.m2Yoy)}%",
                    style = MaterialTheme.typography.bodySmall)
                Text("산업생산 YoY: ${String.format("%+.1f", macro.iipYoy)}%",
                    style = MaterialTheme.typography.bodySmall)
                Text("USD/KRW YoY: ${String.format("%+.1f", macro.usdKrwYoy)}%",
                    style = MaterialTheme.typography.bodySmall)
                Text("소비자물가 YoY: ${String.format("%+.1f", macro.cpiYoy)}%",
                    style = MaterialTheme.typography.bodySmall)
                if (macro.referenceMonth.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("기준월: ${macro.referenceMonth}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                EngineInterpretationBlock(engineInterpretations["macro"])
            }
        }
    }

    Text("실행: ${result.executionMetadata.totalTimeMs}ms" +
            if (result.executionMetadata.failedEngines.isNotEmpty())
                " | 실패: ${result.executionMetadata.failedEngines.joinToString()}" else "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 개별 엔진 해석 블록 — 로컬 분석 시 각 카드 하단에 표시 */
@Composable
internal fun EngineInterpretationBlock(interpretation: String?) {
    if (interpretation.isNullOrBlank()) return

    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(6.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            Icons.Default.Lightbulb,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.tertiary
        )
        Text("해석", style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary)
    }
    Spacer(Modifier.height(4.dp))
    Text(
        interpretation,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 포지션 가이드 카드 — Kelly + CVaR 기반 추천 비중 */
@Composable
internal fun PositionGuideCard(
    positionRecommendation: PositionRecommendation,
    conflictMultiplier: Float = 1f,
) {
    val financeColors = LocalFinanceColors.current
    val pr = positionRecommendation
    val isConflictReduced = conflictMultiplier < 1f

    val unavailableReason = pr.unavailableReason
    if (unavailableReason != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Position Guide", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(unavailableReason, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val maxPos = 0.20
    val recPct = pr.recommendedPct * conflictMultiplier
    val barFraction = (recPct / maxPos).toFloat().coerceIn(0f, 1f)

    val barColor = when {
        pr.sizeReasonCode == SizeReasonCode.NO_EDGE ->
            MaterialTheme.colorScheme.onSurfaceVariant
        recPct >= 0.15 -> financeColors.positive
        recPct >= 0.08 -> Color(0xFFFF9800)
        recPct > 0.0 -> Color(0xFF2196F3)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val reasonLabel = when (pr.sizeReasonCode) {
        SizeReasonCode.KELLY_BOUND -> "켈리 제한"
        SizeReasonCode.CVAR_BOUND -> "CVaR 제한"
        SizeReasonCode.MAX_POSITION -> "최대 비중"
        SizeReasonCode.NO_EDGE -> "신호 우위 없음"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Analytics, contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary)
                Text("Position Guide", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                SuggestionChip(
                    onClick = {},
                    label = { Text(reasonLabel, style = MaterialTheme.typography.labelSmall) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = barColor.copy(alpha = 0.2f)
                    ),
                    border = null
                )
                if (isConflictReduced) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(
                            "충돌 축소 ${(conflictMultiplier * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall
                        ) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color(0xFFFCEBEB)
                        ),
                        border = null
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                String.format("%.1f%%", recPct * 100),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = barColor
            )

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
            ) {
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    drawRoundRect(
                        color = Color.Gray.copy(alpha = 0.2f),
                        cornerRadius = CornerRadius(6f)
                    )
                    if (barFraction > 0f) {
                        drawRoundRect(
                            color = barColor.copy(alpha = 0.7f),
                            size = size.copy(width = size.width * barFraction),
                            cornerRadius = CornerRadius(6f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0%", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(String.format("%.1f%%", maxPos * 100),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))

            var showDetails by remember { mutableStateOf(false) }
            TextButton(
                onClick = { showDetails = !showDetails },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(if (showDetails) "상세 접기" else "상세 보기",
                    style = MaterialTheme.typography.labelSmall)
            }

            AnimatedVisibility(visible = showDetails) {
                Column {
                    Text("신호 우위: ${String.format("%+.1f%%p", pr.signalEdge * 100)}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Kelly(원시): ${String.format("%.1f%%", pr.kellyRaw * 100)} | " +
                            "Kelly(분율): ${String.format("%.1f%%", pr.kellyFractional * 100)}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("실현 변동성(20일): ${String.format("%.1f%%", pr.realizedVol * 100)} | " +
                            "W/L Ratio: ${String.format("%.2f", pr.winLossRatio)}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("1일 CVaR(95%): ${String.format("%.2f%%", pr.cvar1d * 100)} | " +
                            "CVaR 한도: ${String.format("%.1f%%", pr.cvarLimit * 100)}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "분석 참고용 자료이며 투자 조언이 아닙니다. 투자 판단의 책임은 본인에게 있습니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
