package com.tinyoscillator.feature.bearsignal.data.remote

import com.tinyoscillator.core.config.ApiConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/** FRED(세인트루이스 연은) 시계열 관측값 한 건 */
data class FredObservation(val date: String, val value: Double)

/**
 * FRED(Federal Reserve Economic Data) API 클라이언트 (TASK.md §4 "미 연준 상단").
 *
 * 기본 시리즈는 `DFEDTARU`(연방기금금리 목표 상단, Daily) — §3.4 `scoreGate`의 `rate` 입력.
 * FRED는 결측치를 문자열 `"."`으로 표기하므로 이를 건너뛰고 최신 유효값을 반환한다.
 *
 * Rate limit: 1000ms per request (mutex 기반, 기존 BokEcosApiClient 관례).
 */
class FredApiClient(
    private val httpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    companion object {
        private const val BASE_URL = "https://api.stlouisfed.org/fred/series/observations"

        /** 연방기금금리 목표 상단(Upper Limit) — §3.4 `rate` 입력의 기본 시리즈 */
        const val SERIES_FED_FUNDS_TARGET_UPPER = "DFEDTARU"

        /** FRED가 결측치를 표기하는 값 */
        private const val MISSING_VALUE_MARKER = "."
    }

    private val rateLimitMutex = Mutex()
    private var lastRequestTime = 0L

    /**
     * 시리즈의 최신 유효 관측값 조회 (결측치 자동 스킵).
     *
     * @param apiKey FRED API 인증키
     * @param seriesId FRED 시리즈 ID (기본 [SERIES_FED_FUNDS_TARGET_UPPER])
     * @param limit 최신순으로 조회할 관측값 개수(결측치가 섞여 있을 수 있어 여유 있게 조회)
     * @return 최신 유효 관측값, 없으면 null
     */
    suspend fun fetchLatestObservation(
        apiKey: String,
        seriesId: String = SERIES_FED_FUNDS_TARGET_UPPER,
        limit: Int = 10
    ): FredObservation? = withContext(Dispatchers.IO) {
        throttle()

        val url = "$BASE_URL?series_id=$seriesId&api_key=$apiKey&file_type=json" +
            "&sort_order=desc&limit=$limit"
        val request = Request.Builder().url(url).build()

        try {
            // execute().use — 조기 return 경로에서도 body를 닫아 커넥션 풀 누수 방지(Phase 3-3)
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.e("FRED API HTTP 오류: %d (%s)", response.code, seriesId)
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                parseLatestValid(body)
            }
        } catch (e: Exception) {
            Timber.e(e, "FRED API 호출 실패 (%s)", seriesId)
            null
        }
    }

    internal fun parseLatestValid(body: String): FredObservation? {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val observations = root["observations"]?.jsonArray ?: return null
            for (element in observations) {
                val obj = element.jsonObject
                val date = obj["date"]?.jsonPrimitive?.content ?: continue
                val rawValue = obj["value"]?.jsonPrimitive?.content ?: continue
                if (rawValue == MISSING_VALUE_MARKER) continue
                val value = rawValue.toDoubleOrNull() ?: continue
                return FredObservation(date = date, value = value)
            }
            null
        } catch (e: Exception) {
            Timber.e(e, "FRED 응답 파싱 실패")
            null
        }
    }

    private suspend fun throttle() {
        rateLimitMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime
            if (elapsed < ApiConstants.FRED_RATE_LIMIT_MS) {
                kotlinx.coroutines.delay(ApiConstants.FRED_RATE_LIMIT_MS - elapsed)
            }
            lastRequestTime = System.currentTimeMillis()
        }
    }
}
