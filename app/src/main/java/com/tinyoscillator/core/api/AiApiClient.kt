package com.tinyoscillator.core.api

import com.tinyoscillator.domain.model.AiAnalysisResult
import com.tinyoscillator.domain.model.AiAnalysisType
import com.tinyoscillator.domain.model.AiApiKeyConfig
import com.tinyoscillator.domain.model.AiProvider
import com.tinyoscillator.domain.model.ClaudeResponse
import com.tinyoscillator.domain.model.GeminiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
            return@withContext Result.failure(ApiError.NetworkError("AI API 일시 중단 (연속 실패)"))
        }

        var lastResult = analyzeOnce(config, systemPrompt, userMessage, analysisType, maxTokens, temperature)

        // 재시도 (429, 5xx, network, timeout)
        if (lastResult.isFailure && ApiError.isRetriableError(lastResult.exceptionOrNull())) {
            for (attempt in 1..MAX_RETRIES) {
                delay(RETRY_DELAYS[attempt - 1])
                Timber.d("AI API 재시도 %d/%d", attempt, MAX_RETRIES)
                lastResult = analyzeOnce(config, systemPrompt, userMessage, analysisType, maxTokens, temperature)
                if (lastResult.isSuccess || !ApiError.isRetriableError(lastResult.exceptionOrNull())) break
            }
        }

        updateCircuitBreaker(lastResult.isSuccess)

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
            waitForRateLimit()

            return when (config.provider) {
                AiProvider.CLAUDE_HAIKU, AiProvider.CLAUDE_SONNET ->
                    callClaude(config, systemPrompt, userMessage, analysisType, maxTokens, temperature)
                AiProvider.GEMINI_FLASH ->
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
            put("model", config.provider.modelId)
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

        Timber.d("Claude API call: %s", config.provider.modelId)

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
                put("maxOutputTokens", maxTokens)
            })
        }.toString()

        val url = "${config.getBaseUrl()}/v1beta/models/${config.provider.modelId}:generateContent"

        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", config.apiKey)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        Timber.d("Gemini API call: %s", config.provider.modelId)

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

    private fun mapHttpError(code: Int): ApiError = when (code) {
        401, 403 -> ApiError.AuthError("AI API 인증 실패 (HTTP $code)")
        429 -> ApiError.ApiCallError(429, "요청 한도 초과, 잠시 후 다시 시도해주세요")
        in 500..599 -> ApiError.NetworkError("AI API 서버 오류 (HTTP $code)")
        else -> ApiError.ApiCallError(code, "HTTP $code")
    }

    companion object {
        private const val MAX_RETRIES = 2
        private const val RATE_LIMIT_MS = 1000L
        private val RETRY_DELAYS = listOf(2000L, 4000L)
    }
}
