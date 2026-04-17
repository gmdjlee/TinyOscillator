package com.tinyoscillator.core.api

import org.junit.Assert.*
import org.junit.Test

/**
 * BaseApiClient.updateCircuitBreaker 필터 동작 테스트.
 *
 * 목적: 일시 장애(Network/Timeout/429/5xx)만 브레이커에 기록하고,
 * 클라이언트 측 오류(Parse/Auth/NoApiKey)는 무시함을 검증.
 */
class BaseApiClientCircuitBreakerTest {

    /** 테스트용 최소 구현. rateLimit 0으로 빠른 실행. */
    private class TestClient(threshold: Int = 3) : BaseApiClient(
        rateLimitMs = 0L,
        circuitBreakerThreshold = threshold,
        circuitBreakerCooldownMs = 60_000L
    ) {
        fun update(result: Result<*>) = updateCircuitBreaker(result)
    }

    @Test
    fun `NetworkError 3회는 브레이커를 OPEN 시킨다`() {
        val client = TestClient()
        val err = Result.failure<Unit>(ApiError.NetworkError("네트워크 오류"))
        client.update(err)
        client.update(err)
        client.update(err)
        assertTrue(client.circuitBreaker.isOpen)
    }

    @Test
    fun `TimeoutError 3회는 브레이커를 OPEN 시킨다`() {
        val client = TestClient()
        val err = Result.failure<Unit>(ApiError.TimeoutError("시간 초과"))
        repeat(3) { client.update(err) }
        assertTrue(client.circuitBreaker.isOpen)
    }

    @Test
    fun `429 rate limit 3회는 브레이커를 OPEN 시킨다`() {
        val client = TestClient()
        val err = Result.failure<Unit>(ApiError.ApiCallError(429, "rate limit"))
        repeat(3) { client.update(err) }
        assertTrue(client.circuitBreaker.isOpen)
    }

    @Test
    fun `5xx 서버 오류 3회는 브레이커를 OPEN 시킨다`() {
        val client = TestClient()
        repeat(3) {
            client.update(Result.failure<Unit>(ApiError.ApiCallError(503, "unavailable")))
        }
        assertTrue(client.circuitBreaker.isOpen)
    }

    @Test
    fun `ParseError는 브레이커를 트립시키지 않는다`() {
        val client = TestClient()
        val err = Result.failure<Unit>(ApiError.ParseError("JSON 파싱 실패"))
        repeat(10) { client.update(err) }
        assertFalse("ParseError는 카운트되지 않아야 한다", client.circuitBreaker.isOpen)
    }

    @Test
    fun `AuthError는 브레이커를 트립시키지 않는다`() {
        val client = TestClient()
        val err = Result.failure<Unit>(ApiError.AuthError("HTTP 401"))
        repeat(10) { client.update(err) }
        assertFalse("AuthError는 카운트되지 않아야 한다", client.circuitBreaker.isOpen)
    }

    @Test
    fun `NoApiKeyError는 브레이커를 트립시키지 않는다`() {
        val client = TestClient()
        val err = Result.failure<Unit>(ApiError.NoApiKeyError())
        repeat(10) { client.update(err) }
        assertFalse("NoApiKeyError는 카운트되지 않아야 한다", client.circuitBreaker.isOpen)
    }

    @Test
    fun `4xx 클라이언트 오류(400 등)는 브레이커를 트립시키지 않는다`() {
        val client = TestClient()
        val err = Result.failure<Unit>(ApiError.ApiCallError(400, "bad request"))
        repeat(10) { client.update(err) }
        assertFalse("400은 비재시도성이므로 카운트되지 않아야 한다", client.circuitBreaker.isOpen)
    }

    @Test
    fun `비재시도성 오류와 재시도성 오류가 섞이면 재시도성만 카운트된다`() {
        val client = TestClient(threshold = 3)
        val retriable = Result.failure<Unit>(ApiError.NetworkError("net"))
        val nonRetriable = Result.failure<Unit>(ApiError.ParseError("parse"))

        client.update(retriable)     // 1
        client.update(nonRetriable)  // 무시, 카운터 2 아님
        client.update(retriable)     // 2
        client.update(nonRetriable)  // 무시
        assertFalse("아직 2회만 실패, OPEN 아님", client.circuitBreaker.isOpen)

        client.update(retriable)     // 3
        assertTrue("3회째 재시도성 실패로 OPEN", client.circuitBreaker.isOpen)
    }

    @Test
    fun `성공은 실패 카운터를 리셋한다`() {
        val client = TestClient(threshold = 3)
        val err = Result.failure<Unit>(ApiError.NetworkError("net"))
        client.update(err)
        client.update(err)
        client.update(Result.success(Unit))  // reset

        client.update(err)
        client.update(err)
        assertFalse("리셋 후 2회만 실패 → CLOSED 유지", client.circuitBreaker.isOpen)
    }

    @Test
    fun `비재시도성 오류 사이에 성공이 있어도 성공은 여전히 리셋한다`() {
        val client = TestClient(threshold = 3)
        client.update(Result.failure<Unit>(ApiError.NetworkError("net")))
        client.update(Result.failure<Unit>(ApiError.NetworkError("net")))
        // 여기까지 카운터 = 2
        client.update(Result.success(Unit))
        // 리셋
        client.update(Result.failure<Unit>(ApiError.NetworkError("net")))
        client.update(Result.failure<Unit>(ApiError.NetworkError("net")))
        assertFalse(client.circuitBreaker.isOpen)
    }
}
