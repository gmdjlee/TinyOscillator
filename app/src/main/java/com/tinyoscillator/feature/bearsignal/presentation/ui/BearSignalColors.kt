package com.tinyoscillator.feature.bearsignal.presentation.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tinyoscillator.feature.bearsignal.domain.model.AutoIndicator
import com.tinyoscillator.feature.bearsignal.domain.model.BearPhase
import com.tinyoscillator.feature.bearsignal.domain.model.InputSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LEVEL 색 매핑(TASK.md §5.4) — 안전=primary(그린) · 주의=secondary(브라스 골드=앰버) ·
 * 경고=오렌지(M3 기본 롤에 없어 보강) · 위험=error(레드). 다크/라이트 모두 대응.
 *
 * 색만으로 구분하지 않는다 — 호출부는 항상 [com.tinyoscillator.feature.bearsignal.domain.model.SignalLevel.label]
 * 등 텍스트를 함께 표기해 색각 이상 사용자도 구분 가능하게 한다(§5.4 접근성).
 */
@Composable
fun levelColor(level: Int): Color {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (level) {
        0 -> MaterialTheme.colorScheme.primary
        1 -> MaterialTheme.colorScheme.secondary
        2 -> if (isDark) Color(0xFFE8823A) else Color(0xFFB25D1E)
        else -> MaterialTheme.colorScheme.error
    }
}

/** [BearPhase] → LEVEL 색 매핑(GREEN=0 · AMBER=1 · ORANGE=2 · RED=3) */
@Composable
fun phaseColor(phase: BearPhase): Color = levelColor(
    when (phase) {
        BearPhase.GREEN -> 0
        BearPhase.AMBER -> 1
        BearPhase.ORANGE -> 2
        BearPhase.RED -> 3
    }
)

/** 국면별 라벨·부제·해설 문구 (프로토타입 `PHASE_META` 1:1, 부록 B #4) */
data class PhaseMeta(val label: String, val sub: String, val desc: String)

fun phaseMeta(phase: BearPhase): PhaseMeta = when (phase) {
    BearPhase.GREEN -> PhaseMeta(
        label = "안정 국면",
        sub = "신호등 소등 · 상승 지속 가능",
        desc = "선행 신호가 켜지지 않았고 금리 방아쇠도 정상화 구간이다. 쏠림 자체는 위험이 아니라는 " +
            "리포트의 전제가 그대로 유효한 상태."
    )
    BearPhase.AMBER -> PhaseMeta(
        label = "신호 점등 · 방아쇠 대기",
        sub = "선행 신호는 켜졌으나 결정타(금리)는 미발동",
        desc = "주변부 균열·양방향 변동성·레버리지 급증으로 신호등엔 불이 켜졌다. 다만 정상화를 넘어선 " +
            "금리 인상과 적자기업 IPO 급증이라는 결정타는 아직 당겨지지 않았다. — 리포트의 현 진단."
    )
    BearPhase.ORANGE -> PhaseMeta(
        label = "방아쇠 임박",
        sub = "금리 임계 접근 · 선행 신호 경고 다수",
        desc = "금리가 정상화 한계선(≈4.5%)에 다가서거나 선행 신호가 경고 이상으로 다수 점등됐다. " +
            "심리 균열이 실물 공백과 세트로 엮이기 직전 구간."
    )
    BearPhase.RED -> PhaseMeta(
        label = "약세장 격발",
        sub = "긴축 돌입 + 선행 신호 위험 = 톱니바퀴 결합",
        desc = "금리가 진짜 긴축으로 인식되며 멀티플·유동성·수요를 동시에 마비시킨다. 심리 압축과 실물 " +
            "둔화가 맞물려 파괴력이 기하급수적으로 증폭되는 국면."
    )
}

/** [InputSource] 배지 — AUTO/MANUAL/리포트 기준값(둘 다 없음) 구분 + 최신 갱신일(§1.2, §5.4) */
@Composable
fun SourceBadge(indicator: AutoIndicator<*>?, modifier: Modifier = Modifier) {
    val (label, color) = when (indicator?.source) {
        InputSource.MANUAL -> "수동" to MaterialTheme.colorScheme.primary
        InputSource.AUTO -> "자동" to MaterialTheme.colorScheme.secondary
        null -> "기준값" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val text = if (indicator != null) "$label · ${formatUpdatedAt(indicator.updatedAt)}" else label
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

fun formatUpdatedAt(epochMillis: Long): String =
    SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date(epochMillis))
