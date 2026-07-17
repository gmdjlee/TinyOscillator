package com.tinyoscillator.feature.bearsignal.presentation.ui

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tinyoscillator.feature.bearsignal.domain.model.Suggestion

/**
 * §4.5 웹/LLM 제안 패널 — "AI 제안 가져오기" 버튼(명시적 사용자 액션에서만 [onFetch] 호출, init/화면
 * 진입 자동 호출 없음) + 제안 목록(개별 승인/무시 + 일괄 승인) (TASK_bear_signal_console.md §4.5).
 *
 * 승인 원칙(§7): 이 패널이 렌더된다는 사실 자체는 어떤 상태도 바꾸지 않는다. [onApprove]/[onApproveAll]을
 * 사용자가 직접 탭했을 때만 반영된다(승인된 값만 `source=AUTO`) — [onDismiss]는 목록에서 제거만 할 뿐
 * Room에 영향이 없다.
 *
 * 부분 실패 격리(§4.5): [groupErrors]는 실패한 그룹만 표기하고, 성공한 그룹의 [suggestions]는 그대로
 * 노출한다.
 *
 * §4.5 v1.3 "Gemini 경로": [searchWidgetsHtml]이 비어있지 않으면 Google 검색 제안 위젯을 WebView로
 * 렌더한다(ToS상 사용자 표시 의무). Claude 제공자에서는 항상 빈 리스트라 렌더되지 않는다.
 */
@Composable
fun SuggestionPanel(
    suggestions: List<Suggestion>,
    isLoading: Boolean,
    groupErrors: List<String>,
    onFetch: () -> Unit,
    onApprove: (Suggestion) -> Unit,
    onApproveAll: () -> Unit,
    onDismiss: (Suggestion) -> Unit,
    modifier: Modifier = Modifier,
    searchWidgetsHtml: List<String> = emptyList()
) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("AI 제안 — 금리·정책방향·신용잔고·IPO 소화", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "승인해야만 반영됩니다(자동 반영 없음)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onFetch, enabled = !isLoading) {
                    Text(if (isLoading) "조회 중…" else "AI 제안 가져오기")
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

            if (!isLoading && suggestions.isEmpty() && groupErrors.isEmpty()) {
                Text(
                    "\"AI 제안 가져오기\"를 눌러 금리·정책방향·신용잔고·IPO 소화의 최신 웹 검색 제안을 조회하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            suggestions.forEach { suggestion ->
                SuggestionRow(
                    suggestion = suggestion,
                    onApprove = { onApprove(suggestion) },
                    onDismiss = { onDismiss(suggestion) }
                )
            }

            if (suggestions.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onApproveAll) {
                        Text("전체 승인 (${suggestions.size})")
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
private fun SuggestionRow(
    suggestion: Suggestion,
    onApprove: () -> Unit,
    onDismiss: () -> Unit
) {
    val description = buildString {
        append(suggestion.field.labelKo)
        append(": ")
        append(suggestion.currentValue ?: "미설정")
        append(" → ")
        append(suggestion.nextValue)
        append(", 기준일 ")
        append(suggestion.asOf)
        if (suggestion.stale) append(", 오래된 값(STALE)")
        append(", 출처 ")
        append(suggestion.origin)
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
                Text(suggestion.field.labelKo, style = MaterialTheme.typography.bodyMedium)
                if (suggestion.stale) {
                    Spacer(Modifier.width(6.dp))
                    StaleBadge()
                }
            }
            Text(
                "${suggestion.currentValue ?: "-"} → ${suggestion.nextValue}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "기준일 ${suggestion.asOf} · ${suggestion.origin}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onApprove) {
            Icon(
                Icons.Default.Check,
                contentDescription = "${suggestion.field.labelKo} 제안 승인",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Default.Close,
                contentDescription = "${suggestion.field.labelKo} 제안 무시",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
