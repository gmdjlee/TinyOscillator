package com.tinyoscillator.core.api

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * BaseApiClient.executeRequest 요청 단위 집계 테스트.
 *
 * 검증 포인트:
 * 1. 블록 내부 여러 호출이 동시에 실패해도 CB에는 1회만 기록되어야 한다 (Phase C 핵심).
 * 2. 블록이 성공 반환하면 recordSuccess (카운터 리셋).
 * 3. 블록 내부 예외는 ApiError로 매핑하여 Result.failure.
 * 4. CB가 차단된 상태에서는 블록이 실행되지 않고 즉시 CircuitBreakerOpenError.
 * 5. 외부(executeRequest 밖)의 직접 호출은 기존 경로를 유지 (호출 단위 CB).
 */
class ExecuteRequestTest {

    /**
     * 테스트용 최소 구현.
     * `makeCall(behavior)`가 내부 HTTP 호출을 흉내냄 — 성공/실패/예외 중 선택.
     * executeRequest 블록 내부에서는 CB를 건너뛰도록 isInRequestScope 검사.
     */
    private class TestClient(
        threshold: Int = 3,
        cooldownMs: Long = 60_000L
    ) : BaseApiClient(
        rateLimitMs = 0L,
        circuitBreakerThreshold = threshold,
        circuitBreakerCooldownMs = cooldownMs
    ) {
        val callCount = AtomicInteger(0)

        suspend fun makeCall(shouldFail: Boolean, error: ApiError = ApiError.NetworkError("net")): Result<String> {
            callCount.incrementAndGet()
            val inScope = isInRequestScope()
            val result: Result<String> = if (shouldFail) Result.failure(error) else Result.success("ok")
            if (!inScope) updateCircuitBreaker(result)
            return result
        }

        fun state(): CircuitBreaker.State = circuitBreaker.currentState
    }

    @Test
    fun `블록 내부 3개 병렬 실패는 CB 카운트를 1회만 증가시킨다`() = runTest {
        val client = TestClient(threshold = 3)
        // 블록 내부에서 3개 호출 동시 실패
        val result = client.executeRequest {
            coroutineScope {
                val a = async { client.makeCall(shouldFail = true) }
                val b = async { client.makeCall(shouldFail = true) }
                val c = async { client.makeCall(shouldFail = true) }
                Triple(a.await(), b.await(), c.await())
            }
        }
        // 블록 자체는 Triple(failure, failure, failure)로 정상 "반환" (예외 없음)
        assertTrue("블록이 정상 반환했으므로 executeRequest는 success", result.isSuccess)
        // 3건의 호출이 있었지만 CB에는 성공 1회만 기록됨 → CLOSED 유지
        assertEquals(CircuitBreaker.State.CLOSED, client.state())
    }

    @Test
    fun `블록이 예외를 throw하면 CB 실패로 1회 기록`() = runTest {
        val client = TestClient(threshold = 3)

        // 같은 블록 패턴을 3회 반복 → 3회 실패 → CB OPEN
        repeat(3) {
            val result = client.executeRequest {
                coroutineScope {
                    // 3개 호출 모두 실패 + 하나는 예외 throw로 블록 전체 실패
                    val a = async { client.makeCall(shouldFail = true) }
                    val b = async { client.makeCall(shouldFail = true) }
                    a.await()
                    b.await()
                    throw ApiError.NetworkError("blob 전체 실패")
                }
            }
            assertTrue(result.isFailure)
        }
        assertEquals(CircuitBreaker.State.OPEN, client.state())
    }

    @Test
    fun `블록 내부 단일 실패라도 CB에는 1회만 기록`() = runTest {
        val client = TestClient(threshold = 3)
        // 5개 호출 중 하나만 실패해도 블록은 그대로 반환 → CB success
        val result = client.executeRequest {
            coroutineScope {
                val jobs = (0 until 5).map { idx ->
                    async { client.makeCall(shouldFail = idx == 2) }
                }
                jobs.awaitAll()
            }
        }
        assertTrue(result.isSuccess)
        assertEquals(CircuitBreaker.State.CLOSED, client.state())
    }

    @Test
    fun `블록이 Result_failure를 반환하는 방식은 지원하지 않고 Result_success로 포장`() = runTest {
        // 블록 시그니처가 () -> T 이므로, 블록이 명시적으로 Result를 반환하면 Result<Result<T>>가 됨.
        // 이 테스트는 "throws-based" 시그니처를 확인한다.
        val client = TestClient(threshold = 3)
        val result: Result<Result<String>> = client.executeRequest {
            Result.failure<String>(ApiError.NetworkError("x"))
        }
        // 블록은 값을 정상 반환했으므로 outer는 success. inner는 failure.
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isFailure)
        // CB에는 성공으로 기록됨 (블록이 정상 반환)
        assertEquals(CircuitBreaker.State.CLOSED, client.state())
    }

    @Test
    fun `블록이 throw하면 ApiError로 매핑되어 실패 반영`() = runTest {
        val client = TestClient(threshold = 3)

        repeat(3) {
            val result = client.executeRequest {
                throw ApiError.NetworkError("boom")
            }
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is ApiError.NetworkError)
        }
        assertEquals(CircuitBreaker.State.OPEN, client.state())
    }

    @Test
    fun `블록이 비재시도성 예외를 throw하면 CB에 기록되지 않음`() = runTest {
        val client = TestClient(threshold = 3)

        repeat(10) {
            val result = client.executeRequest {
                throw ApiError.ParseError("parse")
            }
            assertTrue(result.isFailure)
        }
        assertEquals("ParseError 반복은 CB에 카운트되지 않음", CircuitBreaker.State.CLOSED, client.state())
    }

    @Test
    fun `CB가 OPEN이면 블록이 실행되지 않고 즉시 실패`() = runTest {
        val client = TestClient(threshold = 1, cooldownMs = 60_000L)
        // 강제로 OPEN
        client.circuitBreaker.recordFailure()
        assertEquals(CircuitBreaker.State.OPEN, client.state())

        var blockRan = false
        val result = client.executeRequest {
            blockRan = true
            "should not run"
        }
        assertFalse("블록이 실행되면 안 됨", blockRan)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ApiError.CircuitBreakerOpenError)
    }

    @Test
    fun `executeRequest 외부의 직접 호출은 여전히 개별 CB 처리`() = runTest {
        val client = TestClient(threshold = 3)
        // executeRequest 없이 직접 호출 → 호출 단위 CB 기록
        client.makeCall(shouldFail = true)
        client.makeCall(shouldFail = true)
        assertEquals(CircuitBreaker.State.CLOSED, client.state())
        client.makeCall(shouldFail = true)
        assertEquals("3회 직접 호출 실패 → OPEN", CircuitBreaker.State.OPEN, client.state())
    }

    @Test
    fun `중첩된 executeRequest 호출 — 안쪽 블록은 바깥 scope에서 CB 건너뛴다`() = runTest {
        val client = TestClient(threshold = 3)
        val result = client.executeRequest {
            // 바깥 블록 안에서 또 executeRequest를 호출
            val inner = client.executeRequest {
                client.makeCall(shouldFail = true)
                client.makeCall(shouldFail = true)
                client.makeCall(shouldFail = true)
                "inner done"
            }
            inner.getOrThrow()
        }
        // 중첩 실행은 되지만 CB에는 바깥 executeRequest 1회만 기록
        assertTrue(result.isSuccess)
        assertEquals(CircuitBreaker.State.CLOSED, client.state())
    }
}

/** awaitAll — async 리스트의 모든 결과를 기다린다. */
private suspend fun <T> List<kotlinx.coroutines.Deferred<T>>.awaitAll(): List<T> = map { it.await() }
