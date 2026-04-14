package com.tinyoscillator.core.api

/**
 * Lightweight circuit breaker for API clients.
 *
 * After [threshold] consecutive failures, enters OPEN state for [cooldownMs].
 * During OPEN state, immediately returns failure without making API calls.
 * Resets to CLOSED on success or after cooldown expires.
 *
 * All state transitions are synchronized to prevent race conditions
 * between concurrent callers (e.g., isOpen reset vs recordFailure).
 */
class CircuitBreaker(
    private val threshold: Int = 3,
    private val cooldownMs: Long = 5 * 60 * 1000L  // 5 minutes
) {
    private var consecutiveFailures = 0
    private var openedAt = 0L
    private var halfOpenGate = false

    val isOpen: Boolean
        @Synchronized get() {
            val opened = openedAt
            if (opened == 0L) return false
            if (System.currentTimeMillis() - opened > cooldownMs) {
                // Cooldown expired → reset to CLOSED so all callers can proceed
                consecutiveFailures = 0
                openedAt = 0L
                halfOpenGate = false
                return false
            }
            return true
        }

    @Synchronized
    fun recordSuccess() {
        consecutiveFailures = 0
        openedAt = 0L
        halfOpenGate = false
    }

    @Synchronized
    fun recordFailure() {
        consecutiveFailures++
        if (consecutiveFailures >= threshold) {
            if (openedAt == 0L) {
                openedAt = System.currentTimeMillis()
            }
        }
        halfOpenGate = false
    }

    @Synchronized
    fun reset() {
        consecutiveFailures = 0
        openedAt = 0L
        halfOpenGate = false
    }
}
