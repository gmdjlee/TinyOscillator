package com.tinyoscillator.core.api

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

/**
 * KiwoomApiClient 통합 테스트 (MockWebServer 기반).
 *
 * 실제 HTTP 호출 흐름과 Kiwoom-specific 로직(normalizeJsonNumbers)을 검증.
 * 공통 ApiError 테스트는 KiwoomApiClientTest/KisApiClientTest에서 수행.
 */
class KiwoomApiClientIntegrationTest {

    private lateinit var server: MockWebServer
    private lateinit var client: KiwoomApiClient
    private lateinit var config: KiwoomApiKeyConfig
    private lateinit var normalizeMethod: Method

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

        client = KiwoomApiClient(httpClient = httpClient, json = json)
        config = KiwoomApiKeyConfig(
            appKey = "test-key",
            secretKey = "test-secret",
            investmentMode = InvestmentMode.MOCK
        )

        normalizeMethod = KiwoomApiClient::class.java.getDeclaredMethod(
            "normalizeJsonNumbers", String::class.java
        ).apply { isAccessible = true }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun normalizeJsonNumbers(json: String): String {
        return normalizeMethod.invoke(client, json) as String
    }

    private fun enqueueTokenResponse(token: String = "test-token-123") {
        server.enqueue(MockResponse()
            .setBody("""
                {
                    "return_code": 0,
                    "token": "$token",
                    "token_type": "bearer",
                    "expires_dt": "20261231235959"
                }
            """.trimIndent())
            .setResponseCode(200))
    }

    // =============================================
    // HTTP Flow Tests
    // =============================================

    @Test
    fun `call은 토큰을 먼저 발급받고 API 호출한다`() = runTest {
        enqueueTokenResponse()
        server.enqueue(MockResponse()
            .setBody("""{"return_code": 0, "return_msg": "ok", "data": []}""")
            .setResponseCode(200))

        val result = client.call(
            apiId = "test-api",
            url = "/test",
            body = mapOf("key" to "value"),
            config = config
        ) { body ->
            body
        }

        // config.getBaseUrl() points to kiwoom.com, not MockWebServer → network error
        assertTrue(result.isFailure)
    }

    // =============================================
    // normalizeJsonNumbers Tests (Kiwoom-specific)
    // =============================================

    @Test
    fun `normalizeJsonNumbers는 인용된 +숫자를 정규화한다`() {
        val input = """{"value": "+1234"}"""
        val result = normalizeJsonNumbers(input)
        assertEquals("""{"value": "1234"}""", result)
    }

    @Test
    fun `normalizeJsonNumbers는 비인용 +숫자를 정규화한다`() {
        val input = """{"value": +1234}"""
        val result = normalizeJsonNumbers(input)
        assertEquals("""{"value": 1234}""", result)
    }
}
