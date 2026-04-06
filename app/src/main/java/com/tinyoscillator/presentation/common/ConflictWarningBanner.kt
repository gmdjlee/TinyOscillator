package com.tinyoscillator.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyoscillator.domain.usecase.SignalConflictDetector

/**
 * 알고리즘 신호 충돌 경고 배너.
 *
 * 충돌 수준(LOW/HIGH/CRITICAL)에 따라 다른 색상의 배너를 표시하고,
 * 강세/중립/약세 분포 바와 추천 포지션 배수를 보여준다.
 * 충돌이 없으면(NONE) 아무것도 표시하지 않는다.
 */
@Composable
fun ConflictWarningBanner(
    conflictResult: SignalConflictDetector.ConflictResult,
    modifier: Modifier = Modifier,
) {
    val level = conflictResult.conflictLevel

    if (level == SignalConflictDetector.ConflictLevel.NONE) return

    val backgroundColor: Color
    val borderColor: Color
    val textColor: Color
    val icon: String

    // 테마 색상 기반 — 다크/라이트 모드 양쪽에서 자연스러움
    when (level) {
        SignalConflictDetector.ConflictLevel.LOW -> {
            backgroundColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            borderColor = MaterialTheme.colorScheme.secondary
            textColor = MaterialTheme.colorScheme.onSecondaryContainer
            icon = "!"
        }
        SignalConflictDetector.ConflictLevel.HIGH -> {
            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            borderColor = MaterialTheme.colorScheme.tertiary
            textColor = MaterialTheme.colorScheme.onTertiaryContainer
            icon = "!!"
        }
        SignalConflictDetector.ConflictLevel.CRITICAL -> {
            backgroundColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            borderColor = MaterialTheme.colorScheme.error
            textColor = MaterialTheme.colorScheme.onErrorContainer
            icon = "!!!"
        }
        else -> return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = backgroundColor,
        border = BorderStroke(0.5.dp, borderColor),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = borderColor,
                    modifier = Modifier.size(20.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            icon,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when (level) {
                        SignalConflictDetector.ConflictLevel.LOW -> "알고리즘 의견 경미한 불일치"
                        SignalConflictDetector.ConflictLevel.HIGH -> "알고리즘 의견 충돌 감지"
                        SignalConflictDetector.ConflictLevel.CRITICAL -> "알고리즘 의견 극심한 분열"
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = conflictResult.warningMessage,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
            )
            Spacer(Modifier.height(8.dp))

            ConflictDistributionBar(
                bullCount = conflictResult.bullCount,
                neutralCount = conflictResult.neutralCount,
                bearCount = conflictResult.bearCount,
                total = conflictResult.bullCount +
                        conflictResult.neutralCount +
                        conflictResult.bearCount,
            )

            Spacer(Modifier.height(6.dp))
            Text(
                "추천 포지션 배수: ${(conflictResult.positionMultiplier * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** 강세/중립/약세 분포 바 */
@Composable
fun ConflictDistributionBar(
    bullCount: Int,
    neutralCount: Int,
    bearCount: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    if (total == 0) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
    ) {
        if (bullCount > 0)
            Box(
                modifier = Modifier
                    .weight(bullCount.toFloat())
                    .fillMaxHeight()
                    .background(Color(0xFFD05540))
            )
        if (neutralCount > 0)
            Box(
                modifier = Modifier
                    .weight(neutralCount.toFloat())
                    .fillMaxHeight()
                    .background(Color(0xFF8A8580))
            )
        if (bearCount > 0)
            Box(
                modifier = Modifier
                    .weight(bearCount.toFloat())
                    .fillMaxHeight()
                    .background(Color(0xFF4088CC))
            )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "강세 ${bullCount}개",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFD05540),
            fontSize = 10.sp,
        )
        Text(
            "중립 ${neutralCount}개",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8A8580),
            fontSize = 10.sp,
        )
        Text(
            "약세 ${bearCount}개",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF4088CC),
            fontSize = 10.sp,
        )
    }
}
