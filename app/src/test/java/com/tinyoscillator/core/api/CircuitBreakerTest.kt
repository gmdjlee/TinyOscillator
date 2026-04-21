package com.tinyoscillator.core.api

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * CircuitBreaker 단위 테스트.
 *
 * 상태 머신:
 * - CLOSED → OPEN: threshold 이상 연속 실패
 * - OPEN → HALF_OPEN: 쿨다운 만료 후 최초 tryAcquire
 * - HALF_OPEN → CLOSED: probe 성공
 * - HALF_OPEN → OPEN: probe 실패 (쿨다운 재시작)
 */
class CircuitBreakerTest {

    @Test
    fun `초기 상태는 CLOSED이다`() {
        val cb = CircuitBreaker()
        assertEquals(CircuitBreaker.State.CLOSED, cb.currentState)
        assertTrue("CLOSED에서는 tryAcquire가 true", cb.tryAcquire())
    }

    @Test
    fun `threshold 미만의 실패는 CLOSED 상태를 유지한다`() {
        val cb = CircuitBreaker(threshold = 3)
        cb.recordFailure()
        cb.recordFailure()
        assertEquals(CircuitBreaker.State.CLOSED, cb.currentState)
        assertTrue(cb.tryAcquire())
    }

    @Test
    fun `threshold 이상의 연속 실패는 OPEN 상태로 전환한다`() {
        val cb = CircuitBreaker(threshold = 3, cooldownMs = 60_000L)
        cb.recordFailure()
        cb.recordFailure()
        cb.recordFailure()
        assertEquals(CircuitBreaker.State.OPEN, cb.currentState)
        assertFalse("OPEN에서 쿨다운 중이면 tryAcquire는 false", cb.tryAcquire())
    }

    @Test
    fun `성공하면 CLOSED로 리셋된다`() {
        val cb = CircuitBreaker(threshold = 3, cooldownMs = 60_000L)
        cb.recordFailure()
        cb.recordFailure()
        cb.recordFailure()
        assertEquals(CircuitBreaker.State.OPEN, cb.currentState)

        cb.recordSuccess()
        assertEquals(CircuitBreaker.State.CLOSED, cb.currentState)
        assertTrue(cb.tryAcquire())
    }

    @Test
    fun `reset은 상태를 초기화한다`() {
        val cb = CircuitBreaker(threshold = 2, cooldownMs = 60_000L)
        cb.recordFailure()
        cb.recordFailure()
        assertEquals(CircuitBreaker.State.OPEN, cb.currentState)

        cb.reset()
        assertEquals(CircuitBreaker.State.CLOSED, cb.currentState)
        assertTrue(cb.tryAcquire())
    }

    @Test
    fun `실패 후 성공하면 카운터가 리셋된다`() {
        val cb = CircuitBreaker(threshold = 3)
        cb.recordFailure()
        cb.recordFailure()
        cb.recordSuccess()  // 카운터 리셋
        cb.recordFailure()
        cb.recordFailure()
        // 리셋 후 2회만 실패 → CLOSED 유지
        assertEquals(CircuitBreaker.State.CLOSED, cb.currentState)
    }

    @Test
    fun `쿨다운 만료 후 tryAcquire는 HALF_OPEN으로 전이하고 probe 1건을 허용한다`() {
        val cb = CircuitBreaker(threshold = 1, cooldownMs = 1L)
        cb.recordFailure()  // OPEN
        Thread.sleep(10)    // 쿨다운 만료

        // 첫 tryAcquire: HALF_OPEN 전이 + probe 획득
        assertTrue("첫 호출은 probe 허용", cb.tryAcquire())
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.currentState)

        // 두 번째 tryAcquire: probe 진행 중 → 거부
        assertFalse("probe 진행 중에는 다른 호출 거부", cb.tryAcquire())
        assertFalse("세 번째 호출도 거부", cb.tryAcquire())
    }

    @Test
    fun `HALF_OPEN probe 성공 시 CLOSED로 전환되어 모든 호출 허용`() {
        val cb = CircuitBreaker(threshold = 1, cooldownMs = 1L)
        cb.recordFailure()
        Thread.sleep(10)

        assertTrue(cb.tryAcquire())  // probe 획득
        cb.recordSuccess()           // probe 성공

        assertEquals(CircuitBreaker.State.CLOSED, cb.currentState)
        assertTrue(cb.tryAcquire())
        assertTrue(cb.tryAcquire())
    }

    @Test
    fun `HALF_OPEN probe 실패 시 다시 OPEN으로 돌아가고 쿨다운 재시작`() {
        val cb = CircuitBreaker(threshold = 1, cooldownMs = 50L)
        cb.recordFailure()
        Thread.sleep(60)

        assertTrue(cb.tryAcquire())  // probe 획득, HALF_OPEN
        cb.recordFailure()           // probe 실패

        assertEquals(CircuitBreaker.State.OPEN, cb.currentState)
        assertFalse("재OPEN 직후 쿨다운 중 → 거부", cb.tryAcquire())

        Thread.sleep(60)
        assertTrue("새 쿨다운 만료 후 다시 probe 허용", cb.tryAcquire())
    }

    @Test
    fun `쿨다운 중에는 OPEN 상태가 유지된다`() {
        val cb = CircuitBreaker(threshold = 1, cooldownMs = 60_000L)
        cb.recordFailure()
        assertEquals(CircuitBreaker.State.OPEN, cb.currentState)
        assertFalse(cb.tryAcquire())
    }

    @Test
    fun `threshold=1이면 첫 실패에 OPEN된다`() {
        val cb = CircuitBreaker(threshold = 1, cooldownMs = 60_000L)
        cb.recordFailure()
        assertEquals(CircuitBreaker.State.OPEN, cb.currentState)
    }

    @Test
    fun `기본값은 threshold=3, cooldownMs=5분이다`() {
        val cb = CircuitBreaker()
        cb.recordFailure()
        cb.recordFailure()
        assertEquals(CircuitBreaker.State.CLOSED, cb.currentState)
        cb.recordFailure()
        assertEquals(CircuitBreaker.State.OPEN, cb.currentState)
    }

    @Test
    fun `동시 recordFailure와 tryAcquire 호출이 일관된 상태를 유지한다`() = runTest {
        val cb = CircuitBreaker(threshold = 3, cooldownMs = 60_000L)
        val iterations = 100

        repeat(iterations) { iter ->
            cb.reset()
            val jobs = mutableListOf<Job>()

            repeat(5) {
                jobs += launch(Dispatchers.Default) { cb.recordFailure() }
            }
            repeat(5) {
                jobs += launch(Dispatchers.Default) { cb.tryAcquire() }
            }

            jobs.joinAll()
            assertEquals("iter $iter: 5회 실패 후 OPEN", CircuitBreaker.State.OPEN, cb.currentState)
        }
    }

    @Test
    fun `동시 tryAcquire 호출 시 HALF_OPEN probe는 최대 1건만 통과한다`() = runTest {
        val iterations = 50

        repeat(iterations) { iter ->
            val cb = CircuitBreaker(threshold = 1, cooldownMs = 1L)
            cb.recordFailure()
            Thread.sleep(5)

            val acquired = java.util.concurrent.atomic.AtomicInteger(0)
            val jobs = mutableListOf<Job>()

            repeat(10) {
                jobs += launch(Dispatchers.Default) {
                    if (cb.tryAcquire()) acquired.incrementAndGet()
                }
            }
            jobs.joinAll()

            assertEquals(
                "iter $iter: HALF_OPEN probe는 1건만 허용되어야 함",
                1,
                acquired.get()
            )
        }
    }

    @Test
    fun `isOpen 스냅샷은 상태를 변경하지 않는다`() {
        val cb = CircuitBreaker(threshold = 1, cooldownMs = 1L)
        cb.recordFailure()
        Thread.sleep(10)

        // 쿨다운 만료. isOpen은 OPEN을 계속 보고하고 상태 전이를 트리거하지 않음
        assertTrue(cb.isOpen)
        assertTrue(cb.isOpen)
        assertEquals("isOpen 호출 후에도 상태 OPEN 유지", CircuitBreaker.State.OPEN, cb.currentState)

        // tryAcquire만이 HALF_OPEN으로 전이
        assertTrue(cb.tryAcquire())
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.currentState)
    }

    @Test
    fun `HALF_OPEN에서 tryAcquire 후 상태 보고가 정확하다`() {
        val cb = CircuitBreaker(threshold = 1, cooldownMs = 1L)
        cb.recordFailure()
        Thread.sleep(10)

        cb.tryAcquire()  // HALF_OPEN + probe in flight
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.currentState)
        assertTrue("HALF_OPEN도 isOpen=true로 표시", cb.isOpen)
    }

    @Test
    fun `HALF_OPEN probe 후 record 누락 시 probeTimeout 경과하면 자동 복구된다`() {
        val cb = CircuitBreaker(threshold = 1, cooldownMs = 1L, probeTimeoutMs = 50L)
        cb.recordFailure()
        Thread.sleep(10)

        // probe 획득
        assertTrue(cb.tryAcquire())
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.currentState)

        // probe 타임아웃 경과 전에는 여전히 거부
        Thread.sleep(20)
        assertFalse("probe 타임아웃 미경과 → 거부", cb.tryAcquire())
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.currentState)

        // probe 타임아웃 경과 → stale probe를 실패로 처리하고 OPEN 복구
        Thread.sleep(50)
        assertFalse("stale probe 감지로 false 반환, OPEN 재진입", cb.tryAcquire())
        assertEquals(CircuitBreaker.State.OPEN, cb.currentState)

        // 새 쿨다운 경과 후 다시 probe 획득 가능
        Thread.sleep(10)
        assertTrue("복구 후 새 probe 허용", cb.tryAcquire())
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.currentState)
    }

    @Test
    fun `probe 성공 호출 후에는 probe 타임아웃과 무관하게 CLOSED 유지`() {
        val cb = CircuitBreaker(threshold = 1, cooldownMs = 1L, probeTimeoutMs = 20L)
        cb.recordFailure()
        Thread.sleep(10)

        assertTrue(cb.tryAcquire())
        cb.recordSuccess()

        // probeTimeout 초과 시간이 지나도 CLOSED 유지 (타임아웃은 in-flight에서만 적용)
        Thread.sleep(30)
        assertEquals(CircuitBreaker.State.CLOSED, cb.currentState)
        assertTrue(cb.tryAcquire())
    }

    @Test
    fun `probeTimeoutMs 기본값은 1분이다`() {
        val cb = CircuitBreaker(threshold = 1, cooldownMs = 1L)
        cb.recordFailure()
        Thread.sleep(10)

        assertTrue(cb.tryAcquire())
        // 기본 1분 probeTimeout이면 30ms 경과해도 stale 감지 안 됨
        Thread.sleep(30)
        assertFalse(cb.tryAcquire())
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.currentState)
    }
}
