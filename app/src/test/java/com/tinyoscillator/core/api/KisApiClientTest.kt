package com.tinyoscillator.core.api

import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

/**
 * KisApiClient 유닛 테스트.
 *
 * KiwoomApiClient와 동일한 구조의 private 메서드를 reflection으로 테스트합니다.
 */
class KisApiClientTest {

    private lateinit var client: KisApiClient
    private lateinit var mapExceptionMethod: Method
    private lateinit var isAuthErrorMethod: Method

    @Before
    fun setup() {
        val httpClient = mockk<okhttp3.OkHttpClient>(relaxed = true)
        client = KisApiClient(httpClient = httpClient)

        mapExceptionMethod = KisApiClient::class.java.getDeclaredMethod(
            "mapException", Exception::class.java
        ).apply { isAccessible = true }

        isAuthErrorMethod = KisApiClient::class.java.getDeclaredMethod(
            "isAuthError", Throwable::class.java
        ).apply { isAccessible = true }
    }

    private fun mapException(e: Exception): ApiError {
        return mapExceptionMethod.invoke(client, e) as ApiError
    }

    private fun isAuthError(error: Throwable): Boolean {
        return isAuthErrorMethod.invoke(client, error) as Boolean
    }

    // ==========================================================
    // mapException 테스트
    // ==========================================================

    @Test
    fun `mapException - UnknownHostException은 NetworkError로 변환한다`() {
        val result = mapException(java.net.UnknownHostException("host not found"))
        assertTrue(result is ApiError.NetworkError)
        assertTrue(result.message.contains("네트워크"))
    }

    @Test
    fun `mapException - SocketTimeoutException은 TimeoutError로 변환한다`() {
        val result = mapException(java.net.SocketTimeoutException("read timed out"))
        assertTrue(result is ApiError.TimeoutError)
        assertTrue(result.message.contains("시간"))
    }

    @Test
    fun `mapException - SerializationException은 ParseError로 변환한다`() {
        val result = mapException(kotlinx.serialization.SerializationException("parse error"))
        assertTrue(result is ApiError.ParseError)
        assertTrue(result.message.contains("파싱"))
    }

    @Test
    fun `mapException - ApiError는 그대로 반환한다`() {
        val e = ApiError.AuthError("인증 실패")
        val result = mapException(e)
        assertTrue(result is ApiError.AuthError)
        assertEquals("인증 실패", result.message)
    }

    @Test
    fun `mapException - 기타 Exception은 ApiCallError로 변환한다`() {
        val result = mapException(RuntimeException("unexpected"))
        assertTrue(result is ApiError.ApiCallError)
        assertTrue(result.message.contains("unexpected"))
    }

    @Test
    fun `mapException - 메시지가 null인 Exception은 알 수 없는 오류로 변환한다`() {
        val result = mapException(RuntimeException())
        assertTrue(result is ApiError.ApiCallError)
        assertTrue(result.message.contains("알 수 없는"))
    }

    // ==========================================================
    // isAuthError 테스트
    // ==========================================================

    @Test
    fun `isAuthError - AuthError는 true를 반환한다`() {
        assertTrue(isAuthError(ApiError.AuthError("인증 오류")))
    }

    @Test
    fun `isAuthError - ApiCallError 401은 true를 반환한다`() {
        assertTrue(isAuthError(ApiError.ApiCallError(401, "Unauthorized")))
    }

    @Test
    fun `isAuthError - ApiCallError 403은 true를 반환한다`() {
        assertTrue(isAuthError(ApiError.ApiCallError(403, "Forbidden")))
    }

    @Test
    fun `isAuthError - ApiCallError 500은 false를 반환한다`() {
        assertFalse(isAuthError(ApiError.ApiCallError(500, "Server Error")))
    }

    @Test
    fun `isAuthError - NetworkError는 false를 반환한다`() {
        assertFalse(isAuthError(ApiError.NetworkError("네트워크 오류")))
    }

    @Test
    fun `isAuthError - TimeoutError는 false를 반환한다`() {
        assertFalse(isAuthError(ApiError.TimeoutError("timeout")))
    }

    @Test
    fun `isAuthError - ParseError는 false를 반환한다`() {
        assertFalse(isAuthError(ApiError.ParseError("parse")))
    }

    @Test
    fun `isAuthError - 일반 Exception은 false를 반환한다`() {
        assertFalse(isAuthError(RuntimeException("some error")))
    }

    // ==========================================================
    // KisApiKeyConfig 검증
    // ==========================================================

    @Test
    fun `KisApiKeyConfig - 빈 키는 유효하지 않다`() {
        val config = KisApiKeyConfig(appKey = "", appSecret = "")
        assertFalse(config.isValid())
    }

    @Test
    fun `KisApiKeyConfig - appKey만 있고 appSecret이 비어있으면 유효하지 않다`() {
        val config = KisApiKeyConfig(appKey = "key", appSecret = "")
        assertFalse(config.isValid())
    }

    @Test
    fun `KisApiKeyConfig - appSecret만 있고 appKey가 비어있으면 유효하지 않다`() {
        val config = KisApiKeyConfig(appKey = "", appSecret = "secret")
        assertFalse(config.isValid())
    }

    @Test
    fun `KisApiKeyConfig - 유효한 키는 isValid가 true이다`() {
        val config = KisApiKeyConfig(appKey = "myKey", appSecret = "mySecret")
        assertTrue(config.isValid())
    }

    @Test
    fun `KisApiKeyConfig - MOCK 모드의 baseUrl이 올바르다`() {
        val config = KisApiKeyConfig(
            appKey = "k", appSecret = "s",
            investmentMode = InvestmentMode.MOCK
        )
        assertEquals("https://openapivts.koreainvestment.com:29443", config.getBaseUrl())
    }

    @Test
    fun `KisApiKeyConfig - PRODUCTION 모드의 baseUrl이 올바르다`() {
        val config = KisApiKeyConfig(
            appKey = "k", appSecret = "s",
            investmentMode = InvestmentMode.PRODUCTION
        )
        assertEquals("https://openapi.koreainvestment.com:9443", config.getBaseUrl())
    }
}
