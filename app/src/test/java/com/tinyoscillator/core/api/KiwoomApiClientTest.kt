package com.tinyoscillator.core.api

import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

/**
 * KiwoomApiClient 유닛 테스트.
 *
 * normalizeJsonNumbers는 private 메서드이므로 reflection을 사용.
 * mapException, isAuthError는 ApiError.Companion으로 이동되어 직접 호출.
 */
class KiwoomApiClientTest {

    private lateinit var client: KiwoomApiClient
    private lateinit var normalizeMethod: Method

    @Before
    fun setup() {
        val httpClient = mockk<OkHttpClient>(relaxed = true)
        client = KiwoomApiClient(httpClient = httpClient)

        normalizeMethod = KiwoomApiClient::class.java.getDeclaredMethod(
            "normalizeJsonNumbers", String::class.java
        ).apply { isAccessible = true }
    }

    private fun normalizeJsonNumbers(json: String): String {
        return normalizeMethod.invoke(client, json) as String
    }

    // ==========================================================
    // normalizeJsonNumbers 테스트
    // ==========================================================

    @Test
    fun `normalizeJsonNumbers - 따옴표 안의 +숫자를 숫자로 변환한다`() {
        val input = """{"value":"+1234","name":"test"}"""
        val expected = """{"value":"1234","name":"test"}"""

        assertEquals(expected, normalizeJsonNumbers(input))
    }

    @Test
    fun `normalizeJsonNumbers - 여러 따옴표 안의 +숫자를 모두 변환한다`() {
        val input = """{"a":"+100","b":"+200","c":"hello"}"""
        val expected = """{"a":"100","b":"200","c":"hello"}"""

        assertEquals(expected, normalizeJsonNumbers(input))
    }

    @Test
    fun `normalizeJsonNumbers - 따옴표 없는 +숫자를 콜론 또는 콤마 뒤에서 변환한다`() {
        val input = """{"value":+1234,"second":+5678}"""
        val expected = """{"value":1234,"second":5678}"""

        assertEquals(expected, normalizeJsonNumbers(input))
    }

    @Test
    fun `normalizeJsonNumbers - 일반 JSON은 변경하지 않는다`() {
        val input = """{"value":1234,"name":"test","arr":[1,2,3]}"""

        assertEquals(input, normalizeJsonNumbers(input))
    }

    @Test
    fun `normalizeJsonNumbers - 빈 문자열은 빈 문자열을 반환한다`() {
        assertEquals("", normalizeJsonNumbers(""))
    }

    @Test
    fun `normalizeJsonNumbers - 음수는 변경하지 않는다`() {
        val input = """{"value":"-1234","negative":-5678}"""

        assertEquals(input, normalizeJsonNumbers(input))
    }

    // ==========================================================
    // ApiError.mapException 테스트
    // ==========================================================

    @Test
    fun `mapException - UnknownHostException은 NetworkError로 변환한다`() {
        val e = java.net.UnknownHostException("host not found")
        val result = ApiError.mapException(e)

        assertTrue(result is ApiError.NetworkError)
        assertTrue(result.message.contains("네트워크"))
    }

    @Test
    fun `mapException - SocketTimeoutException은 TimeoutError로 변환한다`() {
        val e = java.net.SocketTimeoutException("read timed out")
        val result = ApiError.mapException(e)

        assertTrue(result is ApiError.TimeoutError)
        assertTrue(result.message.contains("시간"))
    }

    @Test
    fun `mapException - SerializationException은 ParseError로 변환한다`() {
        val e = kotlinx.serialization.SerializationException("failed to parse")
        val result = ApiError.mapException(e)

        assertTrue(result is ApiError.ParseError)
        assertTrue(result.message.contains("파싱"))
    }

    @Test
    fun `mapException - ApiError는 그대로 반환한다`() {
        val e = ApiError.AuthError("인증 실패")
        val result = ApiError.mapException(e)

        assertTrue(result is ApiError.AuthError)
        assertEquals("인증 실패", result.message)
    }

    @Test
    fun `mapException - 기타 Exception은 ApiCallError로 변환한다`() {
        val e = RuntimeException("unexpected error")
        val result = ApiError.mapException(e)

        assertTrue(result is ApiError.ApiCallError)
        assertTrue(result.message.contains("unexpected error"))
    }

    // ==========================================================
    // ApiError.isAuthError 테스트
    // ==========================================================

    @Test
    fun `isAuthError - AuthError는 true를 반환한다`() {
        assertTrue(ApiError.isAuthError(ApiError.AuthError("인증 오류")))
    }

    @Test
    fun `isAuthError - ApiCallError 401은 true를 반환한다`() {
        assertTrue(ApiError.isAuthError(ApiError.ApiCallError(401, "Unauthorized")))
    }

    @Test
    fun `isAuthError - ApiCallError 403은 true를 반환한다`() {
        assertTrue(ApiError.isAuthError(ApiError.ApiCallError(403, "Forbidden")))
    }

    @Test
    fun `isAuthError - ApiCallError 500은 false를 반환한다`() {
        assertFalse(ApiError.isAuthError(ApiError.ApiCallError(500, "Server Error")))
    }

    @Test
    fun `isAuthError - NetworkError는 false를 반환한다`() {
        assertFalse(ApiError.isAuthError(ApiError.NetworkError("네트워크 오류")))
    }

    @Test
    fun `isAuthError - 일반 Exception은 false를 반환한다`() {
        assertFalse(ApiError.isAuthError(RuntimeException("some error")))
    }

    // ==========================================================
    // KiwoomApiKeyConfig 검증
    // ==========================================================

    @Test
    fun `KiwoomApiKeyConfig - 빈 키는 유효하지 않다`() {
        val config = KiwoomApiKeyConfig(appKey = "", secretKey = "")
        assertFalse(config.isValid())
    }

    @Test
    fun `KiwoomApiKeyConfig - 유효한 키는 isValid가 true이다`() {
        val config = KiwoomApiKeyConfig(appKey = "myKey", secretKey = "mySecret")
        assertTrue(config.isValid())
    }

    @Test
    fun `KiwoomApiKeyConfig - MOCK 모드의 baseUrl이 올바르다`() {
        val config = KiwoomApiKeyConfig(
            appKey = "k", secretKey = "s",
            investmentMode = InvestmentMode.MOCK
        )
        assertEquals("https://mockapi.kiwoom.com", config.getBaseUrl())
    }

    @Test
    fun `KiwoomApiKeyConfig - PRODUCTION 모드의 baseUrl이 올바르다`() {
        val config = KiwoomApiKeyConfig(
            appKey = "k", secretKey = "s",
            investmentMode = InvestmentMode.PRODUCTION
        )
        assertEquals("https://api.kiwoom.com", config.getBaseUrl())
    }
}
