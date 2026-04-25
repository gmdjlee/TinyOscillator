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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import okhttp3.CertificatePinner
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Kiwoom REST API 페이지네이션 헤더.
 *
 * `cont-yn`/`next-key`는 ka90001/ka90002 등 일부 TR이 1회 응답에 다 담지 못할 때 사용.
 * `apiId`는 응답 헤더의 `api-id`(TR 식별자) — 디버그/로깅용.
 *
 * @property contYn "Y"이면 다음 페이지가 존재. "N"이면 종료.
 * @property nextKey 다음 페이지 요청 시 헤더로 그대로 echo back. 빈 문자열이면 첫 페이지.
 * @property apiId 응답 헤더의 api-id 값.
 */
data class PageHeaders(
    val contYn: String,
    val nextKey: String,
    val apiId: String
)

/**
 * Kiwoom REST API 클라이언트.
 *
 * 토큰 관리, 레이트 리밋, JSON 정규화를 포함한 직접 API 호출.
 */
class KiwoomApiClient(
    private val httpClient: OkHttpClient = createDefaultClient(),
    private val json: Json = createDefaultJson()
) : BaseApiClient(rateLimitMs = ApiConstants.KIWOOM_RATE_LIMIT_MS) {
    // 토큰 캐시 (baseUrl + appKey 조합)
    private val tokenCache = ConcurrentHashMap<String, TokenInfo>()
    private val tokenMutex = Mutex()

    // 토큰 엔드포인트 전용 rate limiter
    // tokenRateMutex 내부에서만 접근하므로 @Volatile 불필요 (mutex가 happens-before 보장).
    private val tokenRateMutex = Mutex()
    private var nextTokenAvailableAt = 0L

    /**
     * Kiwoom API 호출.
     * 401/403 오류 시 토큰 갱신 후 자동 재시도. 재시도 정책은 [executeWithRetry] 참조.
     */
    suspend fun <T> call(
        apiId: String,
        url: String,
        body: Map<String, String>,
        config: KiwoomApiKeyConfig,
        parser: (String) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        executeWithRetry(
            tag = apiId,
            onAuthFailure = { refreshToken(config) },
        ) {
            callOnce(apiId, url, body, config, parser)
        }
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

    /**
     * 응답 헤더(`cont-yn`/`next-key` 등)에 의존하는 페이지네이션 TR 호출.
     *
     * 기존 [call]과 동일한 토큰/레이트 리밋/재시도 경로를 사용하되,
     *  1. [extraHeaders]를 요청 헤더에 병합 (페이지 진행 시 `cont-yn=Y`, `next-key=<echo>` 전달용)
     *  2. 응답 헤더에서 `cont-yn`/`next-key`/`api-id`를 추출해 [parser]에 [PageHeaders]로 전달
     *
     * OkHttp `Response.header()`는 case-insensitive이므로 헤더 케이스 차이는 안전하게 흡수된다.
     */
    suspend fun <T> callWithHeaders(
        apiId: String,
        url: String,
        body: Map<String, String>,
        config: KiwoomApiKeyConfig,
        extraHeaders: Map<String, String> = emptyMap(),
        parser: (body: String, headers: PageHeaders) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        executeWithRetry(
            tag = apiId,
            onAuthFailure = { refreshToken(config) },
        ) {
            callOnceWithHeaders(apiId, url, body, config, extraHeaders, parser)
        }
    }

    private suspend fun <T> callOnceWithHeaders(
        apiId: String,
        url: String,
        body: Map<String, String>,
        config: KiwoomApiKeyConfig,
        extraHeaders: Map<String, String>,
        parser: (body: String, headers: PageHeaders) -> T
    ): Result<T> {
        try {
            waitForRateLimit()

            val token = getToken(config).getOrElse { return Result.failure(it) }
            val baseUrl = config.getBaseUrl()
            val requestBodyJson = json.encodeToString(body)

            val requestBuilder = Request.Builder()
                .url("$baseUrl$url")
                .addHeader("api-id", apiId)
                .addHeader("authorization", token.bearer)
                .addHeader("Content-Type", "application/json;charset=UTF-8")
            extraHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val request = requestBuilder
                .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            Timber.d("API call (paged): $apiId -> $url (extraHeaders=$extraHeaders)")

            data class ResponseSnapshot(
                val body: String?,
                val code: Int,
                val successful: Boolean,
                val contYn: String,
                val nextKey: String,
                val apiIdHeader: String
            )

            val snapshot = httpClient.newCall(request).await().use { response ->
                ResponseSnapshot(
                    body = response.body?.string(),
                    code = response.code,
                    successful = response.isSuccessful,
                    contYn = response.header("cont-yn") ?: "N",
                    nextKey = response.header("next-key") ?: "",
                    apiIdHeader = response.header("api-id") ?: apiId
                )
            }

            if (!snapshot.successful || snapshot.body == null) {
                return Result.failure(ApiError.ApiCallError(snapshot.code, "HTTP ${snapshot.code}"))
            }

            val normalizedBody = normalizeJsonNumbers(snapshot.body)
            val apiResponse = json.decodeFromString<ApiResponse>(normalizedBody)
            if (apiResponse.returnCode != 0) {
                return Result.failure(
                    ApiError.ApiCallError(apiResponse.returnCode, apiResponse.returnMsg ?: "API 오류")
                )
            }

            val pageHeaders = PageHeaders(
                contYn = snapshot.contYn,
                nextKey = snapshot.nextKey,
                apiId = snapshot.apiIdHeader
            )
            return Result.success(parser(normalizedBody, pageHeaders))
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
        return Result.failure(lastError ?: ApiError.AuthError("토큰 발급 실패"))
    }

    /**
     * 토큰 엔드포인트 호출 직렬화 (slot reservation).
     *
     * 여러 코루틴이 동시에 진입해도 mutex 내부에서 각 호출에 대해 고유한 "다음 사용 가능 시각"을
     * 계산·예약하므로, 각 호출은 서로 ApiConstants.KIWOOM_TOKEN_MIN_INTERVAL_MS 이상 간격으로 직렬화된다.
     */
    private suspend fun waitForTokenRateLimit() {
        val delayMs: Long
        tokenRateMutex.withLock {
            val now = System.currentTimeMillis()
            val scheduledAt = maxOf(now, nextTokenAvailableAt)
            delayMs = scheduledAt - now
            nextTokenAvailableAt = scheduledAt + ApiConstants.KIWOOM_TOKEN_MIN_INTERVAL_MS
        }
        if (delayMs > 0L) {
            Timber.d("토큰 레이트 리밋 대기: %dms", delayMs)
            delay(delayMs)
        }
    }

    private suspend fun fetchTokenOnce(config: KiwoomApiKeyConfig): Result<TokenInfo> {
        try {
            waitForTokenRateLimit()

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
                return Result.failure(
                    when (responseCode) {
                        429 -> ApiError.ApiCallError(429, "토큰 발급 실패: 요청 한도 초과")
                        in 500..599 -> ApiError.NetworkError("토큰 발급 실패: 서버 오류 (HTTP $responseCode)")
                        else -> ApiError.AuthError("토큰 발급 실패: HTTP $responseCode")
                    }
                )
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

    /**
     * API 키 유효성 검증 — 토큰 발급을 시도하여 키가 유효한지 확인한다.
     * 기존 토큰 캐시나 서킷 브레이커에 영향을 주지 않는다.
     */
    suspend fun validateCredentials(config: KiwoomApiKeyConfig): Result<Unit> = withContext(Dispatchers.IO) {
        if (!config.isValid()) {
            return@withContext Result.failure(ApiError.NoApiKeyError())
        }
        fetchTokenOnce(config).map { }
    }

    companion object {
        private val TOKEN_RATE_LIMIT_RETRY_DELAYS = listOf(11_000L, 12_000L, 14_000L)

        /**
         * 공용 OkHttpClient 생성 (KIS/Kiwoom/DART/BOK ECOS/AI 모두 재사용).
         *
         * 기본값 `enablePinning = !BuildConfig.DEBUG`는 릴리스 빌드에서만 OkHttp 레벨 pinning을 활성화한다.
         * 디버그 빌드에서 비활성화하는 이유는 MockWebServer 기반 통합 테스트가 self-signed 인증서를
         * 쓰기 때문이다. 그러나 **플랫폼 레벨 pinning은 별도로 유지된다** — `res/xml/network_security_config.xml`에
         * Kiwoom/KIS 도메인에 대한 SHA-256 pin이 빌드 타입 무관하게 강제된다.
         * 즉, 디버그 빌드에서 실제 Kiwoom/KIS endpoint를 호출해도 OS 레이어에서 pin 검증이 수행된다.
         */
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
            // Kiwoom API - Sectigo Public Server Authentication CA EV R36 (intermediate, 2026-03 renewal)
            //            + Sectigo Public Server Authentication Root R46 (cross-signed)
            //            + USERTrust RSA (root backup)
            .add("*.kiwoom.com",
                "sha256/0RfXDfccNqREsDzRXraedr+pPfaWA8fMplHCU30xYlo=",
                "sha256/Douxi77vs4G+Ib/BogbTFymEYq0QSFXwSgVCaZcI09Q=",
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
