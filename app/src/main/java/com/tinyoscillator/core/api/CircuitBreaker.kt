package com.tinyoscillator.core.api

/**
 * Lightweight circuit breaker for API clients.
 *
 * 상태 머신:
 * - CLOSED: 정상. 모든 요청 통과.
 * - OPEN: 차단. [cooldownMs] 경과 전까지 모든 요청 즉시 거부.
 * - HALF_OPEN: 쿨다운 만료 후 probe 1건만 허용. 그 결과로 CLOSED/OPEN 전이.
 *
 * [threshold] 이상의 연속 실패 시 CLOSED → OPEN.
 * 쿨다운 경과 후 [tryAcquire] 호출 시 OPEN → HALF_OPEN.
 * HALF_OPEN에서 probe 성공 → CLOSED, 실패 → OPEN (쿨다운 재시작).
 *
 * 모든 상태 전이는 `@Synchronized`로 보호되어 동시 호출 안전.
 */
class CircuitBreaker(
    private val threshold: Int = 3,
    private val cooldownMs: Long = 5 * 60 * 1000L  // 5 minutes
) {
    enum class State { CLOSED, OPEN, HALF_OPEN }

    private var state: State = State.CLOSED
    private var consecutiveFailures = 0
    private var openedAt = 0L
    private var halfOpenProbeInFlight = false

    /**
     * 현재 상태 스냅샷. 상태를 변경하지 않는다. 테스트/모니터링용.
     */
    val currentState: State
        @Synchronized get() = state

    /**
     * 브레이커가 차단 상태인지 여부(스냅샷). 상태를 변경하지 않는다.
     *
     * 주의: `tryAcquire()`와 달리 쿨다운 만료에 의한 HALF_OPEN 전이를 트리거하지
     * 않으므로, 쿨다운이 만료된 OPEN 상태도 true를 반환한다. 실제 호출 가능 여부
     * 판정에는 `tryAcquire()`를 사용할 것.
     */
    val isOpen: Boolean
        @Synchronized get() = state != State.CLOSED

    /**
     * 호출 가능 여부를 판정하고 필요한 상태 전이를 수행한다.
     *
     * - CLOSED → true
     * - OPEN & 쿨다운 미만료 → false
     * - OPEN & 쿨다운 만료 → HALF_OPEN으로 전이하고 probe 허용(true)
     * - HALF_OPEN & probe 진행 중 → false
     * - HALF_OPEN & probe 완료 대기 → true (새 probe 허용)
     *
     * true 반환 시 호출자는 반드시 결과에 따라 `recordSuccess()` 또는
     * `recordFailure()`를 호출해야 한다. 호출하지 않으면 HALF_OPEN 상태에서
     * 다른 호출자가 probe를 획득할 수 없다.
     */
    @Synchronized
    fun tryAcquire(): Boolean {
        return when (state) {
            State.CLOSED -> true
            State.OPEN -> {
                if (System.currentTimeMillis() - openedAt > cooldownMs) {
                    state = State.HALF_OPEN
                    halfOpenProbeInFlight = true
                    true
                } else {
                    false
                }
            }
            State.HALF_OPEN -> {
                if (halfOpenProbeInFlight) {
                    false
                } else {
                    halfOpenProbeInFlight = true
                    true
                }
            }
        }
    }

    @Synchronized
    fun recordSuccess() {
        state = State.CLOSED
        consecutiveFailures = 0
        openedAt = 0L
        halfOpenProbeInFlight = false
    }

    @Synchronized
    fun recordFailure() {
        when (state) {
            State.HALF_OPEN -> {
                // probe 실패 → 다시 OPEN (쿨다운 재시작)
                state = State.OPEN
                openedAt = System.currentTimeMillis()
                halfOpenProbeInFlight = false
            }
            State.CLOSED -> {
                consecutiveFailures++
                if (consecutiveFailures >= threshold) {
                    state = State.OPEN
                    openedAt = System.currentTimeMillis()
                }
            }
            State.OPEN -> {
                // 이미 OPEN. tryAcquire를 통과하지 않고 recordFailure가 호출된
                // 비정상 경로. 상태 변경 없이 카운터만 유지.
            }
        }
    }

    @Synchronized
    fun reset() {
        state = State.CLOSED
        consecutiveFailures = 0
        openedAt = 0L
        halfOpenProbeInFlight = false
    }
}
