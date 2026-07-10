package com.tinyoscillator.feature.bearsignal.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalResult
import com.tinyoscillator.feature.bearsignal.domain.model.Depth
import com.tinyoscillator.feature.bearsignal.domain.model.SignalLevel

/**
 * 섹션 2 · 선행 신호 3 카드 (TASK.md §5.2-2, 부록 B #1) — 신호1/2/3 게이지 + 레벨 칩 + 자동값/근거.
 *
 * 신호1(국가별 수익률)은 §5.2 섹션3 표에서 인라인 편집하고, 신호2(±3σ 통계)는 [A] 완전자동으로
 * 수동 오버라이드 경로가 없다(§1.1). 신호3만 [C]/[D] 등급 수동 입력 경로(loss/big)가 있어
 * "수동 입력" 버튼을 노출한다.
 */
@Composable
fun BearSignalLeadingSignalsSection(
    inputs: BearSignalInputs,
    result: BearSignalResult,
    auto: AutoBearSignalInputs?,
    onManualInputClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SignalCard(
            tag = "신호 1 · 주변부 압착",
            title = "주변부부터 식어가는가",
            level = result.s1
        ) {
            ReadoutRow(
                listOf(
                    Triple("이탈 지수 수", "${result.ma.neg} / 20", if (result.ma.neg >= 7) levelColor(2) else MaterialTheme.colorScheme.onSurfaceVariant),
                    Triple("신규 이탈 최저 낙폭", "${"%.1f".format(result.ma.worstNew)}%", if (result.ma.worstNew <= -6) levelColor(2) else MaterialTheme.colorScheme.onSurfaceVariant),
                    Triple("낙폭 판정", depthLabel(result.ma.depth), levelColor(result.s1))
                )
            )
            Text(
                "닷컴 정점 직전 1개월 = 7개국 이탈 · 아래 국가별 수익률 표(§섹션3)에서 자동 산출·인라인 편집",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SignalCard(
            tag = "신호 2 · 변동성 무게중심",
            title = "급락이 급등을 앞지르나",
            level = result.s2
        ) {
            val ratio = if (inputs.up == 0) 0.0 else inputs.down.toDouble() / inputs.up
            ReadoutRow(
                listOf(
                    Triple("±3σ 상승/하락일", "${inputs.up} / ${inputs.down}", MaterialTheme.colorScheme.onSurfaceVariant),
                    Triple("하락/상승 비율", "%.2f".format(ratio), levelColor(result.s2)),
                    Triple(
                        "±4σ 상승/하락(참고)",
                        "${auto?.up4?.value ?: "-"} / ${auto?.down4?.value ?: "-"}",
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            )
            Row(Modifier.padding(top = 6.dp)) {
                SourceBadge(indicator = auto?.up3)
            }
            Text(
                "1.0 초과 시 천장 신호 · [A] 완전자동(KRX 코스피 일별 종가) — 수동 오버라이드 경로 없음",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SignalCard(
            tag = "신호 3 · IPO 질",
            title = "위험선호의 거울",
            level = result.s3
        ) {
            ReadoutRow(
                listOf(
                    Triple("적자상장 비중", "${"%.0f".format(inputs.loss)}%", MaterialTheme.colorScheme.onSurfaceVariant),
                    Triple("IPO ETF 방향", etfLabel(inputs.etf), MaterialTheme.colorScheme.onSurfaceVariant),
                    Triple("대어 공모 소화", bigLabel(inputs.big), levelColor(result.s3))
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "양이 아니라 질 — 적자상장 비중 급등·대어 흥행 실패가 위험선호 과열의 거울",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onManualInputClick) { Text("수동 입력") }
            }
        }
    }
}

@Composable
internal fun SignalCard(
    tag: String,
    title: String,
    level: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                LevelChip(level = level)
            }
            SignalGauge(level = level, modifier = Modifier.fillMaxWidth())
            content()
        }
    }
}

@Composable
internal fun LevelChip(level: Int, labels: List<String> = SignalLevel.entries.map { it.label }, modifier: Modifier = Modifier) {
    val color = levelColor(level)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = labels.getOrElse(level) { "-" },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
internal fun ReadoutRow(items: List<Triple<String, String, Color>>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        items.forEach { (label, value, color) ->
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
            }
        }
    }
}

private fun depthLabel(depth: Depth): String = when (depth) {
    Depth.SHALLOW -> "얕음"
    Depth.DEEPENING -> "심화 중"
    Depth.DEEP -> "깊음"
}

private fun etfLabel(etf: String): String = when (etf) {
    "up" -> "상승/회복"
    "down" -> "하락 전환"
    else -> "횡보"
}

private fun bigLabel(big: String): String = when (big) {
    "smooth" -> "원활"
    "pending" -> "대기"
    "failed" -> "실패/삐끗"
    else -> big
}
