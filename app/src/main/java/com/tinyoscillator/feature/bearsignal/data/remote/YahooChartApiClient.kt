package com.tinyoscillator.feature.bearsignal.data.remote

import com.tinyoscillator.core.config.ApiConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URLEncoder
import java.time.LocalDate

/**
 * Yahoo Finance chart API 클라이언트 (TASK.md §4 "IPO ETF 방향", "해외 19개 지수").
 *
 * 인증키 불필요 JSON 엔드포인트(`query1.finance.yahoo.com/v8/finance/chart/{symbol}`) —
 * Stooq 안티봇 차단(2026-07 QA) 이후 [com.tinyoscillator.feature.bearsignal.domain.model.GlobalIndexSource.YAHOO]
 * 기본 소스로 채택. 해외지수(`^DJI` 등)와 IPO ETF(`IPO`)가 동일 응답 포맷을 공유하므로 공용
 * 클라이언트로 재사용한다. 기본 UA는 Yahoo가 간헐 차단하므로 브라우저 UA를 명시한다.
 *
 * 조회 범위는 2년 일봉 — 12M 수익률(252거래일+1,
 * [com.tinyoscillator.feature.bearsignal.domain.usecase.GlobalIndexReturnCalculator.LOOKBACK_12M]) 확보용.
 *
 * Rate limit: 1000ms per request (mutex 기반, 기존 관례).
 */
class YahooChartApiClient(
    private val httpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    companion object {
        private const val BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart"

        /** 12M 수익률 계산에 253 종가 필요 → 여유 있게 2년치 일봉 조회 */
        private const val RANGE = "2y"
        private const val INTERVAL = "1d"

        /** OkHttp 기본 UA(`okhttp/x.y`)는 Yahoo가 간헐적으로 429/403 차단 */
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        private const val SECONDS_PER_DAY = 86_400L
    }

    private val rateLimitMutex = Mutex()
    private var lastRequestTime = 0L

    /**
     * 일별 종가 시계열 조회 (오름차순 — 오래된 날짜가 먼저).
     *
     * @param ticker Yahoo 심볼 (예: `IPO`, `^DJI`)
     * @return 종가 리스트(날짜 오름차순), 실패·데이터 없음 시 빈 리스트
     */
    suspend fun fetchDailyCloses(ticker: String): List<IndexDailyBar> = withContext(Dispatchers.IO) {
        throttle()

        val encodedTicker = URLEncoder.encode(ticker, Charsets.UTF_8.name())
        val url = "$BASE_URL/$encodedTicker?range=$RANGE&interval=$INTERVAL"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()

        try {
            // execute().use — 조기 return 경로에서도 body를 닫아 커넥션 풀 누수 방지(Phase 3-3)
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.e("Yahoo chart HTTP 오류: %d (%s)", response.code, ticker)
                    return@withContext emptyList()
                }
                val body = response.body?.string() ?: return@withContext emptyList()
                parseChart(body)
            }
        } catch (e: Exception) {
            Timber.e(e, "Yahoo chart 호출 실패 (%s)", ticker)
            emptyList()
        }
    }

    /**
     * 응답 형태: `{"chart":{"result":[{"timestamp":[...],"indicators":{"quote":[{"close":[...]}]}}],"error":null}}`.
     * 미지 심볼·오류 시 `result`가 null이고 `error`에 사유가 담긴다. `close` 배열의 null은 휴장·결측일.
     */
    internal fun parseChart(body: String): List<IndexDailyBar> {
        return try {
            val chart = json.parseToJsonElement(body).jsonObject["chart"]?.jsonObject ?: return emptyList()
            val result = chart["result"]?.takeIf { it !is JsonNull }?.jsonArray?.firstOrNull()?.jsonObject
                ?: return emptyList()
            val timestamps = result["timestamp"]?.takeIf { it !is JsonNull }?.jsonArray ?: return emptyList()
            val closes = result["indicators"]?.jsonObject
                ?.get("quote")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("close")?.takeIf { it !is JsonNull }?.jsonArray
                ?: return emptyList()

            val bars = mutableListOf<IndexDailyBar>()
            for (i in timestamps.indices) {
                if (i >= closes.size) break
                val closeElement = closes[i]
                if (closeElement is JsonNull) continue
                val close = closeElement.jsonPrimitive.doubleOrNull ?: continue
                val epochSec = timestamps[i].jsonPrimitive.longOrNull ?: continue
                bars.add(IndexDailyBar(date = LocalDate.ofEpochDay(epochSec / SECONDS_PER_DAY).toString(), close = close))
            }
            bars.sortedBy { it.date }
        } catch (e: Exception) {
            Timber.e(e, "Yahoo chart 응답 파싱 실패")
            emptyList()
        }
    }

    private suspend fun throttle() {
        rateLimitMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime
            if (elapsed < ApiConstants.YAHOO_CHART_RATE_LIMIT_MS) {
                kotlinx.coroutines.delay(ApiConstants.YAHOO_CHART_RATE_LIMIT_MS - elapsed)
            }
            lastRequestTime = System.currentTimeMillis()
        }
    }
}
