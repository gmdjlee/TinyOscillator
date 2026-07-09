package com.tinyoscillator.feature.bearsignal.data.remote

import com.tinyoscillator.core.config.ApiConstants
import com.tinyoscillator.feature.bearsignal.domain.model.CustomsTradeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * 관세청 무역통계 Open API(`apis.data.go.kr/1220000/nitemtrade/getNitemtradeList`) 클라이언트
 * (TASK.md §4 "수출 비중").
 *
 * 공공데이터포털 표준 응답 래퍼(`response.header`/`response.body.items.item[]`)를 JSON으로
 * 파싱한다(`type=json` 쿼리 파라미터). 15대 품목별 수출입 실적을 반환하며, 각 품목의 카테고리
 * 분류(반도체/자동차/일반기계/석유제품)는 [com.tinyoscillator.feature.bearsignal.domain.usecase.CustomsTradeCalculator]가
 * 담당한다.
 *
 * Rate limit: 1000ms per request (mutex 기반, 기존 BokEcosApiClient/DartApiClient 관례).
 */
class CustomsTradeApiClient(
    private val httpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    companion object {
        private const val BASE_URL = "https://apis.data.go.kr/1220000/nitemtrade/getNitemtradeList"
        private const val RESULT_CODE_SUCCESS = "00"
    }

    private val rateLimitMutex = Mutex()
    private var lastRequestTime = 0L

    /**
     * 15대 품목별 수출입 실적 조회.
     *
     * @param apiKey 공공데이터포털 인증키(디코딩된 값 — OkHttp가 쿼리 인코딩을 처리하므로 원문 전달)
     * @param strtYymm 조회 시작 연월(yyyymm)
     * @param endYymm 조회 종료 연월(yyyymm)
     * @return 품목별 실적 리스트, 실패 시 빈 리스트
     */
    suspend fun fetchNitemTrade(
        apiKey: String,
        strtYymm: String,
        endYymm: String
    ): List<CustomsTradeItem> = withContext(Dispatchers.IO) {
        throttle()

        val url = "$BASE_URL?serviceKey=$apiKey&strtYymm=$strtYymm&endYymm=$endYymm&type=json&numOfRows=100"
        val request = Request.Builder().url(url).build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.e("관세청 무역통계 API HTTP 오류: %d", response.code)
                return@withContext emptyList()
            }
            val body = response.body?.string() ?: return@withContext emptyList()
            parseResponse(body)
        } catch (e: Exception) {
            Timber.e(e, "관세청 무역통계 API 호출 실패")
            emptyList()
        }
    }

    internal fun parseResponse(body: String): List<CustomsTradeItem> {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val response = root["response"]?.jsonObject ?: return emptyList()

            val resultCode = response["header"]?.jsonObject?.get("resultCode")?.jsonPrimitive?.content
            if (resultCode != null && resultCode != RESULT_CODE_SUCCESS) {
                val resultMsg = response["header"]?.jsonObject?.get("resultMsg")?.jsonPrimitive?.content
                Timber.w("관세청 무역통계 API 응답 오류: code=%s, msg=%s", resultCode, resultMsg)
                return emptyList()
            }

            val itemsElement = response["body"]?.jsonObject?.get("items") ?: return emptyList()
            val itemArray: JsonArray = when (itemsElement) {
                is JsonArray -> itemsElement
                is JsonObject -> when (val item = itemsElement["item"]) {
                    is JsonArray -> item
                    is JsonObject -> JsonArray(listOf(item))
                    else -> return emptyList()
                }
                else -> return emptyList()
            }

            itemArray.mapNotNull { parseItem(it) }
        } catch (e: Exception) {
            Timber.e(e, "관세청 무역통계 응답 파싱 실패")
            emptyList()
        }
    }

    private fun parseItem(element: JsonElement): CustomsTradeItem? {
        return try {
            val obj = element.jsonObject
            val statKor = obj["statKor"]?.jsonPrimitive?.content ?: return null
            val hsCd = obj["hsCd"]?.jsonPrimitive?.content
                ?: obj["statCd"]?.jsonPrimitive?.content
                ?: ""
            val expDlr = obj["expDlr"]?.jsonPrimitive?.content?.replace(",", "")?.toDoubleOrNull() ?: return null
            val impDlr = obj["impDlr"]?.jsonPrimitive?.content?.replace(",", "")?.toDoubleOrNull() ?: 0.0
            val year = obj["year"]?.jsonPrimitive?.content ?: ""
            CustomsTradeItem(
                statKor = statKor,
                hsCd = hsCd,
                exportUsdThousand = expDlr,
                importUsdThousand = impDlr,
                yearMonth = year
            )
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun throttle() {
        rateLimitMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime
            if (elapsed < ApiConstants.CUSTOMS_TRADE_RATE_LIMIT_MS) {
                kotlinx.coroutines.delay(ApiConstants.CUSTOMS_TRADE_RATE_LIMIT_MS - elapsed)
            }
            lastRequestTime = System.currentTimeMillis()
        }
    }
}
