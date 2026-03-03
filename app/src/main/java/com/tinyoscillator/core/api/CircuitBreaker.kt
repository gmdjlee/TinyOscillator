package com.tinyoscillator.core.api

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight circuit breaker for API clients.
 *
 * After [threshold] consecutive failures, enters OPEN state for [cooldownMs].
 * During OPEN state, immediately returns failure without making API calls.
 * Resets to CLOSED on success.
 */
class CircuitBreaker(
    private val threshold: Int = 3,
    private val cooldownMs: Long = 5 * 60 * 1000L  // 5 minutes
) {
    private val consecutiveFailures = AtomicInteger(0)
    private val openedAt = AtomicLong(0L)

    val isOpen: Boolean
        get() {
            val opened = openedAt.get()
            if (opened == 0L) return false
            if (System.currentTimeMillis() - opened > cooldownMs) {
                // Cooldown expired → half-open (allow next attempt)
                openedAt.set(0L)
                return false
            }
            return true
        }

    fun recordSuccess() {
        consecutiveFailures.set(0)
        openedAt.set(0L)
    }

    fun recordFailure() {
        val failures = consecutiveFailures.incrementAndGet()
        if (failures >= threshold) {
            openedAt.compareAndSet(0L, System.currentTimeMillis())
        }
    }

    fun reset() {
        consecutiveFailures.set(0)
        openedAt.set(0L)
    }
}
