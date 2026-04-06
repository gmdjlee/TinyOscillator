package com.tinyoscillator.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tinyoscillator.domain.model.AlgoResult
import kotlin.math.roundToInt

/**
 * 신호 분석 근거 카드 — 펼치기/접기로 알고리즘별 점수 + 한국어 근거 표시.
 *
 * 기본 접힌 상태: 앙상블 점수 + 방향 배지만 표시.
 * 펼침 상태: 알고리즘별 점수 바 + 근거 문자열 목록.
 */
@Composable
fun SignalRationaleCard(
    algoResults: Map<String, AlgoResult>,
    ensembleScore: Float,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            // 헤더: 최종 앙상블 점수 + 펼치기 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "신호 분석 근거",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "앙상블 점수: ${(ensembleScore * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ScoreBadge(score = ensembleScore)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded)
                            Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "접기" else "근거 보기",
                    )
                }
            }

            // 펼쳐진 상태: 알고리즘별 근거 목록
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(thickness = 0.5.dp)
                    Spacer(Modifier.height(8.dp))

                    algoResults.entries
                        .sortedByDescending { it.value.score }
                        .forEach { (_, result) ->
                            AlgoRationaleRow(result = result)
                            Spacer(Modifier.height(6.dp))
                        }
                }
            }
        }
    }
}

@Composable
fun AlgoRationaleRow(result: AlgoResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 알고리즘 이름 (한국어 매핑)
        Text(
            text = ALGO_DISPLAY_NAMES[result.algoName] ?: result.algoName,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(80.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))

        // 점수 바 (Compose Canvas)
        SignalScoreBar(
            score = result.score,
            modifier = Modifier
                .weight(1f)
                .height(6.dp),
        )
        Spacer(Modifier.width(8.dp))

        // 점수 퍼센트
        Text(
            text = "${(result.score * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = scoreColor(result.score),
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End,
        )
    }

    // 근거 문자열
    if (result.rationale.isNotBlank()) {
        Text(
            text = result.rationale,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 88.dp, top = 2.dp),
        )
    }
}

@Composable
fun SignalScoreBar(score: Float, modifier: Modifier = Modifier) {
    val barColor = lerpScoreColor(score)
    Canvas(modifier = modifier) {
        // 배경
        drawRoundRect(
            color = Color(0x22888780),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
        // 점수 바
        drawRoundRect(
            color = barColor,
            size = Size(size.width * score.coerceIn(0f, 1f), size.height),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
    }
}

// 알고리즘 한국어 이름 매핑
val ALGO_DISPLAY_NAMES = mapOf(
    "NaiveBayes" to "나이브베이즈",
    "Logistic" to "로지스틱회귀",
    "HMM" to "HMM",
    "PatternScan" to "패턴스캔",
    "SignalScoring" to "가중신호",
    "BayesianUpdate" to "베이지안",
    "OrderFlow" to "주문흐름",
    "DartEvent" to "공시이벤트",
    "Korea5Factor" to "5팩터",
    "SectorCorrelation" to "섹터상관",
)

fun scoreColor(score: Float): Color = when {
    score >= 0.65f -> Color(0xFFD05540)   // 강세 적색
    score <= 0.35f -> Color(0xFF4088CC)   // 약세 청색
    else -> Color(0xFF8A8580)             // 중립 회색
}

/** 점수에 따라 청색(약세)↔회색(중립)↔적색(강세) 보간 */
private fun lerpScoreColor(score: Float): Color {
    val clamped = score.coerceIn(0f, 1f)
    return when {
        clamped <= 0.5f -> lerp(Color(0xFF4088CC), Color(0xFF8A8580), clamped * 2f)
        else -> lerp(Color(0xFF8A8580), Color(0xFFD05540), (clamped - 0.5f) * 2f)
    }
}

@Composable
fun ScoreBadge(score: Float) {
    val (text, color) = when {
        score >= 0.65f -> "강세" to Color(0xFFD05540)
        score <= 0.35f -> "약세" to Color(0xFF4088CC)
        else -> "중립" to Color(0xFF8A8580)
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
