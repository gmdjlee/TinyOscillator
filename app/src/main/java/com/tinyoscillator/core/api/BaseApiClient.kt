package com.tinyoscillator.core.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * API 클라이언트 공통 인프라: 레이트 리밋, 서킷 브레이커.
 *
 * KiwoomApiClient, KisApiClient, AiApiClient 공통 패턴 추출.
 */
abstract class BaseApiClient(
    private val rateLimitMs: Long,
    circuitBreakerThreshold: Int = 3,
    circuitBreakerCooldownMs: Long = 5 * 60 * 1000L
) {
    val circuitBreaker = CircuitBreaker(circuitBreakerThreshold, circuitBreakerCooldownMs)

    private val rateLimitMutex = Mutex()
    @Volatile
    private var lastCallTime = 0L

    protected suspend fun waitForRateLimit() {
        val delayMs: Long
        rateLimitMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastCallTime
            delayMs = if (elapsed < rateLimitMs) rateLimitMs - elapsed else 0L
            lastCallTime = now + delayMs
        }
        if (delayMs > 0L) delay(delayMs)
    }

    /**
     * 요청 결과를 서킷 브레이커에 반영한다.
     *
     * 성공 → 카운터 리셋 (CLOSED).
     * 실패 → 일시 장애(Network/Timeout/429/5xx)만 카운트. Parse/Auth/NoApiKey 등
     * 쿨다운으로 자해소되지 않는 오류는 무시한다 (브레이커의 목적과 부합하지 않음).
     */
    protected fun updateCircuitBreaker(result: Result<*>) {
        if (result.isSuccess) {
            circuitBreaker.recordSuccess()
            return
        }
        if (ApiError.isRetriableError(result.exceptionOrNull())) {
            circuitBreaker.recordFailure()
        }
    }
}
