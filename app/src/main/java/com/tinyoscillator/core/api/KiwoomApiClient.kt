package com.tinyoscillator.core.api

import android.util.Log
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
    private val tokenCache = mutableMapOf<String, TokenInfo>()
    private val tokenMutex = Mutex()

    // 레이트 리밋 (500ms 간격)
    private var lastCallTime = 0L
    private val rateLimitMutex = Mutex()

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
        val baseUrl = config.getBaseUrl()
        val result = callOnce(apiId, url, body, config, parser)

        result.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                if (isAuthError(error)) {
                    Log.w(TAG, "인증 오류, 토큰 갱신 후 재시도: ${error.message}")
                    refreshToken(config)
                    callOnce(apiId, url, body, config, parser)
                } else {
                    Result.failure(error)
                }
            }
        )
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

            Log.d(TAG, "API call: $apiId -> $url")

            val (responseBody, responseCode, isSuccessful) = httpClient.newCall(request).execute().use { response ->
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
            return Result.failure(mapException(e))
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

    private fun fetchTokenOnce(config: KiwoomApiKeyConfig): Result<TokenInfo> {
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

            val (responseBody, responseCode, isSuccessful) = httpClient.newCall(request).execute().use { response ->
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
            return Result.failure(mapException(e))
        }
    }

    private suspend fun refreshToken(config: KiwoomApiKeyConfig) = tokenMutex.withLock {
        val cacheKey = "${config.getBaseUrl()}:${config.appKey.hashCode()}"
        tokenCache.remove(cacheKey)
    }

    private suspend fun waitForRateLimit() = rateLimitMutex.withLock {
        val now = System.currentTimeMillis()
        val elapsed = now - lastCallTime
        if (elapsed < 500L) {
            delay(500L - elapsed)
        }
        lastCallTime = System.currentTimeMillis()
    }

    private fun isAuthError(error: Throwable): Boolean = when {
        error is ApiError.AuthError -> true
        error is ApiError.ApiCallError -> error.code == 401 || error.code == 403
        else -> false
    }

    private fun mapException(e: Exception): ApiError = when (e) {
        is java.net.UnknownHostException -> ApiError.NetworkError("네트워크 연결을 확인해주세요")
        is java.net.SocketTimeoutException -> ApiError.TimeoutError("요청 시간이 초과되었습니다")
        is kotlinx.serialization.SerializationException -> ApiError.ParseError("응답 파싱 오류: ${e.message}")
        is ApiError -> e
        else -> ApiError.ApiCallError(0, e.message ?: "알 수 없는 오류")
    }

    private fun normalizeJsonNumbers(json: String): String {
        var result = QUOTED_PLUS_REGEX.replace(json) { "\"${it.groupValues[1]}\"" }
        result = UNQUOTED_PLUS_REGEX.replace(result) { "${it.groupValues[1]}${it.groupValues[2]}" }
        return result
    }

    companion object {
        private const val TAG = "KiwoomApiClient"
        private val QUOTED_PLUS_REGEX = Regex("\"\\+(\\d+)\"")
        private val UNQUOTED_PLUS_REGEX = Regex("([,:])\\s*\\+(\\d+)")

        fun createDefaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        fun createDefaultJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            encodeDefaults = true
        }
    }
}
