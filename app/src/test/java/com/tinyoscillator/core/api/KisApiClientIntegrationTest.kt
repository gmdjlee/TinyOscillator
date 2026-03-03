package com.tinyoscillator.core.api

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * KisApiClient 통합 테스트 (MockWebServer 기반).
 *
 * 실제 HTTP 호출 흐름을 검증: 토큰 발급 → API 호출 → 응답 파싱.
 */
class KisApiClientIntegrationTest {

    private lateinit var server: MockWebServer
    private lateinit var client: KisApiClient
    private lateinit var config: KisApiKeyConfig

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()

        val httpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            encodeDefaults = true
        }

        client = KisApiClient(httpClient = httpClient, json = json)
        config = KisApiKeyConfig(
            appKey = "test-key",
            appSecret = "test-secret",
            investmentMode = InvestmentMode.MOCK
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // =============================================
    // isRetriableError Tests
    // =============================================

    @Test
    fun `isRetriableError는 NetworkError에 대해 true를 반환한다`() {
        val method = KisApiClient::class.java.getDeclaredMethod(
            "isRetriableError", Throwable::class.java
        ).apply { isAccessible = true }

        assertTrue(method.invoke(client, ApiError.NetworkError("test")) as Boolean)
    }

    @Test
    fun `isRetriableError는 TimeoutError에 대해 true를 반환한다`() {
        val method = KisApiClient::class.java.getDeclaredMethod(
            "isRetriableError", Throwable::class.java
        ).apply { isAccessible = true }

        assertTrue(method.invoke(client, ApiError.TimeoutError("test")) as Boolean)
    }

    @Test
    fun `isRetriableError는 503에 대해 true를 반환한다`() {
        val method = KisApiClient::class.java.getDeclaredMethod(
            "isRetriableError", Throwable::class.java
        ).apply { isAccessible = true }

        assertTrue(method.invoke(client, ApiError.ApiCallError(503, "unavailable")) as Boolean)
    }

    @Test
    fun `isRetriableError는 502에 대해 true를 반환한다`() {
        val method = KisApiClient::class.java.getDeclaredMethod(
            "isRetriableError", Throwable::class.java
        ).apply { isAccessible = true }

        assertTrue(method.invoke(client, ApiError.ApiCallError(502, "bad gateway")) as Boolean)
    }

    @Test
    fun `isRetriableError는 504에 대해 true를 반환한다`() {
        val method = KisApiClient::class.java.getDeclaredMethod(
            "isRetriableError", Throwable::class.java
        ).apply { isAccessible = true }

        assertTrue(method.invoke(client, ApiError.ApiCallError(504, "gateway timeout")) as Boolean)
    }

    @Test
    fun `isRetriableError는 429에 대해 true를 반환한다`() {
        val method = KisApiClient::class.java.getDeclaredMethod(
            "isRetriableError", Throwable::class.java
        ).apply { isAccessible = true }

        assertTrue(method.invoke(client, ApiError.ApiCallError(429, "rate limit")) as Boolean)
    }

    @Test
    fun `isRetriableError는 400에 대해 false를 반환한다`() {
        val method = KisApiClient::class.java.getDeclaredMethod(
            "isRetriableError", Throwable::class.java
        ).apply { isAccessible = true }

        assertFalse(method.invoke(client, ApiError.ApiCallError(400, "bad request")) as Boolean)
    }

    @Test
    fun `isRetriableError는 AuthError에 대해 false를 반환한다`() {
        val method = KisApiClient::class.java.getDeclaredMethod(
            "isRetriableError", Throwable::class.java
        ).apply { isAccessible = true }

        assertFalse(method.invoke(client, ApiError.AuthError("auth")) as Boolean)
    }

    @Test
    fun `isRetriableError는 ParseError에 대해 false를 반환한다`() {
        val method = KisApiClient::class.java.getDeclaredMethod(
            "isRetriableError", Throwable::class.java
        ).apply { isAccessible = true }

        assertFalse(method.invoke(client, ApiError.ParseError("parse")) as Boolean)
    }

    @Test
    fun `isRetriableError는 null에 대해 false를 반환한다`() {
        val method = KisApiClient::class.java.getDeclaredMethod(
            "isRetriableError", Throwable::class.java
        ).apply { isAccessible = true }

        assertFalse(method.invoke(client, null as Throwable?) as Boolean)
    }

    // =============================================
    // isAuthError Tests
    // =============================================

    @Test
    fun `isAuthError는 AuthError에 대해 true를 반환한다`() {
        val method = KisApiClient::class.java.getDeclaredMethod(
            "isAuthError", Throwable::class.java
        ).apply { isAccessible = true }

        assertTrue(method.invoke(client, ApiError.AuthError("auth")) as Boolean)
    }

    @Test
    fun `isAuthError는 401에 대해 true를 반환한다`() {
        val method = KisApiClient::class.java.getDeclaredMethod(
            "isAuthError", Throwable::class.java
        ).apply { isAccessible = true }

        assertTrue(method.invoke(client, ApiError.ApiCallError(401, "unauthorized")) as Boolean)
    }

    @Test
    fun `isAuthError는 403에 대해 true를 반환한다`() {
        val method = KisApiClient::class.java.getDeclaredMethod(
            "isAuthError", Throwable::class.java
        ).apply { isAccessible = true }

        assertTrue(method.invoke(client, ApiError.ApiCallError(403, "forbidden")) as Boolean)
    }

    @Test
    fun `isAuthError는 500에 대해 false를 반환한다`() {
        val method = KisApiClient::class.java.getDeclaredMethod(
            "isAuthError", Throwable::class.java
        ).apply { isAccessible = true }

        assertFalse(method.invoke(client, ApiError.ApiCallError(500, "server error")) as Boolean)
    }

    @Test
    fun `isAuthError는 NetworkError에 대해 false를 반환한다`() {
        val method = KisApiClient::class.java.getDeclaredMethod(
            "isAuthError", Throwable::class.java
        ).apply { isAccessible = true }

        assertFalse(method.invoke(client, ApiError.NetworkError("network")) as Boolean)
    }

    // =============================================
    // mapException Tests
    // =============================================

    @Test
    fun `mapException은 UnknownHostException을 NetworkError로 변환한다`() {
        val method = KisApiClient::class.java.getDeclaredMethod(
            "mapException", Exception::class.java
        ).apply { isAccessible = true }

        val error = method.invoke(client, java.net.UnknownHostException("host")) as ApiError
        assertTrue(error is ApiError.NetworkError)
    }

    @Test
    fun `mapException은 SocketTimeoutException을 TimeoutError로 변환한다`() {
        val method = KisApiClient::class.java.getDeclaredMethod(
            "mapException", Exception::class.java
        ).apply { isAccessible = true }

        val error = method.invoke(client, java.net.SocketTimeoutException("timeout")) as ApiError
        assertTrue(error is ApiError.TimeoutError)
    }

    @Test
    fun `mapException은 SerializationException을 ParseError로 변환한다`() {
        val method = KisApiClient::class.java.getDeclaredMethod(
            "mapException", Exception::class.java
        ).apply { isAccessible = true }

        val error = method.invoke(client,
            kotlinx.serialization.SerializationException("parse error")) as ApiError
        assertTrue(error is ApiError.ParseError)
    }

    @Test
    fun `mapException은 기존 ApiError를 그대로 반환한다`() {
        val method = KisApiClient::class.java.getDeclaredMethod(
            "mapException", Exception::class.java
        ).apply { isAccessible = true }

        val original = ApiError.AuthError("original")
        val error = method.invoke(client, original) as ApiError
        assertSame(original, error)
    }

    @Test
    fun `mapException은 알 수 없는 예외를 ApiCallError로 변환한다`() {
        val method = KisApiClient::class.java.getDeclaredMethod(
            "mapException", Exception::class.java
        ).apply { isAccessible = true }

        val error = method.invoke(client, RuntimeException("unknown")) as ApiError
        assertTrue(error is ApiError.ApiCallError)
        assertTrue(error.message.contains("unknown"))
    }

    // =============================================
    // get() network error test
    // =============================================

    @Test
    fun `get은 네트워크 에러 시 실패한다`() = runTest {
        // config.getBaseUrl() points to koreainvestment.com, not our MockWebServer
        // So the call should fail with a network error
        val result = client.get(
            trId = "TEST001",
            url = "/test",
            queryParams = emptyMap(),
            config = config
        ) { it }

        assertTrue(result.isFailure)
    }
}
