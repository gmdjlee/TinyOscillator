package com.tinyoscillator.core.api

import com.tinyoscillator.domain.model.AiAnalysisResult
import com.tinyoscillator.domain.model.AiAnalysisType
import com.tinyoscillator.domain.model.AiApiKeyConfig
import com.tinyoscillator.domain.model.AiModelInfo
import com.tinyoscillator.domain.model.AiProvider
import com.tinyoscillator.domain.model.ChatMessage
import com.tinyoscillator.domain.model.ChatRole
import com.tinyoscillator.domain.model.ClaudeModelsResponse
import com.tinyoscillator.domain.model.ClaudeResponse
import com.tinyoscillator.domain.model.GeminiModelsResponse
import com.tinyoscillator.domain.model.GeminiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
) : BaseApiClient(rateLimitMs = RATE_LIMIT_MS) {

    private val geminiRateMutex = Mutex()
    @Volatile
    private var geminiLastCallTime = 0L

    private suspend fun waitForGeminiRateLimit() {
        val delayMs: Long
        geminiRateMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - geminiLastCallTime
            delayMs = if (elapsed < GEMINI_RATE_LIMIT_MS) GEMINI_RATE_LIMIT_MS - elapsed else 0L
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
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
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
        if (circuitBreaker.isOpen) {
            Timber.w("AI 서킷 브레이커 OPEN → 즉시 실패")
            return@withContext Result.failure(ApiError.CircuitBreakerOpenError("AI API 일시 중단 (연속 실패)"))
        }

        var lastResult = analyzeOnce(config, systemPrompt, userMessage, analysisType, maxTokens, temperature)

        // 재시도 (5xx, network, timeout — 429 제외: RPD 낭비 방지)
        val retryError = lastResult.exceptionOrNull()
        val isRateLimited = retryError is ApiError.ApiCallError && retryError.code == 429
        if (lastResult.isFailure && !isRateLimited && ApiError.isRetriableError(retryError)) {
            for (attempt in 1..MAX_RETRIES) {
                delay(RETRY_DELAYS[attempt - 1])
                Timber.d("AI API 재시도 %d/%d", attempt, MAX_RETRIES)
                lastResult = analyzeOnce(config, systemPrompt, userMessage, analysisType, maxTokens, temperature)
                if (lastResult.isSuccess || !ApiError.isRetriableError(lastResult.exceptionOrNull())) break
            }
        }

        updateCircuitBreaker(lastResult)

        lastResult
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
            put("system", systemPrompt)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userMessage)
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("${config.getBaseUrl()}/v1/messages")
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

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
                content = text,
                inputTokens = claudeResponse.usage.inputTokens,
                outputTokens = claudeResponse.usage.outputTokens
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
        if (circuitBreaker.isOpen) {
            return@withContext Result.failure(ApiError.CircuitBreakerOpenError("AI API 일시 중단 (연속 실패)"))
        }

        var lastResult = chatOnce(config, systemPrompt, messages, maxTokens, temperature)

        val retryError = lastResult.exceptionOrNull()
        val isRateLimited = retryError is ApiError.ApiCallError && retryError.code == 429
        if (lastResult.isFailure && !isRateLimited && ApiError.isRetriableError(retryError)) {
            for (attempt in 1..MAX_RETRIES) {
                delay(RETRY_DELAYS[attempt - 1])
                Timber.d("AI Chat 재시도 %d/%d", attempt, MAX_RETRIES)
                lastResult = chatOnce(config, systemPrompt, messages, maxTokens, temperature)
                if (lastResult.isSuccess || !ApiError.isRetriableError(lastResult.exceptionOrNull())) break
            }
        }

        updateCircuitBreaker(lastResult)
        lastResult
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
        val requestBody = buildJsonObject {
            put("model", config.modelId)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            put("system", systemPrompt)
            put("messages", buildJsonArray {
                for (msg in messages) {
                    add(buildJsonObject {
                        put("role", if (msg.role == ChatRole.USER) "user" else "assistant")
                        put("content", msg.content)
                    })
                }
            })
        }.toString()

        val request = Request.Builder()
            .url("${config.getBaseUrl()}/v1/messages")
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

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
        val effectiveMaxTokens = maxTokens + GEMINI_THINKING_OVERHEAD

        val requestBody = buildJsonObject {
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

    private fun mapHttpError(code: Int): ApiError = when (code) {
        401, 403 -> ApiError.AuthError("AI API 인증 실패 (HTTP $code)")
        429 -> ApiError.ApiCallError(429, "요청 한도 초과, 잠시 후 다시 시도해주세요")
        in 500..599 -> ApiError.NetworkError("AI API 서버 오류 (HTTP $code)")
        else -> ApiError.ApiCallError(code, "HTTP $code")
    }

    companion object {
        private const val MAX_RETRIES = 2
        private const val RATE_LIMIT_MS = 1000L
        private const val GEMINI_RATE_LIMIT_MS = 12_000L
        private const val GEMINI_THINKING_OVERHEAD = 8192
        private val RETRY_DELAYS = listOf(2000L, 4000L)
    }
}
