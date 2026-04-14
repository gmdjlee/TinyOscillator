package com.tinyoscillator.core.api

import org.junit.Assert.*
import org.junit.Test

/**
 * CircuitBreaker unit tests.
 *
 * Verifies:
 * 1. Default state is CLOSED (isOpen = false)
 * 2. Opens after threshold consecutive failures
 * 3. Resets on success
 * 4. Cooldown expires after cooldownMs
 */
class CircuitBreakerTest {

    @Test
    fun `초기 상태는 CLOSED이다`() {
        val cb = CircuitBreaker()
        assertFalse(cb.isOpen)
    }

    @Test
    fun `threshold 미만의 실패는 CLOSED 상태를 유지한다`() {
        val cb = CircuitBreaker(threshold = 3)
        cb.recordFailure()
        cb.recordFailure()
        assertFalse(cb.isOpen)
    }

    @Test
    fun `threshold 이상의 연속 실패는 OPEN 상태로 전환한다`() {
        val cb = CircuitBreaker(threshold = 3, cooldownMs = 60_000L)
        cb.recordFailure()
        cb.recordFailure()
        cb.recordFailure()
        assertTrue(cb.isOpen)
    }

    @Test
    fun `성공하면 CLOSED로 리셋된다`() {
        val cb = CircuitBreaker(threshold = 3, cooldownMs = 60_000L)
        cb.recordFailure()
        cb.recordFailure()
        cb.recordFailure()
        assertTrue(cb.isOpen)

        cb.recordSuccess()
        assertFalse(cb.isOpen)
    }

    @Test
    fun `reset은 상태를 초기화한다`() {
        val cb = CircuitBreaker(threshold = 2, cooldownMs = 60_000L)
        cb.recordFailure()
        cb.recordFailure()
        assertTrue(cb.isOpen)

        cb.reset()
        assertFalse(cb.isOpen)
    }

    @Test
    fun `실패 후 성공하면 카운터가 리셋된다`() {
        val cb = CircuitBreaker(threshold = 3)
        cb.recordFailure()
        cb.recordFailure()
        cb.recordSuccess()  // Reset counter
        cb.recordFailure()
        cb.recordFailure()
        // Only 2 consecutive failures after reset → still CLOSED
        assertFalse(cb.isOpen)
    }

    @Test
    fun `쿨다운 만료 후 CLOSED 상태로 완전 복구된다`() {
        val cb = CircuitBreaker(threshold = 1, cooldownMs = 1L) // 1ms cooldown
        cb.recordFailure()
        // Wait for cooldown to expire
        Thread.sleep(10)
        assertFalse("Should be closed after cooldown", cb.isOpen)
        // All subsequent callers should also pass (not just the first one)
        assertFalse("Second check should also pass", cb.isOpen)
        assertFalse("Third check should also pass", cb.isOpen)
    }

    @Test
    fun `쿨다운 중에는 OPEN 상태가 유지된다`() {
        val cb = CircuitBreaker(threshold = 1, cooldownMs = 60_000L) // 1 minute
        cb.recordFailure()
        assertTrue("Should be open during cooldown", cb.isOpen)
    }

    @Test
    fun `threshold=1이면 첫 실패에 OPEN된다`() {
        val cb = CircuitBreaker(threshold = 1, cooldownMs = 60_000L)
        cb.recordFailure()
        assertTrue(cb.isOpen)
    }

    @Test
    fun `기본값은 threshold=3, cooldownMs=5분이다`() {
        val cb = CircuitBreaker()
        // 2 failures: still closed
        cb.recordFailure()
        cb.recordFailure()
        assertFalse(cb.isOpen)
        // 3rd failure: opens
        cb.recordFailure()
        assertTrue(cb.isOpen)
    }
}
