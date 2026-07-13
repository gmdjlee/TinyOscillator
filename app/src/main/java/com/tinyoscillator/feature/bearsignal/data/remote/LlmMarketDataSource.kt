package com.tinyoscillator.feature.bearsignal.data.remote

import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.domain.model.AiApiKeyConfig
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.Suggestion
import com.tinyoscillator.feature.bearsignal.domain.model.SuggestionField
import com.tinyoscillator.feature.bearsignal.domain.model.SuggestionFetchResult
import com.tinyoscillator.feature.bearsignal.domain.model.SuggestionGroupOutcome
import com.tinyoscillator.feature.bearsignal.domain.model.SuggestionValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalDate

/**
 * §4.5 웹/LLM 3-tier 수집 — Anthropic API `/v1/messages` + `web_search` 서버 도구
 * (TASK_bear_signal_console.md §4.5 항목1).
 *
 * `web_search`는 **서버 측 도구**다 — `tools` 배열에 선언만 하면 Anthropic 서버가 검색을 실행하고
 * 결과가 같은 응답의 `content` 블록으로 돌아온다(클라이언트 측 도구 실행 루프 불필요). 도구 타입은
 * 광범위 호환을 위해 `web_search_20250305`를 사용한다(신형 `web_search_20260209`는 Haiku 미지원 —
 * 이 앱은 사용자가 Claude Haiku/Sonnet을 선택할 수 있으므로 기본형을 채택).
 *
 * 그룹 분할 호출로 부분 실패 격리한다(`Promise.allSettled` 상당 — [supervisorScope] + 그룹별
 * `runCatching`): ① `rate`/`dir`, ② `bigDeal`/`lossRatio`, ③ `credit`. 각 그룹의 열거형 필드는
 * [SuggestionValidation]으로 화이트리스트 검증하고, 위반 시 해당 필드 제안만 폐기한다(그룹 전체
 * 폐기 아님).
 *
 * 급변 감지(§4.5): 금리 ±0.5%p 초과 또는 신용잔고 ±30% 초과 제안이면 동일 그룹을 1회 재확인 호출해
 * 두 결과가 일치할 때만 제안 목록에 올린다(불일치·재확인 실패 시 해당 필드 폐기).
 *
 * 구조화 출력과 `web_search`는 병용 불가(도구를 강제하면 검색을 못 한다) — 시스템 프롬프트로 "최종
 * 답은 JSON 객체만"을 지시하고, 응답 파싱은 [parseLlmResponse]/[extractJsonObject] 등 Context 없는
 * 순수 함수로 분리해 직접 테스트한다.
 *
 * 네트워크: [httpClient]는 기존 앱 전역 30s 타임아웃 [OkHttpClient]를 재사용하고(§4.5 항목4),
 * 재시도는 1회 백오프([retryBackoffMs])만 수행한다.
 *
 * @param baseUrl 프로덕션 기본값은 Anthropic API — 테스트에서만 MockWebServer URL로 교체한다.
 * @param retryBackoffMs 1회 백오프 대기 시간 — 테스트에서는 짧게 오버라이드해 속도를 확보한다.
 */
class LlmMarketDataSource(
    private val httpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val retryBackoffMs: Long = DEFAULT_RETRY_BACKOFF_MS
) {

    /**
     * §4.5 그룹①②③을 병렬 조회한다. 개별 그룹 실패는 [SuggestionFetchResult]의 그룹별 `error`로만
     * 표현되며, 이 함수 자체는 예외를 던지지 않는다(취소는 예외).
     */
    suspend fun fetchSuggestions(
        config: AiApiKeyConfig,
        current: BearSignalInputs
    ): SuggestionFetchResult = supervisorScope {
        val rateDirDeferred = async { fetchRateDirGroup(config, current.rate) }
        val bigDealLossDeferred = async { fetchBigDealLossRatioGroup(config, current.big, current.loss) }
        val creditDeferred = async { fetchCreditGroup(config, current.credit) }
        SuggestionFetchResult(
            rateDir = rateDirDeferred.await(),
            bigDealLossRatio = bigDealLossDeferred.await(),
            credit = creditDeferred.await()
        )
    }

    // ── 그룹① rate/dir ────────────────────────────────────────────────

    internal suspend fun fetchRateDirGroup(config: AiApiKeyConfig, currentRate: Double?): SuggestionGroupOutcome =
        try {
            fetchRateDirGroupOrThrow(config, currentRate)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            groupFailure(e, GROUP_LABEL_RATE_DIR)
        }

    private suspend fun fetchRateDirGroupOrThrow(config: AiApiKeyConfig, currentRate: Double?): SuggestionGroupOutcome {
        val text = callClaudeWithWebSearch(config, RATE_DIR_SYSTEM_PROMPT, RATE_DIR_USER_PROMPT)
        val obj = extractJsonObject(text, json)
            ?: return SuggestionGroupOutcome(emptyList(), "$GROUP_LABEL_RATE_DIR 응답 파싱 실패")
        val dto = parseRateDirDto(obj)

        val suggestions = mutableListOf<Suggestion>()

        dto.rate?.let { rate ->
            val reconfirmed = if (SuggestionValidation.isVolatileRateChange(currentRate, rate)) {
                reconfirmMatches(rate) {
                    extractJsonObject(callClaudeWithWebSearch(config, RATE_DIR_SYSTEM_PROMPT, RATE_DIR_USER_PROMPT), json)
                        ?.let { parseRateDirDto(it) }?.rate
                }
            } else {
                true
            }
            if (reconfirmed) {
                val asOf = parseDateOrToday(dto.rateAsOf)
                suggestions += Suggestion(
                    field = SuggestionField.RATE,
                    currentValue = currentRate?.let(::formatNumber),
                    nextValue = formatNumber(rate),
                    asOf = asOf,
                    origin = dto.rateOrigin ?: DEFAULT_ORIGIN,
                    stale = SuggestionValidation.isStale(asOf, LocalDate.now(), SuggestionField.RATE.maxAgeDays)
                )
            }
        }

        dto.dir?.takeIf { SuggestionValidation.isValidDir(it) }?.let { dir ->
            val asOf = parseDateOrToday(dto.dirAsOf)
            suggestions += Suggestion(
                field = SuggestionField.DIR,
                currentValue = null,
                nextValue = dir,
                asOf = asOf,
                origin = dto.dirOrigin ?: DEFAULT_ORIGIN,
                stale = SuggestionValidation.isStale(asOf, LocalDate.now(), SuggestionField.DIR.maxAgeDays)
            )
        }

        return SuggestionGroupOutcome(suggestions, null)
    }

    // ── 그룹② bigDeal/lossRatio ───────────────────────────────────────

    internal suspend fun fetchBigDealLossRatioGroup(
        config: AiApiKeyConfig,
        currentBigDeal: String?,
        currentLossRatio: Double?
    ): SuggestionGroupOutcome =
        try {
            fetchBigDealLossRatioGroupOrThrow(config, currentBigDeal, currentLossRatio)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            groupFailure(e, GROUP_LABEL_BIG_LOSS)
        }

    private suspend fun fetchBigDealLossRatioGroupOrThrow(
        config: AiApiKeyConfig,
        currentBigDeal: String?,
        currentLossRatio: Double?
    ): SuggestionGroupOutcome {
        val text = callClaudeWithWebSearch(config, BIG_LOSS_SYSTEM_PROMPT, BIG_LOSS_USER_PROMPT)
        val obj = extractJsonObject(text, json)
            ?: return SuggestionGroupOutcome(emptyList(), "$GROUP_LABEL_BIG_LOSS 응답 파싱 실패")
        val dto = parseBigDealLossRatioDto(obj)

        val suggestions = mutableListOf<Suggestion>()

        dto.bigDeal?.takeIf { SuggestionValidation.isValidBigDeal(it) }?.let { big ->
            val asOf = parseDateOrToday(dto.bigDealAsOf)
            suggestions += Suggestion(
                field = SuggestionField.BIG_DEAL,
                currentValue = currentBigDeal,
                nextValue = big,
                asOf = asOf,
                origin = dto.bigDealOrigin ?: DEFAULT_ORIGIN,
                stale = SuggestionValidation.isStale(asOf, LocalDate.now(), SuggestionField.BIG_DEAL.maxAgeDays)
            )
        }

        dto.lossRatio?.let { loss ->
            val asOf = parseDateOrToday(dto.lossRatioAsOf)
            suggestions += Suggestion(
                field = SuggestionField.LOSS_RATIO,
                currentValue = currentLossRatio?.let(::formatNumber),
                nextValue = formatNumber(loss),
                asOf = asOf,
                origin = dto.lossRatioOrigin ?: DEFAULT_ORIGIN,
                stale = SuggestionValidation.isStale(asOf, LocalDate.now(), SuggestionField.LOSS_RATIO.maxAgeDays)
            )
        }

        return SuggestionGroupOutcome(suggestions, null)
    }

    // ── 그룹③ credit ──────────────────────────────────────────────────

    internal suspend fun fetchCreditGroup(config: AiApiKeyConfig, currentCredit: Double?): SuggestionGroupOutcome =
        try {
            fetchCreditGroupOrThrow(config, currentCredit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            groupFailure(e, GROUP_LABEL_CREDIT)
        }

    private suspend fun fetchCreditGroupOrThrow(config: AiApiKeyConfig, currentCredit: Double?): SuggestionGroupOutcome {
        val text = callClaudeWithWebSearch(config, CREDIT_SYSTEM_PROMPT, CREDIT_USER_PROMPT)
        val obj = extractJsonObject(text, json)
            ?: return SuggestionGroupOutcome(emptyList(), "$GROUP_LABEL_CREDIT 응답 파싱 실패")
        val dto = parseCreditDto(obj)

        val suggestions = mutableListOf<Suggestion>()
        dto.credit?.let { credit ->
            val reconfirmed = if (SuggestionValidation.isVolatileCreditChange(currentCredit, credit)) {
                reconfirmMatches(credit) {
                    extractJsonObject(callClaudeWithWebSearch(config, CREDIT_SYSTEM_PROMPT, CREDIT_USER_PROMPT), json)
                        ?.let { parseCreditDto(it) }?.credit
                }
            } else {
                true
            }
            if (reconfirmed) {
                val asOf = parseDateOrToday(dto.creditAsOf)
                suggestions += Suggestion(
                    field = SuggestionField.CREDIT,
                    currentValue = currentCredit?.let(::formatNumber),
                    nextValue = formatNumber(credit),
                    asOf = asOf,
                    origin = dto.creditOrigin ?: DEFAULT_ORIGIN,
                    stale = SuggestionValidation.isStale(asOf, LocalDate.now(), SuggestionField.CREDIT.maxAgeDays)
                )
            }
        }
        return SuggestionGroupOutcome(suggestions, null)
    }

    /**
     * §4.5 "급변 재확인" 공통 로직 — [reconfirmFetch]를 1회 호출(예외/파싱실패는 null로 흡수)해
     * 원래 값 [proposed]와 정확히 일치할 때만 true를 반환한다. 재확인 자체가 실패하면(네트워크 오류
     * 등) 확인 불가로 간주해 폐기한다(false) — 낡거나 불확실한 값을 그대로 승인 목록에 올리지 않기
     * 위한 보수적 처리.
     */
    private suspend fun reconfirmMatches(proposed: Double, reconfirmFetch: suspend () -> Double?): Boolean {
        val reconfirmedValue = try {
            reconfirmFetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "§4.5 급변 재확인 호출 실패 — 해당 필드 제안 폐기")
            null
        }
        return reconfirmedValue == proposed
    }

    private fun groupFailure(e: Exception, label: String): SuggestionGroupOutcome {
        val message = (e as? ApiError)?.message ?: e.message ?: "알 수 없는 오류"
        Timber.w(e, "%s §4.5 그룹 조회 실패", label)
        return SuggestionGroupOutcome(emptyList(), "$label: $message")
    }

    // ── Anthropic API 호출(web_search 서버 도구 + pause_turn 재개) ──────────

    /**
     * `web_search` 서버 도구를 사용하는 단일 논리적 호출 — `stop_reason == "pause_turn"`이면 받은
     * assistant `content`를 그대로 messages에 append해 재요청한다(추가 user 메시지 없이). 반복
     * 한도는 [MAX_CONTINUATIONS]. 최종 텍스트(모든 `text` 블록 이어붙임)를 반환한다.
     */
    private suspend fun callClaudeWithWebSearch(
        config: AiApiKeyConfig,
        systemPrompt: String,
        userMessage: String
    ): String {
        var messages: JsonArray = buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", userMessage)
            })
        }
        var continuations = 0
        while (true) {
            val request = buildRequest(config, systemPrompt, messages)
            val body = executeWithRetry(request).getOrThrow()
            val parsed = parseLlmResponse(body, json)
            if (parsed.stopReason == "pause_turn" && continuations < MAX_CONTINUATIONS) {
                continuations++
                messages = appendAssistantContent(messages, parsed.rawContent)
                continue
            }
            return parsed.finalText
        }
    }

    private fun buildRequest(config: AiApiKeyConfig, systemPrompt: String, messages: JsonArray): Request {
        val body = buildJsonObject {
            put("model", config.modelId)
            put("max_tokens", MAX_TOKENS)
            put("system", systemPrompt)
            put("messages", messages)
            put("tools", buildJsonArray {
                add(buildJsonObject {
                    put("type", WEB_SEARCH_TOOL_TYPE)
                    put("name", "web_search")
                    put("max_uses", MAX_SEARCH_USES)
                })
            })
        }.toString()

        return Request.Builder()
            .url("$baseUrl/v1/messages")
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun appendAssistantContent(messages: JsonArray, content: JsonArray): JsonArray = buildJsonArray {
        messages.forEach { add(it) }
        add(buildJsonObject {
            put("role", "assistant")
            put("content", content)
        })
    }

    /** 1회 백오프 재시도(§4.5 항목4) — 재시도 대상은 [ApiError.isRetriableError]가 true인 경우만. */
    private suspend fun executeWithRetry(request: Request): Result<String> {
        val first = executeOnce(request)
        if (first.isSuccess) return first
        val err = first.exceptionOrNull()
        if (err !is ApiError || !ApiError.isRetriableError(err)) return first
        delay(retryBackoffMs)
        return executeOnce(request)
    }

    private suspend fun executeOnce(request: Request): Result<String> = withContext(Dispatchers.IO) {
        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    Result.failure(mapHttpError(response.code))
                } else {
                    Result.success(body)
                }
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(ApiError.TimeoutError("Claude API 요청 시간이 초과되었습니다"))
        } catch (e: IOException) {
            Result.failure(ApiError.NetworkError(e.message ?: "네트워크 오류"))
        }
    }

    private fun mapHttpError(code: Int): ApiError = when (code) {
        401, 403 -> ApiError.AuthError("Claude API 인증 실패 (HTTP $code)")
        429 -> ApiError.ApiCallError(429, "요청 한도 초과, 잠시 후 다시 시도해주세요")
        in 500..599 -> ApiError.NetworkError("Claude API 서버 오류 (HTTP $code)")
        else -> ApiError.ApiCallError(code, "HTTP $code")
    }

    companion object {
        private const val DEFAULT_BASE_URL = "https://api.anthropic.com"

        /** §4.5 항목4 "백오프 1회" 대기 시간 — 테스트에서는 생성자로 짧게 오버라이드한다. */
        private const val DEFAULT_RETRY_BACKOFF_MS = 2_000L

        private const val MAX_TOKENS = 1024

        /** 그룹당 web_search 최대 호출 횟수(도구 정의 `max_uses`). */
        private const val MAX_SEARCH_USES = 3

        /** `pause_turn` 재개 최대 횟수(서버 도구 반복 한도 도달 시). */
        private const val MAX_CONTINUATIONS = 3

        /** 광범위 호환 web_search 도구 타입 — Haiku 4.5도 지원(신형 `web_search_20260209`는 미지원). */
        private const val WEB_SEARCH_TOOL_TYPE = "web_search_20250305"

        private const val DEFAULT_ORIGIN = "Anthropic web_search"

        private const val GROUP_LABEL_RATE_DIR = "미 연준 금리·정책방향"
        private const val GROUP_LABEL_BIG_LOSS = "대어 IPO 소화·적자상장비중"
        private const val GROUP_LABEL_CREDIT = "신용거래융자 잔고"

        private val RATE_DIR_SYSTEM_PROMPT = """
            너는 한국 주식시장 리스크 계기판을 위한 데이터 수집 보조원이다. web_search 도구로 아래 두
            지표의 공식 발표를 조사하고, 조사 결과를 반드시 하나의 JSON 객체로만 답하라(다른 설명
            텍스트는 절대 포함하지 마라).

            1. 미국 연방준비제도(Fed) 목표금리 상단(federal funds rate target upper bound, %) —
               FOMC 공식 성명에 근거하고 발표일(YYYY-MM-DD)을 rate_as_of에 명시하라.
            2. 한국은행 기준금리 정책 방향 — 반드시 "ease"(인하) | "hold"(동결) | "hike"(인상) 중
               하나만 사용하라. 공식 발표에 근거하고 발표일(YYYY-MM-DD)을 dir_as_of에 명시하라.

            JSON 스키마(확실하지 않은 필드는 생략):
            {"rate": number, "rate_as_of": "YYYY-MM-DD", "rate_origin": string,
             "dir": "ease|hold|hike", "dir_as_of": "YYYY-MM-DD", "dir_origin": string}
        """.trimIndent()

        private const val RATE_DIR_USER_PROMPT =
            "미 연준 목표금리 상단과 한국은행 기준금리 정책 방향의 최신 공식 발표를 조사해줘."

        private val BIG_LOSS_SYSTEM_PROMPT = """
            너는 한국 주식시장 리스크 계기판을 위한 데이터 수집 보조원이다. web_search 도구로 아래
            지표를 조사하고, 조사 결과를 반드시 하나의 JSON 객체로만 답하라(다른 설명 텍스트는 절대
            포함하지 마라).

            1. 최근 대어급(OpenAI·Anthropic 등 대형 비상장 AI 기업) IPO/공모 소화 상태 — 반드시
               "smooth"(순조) | "pending"(진행 중) | "failed"(실패/철회) 중 하나만 사용하라.
            2. 최근 한국 신규 상장 기업 중 적자 상태로 상장한 기업의 비중(%, loss_ratio).

            JSON 스키마(확실하지 않은 필드는 생략):
            {"big_deal": "smooth|pending|failed", "big_deal_as_of": "YYYY-MM-DD", "big_deal_origin": string,
             "loss_ratio": number, "loss_ratio_as_of": "YYYY-MM-DD", "loss_ratio_origin": string}
        """.trimIndent()

        private const val BIG_LOSS_USER_PROMPT =
            "최근 대어급 AI 기업 IPO 소화 상태와 한국 신규 상장 기업의 적자상장 비중을 조사해줘."

        private val CREDIT_SYSTEM_PROMPT = """
            너는 한국 주식시장 리스크 계기판을 위한 데이터 수집 보조원이다. web_search 도구로 최신
            신용거래융자 잔고(조원)를 KOFIA(금융투자협회) 주간 통계에 근거해 조사하고, 조사 결과를
            반드시 하나의 JSON 객체로만 답하라(다른 설명 텍스트는 절대 포함하지 마라).

            JSON 스키마(확실하지 않으면 생략):
            {"credit": number, "credit_as_of": "YYYY-MM-DD", "credit_origin": string}
        """.trimIndent()

        private const val CREDIT_USER_PROMPT =
            "KOFIA 신용거래융자 잔고(조원) 최신 주간 통계를 조사해줘."
    }
}
