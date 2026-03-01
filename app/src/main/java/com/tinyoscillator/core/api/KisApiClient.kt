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
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException

/**
 * KIS (한국투자증권) REST API 클라이언트.
 *
 * 재무제표 등 KIS 전용 데이터 조회에 사용.
 */
class KisApiClient(
    private val httpClient: OkHttpClient = KiwoomApiClient.createDefaultClient(),
    private val json: Json = KiwoomApiClient.createDefaultJson()
) {
    // 토큰 캐시
    private val tokenCache = mutableMapOf<String, TokenInfo>()
    private val tokenMutex = Mutex()

    // 레이트 리밋
    private var lastCallTime = 0L
    private val rateLimitMutex = Mutex()

    /**
     * KIS API GET 요청.
     */
    suspend fun <T> get(
        trId: String,
        url: String,
        queryParams: Map<String, String>,
        config: KisApiKeyConfig,
        parser: (String) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        val result = getOnce(trId, url, queryParams, config, parser)

        result.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                if (isAuthError(error)) {
                    Log.w(TAG, "KIS 인증 오류, 토큰 갱신 후 재시도: ${error.message}")
                    refreshToken(config)
                    getOnce(trId, url, queryParams, config, parser)
                } else {
                    Result.failure(error)
                }
            }
        )
    }

    private suspend fun <T> getOnce(
        trId: String,
        url: String,
        queryParams: Map<String, String>,
        config: KisApiKeyConfig,
        parser: (String) -> T
    ): Result<T> {
        try {
            waitForRateLimit()

            val token = getToken(config).getOrElse { return Result.failure(it) }

            val urlBuilder = StringBuilder("${config.getBaseUrl()}$url")
            if (queryParams.isNotEmpty()) {
                urlBuilder.append("?")
                urlBuilder.append(queryParams.entries.joinToString("&") {
                    "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
                })
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .addHeader("authorization", token.bearer)
                .addHeader("appkey", config.appKey)
                .addHeader("appsecret", config.appSecret)
                .addHeader("tr_id", trId)
                .addHeader("Content-Type", "application/json;charset=UTF-8")
                .get()
                .build()

            Log.d(TAG, "KIS API call: $trId -> $url")

            val (responseBody, responseCode, isSuccessful) = httpClient.newCall(request).execute().use { response ->
                Triple(response.body?.string(), response.code, response.isSuccessful)
            }

            if (!isSuccessful || responseBody == null) {
                return Result.failure(ApiError.ApiCallError(responseCode, "HTTP $responseCode"))
            }

            return Result.success(parser(responseBody))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(mapException(e))
        }
    }

    private suspend fun getToken(config: KisApiKeyConfig): Result<TokenInfo> = tokenMutex.withLock {
        val cacheKey = "${config.getBaseUrl()}:${config.appKey.hashCode()}"
        val cached = tokenCache[cacheKey]
        if (cached != null && !cached.isExpired()) {
            return@withLock Result.success(cached)
        }
        return@withLock fetchToken(config).also { result ->
            result.onSuccess { tokenCache[cacheKey] = it }
        }
    }

    private suspend fun fetchToken(config: KisApiKeyConfig): Result<TokenInfo> {
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
        return Result.failure(lastError ?: ApiError.AuthError("KIS 토큰 발급 실패"))
    }

    private fun fetchTokenOnce(config: KisApiKeyConfig): Result<TokenInfo> {
        try {
            val requestBody = json.encodeToString(mapOf(
                "grant_type" to "client_credentials",
                "appkey" to config.appKey,
                "appsecret" to config.appSecret
            ))

            val request = Request.Builder()
                .url("${config.getBaseUrl()}/oauth2/tokenP")
                .addHeader("Content-Type", "application/json;charset=UTF-8")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val (responseBody, responseCode, isSuccessful) = httpClient.newCall(request).execute().use { response ->
                Triple(response.body?.string(), response.code, response.isSuccessful)
            }

            if (!isSuccessful || responseBody == null) {
                return Result.failure(ApiError.AuthError("KIS 토큰 발급 실패: HTTP $responseCode"))
            }

            val tokenResponse = json.decodeFromString<KisTokenResponse>(responseBody)
            val accessToken = tokenResponse.access_token
                ?: return Result.failure(ApiError.AuthError("KIS access_token is null"))

            val expiresAtMillis = try {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                val expiresAt = LocalDateTime.parse(tokenResponse.access_token_token_expired, formatter)
                expiresAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (e: Exception) {
                System.currentTimeMillis() + 24 * 60 * 60 * 1000L // 기본 24시간
            }

            return Result.success(
                TokenInfo(accessToken, expiresAtMillis, tokenResponse.token_type ?: "Bearer")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(mapException(e))
        }
    }

    private suspend fun refreshToken(config: KisApiKeyConfig) = tokenMutex.withLock {
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

    companion object {
        private const val TAG = "KisApiClient"
    }
}
