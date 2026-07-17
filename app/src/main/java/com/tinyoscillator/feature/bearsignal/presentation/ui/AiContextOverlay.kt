package com.tinyoscillator.feature.bearsignal.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextClaim
import com.tinyoscillator.feature.bearsignal.domain.model.ApprovedAiContext

/**
 * §4.7 "렌더" 절 "AI 배지 + as_of + STALE + 출처 각주" — [BearSignalTypesSection]/[BearSignalHistorySection]
 * 오버레이 공용 컴포저블(Phase 7-3). 승인 캐시([ApprovedAiContext])가 존재하는 섹션에서만 호출되며,
 * 없으면 호출측이 정적 fallback([com.tinyoscillator.feature.bearsignal.domain.model.BearSignalStaticContent])을
 * 그대로 렌더한다(§4.7 "캐시 없으면 정적 fallback 그대로").
 */

/**
 * "AI 갱신 · as_of · 제공자" 표기 행 — Gemini 경로는 "출처 약검증" 배지, [stale]이면 STALE 배지를
 * 추가로 병기한다(§4.7 "제공자 정책" · "검증 파이프라인" STALE 규칙).
 */
@Composable
fun AiContextBadgeRow(approved: ApprovedAiContext, stale: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "AI 갱신 · ${approved.asOf} · ${providerLabelKo(approved.provider)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (approved.provider.equals("gemini", ignoreCase = true)) {
            Spacer(Modifier.width(6.dp))
            WeakSourceBadge()
        }
        if (stale) {
            Spacer(Modifier.width(6.dp))
            StaleBadge()
        }
    }
}

/**
 * 클레임별 출처 각주 — "출처 · {sourceTitle}"(탭→외부 브라우저, [openUrlInBrowser]). `sourceTitle`이
 * 비어있으면 URL 원문으로 대체 표시한다.
 */
@Composable
fun AiContextSourceFootnotes(claims: List<AiContextClaim>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier) {
        claims.forEach { claim ->
            val label = claim.sourceTitle.ifBlank { claim.sourceUrl }
            Text(
                "출처 · $label",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable { openUrlInBrowser(context, claim.sourceUrl) }
                    .semantics { contentDescription = "$label 출처 링크 열기" }
            )
        }
    }
}
