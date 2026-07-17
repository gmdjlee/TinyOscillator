package com.tinyoscillator.feature.bearsignal.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextClaimValidation
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextSectionKey
import com.tinyoscillator.feature.bearsignal.domain.model.ApprovedAiContext
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalStaticContent
import com.tinyoscillator.feature.bearsignal.domain.model.BearType
import com.tinyoscillator.feature.bearsignal.domain.model.ClaimType
import com.tinyoscillator.feature.bearsignal.domain.model.RecoveryOutlook
import com.tinyoscillator.ui.theme.LocalFinanceColors
import java.time.LocalDate

/**
 * 유형 인덱스(0~2) → §4.7 `section_key` 매핑 — [AiContextSectionKey.TYPE0_MONITOR] 등
 * `type{0,1,2}_monitor`/`type{0,1,2}_cases`와 대응한다.
 */
private fun monitorSectionKeyFor(index: Int): AiContextSectionKey = when (index) {
    0 -> AiContextSectionKey.TYPE0_MONITOR
    1 -> AiContextSectionKey.TYPE1_MONITOR
    else -> AiContextSectionKey.TYPE2_MONITOR
}

private fun casesSectionKeyFor(index: Int): AiContextSectionKey = when (index) {
    0 -> AiContextSectionKey.TYPE0_CASES
    1 -> AiContextSectionKey.TYPE1_CASES
    else -> AiContextSectionKey.TYPE2_CASES
}

/**
 * 섹션 5 · 약세장 3유형 카드 (TASK.md §5.2-5, 부록 B #5) — 회복 가능성 + 모니터링 체크리스트,
 * 활성 방아쇠(유형3, `gate>=1`) 하이라이트.
 *
 * @param approved §4.7 승인 캐시(P7-3) — `type{N}_monitor`/`type{N}_cases` 캐시가 있으면 정적
 * [BearType.monitor]/[BearType.cases] 대신 승인 클레임으로 오버레이 렌더한다(AI 배지·출처 각주 포함).
 * 캐시가 없는 유형은 기존 정적 렌더 그대로다(§4.7 "캐시 없으면 정적 fallback 그대로").
 */
@Composable
fun BearSignalTypesSection(
    gate: Int,
    modifier: Modifier = Modifier,
    approved: Map<AiContextSectionKey, ApprovedAiContext> = emptyMap()
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BearSignalStaticContent.TYPES.forEach { type ->
            val active = type.index == BearSignalStaticContent.ACTIVE_TYPE_INDEX && gate >= 1
            TypeCard(type = type, active = active, approved = approved)
        }
    }
}

@Composable
private fun TypeCard(type: BearType, active: Boolean, approved: Map<AiContextSectionKey, ApprovedAiContext>) {
    val accent = LocalFinanceColors.current.negative // 프로토타입 C.accent(파랑)와 동일 계열 재사용
    val recoveryColor = when (type.recoveryOutlook) {
        RecoveryOutlook.LOWEST -> MaterialTheme.colorScheme.error
        RecoveryOutlook.MEDIUM -> levelColor(1)
        RecoveryOutlook.PATIENCE -> accent
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = if (active) BorderStroke(1.dp, accent.copy(alpha = 0.6f)) else null,
        colors = CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        "유형 ${type.index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(type.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        type.axis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (active) {
                Text(
                    "● 현재 활성 방아쇠 (리포트 최유력)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
            Text(type.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(type.recoveryLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = recoveryColor)
            Text("이론 · ${type.theory}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // §4.7 "사례" 오버레이 — type{N}_cases 승인 캐시가 있으면 클레임으로 대체, 없으면 정적.
            val casesApproved = approved[casesSectionKeyFor(type.index)]
            if (casesApproved != null) {
                Text(
                    "사례 · " + casesApproved.claims.joinToString(" / ") { it.text },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val today = remember { LocalDate.now() }
                val stale = casesApproved.claims.any {
                    AiContextClaimValidation.isStale(it.sourceDate, today, casesSectionKeyFor(type.index))
                }
                AiContextBadgeRow(casesApproved, stale, modifier = Modifier.padding(top = 2.dp))
                AiContextSourceFootnotes(casesApproved.claims)
            } else {
                Text("사례 · ${type.cases}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(
                "모니터링 체크리스트",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // §4.7 "모니터링" 오버레이 — type{N}_monitor 승인 캐시가 있으면 클레임 목록으로 대체.
            val monitorApproved = approved[monitorSectionKeyFor(type.index)]
            if (monitorApproved != null) {
                val today = remember { LocalDate.now() }
                val stale = monitorApproved.claims.any {
                    AiContextClaimValidation.isStale(it.sourceDate, today, monitorSectionKeyFor(type.index))
                }
                AiContextBadgeRow(monitorApproved, stale, modifier = Modifier.padding(bottom = 2.dp))
                monitorApproved.claims.forEachIndexed { idx, claim ->
                    MonitorChecklistRow(text = claim.text, tint = recoveryColor, saveKey = "bear_ai_type${type.index}_monitor$idx")
                }
                AiContextSourceFootnotes(monitorApproved.claims)
            } else {
                type.monitor.forEachIndexed { idx, item ->
                    MonitorChecklistRow(text = item, tint = recoveryColor, saveKey = "bear_type${type.index}_monitor$idx")
                }
            }
        }
    }
}

/**
 * 체크리스트 개별 행 — 체크 상태는 [rememberSaveable]로 유지한다(2026-07-13 실기 재현된 MINOR:
 * back 후 재진입 시 `remember`만으로는 상태가 소실됨). 동일 컴포저블이 [BearSignalTypesSection]에서
 * 유형(type.index)×항목(idx) 조합으로 반복 호출되므로, 위치 기반 암묵적 키 대신 [saveKey]를 명시
 * 전달해 프로세스 재생성 후에도 항목별 체크 상태가 올바르게 복원되도록 한다.
 */
@Composable
private fun MonitorChecklistRow(text: String, tint: Color, saveKey: String) {
    var checked by rememberSaveable(key = saveKey) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(
            checked = checked,
            onCheckedChange = { checked = it },
            colors = CheckboxDefaults.colors(checkedColor = tint),
            modifier = Modifier.padding(end = 4.dp)
        )
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

/**
 * 섹션 6 · 역사 검증(일본 3충격) + 3대 모니터링 (TASK.md §5.2-6, 부록 B #6).
 *
 * @param approved §4.7 승인 캐시(P7-3) — `history_current`(현재 비교 문단) 캐시가 있으면
 * [BearSignalStaticContent.HISTORY_BODY_CURRENT] 대신 승인 클레임으로 오버레이 렌더한다. 서사부
 * ([BearSignalStaticContent.HISTORY_BODY_STATIC])는 §4.7 동적 갱신 금지 대상이라 항상 정적이다.
 */
@Composable
fun BearSignalHistorySection(
    modifier: Modifier = Modifier,
    approved: Map<AiContextSectionKey, ApprovedAiContext> = emptyMap()
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(BearSignalStaticContent.HISTORY_TITLE, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            // §4.7 "동적 갱신 금지(정적 전용)" — 1980s 서사부는 항상 정적으로 렌더한다.
            Text(
                BearSignalStaticContent.HISTORY_BODY_STATIC,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // §4.7 "현재 비교" 문단 오버레이 — history_current 승인 캐시가 있으면 클레임으로 대체.
            val historyApproved = approved[AiContextSectionKey.HISTORY_CURRENT]
            if (historyApproved != null) {
                val today = remember { LocalDate.now() }
                val stale = historyApproved.claims.any {
                    AiContextClaimValidation.isStale(it.sourceDate, today, AiContextSectionKey.HISTORY_CURRENT)
                }
                AiContextBadgeRow(historyApproved, stale)
                historyApproved.claims.forEach { claim ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (claim.type == ClaimType.INTERPRETATION) {
                            InterpretationBadge()
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(claim.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                AiContextSourceFootnotes(historyApproved.claims)
            } else {
                Text(
                    BearSignalStaticContent.HISTORY_BODY_CURRENT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            BearSignalStaticContent.HISTORY_METRICS.forEach { metric ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            metric.header,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            metric.body,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
