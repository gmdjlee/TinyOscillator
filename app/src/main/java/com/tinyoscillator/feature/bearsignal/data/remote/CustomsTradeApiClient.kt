package com.tinyoscillator.feature.bearsignal.data.remote

import com.tinyoscillator.core.config.ApiConstants
import com.tinyoscillator.feature.bearsignal.domain.model.CustomsTradeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * 관세청 품목별 수출입실적(GW) Open API 클라이언트 (TASK.md §4 "수출 비중").
 *
 * 공공데이터포털 상품 15101609 — 엔드포인트 `apis.data.go.kr/1220000/Itemtrade/getItemtradeList`.
 * 주의: 유사 상품 15100475(품목별 국가별)의 `1220000/nitemtrade/getNitemtradeList`와 혼동 금지 —
 * 활용신청하지 않은 상품 경로를 호출하면 게이트웨이가 HTTP 403을 반환한다(2026-07-16 실키 검증).
 *
 * 응답은 **XML 전용**(`type=json` 무시됨, produces application/xml). HS 10단위 전 품목이
 * 1개월 조회당 ~2.2MB(비압축)로 내려오며, DART corpCode.xml과 동일하게 indexOf 단일 패스
 * 문자열 파싱으로 처리한다(SAX/DOM 회피 — JVM 단위테스트 호환, 파서 의존성 0).
 * 응답 필드: `hsCode`(HS 10단위)/`statKor`(품목명)/`expDlr`·`impDlr`(달러)/`year`(예 "2025.04").
 *
 * Rate limit: 1000ms per request (mutex 기반, 기존 BokEcosApiClient/DartApiClient 관례).
 */
class CustomsTradeApiClient(
    private val httpClient: OkHttpClient
) {

    companion object {
        private const val BASE_URL = "https://apis.data.go.kr/1220000/Itemtrade/getItemtradeList"
        private const val RESULT_CODE_SUCCESS = "00"

        /**
         * data.go.kr 인증키의 base64 유사 문자(`+` `/` `=`)를 percent-encode한다. URL 문자열에
         * 원문을 그대로 결합하면 OkHttp `HttpUrl`이 쿼리의 `+`를 인코딩하지 않고 통과시켜
         * 게이트웨이가 공백으로 복호화 → 키 불일치가 난다(2026-07-16 실키 검증에서 발견).
         * 이미 인코딩된 키(포털의 'Encoding' 키, `%` 포함)는 이중 인코딩을 피해 그대로 반환한다.
         */
        internal fun encodeServiceKey(apiKey: String): String =
            if (apiKey.contains('%')) apiKey
            else java.net.URLEncoder.encode(apiKey, Charsets.UTF_8.name())
    }

    private val rateLimitMutex = Mutex()
    private var lastRequestTime = 0L

    /**
     * HS 10단위 품목별 수출입 실적 조회 (`getItemtradeList`).
     *
     * @param apiKey 공공데이터포털 인증키 — Decoding/Encoding 키 모두 허용. 원문(Decoding) 키는
     * 내부에서 percent-encode하고([encodeServiceKey]), 이미 인코딩된(`%` 포함) 키는 그대로 쓴다.
     * @param strtYymm 조회 시작 연월(yyyymm)
     * @param endYymm 조회 종료 연월(yyyymm)
     * @return 품목별 실적 리스트, 실패 시 빈 리스트
     */
    suspend fun fetchItemTrade(
        apiKey: String,
        strtYymm: String,
        endYymm: String
    ): List<CustomsTradeItem> = withContext(Dispatchers.IO) {
        throttle()

        val url = "$BASE_URL?serviceKey=${encodeServiceKey(apiKey)}&strtYymm=$strtYymm&endYymm=$endYymm"
        val request = Request.Builder().url(url).build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // 오류 body에는 게이트웨이 거부 사유(Unauthorized/Forbidden 등)가 담긴다
                    // — 자격증명은 포함되지 않으므로 앞부분만 로깅해 원인 진단을 돕는다.
                    val reason = response.body?.string()?.take(200)
                    Timber.e("관세청 무역통계 API HTTP 오류: %d %s", response.code, reason ?: "")
                    return@withContext emptyList()
                }
                val body = response.body?.string() ?: return@withContext emptyList()
                parseResponse(body)
            }
        } catch (e: Exception) {
            Timber.e(e, "관세청 무역통계 API 호출 실패")
            emptyList()
        }
    }

    /**
     * XML 응답 파싱 — `<response><header>…</header><body><items><item>…</item>…` 구조.
     * `<item>` 블록을 indexOf 단일 패스로 순회한다([com.tinyoscillator.core.api.DartApiClient]의
     * corpCode.xml 파싱과 동일 관례 — ~1만 엔트리 대량 XML에서 파서 오버헤드 회피).
     */
    internal fun parseResponse(body: String): List<CustomsTradeItem> {
        return try {
            val resultCode = extractTag(body, "resultCode", 0, body.length)
            if (resultCode == null) {
                // <resultCode> 부재 = 표준 응답 래퍼가 아님(게이트웨이 오류 텍스트 등)
                Timber.w("관세청 무역통계 응답에 resultCode 없음: %s", body.take(200))
                return emptyList()
            }
            if (resultCode != RESULT_CODE_SUCCESS) {
                val resultMsg = extractTag(body, "resultMsg", 0, body.length)
                Timber.w("관세청 무역통계 API 응답 오류: code=%s, msg=%s", resultCode, resultMsg)
                return emptyList()
            }

            val items = mutableListOf<CustomsTradeItem>()
            var pos = 0
            while (true) {
                val start = body.indexOf("<item>", pos)
                if (start < 0) break
                val end = body.indexOf("</item>", start)
                if (end < 0) break
                parseItem(body, start, end)?.let { items.add(it) }
                pos = end + 7
            }
            items
        } catch (e: Exception) {
            Timber.e(e, "관세청 무역통계 응답 파싱 실패")
            emptyList()
        }
    }

    private fun parseItem(xml: String, from: Int, to: Int): CustomsTradeItem? {
        val statKor = extractTag(xml, "statKor", from, to) ?: return null
        val hsCd = extractTag(xml, "hsCode", from, to) ?: ""
        // expDlr 부재 항목은 스킵(방어), "0"은 유효값(수입 전용 품목)
        val expDlr = extractTag(xml, "expDlr", from, to)?.replace(",", "")?.toDoubleOrNull() ?: return null
        val impDlr = extractTag(xml, "impDlr", from, to)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        val year = extractTag(xml, "year", from, to) ?: ""
        return CustomsTradeItem(
            statKor = statKor,
            hsCd = hsCd,
            exportUsd = expDlr,
            importUsd = impDlr,
            yearMonth = year
        )
    }

    private fun extractTag(xml: String, tag: String, from: Int, to: Int): String? {
        val open = "<$tag>"
        val start = xml.indexOf(open, from)
        if (start < 0 || start >= to) return null
        val valueStart = start + open.length
        val end = xml.indexOf("</$tag>", valueStart)
        if (end < 0 || end > to) return null
        return xml.substring(valueStart, end).trim().ifEmpty { null }
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
