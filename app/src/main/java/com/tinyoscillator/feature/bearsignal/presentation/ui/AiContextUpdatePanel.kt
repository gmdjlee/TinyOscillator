package com.tinyoscillator.feature.bearsignal.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextClaimValidationResult
import com.tinyoscillator.feature.bearsignal.domain.model.ClaimType

/**
 * §4.7 "정세 업데이트" 승인 미리보기 패널 (TASK_bear_signal_console.md §4.7 "트리거 · 승인 · 저장 ·
 * 렌더", Phase 7-3) — [SuggestionPanel](§4.5)과 동일한 승인 원칙·레이아웃 관례를 따른다.
 *
 * 승인 원칙(§4.7): 이 패널이 렌더된다는 사실 자체는 어떤 상태도 바꾸지 않는다. [onApprove]/[onApproveAll]을
 * 사용자가 직접 탭했을 때만 `bear_signal_ai_context`에 반영된다 — [onDismiss]는 대기 목록에서 제거만
 * 할 뿐 Room에 영향이 없다.
 *
 * [pending]은 [AiContextClaimValidationResult.Accepted.claim.sectionKey]별로 그룹핑해 헤더를 붙인다
 * (유형별 모니터링/사례/역사 비교가 한 번의 조회로 섞여 들어오기 때문).
 *
 * **상태 분기(Phase 6-3)**: fetch 전([hasFetched]=false, [isLoading]=false) → "눌러서 조회" 안내,
 * fetch 중([isLoading]=true) → 진행 표시줄, fetch 후 결과 있음([pending]/[searchWidgetsHtml] 비어있지
 * 않음) → 목록/위젯 렌더, fetch 후 결과 0건([hasFetched]=true·[pending]/[groupErrors] 모두 비어있음)
 * → "새 업데이트 없음" 명시 안내. 무엇을 눌렀는지와 무관하게 fetch 자체가 상태를 바꾸지 않는다는
 * 원칙(§4.7)은 그대로다 — 이 분기는 순수 표시 상태([hasFetched])만 참조한다.
 */
@Composable
fun AiContextUpdatePanel(
    pending: List<AiContextClaimValidationResult.Accepted>,
    provider: String?,
    isLoading: Boolean,
    hasFetched: Boolean,
    groupErrors: List<String>,
    searchWidgetsHtml: List<String>,
    onApprove: (AiContextClaimValidationResult.Accepted) -> Unit,
    onApproveAll: () -> Unit,
    onDismiss: (AiContextClaimValidationResult.Accepted) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(16.dp)) {
            Column {
                Text("정세 업데이트 미리보기", style = MaterialTheme.typography.titleMedium)
                Text(
                    "승인해야만 표시 콘텐츠에 반영됩니다(자동 반영 없음)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (provider != null) {
                    Text(
                        "제공자 · ${providerLabelKo(provider)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isLoading) {
                Spacer(Modifier.padding(top = 8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            groupErrors.forEach { err ->
                Text(
                    text = "실패: $err",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (!isLoading && pending.isEmpty() && groupErrors.isEmpty()) {
                Text(
                    text = if (hasFetched) {
                        "새 업데이트 없음 — 유형별 모니터링·사례·역사 비교 문단이 최신 상태입니다."
                    } else {
                        "\"정세 업데이트\"를 눌러 유형별 모니터링·사례·역사 비교 문단의 최신 웹 검색 결과를 조회하세요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            pending.groupBy { it.claim.sectionKey }.forEach { (sectionKey, claims) ->
                Text(
                    sectionKey.labelKo,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                claims.forEach { accepted ->
                    AiContextClaimRow(
                        accepted = accepted,
                        onApprove = { onApprove(accepted) },
                        onDismiss = { onDismiss(accepted) }
                    )
                }
            }

            if (pending.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onApproveAll) {
                        Text("전체 적용 (${pending.size})")
                    }
                }
            }

            if (searchWidgetsHtml.isNotEmpty()) {
                SearchWidgetsSection(searchWidgetsHtml)
            }
        }
    }
}

@Composable
private fun AiContextClaimRow(
    accepted: AiContextClaimValidationResult.Accepted,
    onApprove: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val claim = accepted.claim
    val description = buildString {
        append(claim.text)
        append(", 기준일 ")
        append(claim.sourceDate)
        if (accepted.stale) append(", 오래된 값(STALE)")
        if (claim.type == ClaimType.INTERPRETATION) append(", AI 견해")
        append(", 출처 ")
        append(claim.sourceTitle)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 6.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (claim.type == ClaimType.INTERPRETATION) {
                    InterpretationBadge()
                    Spacer(Modifier.width(6.dp))
                }
                if (accepted.stale) {
                    StaleBadge()
                    Spacer(Modifier.width(6.dp))
                }
            }
            Text(claim.text, style = MaterialTheme.typography.bodyMedium)
            claim.quote?.takeIf { it.isNotBlank() }?.let { quote ->
                Text(
                    "“$quote”",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                "기준일 ${claim.sourceDate} · ${claim.sourceTitle}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable { openUrlInBrowser(context, claim.sourceUrl) }
            )
        }
        IconButton(onClick = onApprove) {
            Icon(
                Icons.Default.Check,
                contentDescription = "${claim.text} 적용",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Default.Close,
                contentDescription = "${claim.text} 무시",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** "claude"/"gemini" → 표시용 라벨. 알 수 없는 값은 원문 그대로 노출(방어적). */
fun providerLabelKo(provider: String): String = when (provider.lowercase()) {
    "claude" -> "Claude"
    "gemini" -> "Gemini"
    else -> provider
}
