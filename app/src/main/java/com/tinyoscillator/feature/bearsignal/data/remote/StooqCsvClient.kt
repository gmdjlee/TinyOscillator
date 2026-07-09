package com.tinyoscillator.feature.bearsignal.data.remote

import com.tinyoscillator.core.config.ApiConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/** Stooq 일별 종가 한 건 (날짜 오름차순 정렬은 호출자 책임) */
data class StooqDailyBar(val date: String, val close: Double)

/**
 * Stooq 무료 CSV 시세 클라이언트 (TASK.md §4 "IPO ETF 방향", "해외 19개 지수").
 *
 * 인증키 불필요 — TASK.md §1.1 각주1이 명시한 "무료 CSV(예: Stooq)" 폴백 소스. IPO ETF(티커
 * `ipo.us`)와 해외지수([com.tinyoscillator.feature.bearsignal.domain.model.GlobalIndexRegistry]
 * 커버 대상)가 동일 CSV 포맷(`Date,Open,High,Low,Close,Volume`)을 공유하므로 공용 클라이언트로
 * 재사용한다.
 *
 * Rate limit: 1000ms per request (mutex 기반, 기존 관례).
 */
class StooqCsvClient(
    private val httpClient: OkHttpClient
) {

    companion object {
        private const val BASE_URL = "https://stooq.com/q/d/l/"
        private const val CSV_HEADER_PREFIX = "Date,"
        private const val NO_DATA_MARKER = "N/D"
    }

    private val rateLimitMutex = Mutex()
    private var lastRequestTime = 0L

    /**
     * 일별 종가 시계열 조회 (오름차순 — 오래된 날짜가 먼저).
     *
     * @param ticker Stooq 심볼 (예: `ipo.us`, `^dji`)
     * @return 종가 리스트(날짜 오름차순), 실패·데이터 없음 시 빈 리스트
     */
    suspend fun fetchDailyCloses(ticker: String): List<StooqDailyBar> = withContext(Dispatchers.IO) {
        throttle()

        val url = "$BASE_URL?s=$ticker&i=d"
        val request = Request.Builder().url(url).build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.e("Stooq CSV HTTP 오류: %d (%s)", response.code, ticker)
                return@withContext emptyList()
            }
            val body = response.body?.string() ?: return@withContext emptyList()
            parseCsv(body)
        } catch (e: Exception) {
            Timber.e(e, "Stooq CSV 호출 실패 (%s)", ticker)
            emptyList()
        }
    }

    internal fun parseCsv(csv: String): List<StooqDailyBar> {
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyList()
        // Stooq는 심볼이 없거나 데이터가 없으면 "No data" 텍스트를 반환한다.
        if (!lines.first().startsWith(CSV_HEADER_PREFIX)) return emptyList()

        val bars = mutableListOf<StooqDailyBar>()
        for (line in lines.drop(1)) {
            val cols = line.split(",")
            if (cols.size < 5) continue
            val date = cols[0].trim()
            val closeRaw = cols[4].trim()
            if (date.isEmpty() || closeRaw.isEmpty() || closeRaw == NO_DATA_MARKER) continue
            val close = closeRaw.toDoubleOrNull() ?: continue
            bars.add(StooqDailyBar(date = date, close = close))
        }
        return bars.sortedBy { it.date }
    }

    private suspend fun throttle() {
        rateLimitMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime
            if (elapsed < ApiConstants.STOOQ_RATE_LIMIT_MS) {
                kotlinx.coroutines.delay(ApiConstants.STOOQ_RATE_LIMIT_MS - elapsed)
            }
            lastRequestTime = System.currentTimeMillis()
        }
    }
}
