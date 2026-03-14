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

    protected fun updateCircuitBreaker(success: Boolean) {
        if (success) circuitBreaker.recordSuccess()
        else circuitBreaker.recordFailure()
    }
}
