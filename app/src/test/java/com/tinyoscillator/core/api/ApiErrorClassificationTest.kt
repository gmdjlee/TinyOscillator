package com.tinyoscillator.core.api

import org.junit.Assert.*
import org.junit.Test

/**
 * ApiError 분류 체계 테스트.
 *
 * 구조화된 에러 분류가 올바르게 동작하는지 검증합니다.
 */
class ApiErrorClassificationTest {

    @Test
    fun `AuthError는 ApiError의 서브타입이다`() {
        val error = ApiError.AuthError("인증 실패")
        assertTrue(error is ApiError)
        assertTrue(error is Exception)
        assertEquals("인증 실패", error.message)
    }

    @Test
    fun `NetworkError는 ApiError의 서브타입이다`() {
        val error = ApiError.NetworkError("네트워크 오류")
        assertTrue(error is ApiError)
        assertEquals("네트워크 오류", error.message)
    }

    @Test
    fun `ApiCallError는 상태 코드를 포함한다`() {
        val error = ApiError.ApiCallError(429, "Too Many Requests")
        assertTrue(error is ApiError)
        assertEquals(429, error.code)
        assertEquals("[429] Too Many Requests", error.message)
    }

    @Test
    fun `ParseError는 ApiError의 서브타입이다`() {
        val error = ApiError.ParseError("JSON 파싱 실패")
        assertTrue(error is ApiError)
        assertEquals("JSON 파싱 실패", error.message)
    }

    @Test
    fun `TimeoutError는 ApiError의 서브타입이다`() {
        val error = ApiError.TimeoutError("요청 시간 초과")
        assertTrue(error is ApiError)
        assertEquals("요청 시간 초과", error.message)
    }

    @Test
    fun `NoApiKeyError는 기본 메시지를 가진다`() {
        val error = ApiError.NoApiKeyError()
        assertTrue(error is ApiError)
        assertTrue(error.message!!.contains("API 키"))
    }

    @Test
    fun `NoApiKeyError는 커스텀 메시지를 받을 수 있다`() {
        val error = ApiError.NoApiKeyError("Custom message")
        assertEquals("Custom message", error.message)
    }

    @Test
    fun `ApiError 서브타입을 when 문으로 분류할 수 있다`() {
        val errors: List<ApiError> = listOf(
            ApiError.AuthError("auth"),
            ApiError.NetworkError("net"),
            ApiError.CircuitBreakerOpenError(),
            ApiError.ApiCallError(500, "server"),
            ApiError.ParseError("parse"),
            ApiError.TimeoutError("timeout"),
            ApiError.NoApiKeyError()
        )

        val classified = errors.map { error ->
            when (error) {
                is ApiError.AuthError -> "auth"
                is ApiError.NetworkError -> "network"
                is ApiError.CircuitBreakerOpenError -> "circuit_breaker"
                is ApiError.ApiCallError -> "api_${error.code}"
                is ApiError.ParseError -> "parse"
                is ApiError.TimeoutError -> "timeout"
                is ApiError.NoApiKeyError -> "no_key"
            }
        }

        assertEquals(listOf("auth", "network", "circuit_breaker", "api_500", "parse", "timeout", "no_key"), classified)
    }

    @Test
    fun `ApiCallError 코드 0은 유효하다`() {
        val error = ApiError.ApiCallError(0, "Unknown")
        assertEquals(0, error.code)
        assertEquals("[0] Unknown", error.message)
    }

    @Test
    fun `빈 메시지도 허용된다`() {
        val error = ApiError.NetworkError("")
        assertEquals("", error.message)
    }
}
