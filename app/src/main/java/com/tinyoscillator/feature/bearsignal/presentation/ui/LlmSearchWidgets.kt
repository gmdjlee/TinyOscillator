package com.tinyoscillator.feature.bearsignal.presentation.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * §4.5/§4.7 LLM 수집 공용 컴포저블 — 원래 [SuggestionPanel]에 private로 있던
 * `StaleBadge`/`SearchWidgetsSection`/`GoogleSearchWidget`를 공용으로 추출한다(P7-3, §4.7
 * "정세 업데이트" 패널·오버레이 렌더가 동일한 STALE 배지·Gemini 검색 제안 위젯 렌더를 필요로
 * 하기 때문). 동작·스타일은 §4.5 v1.3 원본과 동일하게 유지한다.
 */

/** 라벨 배지 공통 골격 — 색(테마 컨테이너 톤) + 텍스트 병기(§5.4 접근성 "색+텍스트 병기"). */
@Composable
fun LabelBadge(text: String, containerColor: Color, contentColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

/** STALE 배지 — 색(에러 톤) + 텍스트 병기(§5.4 접근성 "색+텍스트 병기"). §4.5/§4.7 공용. */
@Composable
fun StaleBadge(modifier: Modifier = Modifier) {
    LabelBadge(
        text = "STALE",
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier
    )
}

/** §4.7 "출처 약검증" 배지 — Gemini 경로 클레임/오버레이에 병기(§4.7 "제공자 정책"). */
@Composable
fun WeakSourceBadge(modifier: Modifier = Modifier) {
    LabelBadge(
        text = "출처 약검증",
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
    )
}

/** §4.7 "AI 견해" 배지 — `history_current`의 `interpretation` 클레임에 필수 병기. */
@Composable
fun InterpretationBadge(modifier: Modifier = Modifier) {
    LabelBadge(
        text = "AI 견해",
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier
    )
}

/**
 * §4.5 v1.3 "Gemini 경로": [searchWidgetsHtml]이 비어있지 않으면 Google 검색 제안 위젯을 WebView로
 * 렌더한다(ToS상 사용자 표시 의무). Claude 제공자에서는 항상 빈 리스트라 렌더되지 않는다.
 */
@Composable
fun SearchWidgetsSection(searchWidgetsHtml: List<String>, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 8.dp)) {
        Text(
            "Google 검색 제안",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        searchWidgetsHtml.forEach { html ->
            GoogleSearchWidget(html, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * 개별 Google 검색 제안 위젯 렌더러 — JavaScript는 비활성화(기본값 false 유지)하고, 위젯 내 링크는
 * 앱 내부가 아닌 외부 브라우저로 연다([WebViewClient.shouldOverrideUrlLoading]). 브라우저가 없는
 * 기기(워크프로필·키오스크 등)에서는 [ActivityNotFoundException]을 안내 토스트로 흡수한다(크래시 금지).
 * 배경은 투명 처리(다크 테마 카드 위 흰 띠 방지 — 기존 차트 [AndroidView] 임베드 관례) 하고,
 * recomposition마다 동일 HTML을 재로드하지 않도록 `tag`로 마지막 로드 내용을 추적한다.
 */
@Composable
fun GoogleSearchWidget(html: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth().height(64.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        try {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(ctx, "링크를 열 수 있는 앱이 없습니다", Toast.LENGTH_SHORT).show()
                        }
                        return true
                    }
                }
            }
        },
        update = { webView ->
            if (webView.tag != html) {
                webView.tag = html
                webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() }
    )
}

/**
 * §4.7 승인 미리보기·오버레이 출처 각주 "탭→브라우저" 공통 구현 — 브라우저가 없는 기기에서는
 * [ActivityNotFoundException]을 안내 토스트로 흡수한다(크래시 금지, [GoogleSearchWidget]과 동일 정책).
 */
fun openUrlInBrowser(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "링크를 열 수 있는 앱이 없습니다", Toast.LENGTH_SHORT).show()
    }
}
