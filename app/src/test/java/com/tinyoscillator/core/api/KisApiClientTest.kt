package com.tinyoscillator.core.api

import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

/**
 * KisApiClient 유닛 테스트.
 *
 * mapException, isAuthError는 ApiError.Companion으로 이동되어 직접 호출.
 * 공통 테스트는 KiwoomApiClientTest에서 수행하므로 여기서는 KIS-specific + 추가 케이스만 테스트.
 */
class KisApiClientTest {

    // ==========================================================
    // ApiError.mapException 추가 테스트 (KIS-specific)
    // ==========================================================

    @Test
    fun `mapException - UnknownHostException은 NetworkError로 변환한다`() {
        val result = ApiError.mapException(java.net.UnknownHostException("host not found"))
        assertTrue(result is ApiError.NetworkError)
        assertTrue(result.message.contains("네트워크"))
    }

    @Test
    fun `mapException - SocketTimeoutException은 TimeoutError로 변환한다`() {
        val result = ApiError.mapException(java.net.SocketTimeoutException("read timed out"))
        assertTrue(result is ApiError.TimeoutError)
        assertTrue(result.message.contains("시간"))
    }

    @Test
    fun `mapException - SerializationException은 ParseError로 변환한다`() {
        val result = ApiError.mapException(kotlinx.serialization.SerializationException("parse error"))
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
        val result = ApiError.mapException(RuntimeException("unexpected"))
        assertTrue(result is ApiError.ApiCallError)
        assertTrue(result.message.contains("unexpected"))
    }

    @Test
    fun `mapException - 메시지가 null인 Exception은 알 수 없는 오류로 변환한다`() {
        val result = ApiError.mapException(RuntimeException())
        assertTrue(result is ApiError.ApiCallError)
        assertTrue(result.message.contains("알 수 없는"))
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
    fun `isAuthError - TimeoutError는 false를 반환한다`() {
        assertFalse(ApiError.isAuthError(ApiError.TimeoutError("timeout")))
    }

    @Test
    fun `isAuthError - ParseError는 false를 반환한다`() {
        assertFalse(ApiError.isAuthError(ApiError.ParseError("parse")))
    }

    @Test
    fun `isAuthError - 일반 Exception은 false를 반환한다`() {
        assertFalse(ApiError.isAuthError(RuntimeException("some error")))
    }

    // ==========================================================
    // ApiError.isRetriableError 테스트
    // ==========================================================

    @Test
    fun `isRetriableError - NetworkError는 true를 반환한다`() {
        assertTrue(ApiError.isRetriableError(ApiError.NetworkError("network")))
    }

    @Test
    fun `isRetriableError - TimeoutError는 true를 반환한다`() {
        assertTrue(ApiError.isRetriableError(ApiError.TimeoutError("timeout")))
    }

    @Test
    fun `isRetriableError - ApiCallError 429는 true를 반환한다`() {
        assertTrue(ApiError.isRetriableError(ApiError.ApiCallError(429, "Too Many")))
    }

    @Test
    fun `isRetriableError - ApiCallError 500은 true를 반환한다`() {
        assertTrue(ApiError.isRetriableError(ApiError.ApiCallError(500, "Server Error")))
    }

    @Test
    fun `isRetriableError - AuthError는 false를 반환한다`() {
        assertFalse(ApiError.isRetriableError(ApiError.AuthError("auth")))
    }

    @Test
    fun `isRetriableError - null은 false를 반환한다`() {
        assertFalse(ApiError.isRetriableError(null))
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
