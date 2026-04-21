package com.tinyoscillator.core.api

import com.tinyoscillator.core.config.ApiConstants
import com.tinyoscillator.domain.model.CorpCodeEntry
import com.tinyoscillator.domain.model.DartDisclosure
import com.tinyoscillator.domain.model.DartEventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * DART OpenAPI 클라이언트
 *
 * 주요 기능:
 * - corp_code XML 마스터 파일 다운로드 및 파싱 (ZIP → XML → List<CorpCodeEntry>)
 * - 최근 공시 목록 조회 (/api/list.json)
 *
 * DART API 일일 제한: 10,000건 — 배치 및 캐시 필수
 * Rate limit: 1000ms per request (mutex 기반)
 */
class DartApiClient(
    private val httpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    companion object {
        private const val BASE_URL = "https://opendart.fss.or.kr/api"
    }

    private val rateLimitMutex = Mutex()
    private var lastRequestTime = 0L

    /**
     * DART corp_code 마스터 파일 다운로드 (ZIP → XML 파싱)
     *
     * @return 상장 종목만 필터링된 corp_code 목록
     */
    suspend fun downloadCorpCodeMaster(apiKey: String): List<CorpCodeEntry> = withContext(Dispatchers.IO) {
        throttle()

        val url = "$BASE_URL/corpCode.xml?crtfc_key=$apiKey"
        val request = Request.Builder().url(url).build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            Timber.e("DART corpCode 다운로드 실패: HTTP ${response.code}")
            return@withContext emptyList()
        }

        val zipBytes = response.body?.bytes() ?: return@withContext emptyList()
        val xmlContent = unzipFirstEntry(zipBytes) ?: return@withContext emptyList()

        val entries = parseCorpCodeXml(xmlContent)
        // 상장 종목만 (stock_code가 6자리 숫자인 경우)
        val listed = entries.filter { it.stockCode.matches(Regex("^\\d{6}$")) }
        Timber.d("DART corpCode 다운로드 완료: 전체 %d건, 상장 %d건", entries.size, listed.size)
        listed
    }

    /**
     * 특정 기업의 최근 공시 목록 조회
     *
     * @param corpCode DART 고유번호 (8자리)
     * @param daysBack 조회 기간 (일)
     * @return 공시 목록 (최신순)
     */
    suspend fun fetchRecentDisclosures(
        apiKey: String,
        corpCode: String,
        daysBack: Int = 30
    ): List<DartDisclosure> = withContext(Dispatchers.IO) {
        throttle()

        val endDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
        val beginDate = java.time.LocalDate.now().minusDays(daysBack.toLong())
            .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)

        val url = "$BASE_URL/list.json" +
                "?crtfc_key=$apiKey" +
                "&corp_code=$corpCode" +
                "&bgn_de=$beginDate" +
                "&end_de=$endDate" +
                "&pblntf_detail_ty=A001" +  // 정기공시
                "&page_count=100"

        val disclosures = mutableListOf<DartDisclosure>()

        // 정기공시 (A), 주요사항보고 (B), 외부감사 (C)
        val detailTypes = listOf("A001", "B001", "C001")
        for (detailType in detailTypes) {
            throttle()
            val typeUrl = "$BASE_URL/list.json" +
                    "?crtfc_key=$apiKey" +
                    "&corp_code=$corpCode" +
                    "&bgn_de=$beginDate" +
                    "&end_de=$endDate" +
                    "&page_count=100"

            try {
                val result = fetchDisclosurePage(typeUrl, corpCode)
                disclosures.addAll(result)
            } catch (e: Exception) {
                Timber.w(e, "DART 공시 조회 실패 (detailType=$detailType)")
            }
        }

        // 중복 제거 (접수번호 기준) 및 최신순 정렬
        val unique = disclosures.distinctBy { it.rceptNo }.sortedByDescending { it.rceptDt }
        Timber.d("DART 공시 조회 완료: corpCode=%s, %d건 (기간: %s~%s)",
            corpCode, unique.size, beginDate, endDate)
        unique
    }

    private fun fetchDisclosurePage(url: String, corpCode: String): List<DartDisclosure> {
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            Timber.w("DART API HTTP ${response.code}")
            return emptyList()
        }

        val body = response.body?.string() ?: return emptyList()
        val jsonObj = json.parseToJsonElement(body).jsonObject

        val status = jsonObj["status"]?.jsonPrimitive?.content
        if (status != "000") {
            // 000 = 정상, 013 = 조회된 데이터 없음
            if (status != "013") {
                Timber.w("DART API 응답 오류: status=%s, message=%s",
                    status, jsonObj["message"]?.jsonPrimitive?.content)
            }
            return emptyList()
        }

        val list = jsonObj["list"]?.jsonArray ?: return emptyList()
        return list.mapNotNull { element ->
            try {
                val obj = element.jsonObject
                val reportNm = obj["report_nm"]?.jsonPrimitive?.content ?: return@mapNotNull null
                DartDisclosure(
                    rceptNo = obj["rcept_no"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    rceptDt = obj["rcept_dt"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    reportNm = reportNm,
                    corpName = obj["corp_name"]?.jsonPrimitive?.content ?: "",
                    corpCode = corpCode,
                    eventType = DartEventType.classify(reportNm)
                )
            } catch (e: Exception) {
                Timber.w(e, "DART 공시 파싱 실패")
                null
            }
        }
    }

    private fun unzipFirstEntry(zipBytes: ByteArray): String? {
        return try {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                val entry = zis.nextEntry ?: return null
                zis.readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Timber.e(e, "ZIP 해제 실패")
            null
        }
    }

    /**
     * corpCode.xml 파싱 — 간단한 문자열 기반 파싱 (SAX/DOM 대신)
     *
     * XML 형식:
     * <result>
     *   <list>
     *     <corp_code>00126380</corp_code>
     *     <corp_name>삼성전자</corp_name>
     *     <stock_code>005930</stock_code>
     *     ...
     *   </list>
     *   ...
     * </result>
     */
    private fun parseCorpCodeXml(xml: String): List<CorpCodeEntry> {
        val entries = mutableListOf<CorpCodeEntry>()
        val listPattern = Regex("<list>(.*?)</list>", RegexOption.DOT_MATCHES_ALL)
        val corpCodePattern = Regex("<corp_code>(.+?)</corp_code>")
        val corpNamePattern = Regex("<corp_name>(.+?)</corp_name>")
        val stockCodePattern = Regex("<stock_code>(.+?)</stock_code>")

        for (match in listPattern.findAll(xml)) {
            val block = match.groupValues[1]
            val corpCode = corpCodePattern.find(block)?.groupValues?.get(1)?.trim() ?: continue
            val corpName = corpNamePattern.find(block)?.groupValues?.get(1)?.trim() ?: ""
            val stockCode = stockCodePattern.find(block)?.groupValues?.get(1)?.trim() ?: ""

            entries.add(CorpCodeEntry(corpCode = corpCode, corpName = corpName, stockCode = stockCode))
        }
        return entries
    }

    private suspend fun throttle() {
        rateLimitMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime
            if (elapsed < ApiConstants.DART_RATE_LIMIT_MS) {
                kotlinx.coroutines.delay(ApiConstants.DART_RATE_LIMIT_MS - elapsed)
            }
            lastRequestTime = System.currentTimeMillis()
        }
    }
}
