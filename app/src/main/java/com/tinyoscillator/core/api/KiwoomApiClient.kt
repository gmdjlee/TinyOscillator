package com.tinyoscillator.core.api

import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import okhttp3.CertificatePinner
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Kiwoom REST API 클라이언트.
 *
 * 토큰 관리, 레이트 리밋, JSON 정규화를 포함한 직접 API 호출.
 */
class KiwoomApiClient(
    private val httpClient: OkHttpClient = createDefaultClient(),
    private val json: Json = createDefaultJson()
) {
    // 토큰 캐시 (baseUrl + appKey 조합)
    private val tokenCache = ConcurrentHashMap<String, TokenInfo>()
    private val tokenMutex = Mutex()

    // 레이트 리밋 (500ms 간격)
    @Volatile
    private var lastCallTime = 0L
    private val rateLimitMutex = Mutex()

    // 서킷 브레이커: 연속 3회 실패 시 5분간 즉시 실패 반환
    private val circuitBreaker = CircuitBreaker()

    /**
     * Kiwoom API 호출.
     * 401/403 오류 시 토큰 갱신 후 자동 재시도.
     */
    suspend fun <T> call(
        apiId: String,
        url: String,
        body: Map<String, String>,
        config: KiwoomApiKeyConfig,
        parser: (String) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        // Circuit breaker: skip API call if circuit is open
        if (circuitBreaker.isOpen) {
            Timber.w("서킷 브레이커 OPEN → 즉시 실패: %s", apiId)
            return@withContext Result.failure(ApiError.NetworkError("API 일시 중단 (연속 실패)"))
        }

        var lastResult = callOnce(apiId, url, body, config, parser)

        // Auth retry: refresh token on 401/403
        lastResult.onFailure { error ->
            if (ApiError.isAuthError(error)) {
                Timber.w("인증 오류, 토큰 갱신 후 재시도")
                refreshToken(config)
                lastResult = callOnce(apiId, url, body, config, parser)
            }
        }

        // Retry on retriable errors (network, timeout)
        if (lastResult.isFailure && ApiError.isRetriableError(lastResult.exceptionOrNull())) {
            for (attempt in 1..MAX_RETRIES) {
                delay(RETRY_DELAYS[attempt - 1])
                Timber.d("재시도 %d/%d: %s", attempt, MAX_RETRIES, apiId)
                lastResult = callOnce(apiId, url, body, config, parser)
                if (lastResult.isSuccess || !ApiError.isRetriableError(lastResult.exceptionOrNull())) break
            }
        }

        // Update circuit breaker state
        if (lastResult.isSuccess) {
            circuitBreaker.recordSuccess()
        } else {
            circuitBreaker.recordFailure()
        }

        lastResult
    }

    private suspend fun <T> callOnce(
        apiId: String,
        url: String,
        body: Map<String, String>,
        config: KiwoomApiKeyConfig,
        parser: (String) -> T
    ): Result<T> {
        try {
            waitForRateLimit()

            val token = getToken(config).getOrElse { return Result.failure(it) }
            val baseUrl = config.getBaseUrl()
            val requestBodyJson = json.encodeToString(body)

            val request = Request.Builder()
                .url("$baseUrl$url")
                .addHeader("api-id", apiId)
                .addHeader("authorization", token.bearer)
                .addHeader("Content-Type", "application/json;charset=UTF-8")
                .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            Timber.d("API call: $apiId -> $url")

            val (responseBody, responseCode, isSuccessful) = httpClient.newCall(request).await().use { response ->
                Triple(response.body?.string(), response.code, response.isSuccessful)
            }

            if (!isSuccessful || responseBody == null) {
                return Result.failure(ApiError.ApiCallError(responseCode, "HTTP $responseCode"))
            }

            val normalizedBody = normalizeJsonNumbers(responseBody)
            val apiResponse = json.decodeFromString<ApiResponse>(normalizedBody)
            if (apiResponse.returnCode != 0) {
                return Result.failure(
                    ApiError.ApiCallError(apiResponse.returnCode, apiResponse.returnMsg ?: "API 오류")
                )
            }

            return Result.success(parser(normalizedBody))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(ApiError.mapException(e))
        }
    }

    private suspend fun getToken(config: KiwoomApiKeyConfig): Result<TokenInfo> = tokenMutex.withLock {
        val cacheKey = "${config.getBaseUrl()}:${config.appKey.hashCode()}"
        val cached = tokenCache[cacheKey]
        if (cached != null && !cached.isExpired()) {
            return@withLock Result.success(cached)
        }
        return@withLock fetchToken(config).also { result ->
            result.onSuccess { tokenCache[cacheKey] = it }
        }
    }

    private suspend fun fetchToken(config: KiwoomApiKeyConfig): Result<TokenInfo> {
        var lastError: Exception? = null
        for (attempt in 0..2) {
            val result = fetchTokenOnce(config)
            if (result.isSuccess) return result
            lastError = result.exceptionOrNull() as? Exception
            val isRetriable = lastError is ApiError.NetworkError || lastError is ApiError.TimeoutError
            if (isRetriable && attempt < 2) {
                delay(listOf(1000L, 2000L, 4000L)[attempt])
            } else break
        }
        return Result.failure(lastError ?: ApiError.AuthError("토큰 발급 실패"))
    }

    private suspend fun fetchTokenOnce(config: KiwoomApiKeyConfig): Result<TokenInfo> {
        try {
            val requestBody = json.encodeToString(mapOf(
                "grant_type" to "client_credentials",
                "appkey" to config.appKey,
                "secretkey" to config.secretKey
            ))

            val request = Request.Builder()
                .url("${config.getBaseUrl()}/oauth2/token")
                .addHeader("api-id", "au10001")
                .addHeader("Content-Type", "application/json;charset=UTF-8")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val (responseBody, responseCode, isSuccessful) = httpClient.newCall(request).await().use { response ->
                Triple(response.body?.string(), response.code, response.isSuccessful)
            }

            if (!isSuccessful || responseBody == null) {
                return Result.failure(ApiError.AuthError("토큰 발급 실패: HTTP $responseCode"))
            }

            val tokenResponse = json.decodeFromString<TokenResponse>(responseBody)
            if (tokenResponse.returnCode != 0) {
                return Result.failure(ApiError.AuthError(tokenResponse.returnMsg ?: "토큰 발급 실패"))
            }

            val token = tokenResponse.token ?: return Result.failure(ApiError.AuthError("토큰이 null입니다"))
            val expiresDt = tokenResponse.expiresDt ?: return Result.failure(ApiError.AuthError("만료일이 null입니다"))

            val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            val expiresAt = LocalDateTime.parse(expiresDt, formatter)
            val expiresAtMillis = expiresAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

            return Result.success(TokenInfo(token, expiresAtMillis, tokenResponse.tokenType ?: "bearer"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(ApiError.mapException(e))
        }
    }

    private suspend fun refreshToken(config: KiwoomApiKeyConfig) = tokenMutex.withLock {
        val cacheKey = "${config.getBaseUrl()}:${config.appKey.hashCode()}"
        tokenCache.remove(cacheKey)
    }

    private suspend fun waitForRateLimit() {
        val delayMs: Long
        rateLimitMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastCallTime
            delayMs = if (elapsed < RATE_LIMIT_MS) RATE_LIMIT_MS - elapsed else 0L
            lastCallTime = now + delayMs  // Reserve this time slot
        }
        if (delayMs > 0L) delay(delayMs)
    }

    /**
     * Remove leading '+' from numeric values in JSON responses.
     * Single-pass character scanner (no regex allocation per call).
     *
     * Handles: "+1234" → "1234" and : +1234 → : 1234
     */
    private fun normalizeJsonNumbers(jsonStr: String): String {
        val len = jsonStr.length
        if (len == 0) return jsonStr

        val sb = StringBuilder(len)
        var i = 0
        while (i < len) {
            val c = jsonStr[i]
            if (c == '+' && i + 1 < len && jsonStr[i + 1].isDigit()) {
                // Check context: is '+' inside quotes or after separator?
                if (i > 0) {
                    val prev = jsonStr[i - 1]
                    if (prev == '"' || prev == ':' || prev == ',' || prev == ' ') {
                        // Skip the '+', digit will be appended next iteration
                        i++
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    companion object {
        private const val MAX_RETRIES = 2
        private const val RATE_LIMIT_MS = 500L
        private val RETRY_DELAYS = listOf(1000L, 2000L)

        fun createDefaultClient(enablePinning: Boolean = !com.tinyoscillator.BuildConfig.DEBUG): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)

            if (enablePinning) {
                builder.certificatePinner(createCertificatePinner())
            }

            return builder.build()
        }

        /** Pin intermediate CA certificates for KIS/Kiwoom APIs. */
        fun createCertificatePinner(): CertificatePinner = CertificatePinner.Builder()
            // Kiwoom API - Sectigo RSA EV Secure Server CA (intermediate) + USERTrust RSA (root backup)
            .add("*.kiwoom.com",
                "sha256/hEJ5FNYP7ZpNILySZtJgiP6UtW3ClYUTRFxXGqWSWQ0=",
                "sha256/x4QzPSC810K5/cMjb05Qm4k3Bw5zBn4lTdO/nEW/Td4="
            )
            // KIS API - Sectigo RSA OV Secure Server CA (intermediate) + USERTrust RSA (root backup)
            .add("*.koreainvestment.com",
                "sha256/RkhWTcfJAQN/YxOR12VkPo+PhmIoSfWd/JVkg44einY=",
                "sha256/x4QzPSC810K5/cMjb05Qm4k3Bw5zBn4lTdO/nEW/Td4="
            )
            .build()

        fun createDefaultJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            encodeDefaults = true
        }
    }
}
