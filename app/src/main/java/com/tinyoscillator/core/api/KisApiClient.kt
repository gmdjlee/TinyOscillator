package com.tinyoscillator.core.api

import com.tinyoscillator.core.config.ApiConstants
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
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * KIS (한국투자증권) REST API 클라이언트.
 *
 * 재무제표 등 KIS 전용 데이터 조회에 사용.
 */
class KisApiClient(
    private val httpClient: OkHttpClient = KiwoomApiClient.createDefaultClient(),
    private val json: Json = KiwoomApiClient.createDefaultJson()
) : BaseApiClient(rateLimitMs = ApiConstants.KIS_RATE_LIMIT_MS) {
    // 토큰 캐시
    private val tokenCache = ConcurrentHashMap<String, TokenInfo>()
    private val tokenMutex = Mutex()

    // 토큰 엔드포인트 전용 rate limiter (KIS: 1분당 1회 제한)
    // tokenRateMutex 내부에서만 접근하므로 @Volatile 불필요 (mutex가 happens-before 보장).
    private val tokenRateMutex = Mutex()
    private var nextTokenAvailableAt = 0L

    /**
     * KIS API GET 요청. 401/403 오류 시 토큰 갱신 후 자동 재시도. 재시도 정책은
     * [executeWithRetry] 참조.
     */
    suspend fun <T> get(
        trId: String,
        url: String,
        queryParams: Map<String, String>,
        config: KisApiKeyConfig,
        parser: (String) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        executeWithRetry(
            tag = "KIS $trId",
            onAuthFailure = { refreshToken(config) },
        ) {
            getOnce(trId, url, queryParams, config, parser)
        }
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

            Timber.d("KIS API call: $trId -> $url")

            val (responseBody, responseCode, isSuccessful) = httpClient.newCall(request).await().use { response ->
                Triple(response.body?.string(), response.code, response.isSuccessful)
            }

            if (!isSuccessful || responseBody == null) {
                return Result.failure(ApiError.ApiCallError(responseCode, "HTTP $responseCode"))
            }

            return Result.success(parser(responseBody))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(ApiError.mapException(e))
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
            val isRateLimit = lastError is ApiError.ApiCallError &&
                (lastError as ApiError.ApiCallError).code == 429
            val isRetriable = lastError is ApiError.NetworkError ||
                lastError is ApiError.TimeoutError || isRateLimit
            if (isRetriable && attempt < 2) {
                val delayMs = if (isRateLimit) {
                    TOKEN_RATE_LIMIT_RETRY_DELAYS[attempt]
                } else {
                    listOf(1000L, 2000L, 4000L)[attempt]
                }
                delay(delayMs)
            } else break
        }
        return Result.failure(lastError ?: ApiError.AuthError("KIS 토큰 발급 실패"))
    }

    /**
     * 토큰 엔드포인트 호출 직렬화 (slot reservation).
     *
     * 여러 코루틴이 동시에 진입해도 mutex 내부에서 각 호출에 대해 고유한 "다음 사용 가능 시각"을
     * 계산·예약하므로, 각 호출은 서로 ApiConstants.KIS_TOKEN_MIN_INTERVAL_MS 이상 간격으로 직렬화된다.
     *
     * - nextTokenAvailableAt: 다음 토큰 호출이 발사될 수 있는 절대 타임스탬프(ms).
     * - 첫 호출: now가 예약값보다 크므로 즉시(delayMs=0) 발사, 다음 예약을 now + interval로 설정.
     * - 연속 호출: max(now, 기존 예약)을 기준으로 새 예약을 그 뒤로 미뤄 interval을 보장.
     */
    private suspend fun waitForTokenRateLimit() {
        val delayMs: Long
        tokenRateMutex.withLock {
            val now = System.currentTimeMillis()
            val scheduledAt = maxOf(now, nextTokenAvailableAt)
            delayMs = scheduledAt - now
            nextTokenAvailableAt = scheduledAt + ApiConstants.KIS_TOKEN_MIN_INTERVAL_MS
        }
        if (delayMs > 0L) {
            Timber.d("KIS 토큰 레이트 리밋 대기: %dms", delayMs)
            delay(delayMs)
        }
    }

    private suspend fun fetchTokenOnce(config: KisApiKeyConfig): Result<TokenInfo> {
        try {
            waitForTokenRateLimit()

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

            val (responseBody, responseCode, isSuccessful) = httpClient.newCall(request).await().use { response ->
                Triple(response.body?.string(), response.code, response.isSuccessful)
            }

            if (!isSuccessful || responseBody == null) {
                return Result.failure(
                    when (responseCode) {
                        429 -> ApiError.ApiCallError(429, "KIS 토큰 발급 실패: 요청 한도 초과")
                        in 500..599 -> ApiError.NetworkError("KIS 토큰 발급 실패: 서버 오류 (HTTP $responseCode)")
                        else -> ApiError.AuthError("KIS 토큰 발급 실패: HTTP $responseCode")
                    }
                )
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
            return Result.failure(ApiError.mapException(e))
        }
    }

    private suspend fun refreshToken(config: KisApiKeyConfig) = tokenMutex.withLock {
        val cacheKey = "${config.getBaseUrl()}:${config.appKey.hashCode()}"
        tokenCache.remove(cacheKey)
    }

    /**
     * API 키 유효성 검증 — 토큰 발급을 시도하여 키가 유효한지 확인한다.
     * 기존 토큰 캐시나 서킷 브레이커에 영향을 주지 않는다.
     */
    suspend fun validateCredentials(config: KisApiKeyConfig): Result<Unit> = withContext(Dispatchers.IO) {
        if (!config.isValid()) {
            return@withContext Result.failure(ApiError.NoApiKeyError())
        }
        fetchTokenOnce(config).map { }
    }

    companion object {
        private val TOKEN_RATE_LIMIT_RETRY_DELAYS = listOf(61_000L, 62_000L, 64_000L)
    }

}
