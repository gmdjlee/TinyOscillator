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
 * @param rawContent 원본 `content` 배열 — pause_turn 재개 시 그대로 재사용(가공하지 않음).
 */
internal data class ParsedLlmResponse(
    val stopReason: String?,
    val finalText: String,
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
    return ParsedLlmResponse(stopReason, text, content)
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

/** `as_of` 문자열(YYYY-MM-DD)을 파싱한다. 없거나 파싱 실패면 오늘 날짜로 폴백(신선도는 낙관적으로 처리). */
internal fun parseDateOrToday(raw: String?): LocalDate {
    if (raw.isNullOrBlank()) return LocalDate.now()
    return try {
        LocalDate.parse(raw)
    } catch (e: DateTimeParseException) {
        LocalDate.now()
    }
}

/** 표시·인코딩용 숫자 포맷 — Locale 고정(소수점 콤마 로케일 방지). */
internal fun formatNumber(value: Double): String = String.format(Locale.US, "%.2f", value)
