package com.tinyoscillator.core.api

import com.tinyoscillator.core.config.ApiConstants
import com.tinyoscillator.domain.model.AiAnalysisResult
import com.tinyoscillator.domain.model.AiAnalysisType
import com.tinyoscillator.domain.model.AiApiKeyConfig
import com.tinyoscillator.domain.model.AiModelInfo
import com.tinyoscillator.domain.model.AiProvider
import com.tinyoscillator.domain.model.AiStreamEvent
import com.tinyoscillator.domain.model.ChatMessage
import com.tinyoscillator.domain.model.ChatRole
import com.tinyoscillator.domain.model.ClaudeModelsResponse
import com.tinyoscillator.domain.model.ClaudeResponse
import com.tinyoscillator.domain.model.ClaudeStreamEvent
import com.tinyoscillator.domain.model.GeminiModelsResponse
import com.tinyoscillator.domain.model.GeminiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

/**
 * AI API 클라이언트 (Claude / Gemini).
 *
 * KisApiClient 패턴 준수: OkHttpClient 싱글톤, Result<T>, ApiError, CircuitBreaker, Mutex 레이트리밋.
 */
class AiApiClient(
    private val httpClient: OkHttpClient = KiwoomApiClient.createDefaultClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
) : BaseApiClient(rateLimitMs = ApiConstants.CLAUDE_RATE_LIMIT_MS) {

    private val geminiRateMutex = Mutex()
    @Volatile
    private var geminiLastCallTime = 0L

    private suspend fun waitForGeminiRateLimit() {
        val delayMs: Long
        geminiRateMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - geminiLastCallTime
            delayMs = if (elapsed < ApiConstants.GEMINI_RATE_LIMIT_MS) ApiConstants.GEMINI_RATE_LIMIT_MS - elapsed else 0L
            geminiLastCallTime = now + delayMs
        }
        if (delayMs > 0L) delay(delayMs)
    }

    // region Model List Fetching

    /** 제공자로부터 사용 가능한 모델 목록을 조회한다. */
    suspend fun fetchModels(
        provider: AiProvider,
        apiKey: String
    ): Result<List<AiModelInfo>> = withContext(Dispatchers.IO) {
        try {
            when (provider) {
                AiProvider.CLAUDE -> fetchClaudeModels(apiKey)
                AiProvider.GEMINI -> fetchGeminiModels(apiKey)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(ApiError.mapException(e))
        }
    }

    private suspend fun fetchClaudeModels(apiKey: String): Result<List<AiModelInfo>> {
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/models")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .get()
            .build()

        val (body, code, ok) = httpClient.newCall(request).await().use { response ->
            Triple(response.body?.string(), response.code, response.isSuccessful)
        }
        if (!ok || body == null) return Result.failure(mapHttpError(code))

        val models = json.decodeFromString<ClaudeModelsResponse>(body)
        return Result.success(
            models.data.map { AiModelInfo(id = it.id, displayName = it.displayName) }
        )
    }

    private suspend fun fetchGeminiModels(apiKey: String): Result<List<AiModelInfo>> {
        val request = Request.Builder()
            // API 키는 URL 쿼리 대신 헤더로 전달 — 예외/로그 URL 유출 방지 및 타 Gemini 호출과 통일(Phase 3-8)
            .url("https://generativelanguage.googleapis.com/v1beta/models")
            .addHeader("x-goog-api-key", apiKey)
            .get()
            .build()

        val (body, code, ok) = httpClient.newCall(request).await().use { response ->
            Triple(response.body?.string(), response.code, response.isSuccessful)
        }
        if (!ok || body == null) return Result.failure(mapHttpError(code))

        val models = json.decodeFromString<GeminiModelsResponse>(body)
        return Result.success(
            models.models
                .filter { "generateContent" in it.supportedGenerationMethods }
                .map { AiModelInfo(id = it.name.removePrefix("models/"), displayName = it.displayName) }
        )
    }

    // endregion

    // region Analysis

    suspend fun analyze(
        config: AiApiKeyConfig,
        systemPrompt: String,
        userMessage: String,
        analysisType: AiAnalysisType = AiAnalysisType.STOCK_OSCILLATOR,
        maxTokens: Int = 1024,
        temperature: Double = 0.3
    ): Result<AiAnalysisResult> = withContext(Dispatchers.IO) {
        executeWithRetry(
            tag = "AI API",
            retryDelaysMs = BaseApiClient.AI_RETRY_DELAYS_MS,
            retryableFilter = AI_RETRYABLE_FILTER,
        ) {
            analyzeOnce(config, systemPrompt, userMessage, analysisType, maxTokens, temperature)
        }
    }

    private suspend fun analyzeOnce(
        config: AiApiKeyConfig,
        systemPrompt: String,
        userMessage: String,
        analysisType: AiAnalysisType,
        maxTokens: Int,
        temperature: Double
    ): Result<AiAnalysisResult> {
        try {
            if (config.provider == AiProvider.GEMINI) waitForGeminiRateLimit()
            else waitForRateLimit()

            return when (config.provider) {
                AiProvider.CLAUDE ->
                    callClaude(config, systemPrompt, userMessage, analysisType, maxTokens, temperature)
                AiProvider.GEMINI ->
                    callGemini(config, systemPrompt, userMessage, analysisType, maxTokens, temperature)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(ApiError.mapException(e))
        }
    }

    private suspend fun callClaude(
        config: AiApiKeyConfig,
        systemPrompt: String,
        userMessage: String,
        analysisType: AiAnalysisType,
        maxTokens: Int,
        temperature: Double
    ): Result<AiAnalysisResult> {
        val requestBody = buildJsonObject {
            put("model", config.modelId)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            put("system", claudeCachedSystem(systemPrompt))
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userMessage)
                })
            })
        }.toString()

        val request = claudeRequest(config, requestBody)

        Timber.d("Claude API call: %s", config.modelId)

        val (responseBody, responseCode, isSuccessful) = httpClient.newCall(request).await().use { response ->
            Triple(response.body?.string(), response.code, response.isSuccessful)
        }

        if (!isSuccessful || responseBody == null) {
            return Result.failure(mapHttpError(responseCode))
        }

        val claudeResponse = json.decodeFromString<ClaudeResponse>(responseBody)
        val text = claudeResponse.content.firstOrNull { it.type == "text" }?.text ?: ""

        return Result.success(
            AiAnalysisResult(
                type = analysisType,
                provider = config.provider,
                modelId = config.modelId,
                content = text,
                inputTokens = claudeResponse.usage.inputTokens,
                outputTokens = claudeResponse.usage.outputTokens,
                cacheCreationTokens = claudeResponse.usage.cacheCreationInputTokens,
                cacheReadTokens = claudeResponse.usage.cacheReadInputTokens
            )
        )
    }

    private suspend fun callGemini(
        config: AiApiKeyConfig,
        systemPrompt: String,
        userMessage: String,
        analysisType: AiAnalysisType,
        maxTokens: Int,
        temperature: Double
    ): Result<AiAnalysisResult> {
        val combinedMessage = "$systemPrompt\n\n$userMessage"

        // Gemini 2.5+ 모델은 thinking 토큰이 maxOutputTokens에 포함됨
        // thinking 오버헤드를 반영하여 실제 응답 토큰이 충분하도록 증가
        val effectiveMaxTokens = maxTokens + GEMINI_THINKING_OVERHEAD

        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", combinedMessage)
                        })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", temperature)
                put("maxOutputTokens", effectiveMaxTokens)
            })
        }.toString()

        val url = "${config.getBaseUrl()}/v1beta/models/${config.modelId}:generateContent"

        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", config.apiKey)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        Timber.d("Gemini API call: %s", config.modelId)

        val (responseBody, responseCode, isSuccessful) = httpClient.newCall(request).await().use { response ->
            Triple(response.body?.string(), response.code, response.isSuccessful)
        }

        if (!isSuccessful || responseBody == null) {
            return Result.failure(mapHttpError(responseCode))
        }

        val geminiResponse = json.decodeFromString<GeminiResponse>(responseBody)
        val text = geminiResponse.candidates.firstOrNull()
            ?.content?.parts?.firstOrNull()?.text ?: ""

        return Result.success(
            AiAnalysisResult(
                type = analysisType,
                provider = config.provider,
                modelId = config.modelId,
                content = text,
                inputTokens = geminiResponse.usageMetadata.promptTokenCount,
                outputTokens = geminiResponse.usageMetadata.candidatesTokenCount
            )
        )
    }

    // endregion

    // region Chat (multi-turn)

    /**
     * 멀티턴 대화. systemPrompt + 대화 히스토리를 전송하고 응답을 반환한다.
     */
    suspend fun chat(
        config: AiApiKeyConfig,
        systemPrompt: String,
        messages: List<ChatMessage>,
        maxTokens: Int = 1024,
        temperature: Double = 0.3
    ): Result<String> = withContext(Dispatchers.IO) {
        executeWithRetry(
            tag = "AI Chat",
            retryDelaysMs = BaseApiClient.AI_RETRY_DELAYS_MS,
            retryableFilter = AI_RETRYABLE_FILTER,
        ) {
            chatOnce(config, systemPrompt, messages, maxTokens, temperature)
        }
    }

    private suspend fun chatOnce(
        config: AiApiKeyConfig,
        systemPrompt: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        temperature: Double
    ): Result<String> {
        try {
            if (config.provider == AiProvider.GEMINI) waitForGeminiRateLimit()
            else waitForRateLimit()

            return when (config.provider) {
                AiProvider.CLAUDE -> callClaudeChat(config, systemPrompt, messages, maxTokens, temperature)
                AiProvider.GEMINI -> callGeminiChat(config, systemPrompt, messages, maxTokens, temperature)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(ApiError.mapException(e))
        }
    }

    private suspend fun callClaudeChat(
        config: AiApiKeyConfig,
        systemPrompt: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        temperature: Double
    ): Result<String> {
        val requestBody = claudeChatRequestBody(config, systemPrompt, messages, maxTokens, temperature, stream = false)

        val request = claudeRequest(config, requestBody)

        Timber.d("Claude Chat call: %s (%d messages)", config.modelId, messages.size)

        val (responseBody, responseCode, isSuccessful) = httpClient.newCall(request).await().use { response ->
            Triple(response.body?.string(), response.code, response.isSuccessful)
        }

        if (!isSuccessful || responseBody == null) {
            return Result.failure(mapHttpError(responseCode))
        }

        val claudeResponse = json.decodeFromString<ClaudeResponse>(responseBody)
        val text = claudeResponse.content.firstOrNull { it.type == "text" }?.text ?: ""
        return Result.success(text)
    }

    private suspend fun callGeminiChat(
        config: AiApiKeyConfig,
        systemPrompt: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        temperature: Double
    ): Result<String> {
        val requestBody = geminiChatRequestBody(systemPrompt, messages, maxTokens, temperature)

        val url = "${config.getBaseUrl()}/v1beta/models/${config.modelId}:generateContent"

        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", config.apiKey)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        Timber.d("Gemini Chat call: %s (%d messages)", config.modelId, messages.size)

        val (responseBody, responseCode, isSuccessful) = httpClient.newCall(request).await().use { response ->
            Triple(response.body?.string(), response.code, response.isSuccessful)
        }

        if (!isSuccessful || responseBody == null) {
            return Result.failure(mapHttpError(responseCode))
        }

        val geminiResponse = json.decodeFromString<GeminiResponse>(responseBody)
        val text = geminiResponse.candidates.firstOrNull()
            ?.content?.parts?.firstOrNull()?.text ?: ""
        return Result.success(text)
    }

    // endregion

    // region Chat streaming (SSE)

    /**
     * 멀티턴 대화 스트리밍. 텍스트 증분([AiStreamEvent.Delta])을 순차 방출하고
     * 종료 시 [AiStreamEvent.Done]으로 전체 텍스트 + 토큰 사용량을 전달한다.
     *
     * 연결/HTTP 오류는 [ApiError]로 throw — 수집 측에서 catch하여 처리한다.
     * 스트리밍 특성상 자동 재시도는 하지 않는다 (부분 출력 중복 방지).
     */
    fun chatStream(
        config: AiApiKeyConfig,
        systemPrompt: String,
        messages: List<ChatMessage>,
        maxTokens: Int = 1024,
        temperature: Double = 0.3
    ): Flow<AiStreamEvent> = flow {
        if (!circuitBreaker.tryAcquire()) throw ApiError.CircuitBreakerOpenError()
        try {
            if (config.provider == AiProvider.GEMINI) waitForGeminiRateLimit()
            else waitForRateLimit()

            when (config.provider) {
                AiProvider.CLAUDE -> collectClaudeStream(config, systemPrompt, messages, maxTokens, temperature)
                AiProvider.GEMINI -> collectGeminiStream(config, systemPrompt, messages, maxTokens, temperature)
            }
            circuitBreaker.recordSuccess()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val apiErr = if (e is ApiError) e else ApiError.mapException(e)
            if (ApiError.isRetriableError(apiErr)) circuitBreaker.recordFailure()
            throw apiErr
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<AiStreamEvent>.collectClaudeStream(
        config: AiApiKeyConfig,
        systemPrompt: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        temperature: Double
    ) {
        val requestBody = claudeChatRequestBody(config, systemPrompt, messages, maxTokens, temperature, stream = true)
        val request = claudeRequest(config, requestBody)

        Timber.d("Claude Chat stream: %s (%d messages)", config.modelId, messages.size)

        httpClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw mapHttpError(response.code)
            val source = response.body?.source() ?: throw mapHttpError(response.code)

            val fullText = StringBuilder()
            var inputTokens = 0
            var outputTokens = 0
            var cacheReadTokens = 0

            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) continue

                val event = try {
                    json.decodeFromString<ClaudeStreamEvent>(payload)
                } catch (e: Exception) {
                    continue
                }
                when (event.type) {
                    "message_start" -> event.message?.usage?.let {
                        inputTokens = it.inputTokens
                        cacheReadTokens = it.cacheReadInputTokens
                    }
                    "content_block_delta" -> {
                        val text = event.delta?.takeIf { it.type == "text_delta" }?.text
                        if (!text.isNullOrEmpty()) {
                            fullText.append(text)
                            emit(AiStreamEvent.Delta(text))
                        }
                    }
                    "message_delta" -> event.usage?.let { outputTokens = it.outputTokens }
                    "message_stop" -> break
                }
            }
            emit(AiStreamEvent.Done(fullText.toString(), inputTokens, outputTokens, cacheReadTokens))
        }
    }

    private suspend fun FlowCollector<AiStreamEvent>.collectGeminiStream(
        config: AiApiKeyConfig,
        systemPrompt: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        temperature: Double
    ) {
        val requestBody = geminiChatRequestBody(systemPrompt, messages, maxTokens, temperature)
        val url = "${config.getBaseUrl()}/v1beta/models/${config.modelId}:streamGenerateContent?alt=sse"

        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", config.apiKey)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        Timber.d("Gemini Chat stream: %s (%d messages)", config.modelId, messages.size)

        httpClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw mapHttpError(response.code)
            val source = response.body?.source() ?: throw mapHttpError(response.code)

            val fullText = StringBuilder()
            var inputTokens = 0
            var outputTokens = 0

            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) continue

                val chunk = try {
                    json.decodeFromString<GeminiResponse>(payload)
                } catch (e: Exception) {
                    continue
                }
                val text = chunk.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!text.isNullOrEmpty()) {
                    fullText.append(text)
                    emit(AiStreamEvent.Delta(text))
                }
                if (chunk.usageMetadata.promptTokenCount > 0) inputTokens = chunk.usageMetadata.promptTokenCount
                if (chunk.usageMetadata.candidatesTokenCount > 0) outputTokens = chunk.usageMetadata.candidatesTokenCount
            }
            emit(AiStreamEvent.Done(fullText.toString(), inputTokens, outputTokens))
        }
    }

    // endregion

    // region Structured output

    /**
     * 구조화 출력 분석. Claude는 tool_choice로 JSON 스키마를 강제하고,
     * Gemini는 JSON 응답 모드(responseMimeType)를 사용한다.
     * 성공 시 [AiAnalysisResult.content]는 스키마를 따르는 JSON 문자열이다.
     */
    suspend fun analyzeStructured(
        config: AiApiKeyConfig,
        systemPrompt: String,
        userMessage: String,
        schema: JsonObject,
        analysisType: AiAnalysisType = AiAnalysisType.PROBABILITY_INTERPRETATION,
        maxTokens: Int = 1500,
        temperature: Double = 0.3
    ): Result<AiAnalysisResult> = withContext(Dispatchers.IO) {
        executeWithRetry(
            tag = "AI Structured",
            retryDelaysMs = BaseApiClient.AI_RETRY_DELAYS_MS,
            retryableFilter = AI_RETRYABLE_FILTER,
        ) {
            try {
                if (config.provider == AiProvider.GEMINI) waitForGeminiRateLimit()
                else waitForRateLimit()

                when (config.provider) {
                    AiProvider.CLAUDE ->
                        callClaudeStructured(config, systemPrompt, userMessage, schema, analysisType, maxTokens, temperature)
                    AiProvider.GEMINI ->
                        callGeminiStructured(config, systemPrompt, userMessage, analysisType, maxTokens, temperature)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(ApiError.mapException(e))
            }
        }
    }

    private suspend fun callClaudeStructured(
        config: AiApiKeyConfig,
        systemPrompt: String,
        userMessage: String,
        schema: JsonObject,
        analysisType: AiAnalysisType,
        maxTokens: Int,
        temperature: Double
    ): Result<AiAnalysisResult> {
        val requestBody = buildJsonObject {
            put("model", config.modelId)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            put("system", claudeCachedSystem(systemPrompt))
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userMessage)
                })
            })
            put("tools", buildJsonArray {
                add(buildJsonObject {
                    put("name", STRUCTURED_TOOL_NAME)
                    put("description", "분석 결과를 구조화된 형식으로 제출한다.")
                    put("input_schema", schema)
                })
            })
            put("tool_choice", buildJsonObject {
                put("type", "tool")
                put("name", STRUCTURED_TOOL_NAME)
            })
        }.toString()

        val request = claudeRequest(config, requestBody)

        Timber.d("Claude Structured call: %s", config.modelId)

        val (responseBody, responseCode, isSuccessful) = httpClient.newCall(request).await().use { response ->
            Triple(response.body?.string(), response.code, response.isSuccessful)
        }

        if (!isSuccessful || responseBody == null) {
            return Result.failure(mapHttpError(responseCode))
        }

        val claudeResponse = json.decodeFromString<ClaudeResponse>(responseBody)
        val toolInput = claudeResponse.content.firstOrNull { it.type == "tool_use" }?.input
            ?: return Result.failure(ApiError.ParseError("구조화 출력(tool_use)이 응답에 없습니다"))

        return Result.success(
            AiAnalysisResult(
                type = analysisType,
                provider = config.provider,
                modelId = config.modelId,
                content = toolInput.toString(),
                inputTokens = claudeResponse.usage.inputTokens,
                outputTokens = claudeResponse.usage.outputTokens,
                cacheCreationTokens = claudeResponse.usage.cacheCreationInputTokens,
                cacheReadTokens = claudeResponse.usage.cacheReadInputTokens
            )
        )
    }

    private suspend fun callGeminiStructured(
        config: AiApiKeyConfig,
        systemPrompt: String,
        userMessage: String,
        analysisType: AiAnalysisType,
        maxTokens: Int,
        temperature: Double
    ): Result<AiAnalysisResult> {
        val combinedMessage = "$systemPrompt\n\n$userMessage"
        val effectiveMaxTokens = maxTokens + GEMINI_THINKING_OVERHEAD

        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", combinedMessage) })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", temperature)
                put("maxOutputTokens", effectiveMaxTokens)
                put("responseMimeType", "application/json")
            })
        }.toString()

        val url = "${config.getBaseUrl()}/v1beta/models/${config.modelId}:generateContent"

        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", config.apiKey)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        Timber.d("Gemini Structured call: %s", config.modelId)

        val (responseBody, responseCode, isSuccessful) = httpClient.newCall(request).await().use { response ->
            Triple(response.body?.string(), response.code, response.isSuccessful)
        }

        if (!isSuccessful || responseBody == null) {
            return Result.failure(mapHttpError(responseCode))
        }

        val geminiResponse = json.decodeFromString<GeminiResponse>(responseBody)
        val text = geminiResponse.candidates.firstOrNull()
            ?.content?.parts?.firstOrNull()?.text ?: ""

        return Result.success(
            AiAnalysisResult(
                type = analysisType,
                provider = config.provider,
                modelId = config.modelId,
                content = text,
                inputTokens = geminiResponse.usageMetadata.promptTokenCount,
                outputTokens = geminiResponse.usageMetadata.candidatesTokenCount
            )
        )
    }

    // endregion

    // region Request builders

    /**
     * Claude system 블록 — prompt caching 적용.
     * 시스템 프롬프트(데이터 컨텍스트 포함)는 턴마다 동일하므로 ephemeral 캐시로
     * 멀티턴 대화의 입력 토큰 비용을 대폭 줄인다.
     */
    private fun claudeCachedSystem(systemPrompt: String) = buildJsonArray {
        add(buildJsonObject {
            put("type", "text")
            put("text", systemPrompt)
            put("cache_control", buildJsonObject { put("type", "ephemeral") })
        })
    }

    private fun claudeRequest(config: AiApiKeyConfig, requestBody: String): Request =
        Request.Builder()
            .url("${config.getBaseUrl()}/v1/messages")
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

    private fun claudeChatRequestBody(
        config: AiApiKeyConfig,
        systemPrompt: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        temperature: Double,
        stream: Boolean
    ): String = buildJsonObject {
        put("model", config.modelId)
        put("max_tokens", maxTokens)
        put("temperature", temperature)
        if (stream) put("stream", true)
        put("system", claudeCachedSystem(systemPrompt))
        put("messages", buildJsonArray {
            messages.forEachIndexed { index, msg ->
                add(buildJsonObject {
                    put("role", if (msg.role == ChatRole.USER) "user" else "assistant")
                    if (index == messages.lastIndex) {
                        // 마지막 메시지에 캐시 브레이크포인트 → 다음 턴에서 대화 프리픽스 재사용
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", msg.content)
                                put("cache_control", buildJsonObject { put("type", "ephemeral") })
                            })
                        })
                    } else {
                        put("content", msg.content)
                    }
                })
            }
        })
    }.toString()

    private fun geminiChatRequestBody(
        systemPrompt: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        temperature: Double
    ): String {
        val effectiveMaxTokens = maxTokens + GEMINI_THINKING_OVERHEAD
        return buildJsonObject {
            put("contents", buildJsonArray {
                // 시스템 프롬프트를 첫 user 메시지에 포함
                for ((index, msg) in messages.withIndex()) {
                    add(buildJsonObject {
                        put("role", if (msg.role == ChatRole.USER) "user" else "model")
                        put("parts", buildJsonArray {
                            add(buildJsonObject {
                                val text = if (index == 0 && msg.role == ChatRole.USER) {
                                    "$systemPrompt\n\n${msg.content}"
                                } else {
                                    msg.content
                                }
                                put("text", text)
                            })
                        })
                    })
                }
            })
            put("generationConfig", buildJsonObject {
                put("temperature", temperature)
                put("maxOutputTokens", effectiveMaxTokens)
            })
        }.toString()
    }

    // endregion

    private fun mapHttpError(code: Int): ApiError = when (code) {
        401, 403 -> ApiError.AuthError("AI API 인증 실패 (HTTP $code)")
        429 -> ApiError.ApiCallError(429, "요청 한도 초과, 잠시 후 다시 시도해주세요")
        in 500..599 -> ApiError.NetworkError("AI API 서버 오류 (HTTP $code)")
        else -> ApiError.ApiCallError(code, "HTTP $code")
    }

    companion object {
        private const val GEMINI_THINKING_OVERHEAD = 8192
        private const val STRUCTURED_TOOL_NAME = "submit_analysis"

        /**
         * AI API 재시도 필터 — 일시 장애(5xx, network, timeout)만 재시도하고
         * 429(Rate limit)는 제외한다. 429를 재시도하면 일일 할당량(RPD)이 빠르게
         * 소진되어 이후 정상 요청까지 모두 차단되는 역효과가 발생한다.
         */
        private val AI_RETRYABLE_FILTER: (Throwable?) -> Boolean = { err ->
            val isRateLimited = err is ApiError.ApiCallError && err.code == 429
            !isRateLimited && ApiError.isRetriableError(err)
        }
    }
}
