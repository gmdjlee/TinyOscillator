package com.tinyoscillator.core.api

import com.tinyoscillator.core.config.ApiConstants
import com.tinyoscillator.domain.model.EcosDataPoint
import com.tinyoscillator.domain.model.EcosIndicatorSpec
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

/**
 * BOK ECOS (한국은행 경제통계시스템) REST API 클라이언트
 *
 * 5개 매크로 지표를 수집:
 * - base_rate: 기준금리
 * - m2: M2 통화량
 * - iip: 산업생산지수
 * - usd_krw: USD/KRW 환율
 * - cpi: 소비자물가지수
 *
 * Rate limit: 1000ms per request (mutex 기반)
 */
class BokEcosApiClient(
    private val httpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    companion object {
        private const val BASE_URL = "https://ecos.bok.or.kr/api/StatisticSearch"

        /**
         * 5개 매크로 지표 정의
         *
         * stat_code / freq / item_code 매핑:
         * - base_rate: 한국은행 기준금리 (722Y001 / M / 0101000)
         * - m2: M2 광의통화 (101Y004 / M / BBGA00)
         * - iip: 전산업생산지수 (901Y033 / M / I11AA)
         * - usd_krw: 원/달러 환율 (731Y001 / M / 0000001)
         * - cpi: 소비자물가지수 (901Y009 / M / 0)
         */
        val INDICATORS: Map<String, EcosIndicatorSpec> = mapOf(
            "base_rate" to EcosIndicatorSpec("722Y001", "M", "0101000"),
            "m2" to EcosIndicatorSpec("101Y004", "M", "BBGA00"),
            "iip" to EcosIndicatorSpec("901Y033", "M", "I11AA"),
            "usd_krw" to EcosIndicatorSpec("731Y001", "M", "0000001"),
            "cpi" to EcosIndicatorSpec("901Y009", "M", "0")
        )
    }

    private val rateLimitMutex = Mutex()
    private var lastRequestTime = 0L

    /**
     * 단일 지표 시계열 조회
     *
     * @param apiKey ECOS API 인증키
     * @param indicatorKey INDICATORS 맵의 키 (base_rate, m2, iip, usd_krw, cpi)
     * @param startYyyymm 시작 연월 (예: 202401)
     * @param endYyyymm 종료 연월 (예: 202612)
     * @return 시계열 데이터 (시간순 정렬), 실패 시 빈 리스트
     */
    suspend fun fetchSeries(
        apiKey: String,
        indicatorKey: String,
        startYyyymm: String,
        endYyyymm: String
    ): List<EcosDataPoint> = withContext(Dispatchers.IO) {
        val spec = INDICATORS[indicatorKey]
        if (spec == null) {
            Timber.e("ECOS: 알 수 없는 지표 키: %s", indicatorKey)
            return@withContext emptyList()
        }

        throttle()

        val url = "$BASE_URL/$apiKey/json/kr/1/100/${spec.statCode}/${spec.freq}/$startYyyymm/$endYyyymm/${spec.itemCode}"
        val request = Request.Builder()
            .url(url)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.e("ECOS API HTTP 오류: %d (%s)", response.code, indicatorKey)
                return@withContext emptyList()
            }

            val body = response.body?.string() ?: return@withContext emptyList()
            parseResponse(body, indicatorKey)
        } catch (e: Exception) {
            Timber.e(e, "ECOS API 호출 실패 (%s)", indicatorKey)
            emptyList()
        }
    }

    /**
     * 5개 지표 전체 조회
     *
     * @return indicatorKey → List<EcosDataPoint> 매핑
     */
    suspend fun fetchAll(
        apiKey: String,
        startYyyymm: String,
        endYyyymm: String
    ): Map<String, List<EcosDataPoint>> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, List<EcosDataPoint>>()
        for (key in INDICATORS.keys) {
            try {
                val series = fetchSeries(apiKey, key, startYyyymm, endYyyymm)
                results[key] = series
            } catch (e: Exception) {
                Timber.w(e, "ECOS 지표 조회 실패: %s — 건너뜀", key)
                results[key] = emptyList()
            }
        }
        results
    }

    private fun parseResponse(body: String, indicatorKey: String): List<EcosDataPoint> {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val statSearch = root["StatisticSearch"]?.jsonObject
            if (statSearch == null) {
                // 에러 응답 확인
                val resultCode = root["RESULT"]?.jsonObject?.get("CODE")?.jsonPrimitive?.content
                val resultMessage = root["RESULT"]?.jsonObject?.get("MESSAGE")?.jsonPrimitive?.content
                Timber.w("ECOS 응답 오류 (%s): code=%s, msg=%s", indicatorKey, resultCode, resultMessage)
                return emptyList()
            }

            val rows = statSearch["row"]?.jsonArray ?: return emptyList()
            rows.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    val time = obj["TIME"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val valueStr = obj["DATA_VALUE"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val value = valueStr.replace(",", "").toDoubleOrNull() ?: return@mapNotNull null
                    EcosDataPoint(time = time, value = value)
                } catch (e: Exception) {
                    null
                }
            }.sortedBy { it.time }
        } catch (e: Exception) {
            Timber.e(e, "ECOS 응답 파싱 실패 (%s)", indicatorKey)
            emptyList()
        }
    }

    private suspend fun throttle() {
        rateLimitMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime
            if (elapsed < ApiConstants.BOK_ECOS_RATE_LIMIT_MS) {
                kotlinx.coroutines.delay(ApiConstants.BOK_ECOS_RATE_LIMIT_MS - elapsed)
            }
            lastRequestTime = System.currentTimeMillis()
        }
    }
}
