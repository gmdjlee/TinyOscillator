package com.tinyoscillator.feature.bearsignal.data.remote

import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.core.config.ApiConstants
import com.tinyoscillator.domain.model.AiApiKeyConfig
import com.tinyoscillator.domain.model.AiProvider
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextClaimDraft
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextClaimRejection
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextClaimValidation
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextClaimValidationResult
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextFetchResult
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextGroupOutcome
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextSectionKey
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalStaticContent
import com.tinyoscillator.feature.bearsignal.domain.model.ClaimType
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * §4.5 웹/LLM 3-tier 수집 — 설정의 AI 제공자를 따른다(v1.3 개정): **Claude(Anthropic API +
 * `web_search` 서버 도구) 또는 Gemini(Gemini API + `google_search` grounding)**
 * (TASK_bear_signal_console.md §4.5 항목1).
 *
 * `web_search`/`google_search`는 모두 **서버 측 도구**다 — `tools` 배열에 선언만 하면 서버가 검색을
 * 실행하고 결과가 같은 응답으로 돌아온다(클라이언트 측 도구 실행 루프 불필요). Claude 도구 타입은
 * 광범위 호환을 위해 `web_search_20250305`를 사용한다(신형 `web_search_20260209`는 Haiku 미지원 —
 * 이 앱은 사용자가 Claude Haiku/Sonnet을 선택할 수 있으므로 기본형을 채택). Gemini는 `pause_turn`
 * 개념이 없어 단일 호출로 끝난다.
 *
 * **Gemini 2.5 이하는 구조화 출력(`responseMimeType`/`responseSchema`)과 `google_search`를 병용할
 * 수 없다(HTTP 400)** — Claude 경로와 동일하게 시스템 프롬프트로 "최종 답은 JSON 객체만"을 지시하고
 * 관용적 파싱([extractJsonObject])으로 응답을 처리한다.
 *
 * 그룹 분할 호출로 부분 실패 격리한다(`Promise.allSettled` 상당 — [supervisorScope] + 그룹별
 * `runCatching`): ① `rate`/`dir`, ② `bigDeal`/`lossRatio`, ③ `credit`. 각 그룹의 열거형 필드는
 * [SuggestionValidation]으로 화이트리스트 검증하고, 위반 시 해당 필드 제안만 폐기한다(그룹 전체
 * 폐기 아님). 화이트리스트·급변 재확인·STALE 규칙은 제공자와 무관하게 동일하게 적용된다.
 *
 * 급변 감지(§4.5): 금리 ±0.5%p 초과 또는 신용잔고 ±30% 초과 제안이면 동일 그룹을 1회 재확인 호출해
 * 두 결과가 일치할 때만 제안 목록에 올린다(불일치·재확인 실패 시 해당 필드 폐기).
 *
 * 응답 파싱은 [parseLlmResponse]/[parseGeminiLlmResponse]/[extractJsonObject] 등 Context 없는 순수
 * 함수로 분리해 직접 테스트한다.
 *
 * Gemini 응답의 `groundingMetadata.searchEntryPoint.renderedContent`(Google 검색 제안 위젯)는 ToS상
 * 사용자 표시 의무가 있어 [SuggestionGroupOutcome.searchWidgetHtml]로 전달한다(급변 재확인 호출의
 * widget은 무시하고 최초 호출 것만 사용).
 *
 * 네트워크: [httpClient]는 앱 전역 [OkHttpClient]에서 파생하되 read 타임아웃을
 * [com.tinyoscillator.core.config.ApiConstants.LLM_WEB_SEARCH_TIMEOUT_SECONDS]로 연장해 주입받는다
 * (DI 참조 — 서버 측 웹 검색 + §4.7 장문 생성이 전역 30초를 상시 초과). 재시도는 1회
 * 백오프([retryBackoffMs])만 수행한다. Gemini는 인스턴스 [Mutex]로 호출 간
 * [geminiRateLimitMs] 간격을 둔다(AiApiClient의 기존 Gemini rate limit 관례 재사용).
 *
 * @param baseUrl 프로덕션 기본값은 Anthropic API — 테스트에서만 MockWebServer URL로 교체한다.
 * @param geminiBaseUrl 프로덕션 기본값은 Gemini API — 테스트에서만 MockWebServer URL로 교체한다.
 * @param retryBackoffMs 1회 백오프 대기 시간 — 테스트에서는 짧게 오버라이드해 속도를 확보한다.
 * @param geminiRateLimitMs Gemini 호출 간 최소 간격(ms) — 테스트에서는 0으로 오버라이드해 속도를 확보한다.
 */
class LlmMarketDataSource(
    private val httpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val geminiBaseUrl: String = DEFAULT_GEMINI_BASE_URL,
    private val retryBackoffMs: Long = DEFAULT_RETRY_BACKOFF_MS,
    private val geminiRateLimitMs: Long = DEFAULT_GEMINI_RATE_LIMIT_MS
) {

    private val geminiRateMutex = Mutex()
    @Volatile
    private var geminiLastCallTime = 0L

    /** AiApiClient의 Gemini rate limit 관례 재사용 — 그룹 병렬 호출 + 급변 재확인 호출을 직렬화한다. */
    private suspend fun waitForGeminiRateLimit() {
        val delayMs: Long
        geminiRateMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - geminiLastCallTime
            delayMs = if (elapsed < geminiRateLimitMs) geminiRateLimitMs - elapsed else 0L
            geminiLastCallTime = now + delayMs
        }
        if (delayMs > 0L) delay(delayMs)
    }

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

    // ── §4.7 그룹④⑤⑥ 정세 업데이트(모니터·사례·역사 현재비교, Phase 7-2) ──────────

    /**
     * §4.7 그룹④monitor·⑤cases·⑥history_current를 병렬 조회한다(§4.5 [fetchSuggestions]와 동일한
     * 부분 실패 격리 패턴). **저장하지 않는다** — 검증 통과 클레임은 [AiContextGroupOutcome.pending]에
     * 담겨 승인 대기 상태로만 반환되고, Room 반영은
     * [com.tinyoscillator.feature.bearsignal.data.repository.AiContextRepositoryImpl.approve]가
     * 사용자 승인 후에만 수행한다(§4.7 "승인 없이는 표시 콘텐츠 불변").
     *
     * @param today STALE 판정·클레임 검증 기준일 — 테스트에서만 오버라이드, 프로덕션은 오늘 날짜.
     */
    suspend fun fetchAiContextUpdates(
        config: AiApiKeyConfig,
        today: LocalDate = LocalDate.now()
    ): AiContextFetchResult = supervisorScope {
        val monitorDeferred = async { fetchMonitorGroup(config, today) }
        val casesDeferred = async { fetchCasesGroup(config, today) }
        val historyDeferred = async { fetchHistoryCurrentGroup(config, today) }
        AiContextFetchResult(
            monitor = monitorDeferred.await(),
            cases = casesDeferred.await(),
            historyCurrent = historyDeferred.await()
        )
    }

    // ── 그룹④ monitor(type0/1/2_monitor) ─────────────────────────────

    internal suspend fun fetchMonitorGroup(
        config: AiApiKeyConfig,
        today: LocalDate = LocalDate.now()
    ): AiContextGroupOutcome =
        try {
            fetchAiContextGroupOrThrow(config, MONITOR_SYSTEM_PROMPT, MONITOR_USER_PROMPT, GROUP_LABEL_MONITOR, today)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            aiContextGroupFailure(e, GROUP_LABEL_MONITOR, config)
        }

    // ── 그룹⑤ cases(type0/1/2_cases) ─────────────────────────────────

    internal suspend fun fetchCasesGroup(
        config: AiApiKeyConfig,
        today: LocalDate = LocalDate.now()
    ): AiContextGroupOutcome =
        try {
            fetchAiContextGroupOrThrow(config, CASES_SYSTEM_PROMPT, CASES_USER_PROMPT, GROUP_LABEL_CASES, today)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            aiContextGroupFailure(e, GROUP_LABEL_CASES, config)
        }

    // ── 그룹⑥ history_current ────────────────────────────────────────

    internal suspend fun fetchHistoryCurrentGroup(
        config: AiApiKeyConfig,
        today: LocalDate = LocalDate.now()
    ): AiContextGroupOutcome =
        try {
            fetchAiContextGroupOrThrow(
                config,
                HISTORY_CURRENT_SYSTEM_PROMPT,
                HISTORY_CURRENT_USER_PROMPT,
                GROUP_LABEL_HISTORY_CURRENT,
                today
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            aiContextGroupFailure(e, GROUP_LABEL_HISTORY_CURRENT, config)
        }

    /**
     * §4.7 그룹④⑤⑥ 공통 처리 — LLM 호출 → `claims[]` 파싱 → [AiContextSectionKey]/[ClaimType] 화이트
     * 리스트 매핑(알 수 없는 값은 해당 클레임만 스킵, [toAiContextDraft]) → [AiContextClaimValidation]
     * 클레임 단위 검증(폐기 사유별 집계, 그룹 폐기 아님). §4.7은 §4.5보다 클레임 수가 많을 수 있어
     * (유형 3개 × 항목 다수) [AI_CONTEXT_MAX_TOKENS]로 응답 여유를 더 둔다.
     */
    private suspend fun fetchAiContextGroupOrThrow(
        config: AiApiKeyConfig,
        systemPrompt: String,
        userMessage: String,
        label: String,
        today: LocalDate
    ): AiContextGroupOutcome {
        val callResult = callLlmWithSearch(config, systemPrompt, userMessage, AI_CONTEXT_MAX_TOKENS)
        val provider = providerLabel(config)
        val obj = extractJsonObject(callResult.text, json)
            ?: return AiContextGroupOutcome(
                emptyList(),
                emptyMap(),
                "$label 응답 파싱 실패",
                callResult.searchWidgetHtml,
                provider
            )

        val drafts = parseAiContextClaimsDto(obj).mapNotNull(::toAiContextDraft)
        val pending = mutableListOf<AiContextClaimValidationResult.Accepted>()
        val rejectedCounts = mutableMapOf<AiContextClaimRejection, Int>()
        drafts.forEach { draft ->
            when (val result = AiContextClaimValidation.validate(draft, callResult.resultUrls, today)) {
                is AiContextClaimValidationResult.Accepted -> pending += result
                is AiContextClaimValidationResult.Rejected ->
                    rejectedCounts[result.reason] = (rejectedCounts[result.reason] ?: 0) + 1
            }
        }
        return AiContextGroupOutcome(pending, rejectedCounts, null, callResult.searchWidgetHtml, provider)
    }

    /**
     * 원시 클레임 → [AiContextClaimDraft]. `section_key`/`type`이 화이트리스트 밖이면 null(호출측이
     * 스킵 — TASK_bear_signal_console.md §4.7 "알 수 없는 section_key·type은 해당 클레임 스킵/폐기").
     * `text`가 비어있는 클레임도 쓸모없는 항목이라 함께 스킵한다(검증 파이프라인의 정식 폐기 사유는
     * 아니며, 파싱 단계의 방어적 필터링).
     *
     * `source_date`는 [parseDateOrToday]가 아니라 [parseDateOrNull]로 파싱한다 — §4.7 검증2
     * "source_date 부재 → 폐기"가 실제 null을 요구하기 때문이다(§4.5의 낙관적 오늘 폴백과 다름).
     */
    private fun toAiContextDraft(raw: AiContextClaimRaw): AiContextClaimDraft? {
        val sectionKey = raw.sectionKey?.let { AiContextSectionKey.fromKey(it) } ?: return null
        val type = raw.type?.let { ClaimType.fromKey(it) } ?: return null
        val text = raw.text?.takeIf { it.isNotBlank() } ?: return null
        return AiContextClaimDraft(
            sectionKey = sectionKey,
            text = text,
            type = type,
            sourceUrl = raw.sourceUrl,
            sourceTitle = raw.sourceTitle,
            sourceDate = parseDateOrNull(raw.sourceDate),
            quote = raw.quote
        )
    }

    private fun aiContextGroupFailure(e: Exception, label: String, config: AiApiKeyConfig): AiContextGroupOutcome {
        val message = (e as? ApiError)?.message ?: e.message ?: "알 수 없는 오류"
        Timber.w(e, "%s §4.7 그룹 조회 실패", label)
        return AiContextGroupOutcome(emptyList(), emptyMap(), "$label: $message", null, providerLabel(config))
    }

    /** [BearSignalAiContextEntity.provider][com.tinyoscillator.feature.bearsignal.data.local.BearSignalAiContextEntity.provider] 규약("claude"/"gemini")과 동일하게 소문자로 표기한다. */
    private fun providerLabel(config: AiApiKeyConfig): String = config.provider.name.lowercase()

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
        val callResult = callLlmWithSearch(config, RATE_DIR_SYSTEM_PROMPT, RATE_DIR_USER_PROMPT)
        val obj = extractJsonObject(callResult.text, json)
            ?: return SuggestionGroupOutcome(emptyList(), "$GROUP_LABEL_RATE_DIR 응답 파싱 실패", callResult.searchWidgetHtml)
        val dto = parseRateDirDto(obj)
        val defaultOrigin = defaultOriginFor(config)

        val suggestions = mutableListOf<Suggestion>()

        dto.rate?.let { rate ->
            val reconfirmed = if (SuggestionValidation.isVolatileRateChange(currentRate, rate)) {
                reconfirmMatches(rate) {
                    extractJsonObject(callLlmWithSearch(config, RATE_DIR_SYSTEM_PROMPT, RATE_DIR_USER_PROMPT).text, json)
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
                    origin = dto.rateOrigin ?: defaultOrigin,
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
                origin = dto.dirOrigin ?: defaultOrigin,
                stale = SuggestionValidation.isStale(asOf, LocalDate.now(), SuggestionField.DIR.maxAgeDays)
            )
        }

        return SuggestionGroupOutcome(suggestions, null, callResult.searchWidgetHtml)
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
        val callResult = callLlmWithSearch(config, BIG_LOSS_SYSTEM_PROMPT, BIG_LOSS_USER_PROMPT)
        val obj = extractJsonObject(callResult.text, json)
            ?: return SuggestionGroupOutcome(emptyList(), "$GROUP_LABEL_BIG_LOSS 응답 파싱 실패", callResult.searchWidgetHtml)
        val dto = parseBigDealLossRatioDto(obj)
        val defaultOrigin = defaultOriginFor(config)

        val suggestions = mutableListOf<Suggestion>()

        dto.bigDeal?.takeIf { SuggestionValidation.isValidBigDeal(it) }?.let { big ->
            val asOf = parseDateOrToday(dto.bigDealAsOf)
            suggestions += Suggestion(
                field = SuggestionField.BIG_DEAL,
                currentValue = currentBigDeal,
                nextValue = big,
                asOf = asOf,
                origin = dto.bigDealOrigin ?: defaultOrigin,
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
                origin = dto.lossRatioOrigin ?: defaultOrigin,
                stale = SuggestionValidation.isStale(asOf, LocalDate.now(), SuggestionField.LOSS_RATIO.maxAgeDays)
            )
        }

        return SuggestionGroupOutcome(suggestions, null, callResult.searchWidgetHtml)
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
        val callResult = callLlmWithSearch(config, CREDIT_SYSTEM_PROMPT, CREDIT_USER_PROMPT)
        val obj = extractJsonObject(callResult.text, json)
            ?: return SuggestionGroupOutcome(emptyList(), "$GROUP_LABEL_CREDIT 응답 파싱 실패", callResult.searchWidgetHtml)
        val dto = parseCreditDto(obj)
        val defaultOrigin = defaultOriginFor(config)

        val suggestions = mutableListOf<Suggestion>()
        dto.credit?.let { credit ->
            val reconfirmed = if (SuggestionValidation.isVolatileCreditChange(currentCredit, credit)) {
                reconfirmMatches(credit) {
                    extractJsonObject(callLlmWithSearch(config, CREDIT_SYSTEM_PROMPT, CREDIT_USER_PROMPT).text, json)
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
                    origin = dto.creditOrigin ?: defaultOrigin,
                    stale = SuggestionValidation.isStale(asOf, LocalDate.now(), SuggestionField.CREDIT.maxAgeDays)
                )
            }
        }
        return SuggestionGroupOutcome(suggestions, null, callResult.searchWidgetHtml)
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

    /** §4.5 v1.3 "제공자별 기본 origin" — DTO에 origin이 없을 때의 폴백. */
    private fun defaultOriginFor(config: AiApiKeyConfig): String =
        if (config.provider == AiProvider.GEMINI) ORIGIN_GEMINI else ORIGIN_ANTHROPIC

    // ── LLM 호출 디스패치(§4.5 v1.3 제공자 이원화) ────────────────────────

    /**
     * 그룹별 단일 논리적 호출 결과 — [text]에서 JSON을 추출하고, Gemini 경로는 [searchWidgetHtml]을
     * 함께 담는다. [resultUrls]는 §4.7 검증1 "URL 교차검증" 입력([AiContextClaimValidation.validate]) —
     * §4.5 그룹①②③은 이 필드를 사용하지 않는다(빈 리스트로 흘러도 무해).
     */
    private data class LlmCallResult(val text: String, val resultUrls: List<String>, val searchWidgetHtml: String?)

    private suspend fun callLlmWithSearch(
        config: AiApiKeyConfig,
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int = MAX_TOKENS
    ): LlmCallResult = when (config.provider) {
        AiProvider.CLAUDE -> callClaudeWithWebSearch(config, systemPrompt, userMessage, maxTokens).let {
            LlmCallResult(it.text, it.resultUrls, null)
        }
        AiProvider.GEMINI -> callGeminiWithGoogleSearch(config, systemPrompt, userMessage, maxTokens)
    }

    // ── Anthropic API 호출(web_search 서버 도구 + pause_turn 재개) ──────────

    /** [callClaudeWithWebSearch] 결과 — [resultUrls]는 pause_turn 재개 전 구간을 포함한 전체 누적값(§4.7). */
    private data class ClaudeCallResult(val text: String, val resultUrls: List<String>)

    /**
     * `web_search` 서버 도구를 사용하는 단일 논리적 호출 — `stop_reason == "pause_turn"`이면 받은
     * assistant `content`를 그대로 messages에 append해 재요청한다(추가 user 메시지 없이). 반복
     * 한도는 [MAX_CONTINUATIONS]. 최종 텍스트(모든 `text` 블록 이어붙임)와, 재개 과정에서 오간 모든
     * 응답의 [ParsedLlmResponse.resultUrls]를 합집합으로 누적해 반환한다 — §4.7 검증1은 "같은
     * 응답에 동봉된" URL을 요구하지만 pause_turn 재개는 논리적으로 하나의 호출이므로, 앞선 응답의
     * `web_search_tool_result`에서만 나온 URL을 최종 클레임이 인용해도 검증을 통과해야 한다.
     */
    private suspend fun callClaudeWithWebSearch(
        config: AiApiKeyConfig,
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int = MAX_TOKENS
    ): ClaudeCallResult {
        var messages: JsonArray = buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", userMessage)
            })
        }
        var continuations = 0
        val accumulatedUrls = mutableListOf<String>()
        while (true) {
            val request = buildClaudeRequest(config, systemPrompt, messages, maxTokens)
            val body = executeWithRetry(request, AiProvider.CLAUDE).getOrThrow()
            val parsed = parseLlmResponse(body, json)
            accumulatedUrls += parsed.resultUrls
            if (parsed.stopReason == "pause_turn" && continuations < MAX_CONTINUATIONS) {
                continuations++
                messages = appendAssistantContent(messages, parsed.rawContent)
                continue
            }
            return ClaudeCallResult(parsed.finalText, accumulatedUrls.distinct())
        }
    }

    private fun buildClaudeRequest(
        config: AiApiKeyConfig,
        systemPrompt: String,
        messages: JsonArray,
        maxTokens: Int = MAX_TOKENS
    ): Request {
        val body = buildJsonObject {
            put("model", config.modelId)
            put("max_tokens", maxTokens)
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

    // ── Gemini API 호출(google_search grounding, v1.3) ──────────────────

    /**
     * `google_search` grounding 도구를 사용하는 단일 호출 — Claude와 달리 `pause_turn` 개념이 없어
     * 재개 루프가 필요 없다. 구조화 출력(`responseMimeType`)은 절대 설정하지 않는다(Gemini 2.5 이하
     * `google_search` 병용 시 HTTP 400 — §4.5 v1.3 "Gemini 경로").
     */
    private suspend fun callGeminiWithGoogleSearch(
        config: AiApiKeyConfig,
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int = MAX_TOKENS
    ): LlmCallResult {
        waitForGeminiRateLimit()
        val request = buildGeminiRequest(config, systemPrompt, userMessage, maxTokens)
        val body = executeWithRetry(request, AiProvider.GEMINI).getOrThrow()
        val parsed = parseGeminiLlmResponse(body, json)
        return LlmCallResult(parsed.finalText, parsed.resultUrls, parsed.searchWidgetHtml)
    }

    private fun buildGeminiRequest(
        config: AiApiKeyConfig,
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int = MAX_TOKENS
    ): Request {
        // Gemini는 systemInstruction을 쓰지 않고 시스템 프롬프트를 user 텍스트 앞에 접합한다
        // (기존 AiApiClient Gemini 관례 재사용).
        val combinedText = "$systemPrompt\n\n$userMessage"
        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", combinedText) })
                    })
                })
            })
            put("tools", buildJsonArray {
                add(buildJsonObject {
                    put("google_search", buildJsonObject {})
                })
            })
            put("generationConfig", buildJsonObject {
                // Gemini 2.5+ thinking 오버헤드 반영(AiApiClient GEMINI_THINKING_OVERHEAD 관례) —
                // responseMimeType은 절대 넣지 않는다(google_search와 병용 불가, HTTP 400).
                put("maxOutputTokens", maxTokens + GEMINI_THINKING_OVERHEAD)
            })
        }.toString()

        return Request.Builder()
            .url("$geminiBaseUrl/v1beta/models/${config.modelId}:generateContent")
            .addHeader("x-goog-api-key", config.apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
    }

    // ── 공통 네트워크 실행 ────────────────────────────────────────────

    /** 1회 백오프 재시도(§4.5 항목4) — 재시도 대상은 [ApiError.isRetriableError]가 true인 경우만. */
    private suspend fun executeWithRetry(request: Request, provider: AiProvider): Result<String> {
        val first = executeOnce(request, provider)
        if (first.isSuccess) return first
        val err = first.exceptionOrNull()
        if (err !is ApiError || !ApiError.isRetriableError(err)) return first
        delay(retryBackoffMs)
        // 재시도 호출도 rate limit 슬롯을 예약해야 병렬 그룹과의 간격 보장이 깨지지 않는다.
        if (provider == AiProvider.GEMINI) waitForGeminiRateLimit()
        return executeOnce(request, provider)
    }

    private suspend fun executeOnce(request: Request, provider: AiProvider): Result<String> = withContext(Dispatchers.IO) {
        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    Result.failure(mapHttpError(response.code, provider, body))
                } else {
                    Result.success(body)
                }
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(ApiError.TimeoutError("AI API 요청 시간이 초과되었습니다"))
        } catch (e: IOException) {
            Result.failure(ApiError.NetworkError(e.message ?: "네트워크 오류"))
        }
    }

    /**
     * HTTP 오류 매핑 — 제공자 중립 메시지가 기본이나, Gemini 400은 원인별로 분기한다(§4.5 v1.3):
     * Gemini는 무효 API 키도 401/403이 아닌 **400(`API_KEY_INVALID`)** 으로 반환하므로, 오류 body에서
     * 키 무효 사유를 감지해 인증 오류로 안내한다(가장 흔한 실패를 오진하지 않기 위함). 그 외 400은
     * `google_search` 미지원 모델/구조화 출력 병용 제약 안내.
     */
    private fun mapHttpError(code: Int, provider: AiProvider, errorBody: String? = null): ApiError = when {
        provider == AiProvider.GEMINI && code == 400 &&
            (errorBody?.contains("API_KEY_INVALID") == true || errorBody?.contains("API key not valid") == true) ->
            ApiError.AuthError("Gemini API 키가 유효하지 않습니다 (HTTP 400)")
        provider == AiProvider.GEMINI && code == 400 ->
            ApiError.ApiCallError(
                400,
                "Gemini API 요청 오류 — 선택한 모델이 google_search를 지원하지 않을 수 있습니다 (HTTP 400)"
            )
        code == 401 || code == 403 -> ApiError.AuthError("AI API 인증 실패 (HTTP $code)")
        code == 429 -> ApiError.ApiCallError(429, "요청 한도 초과, 잠시 후 다시 시도해주세요")
        code in 500..599 -> ApiError.NetworkError("AI API 서버 오류 (HTTP $code)")
        else -> ApiError.ApiCallError(code, "HTTP $code")
    }

    companion object {
        private const val DEFAULT_BASE_URL = "https://api.anthropic.com"
        private const val DEFAULT_GEMINI_BASE_URL = "https://generativelanguage.googleapis.com"

        /** §4.5 항목4 "백오프 1회" 대기 시간 — 테스트에서는 생성자로 짧게 오버라이드한다. */
        private const val DEFAULT_RETRY_BACKOFF_MS = 2_000L

        /**
         * Gemini 호출 간 최소 간격 — [ApiConstants.GEMINI_RATE_LIMIT_MS](12s, 무료 티어 5 RPM 기준)를
         * 그대로 재사용한다(AiApiClient와 동일 관례). 테스트에서는 0으로 오버라이드한다.
         */
        private const val DEFAULT_GEMINI_RATE_LIMIT_MS = ApiConstants.GEMINI_RATE_LIMIT_MS

        private const val MAX_TOKENS = 1024

        /**
         * §4.7 그룹④⑤⑥ 전용 응답 토큰 한도 — §4.5 그룹①②③(1~2개 스칼라 필드)보다 클레임 개수가
         * 많을 수 있어(유형 3개 × 체크리스트/사례 항목 + quote 원문 인용) 여유를 더 둔다.
         */
        private const val AI_CONTEXT_MAX_TOKENS = 4096

        /** Gemini 2.5+ thinking 토큰이 maxOutputTokens에 포함되는 오버헤드(AiApiClient 관례 재사용). */
        private const val GEMINI_THINKING_OVERHEAD = 8192

        /** 그룹당 web_search 최대 호출 횟수(도구 정의 `max_uses`). */
        private const val MAX_SEARCH_USES = 3

        /** `pause_turn` 재개 최대 횟수(서버 도구 반복 한도 도달 시, Claude 전용). */
        private const val MAX_CONTINUATIONS = 3

        /** 광범위 호환 web_search 도구 타입 — Haiku 4.5도 지원(신형 `web_search_20260209`는 미지원). */
        private const val WEB_SEARCH_TOOL_TYPE = "web_search_20250305"

        private const val ORIGIN_ANTHROPIC = "Anthropic web_search"
        private const val ORIGIN_GEMINI = "Gemini google_search"

        private const val GROUP_LABEL_RATE_DIR = "미 연준 금리·정책방향"
        private const val GROUP_LABEL_BIG_LOSS = "대어 IPO 소화·적자상장비중"
        private const val GROUP_LABEL_CREDIT = "신용거래융자 잔고"

        private val RATE_DIR_SYSTEM_PROMPT = """
            너는 한국 주식시장 리스크 계기판을 위한 데이터 수집 보조원이다. 웹 검색 도구로 아래 두
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
            너는 한국 주식시장 리스크 계기판을 위한 데이터 수집 보조원이다. 웹 검색 도구로 아래
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
            너는 한국 주식시장 리스크 계기판을 위한 데이터 수집 보조원이다. 웹 검색 도구로 최신
            신용거래융자 잔고(조원)를 KOFIA(금융투자협회) 주간 통계에 근거해 조사하고, 조사 결과를
            반드시 하나의 JSON 객체로만 답하라(다른 설명 텍스트는 절대 포함하지 마라).

            JSON 스키마(확실하지 않으면 생략):
            {"credit": number, "credit_as_of": "YYYY-MM-DD", "credit_origin": string}
        """.trimIndent()

        private const val CREDIT_USER_PROMPT =
            "KOFIA 신용거래융자 잔고(조원) 최신 주간 통계를 조사해줘."

        // ── §4.7 그룹④⑤⑥ 프롬프트(정세 업데이트, Phase 7-2) ──────────────────

        private const val GROUP_LABEL_MONITOR = "약세장 3유형 모니터링 체크리스트"
        private const val GROUP_LABEL_CASES = "약세장 3유형 사례"
        private const val GROUP_LABEL_HISTORY_CURRENT = "역사 검증 현재 비교"

        /**
         * §4.7 그룹④monitor 시스템 프롬프트 — [BearSignalStaticContent.TYPES]의 기존 체크리스트를
         * 기준선으로 제시하고, `fact`만 요구한다(§4.7 "monitor·cases 클레임은 type=fact만 허용").
         */
        private val MONITOR_SYSTEM_PROMPT: String = buildString {
            appendLine(
                "너는 한국 주식시장 리스크 계기판을 위한 데이터 수집 보조원이다. 아래 약세장 3유형별 " +
                    "모니터링 체크리스트가 최신 상황을 반영하는지 웹 검색으로 점검하고, 갱신·추가가 " +
                    "필요한 사실적 근거를 조사해줘."
            )
            BearSignalStaticContent.TYPES.forEach { type ->
                appendLine()
                appendLine("[유형${type.index} ${type.title}] 기존 체크리스트:")
                type.monitor.forEach { item -> appendLine("- $item") }
            }
            appendLine()
            appendLine("조사 결과를 반드시 하나의 JSON 객체로만 답하라(다른 설명 텍스트는 절대 포함하지 마라).")
            appendLine("각 클레임은 검증 가능한 사실(fact)만 담아라 — type은 항상 \"fact\"만 사용하라(해석·의견 금지).")
            appendLine("quote에는 출처 페이지의 원문을 그대로 인용하라(요약 금지, 검증·증거 보존용).")
            appendLine()
            appendLine("JSON 스키마(claims 배열, 항목 0개 이상):")
            appendLine(
                "{\"claims\": [{\"section_key\": \"type0_monitor|type1_monitor|type2_monitor\", " +
                    "\"text\": \"...\", \"type\": \"fact\", \"source_url\": \"...\", " +
                    "\"source_title\": \"...\", \"source_date\": \"YYYY-MM-DD\", \"quote\": \"...\"}]}"
            )
        }

        private const val MONITOR_USER_PROMPT = "약세장 3유형 모니터링 체크리스트를 최신 뉴스로 점검해줘."

        /** §4.7 그룹⑤cases 시스템 프롬프트 — 기존 사례([BearSignalStaticContent.TYPES]`.cases`)를 기준선으로 최근 사례를 요청한다. */
        private val CASES_SYSTEM_PROMPT: String = buildString {
            appendLine(
                "너는 한국 주식시장 리스크 계기판을 위한 데이터 수집 보조원이다. 아래 약세장 3유형별 " +
                    "사례가 최근 상황을 반영하는지 웹 검색으로 점검하고, 추가하거나 갱신할 만한 " +
                    "검증 가능한 최근 사례를 조사해줘."
            )
            BearSignalStaticContent.TYPES.forEach { type ->
                appendLine()
                appendLine("[유형${type.index} ${type.title}] 기존 사례: ${type.cases}")
            }
            appendLine()
            appendLine("조사 결과를 반드시 하나의 JSON 객체로만 답하라(다른 설명 텍스트는 절대 포함하지 마라).")
            appendLine("각 클레임은 검증 가능한 사실(fact)만 담아라 — type은 항상 \"fact\"만 사용하라(해석·의견 금지).")
            appendLine("quote에는 출처 페이지의 원문을 그대로 인용하라(요약 금지, 검증·증거 보존용).")
            appendLine()
            appendLine("JSON 스키마(claims 배열, 항목 0개 이상):")
            appendLine(
                "{\"claims\": [{\"section_key\": \"type0_cases|type1_cases|type2_cases\", " +
                    "\"text\": \"...\", \"type\": \"fact\", \"source_url\": \"...\", " +
                    "\"source_title\": \"...\", \"source_date\": \"YYYY-MM-DD\", \"quote\": \"...\"}]}"
            )
        }

        private const val CASES_USER_PROMPT = "약세장 3유형 사례를 최근 검증 가능한 사실로 업데이트해줘."

        /**
         * §4.7 그룹⑥history_current 시스템 프롬프트 — [BearSignalStaticContent.HISTORY_BODY_CURRENT]·
         * [BearSignalStaticContent.HISTORY_METRICS]를 기준선으로 제시한다. `history_current`만
         * `interpretation`을 허용하므로(§4.7 "history_current는 interpretation 허용") fact/interpretation
         * 모두 요청 가능함을 명시한다.
         */
        private val HISTORY_CURRENT_SYSTEM_PROMPT: String = buildString {
            appendLine(
                "너는 한국 주식시장 리스크 계기판을 위한 데이터 수집 보조원이다. 아래는 1980년대 " +
                    "일본 반도체 산업 붕괴와 현재 한국의 위치를 비교하는 문단과, 감시 대상 3대 " +
                    "지표의 기존 기준선이다."
            )
            appendLine()
            appendLine("기존 비교 문단: \"${BearSignalStaticContent.HISTORY_BODY_CURRENT}\"")
            BearSignalStaticContent.HISTORY_METRICS.forEach { metric ->
                appendLine("- [${metric.header}] ${metric.body}")
            }
            appendLine()
            appendLine("웹 검색으로 이 비교 문단·3대 지표를 최신화할 사실적 근거 또는 해석을 조사해줘.")
            appendLine("검증 가능한 사실(fact)에는 quote(출처 페이지 원문 인용)를 반드시 채워라. 사실이")
            appendLine("아닌 해석·전망·예측은 type을 \"interpretation\"으로 표시하라(quote 생략 가능).")
            appendLine()
            appendLine("조사 결과를 반드시 하나의 JSON 객체로만 답하라(다른 설명 텍스트는 절대 포함하지 마라).")
            appendLine("JSON 스키마(claims 배열, 항목 0개 이상):")
            appendLine(
                "{\"claims\": [{\"section_key\": \"history_current\", \"text\": \"...\", " +
                    "\"type\": \"fact|interpretation\", \"source_url\": \"...\", " +
                    "\"source_title\": \"...\", \"source_date\": \"YYYY-MM-DD\", \"quote\": \"...\"}]}"
            )
        }

        private const val HISTORY_CURRENT_USER_PROMPT =
            "일본 1980년대 반도체 붕괴와 현재 한국 위치를 비교하는 문단·3대 지표를 최신 뉴스로 업데이트해줘."
    }
}
