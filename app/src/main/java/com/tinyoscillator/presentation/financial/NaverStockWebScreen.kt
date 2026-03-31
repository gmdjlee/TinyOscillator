package com.tinyoscillator.presentation.financial

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/124.0.0.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NaverStockWebScreen(
    ticker: String?,
    modifier: Modifier = Modifier
) {
    if (ticker == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "종목을 선택해주세요.\n검색 화면에서 종목을 검색하고 선택하세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val context = LocalContext.current
    val cleanTicker = remember(ticker) {
        ticker.removeSuffix(".KS").removeSuffix(".KQ")
    }
    val url = remember(cleanTicker) { WiseReportUrl.of(cleanTicker) }

    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // 종목 변경 시 상태 리셋
    LaunchedEffect(url) {
        isLoading = true
        hasError = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (hasError) {
            NaverWebErrorCard(
                onRetry = {
                    hasError = false
                    isLoading = true
                    webViewInstance?.reload()
                },
                onOpenBrowser = { openInChromeTabs(context, url) }
            )
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).also { wv ->
                        wv.webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) {
                                isLoading = true
                                hasError = false
                            }

                            override fun onPageFinished(view: WebView, u: String?) {
                                isLoading = false
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError
                            ) {
                                if (request.isForMainFrame) {
                                    hasError = true
                                    isLoading = false
                                }
                            }
                        }
                        wv.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            userAgentString = DESKTOP_UA
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }
                        webViewInstance = wv
                        wv.loadUrl(url)
                    }
                },
                update = { wv ->
                    if (wv.url != url) {
                        wv.loadUrl(url)
                    }
                }
            )
        }

        if (isLoading && !hasError) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun NaverWebErrorCard(
    onRetry: () -> Unit,
    onOpenBrowser: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "페이지를 불러올 수 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRetry) {
                        Text("재시도")
                    }
                    Button(onClick = onOpenBrowser) {
                        Text("브라우저로 열기")
                    }
                }
            }
        }
    }
}

private fun openInChromeTabs(context: Context, url: String) {
    CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
        .launchUrl(context, Uri.parse(url))
}
