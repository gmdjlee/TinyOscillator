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
 * 실제 HTTP 호출 흐름을 검증.
 * 공통 ApiError 테스트는 KisApiClientTest/KiwoomApiClientTest에서 수행.
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
    // HTTP Flow Tests
    // =============================================

    @Test
    fun `get은 네트워크 에러 시 실패한다`() = runTest {
        // config.getBaseUrl() points to koreainvestment.com, not MockWebServer
        val result = client.get(
            trId = "TEST001",
            url = "/test",
            queryParams = emptyMap(),
            config = config
        ) { it }

        assertTrue(result.isFailure)
    }
}
