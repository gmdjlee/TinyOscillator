package com.tinyoscillator.feature.bearsignal.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * [LlmMarketDataSource] 응답 파싱 · 검증 순수 함수 모음 — Context/네트워크 의존성 0, 직접
 * 단위테스트 대상이다(TASK_bear_signal_console.md §4.5 "응답 파싱·검증은 Context 없는 순수
 * 함수로 분리해 직접 테스트 가능하게" 요구사항).
 */

/**
 * Anthropic `/v1/messages` 응답 파싱 결과.
 *
 * @param stopReason `"pause_turn"`이면 서버 도구 반복 한도 도달 — [rawContent]를 그대로 다음 요청의
 * assistant 메시지로 append해 재요청해야 한다(추가 user 메시지 없이).
 * @param finalText 응답의 모든 `type=="text"` 블록을 순서대로 이어붙인 문자열. 시스템 프롬프트가
 * "최종 답은 JSON 객체만"을 지시하므로 이 문자열에서 JSON을 추출한다([extractJsonObject]).
 * @param resultUrls §4.7 검증1 "URL 교차검증" 입력 — 이 응답에 동봉된 `web_search_tool_result`
 * 블록에서 수집한 실제 검색결과 URL 목록(TASK_bear_signal_console.md §4.7 라인 352). §4.5 그룹①②③은
 * 이 필드를 사용하지 않는다.
 * @param rawContent 원본 `content` 배열 — pause_turn 재개 시 그대로 재사용(가공하지 않음).
 */
internal data class ParsedLlmResponse(
    val stopReason: String?,
    val finalText: String,
    val resultUrls: List<String>,
    val rawContent: JsonArray
)

/** Anthropic 응답 body를 파싱한다. `content`/`stop_reason` 필드가 없으면 각각 빈 값으로 처리한다. */
internal fun parseLlmResponse(body: String, json: Json): ParsedLlmResponse {
    val root = json.parseToJsonElement(body).jsonObject
    val stopReason = root["stop_reason"]?.jsonPrimitive?.contentOrNull
    val content = root["content"]?.jsonArray ?: JsonArray(emptyList())
    val text = content.joinToString("") { block ->
        val obj = block.jsonObject
        if (obj["type"]?.jsonPrimitive?.contentOrNull == "text") {
            obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        } else {
            ""
        }
    }
    return ParsedLlmResponse(stopReason, text, extractWebSearchResultUrls(content), content)
}

/**
 * §4.7 검증1 "URL 교차검증" 입력 — `content` 배열에서 `type=="web_search_tool_result"` 블록을 찾아
 * 그 안의 실제 검색결과 `url`들을 수집한다(환각/조작 URL 차단의 근거 데이터, [AiContextClaimValidation.isUrlVerified]).
 *
 * `web_search_tool_result`의 `content`는 정상 시 배열([{"type":"web_search_result","url":"…"}, …])이지만,
 * 검색 실패 시 에러 객체([JsonObject], 예: `{"type":"web_search_tool_result_error", …}`)로 올 수 있어
 * 배열이 아니면 조용히 건너뛴다(예외를 던지지 않음 — URL 목록이 비어 있을 뿐 파싱 실패가 아니다).
 */
internal fun extractWebSearchResultUrls(content: JsonArray): List<String> {
    val urls = mutableListOf<String>()
    content.forEach { block ->
        val obj = block.jsonObject
        if (obj["type"]?.jsonPrimitive?.contentOrNull == "web_search_tool_result") {
            val resultContent = obj["content"]
            if (resultContent is JsonArray) {
                resultContent.forEach { item ->
                    item.jsonObject["url"]?.jsonPrimitive?.contentOrNull?.let { urls += it }
                }
            }
        }
    }
    return urls
}

/**
 * 최종 텍스트에서 JSON 객체를 추출한다 — 모델이 지시를 어기고 JSON 앞뒤에 설명을 덧붙이는 경우에도
 * 첫 `{`부터 마지막 `}`까지를 시도한다(관용적 파싱). 실패하면 null(해당 그룹은 파싱 실패로 처리).
 */
internal fun extractJsonObject(text: String, json: Json): JsonObject? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    if (start == -1 || end == -1 || end <= start) return null
    return try {
        json.parseToJsonElement(trimmed.substring(start, end + 1)).jsonObject
    } catch (e: Exception) {
        null
    }
}

/** §4.5 그룹① rate/dir 원시 응답 — 열거형 화이트리스트 검증은 [LlmMarketDataSource]가 담당. */
internal data class RateDirRaw(
    val rate: Double?,
    val rateAsOf: String?,
    val rateOrigin: String?,
    val dir: String?,
    val dirAsOf: String?,
    val dirOrigin: String?
)

internal fun parseRateDirDto(obj: JsonObject): RateDirRaw = RateDirRaw(
    rate = obj["rate"]?.jsonPrimitive?.doubleOrNull,
    rateAsOf = obj["rate_as_of"]?.jsonPrimitive?.contentOrNull,
    rateOrigin = obj["rate_origin"]?.jsonPrimitive?.contentOrNull,
    dir = obj["dir"]?.jsonPrimitive?.contentOrNull,
    dirAsOf = obj["dir_as_of"]?.jsonPrimitive?.contentOrNull,
    dirOrigin = obj["dir_origin"]?.jsonPrimitive?.contentOrNull
)

/** §4.5 그룹② bigDeal/lossRatio 원시 응답. */
internal data class BigDealLossRatioRaw(
    val bigDeal: String?,
    val bigDealAsOf: String?,
    val bigDealOrigin: String?,
    val lossRatio: Double?,
    val lossRatioAsOf: String?,
    val lossRatioOrigin: String?
)

internal fun parseBigDealLossRatioDto(obj: JsonObject): BigDealLossRatioRaw = BigDealLossRatioRaw(
    bigDeal = obj["big_deal"]?.jsonPrimitive?.contentOrNull,
    bigDealAsOf = obj["big_deal_as_of"]?.jsonPrimitive?.contentOrNull,
    bigDealOrigin = obj["big_deal_origin"]?.jsonPrimitive?.contentOrNull,
    lossRatio = obj["loss_ratio"]?.jsonPrimitive?.doubleOrNull,
    lossRatioAsOf = obj["loss_ratio_as_of"]?.jsonPrimitive?.contentOrNull,
    lossRatioOrigin = obj["loss_ratio_origin"]?.jsonPrimitive?.contentOrNull
)

/** §4.5 그룹③ credit 원시 응답. */
internal data class CreditRaw(
    val credit: Double?,
    val creditAsOf: String?,
    val creditOrigin: String?
)

internal fun parseCreditDto(obj: JsonObject): CreditRaw = CreditRaw(
    credit = obj["credit"]?.jsonPrimitive?.doubleOrNull,
    creditAsOf = obj["credit_as_of"]?.jsonPrimitive?.contentOrNull,
    creditOrigin = obj["credit_origin"]?.jsonPrimitive?.contentOrNull
)

/**
 * Gemini `generateContent` 응답 파싱 결과 (§4.5 v1.3 "Gemini 경로").
 *
 * @param finalText 첫 candidate의 `content.parts[].text`를 이어붙인 문자열. Claude 경로와 동일하게
 * 이 문자열에서 JSON을 추출한다([extractJsonObject]).
 * @param searchWidgetHtml `groundingMetadata.searchEntryPoint.renderedContent` — Google 검색 제안
 * 위젯 HTML(ToS상 사용자 표시 의무). 없으면 null(예: candidates가 비어있거나 grounding 미사용).
 * @param resultUrls §4.7 검증1 "URL 교차검증" 입력 — `groundingMetadata.groundingChunks[].web.uri`
 * 목록(TASK_bear_signal_console.md §4.7 라인 352 "Gemini `groundingMetadata.groundingChunks`").
 */
internal data class ParsedGeminiResponse(
    val finalText: String,
    val searchWidgetHtml: String?,
    val resultUrls: List<String>
)

/**
 * Gemini `generateContent` 응답 body를 파싱한다. `candidates`가 비어있으면 [ParsedGeminiResponse.finalText]는
 * 빈 문자열, [ParsedGeminiResponse.searchWidgetHtml]는 null, [ParsedGeminiResponse.resultUrls]는 빈 리스트다.
 */
internal fun parseGeminiLlmResponse(body: String, json: Json): ParsedGeminiResponse {
    val root = json.parseToJsonElement(body).jsonObject
    val firstCandidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject

    val parts = firstCandidate?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: JsonArray(emptyList())
    val text = parts.joinToString("") { part ->
        part.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    val groundingMetadata = firstCandidate?.get("groundingMetadata")?.jsonObject

    val widgetHtml = groundingMetadata
        ?.get("searchEntryPoint")?.jsonObject
        ?.get("renderedContent")?.jsonPrimitive?.contentOrNull

    val resultUrls = groundingMetadata
        ?.get("groundingChunks")?.jsonArray
        ?.mapNotNull { chunk -> chunk.jsonObject["web"]?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull }
        .orEmpty()

    return ParsedGeminiResponse(finalText = text, searchWidgetHtml = widgetHtml, resultUrls = resultUrls)
}

/** `as_of` 문자열(YYYY-MM-DD)을 파싱한다. 없거나 파싱 실패면 오늘 날짜로 폴백(신선도는 낙관적으로 처리). */
internal fun parseDateOrToday(raw: String?): LocalDate {
    if (raw.isNullOrBlank()) return LocalDate.now()
    return try {
        LocalDate.parse(raw)
    } catch (e: DateTimeParseException) {
        LocalDate.now()
    }
}

/**
 * `source_date` 문자열(YYYY-MM-DD)을 파싱한다. §4.7 클레임의 `source_date`는 [parseDateOrToday]와
 * 달리 부재 시 오늘로 낙관 폴백하면 안 된다 — `AiContextClaimValidation.validate`의
 * `SOURCE_DATE_MISSING` 폐기 판정이 실제 null을 필요로 하므로, 없거나 파싱 실패면 그대로 null을
 * 반환한다(TASK_bear_signal_console.md §4.7 검증2).
 */
internal fun parseDateOrNull(raw: String?): LocalDate? {
    if (raw.isNullOrBlank()) return null
    return try {
        LocalDate.parse(raw)
    } catch (e: DateTimeParseException) {
        null
    }
}

/** 표시·인코딩용 숫자 포맷 — Locale 고정(소수점 콤마 로케일 방지). */
internal fun formatNumber(value: Double): String = String.format(Locale.US, "%.2f", value)

/** §4.7 클레임 스키마(`claims[]` 항목 하나) 원시 파싱 결과 — 화이트리스트/enum 매핑은 [LlmMarketDataSource]가 담당. */
internal data class AiContextClaimRaw(
    val sectionKey: String?,
    val text: String?,
    val type: String?,
    val sourceUrl: String?,
    val sourceTitle: String?,
    val sourceDate: String?,
    val quote: String?
)

/**
 * §4.7 프롬프트-JSON `claims[]` 배열을 원시 DTO 목록으로 파싱한다(관용 파싱 — [extractJsonObject]로
 * 이미 추출된 최상위 객체를 입력받는다). `claims` 필드가 없거나 항목이 객체가 아니면 해당 항목만
 * 건너뛴다(전체 파싱 실패로 취급하지 않음 — 그룹 폐기가 아니라 클레임 단위 처리 원칙과 일관).
 */
internal fun parseAiContextClaimsDto(obj: JsonObject): List<AiContextClaimRaw> {
    val claims = obj["claims"]?.jsonArray ?: return emptyList()
    return claims.mapNotNull { element ->
        val c = element as? JsonObject ?: return@mapNotNull null
        AiContextClaimRaw(
            sectionKey = c["section_key"]?.jsonPrimitive?.contentOrNull,
            text = c["text"]?.jsonPrimitive?.contentOrNull,
            type = c["type"]?.jsonPrimitive?.contentOrNull,
            sourceUrl = c["source_url"]?.jsonPrimitive?.contentOrNull,
            sourceTitle = c["source_title"]?.jsonPrimitive?.contentOrNull,
            sourceDate = c["source_date"]?.jsonPrimitive?.contentOrNull,
            quote = c["quote"]?.jsonPrimitive?.contentOrNull
        )
    }
}
