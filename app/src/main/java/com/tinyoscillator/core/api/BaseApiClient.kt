package com.tinyoscillator.core.api

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * API 클라이언트 공통 인프라: 레이트 리밋, 서킷 브레이커, 요청 단위 집계.
 *
 * KiwoomApiClient, KisApiClient, AiApiClient 공통 패턴 추출.
 *
 * 서킷 브레이커 연동 규약:
 * - 독립 호출(`call()`/`get()`/`analyze()`): 호출 단위로 tryAcquire/update 수행.
 * - `executeRequest { ... }` 블록 내부: 개별 호출은 CB를 건너뛰고, 블록 완료 시
 *   합산 결과로 tryAcquire/update를 단 한 번 수행. 병렬 호출 실패가 CB 카운트에
 *   증폭되는 것을 방지한다.
 *
 * 인스턴스별 scope 마커를 사용하므로, 서로 다른 ApiClient의 `executeRequest`가
 * 중첩되더라도 각자 자신의 scope만 인식한다.
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

    /** 이 ApiClient 인스턴스 전용 RequestScope 키. */
    private val requestScopeKey = object : CoroutineContext.Key<RequestScope> {}

    /** 이 ApiClient 인스턴스 전용 RequestScope 엘리먼트. */
    private val requestScopeElement: RequestScope = RequestScope(requestScopeKey)

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

    /**
     * 현재 코루틴이 이 ApiClient의 `executeRequest` 스코프 내부에 있는지 확인한다.
     * 내부 `call()` / `get()` / `analyze()`는 이 값이 true일 때 CB 검사/기록을
     * 건너뛰고, 상위 `executeRequest`가 합산 결과를 단일 이벤트로 처리한다.
     */
    protected suspend fun isInRequestScope(): Boolean =
        currentCoroutineContext()[requestScopeKey] != null

    /**
     * 하나의 "요청"을 구성하는 블록을 실행한다. 블록 내부에서 몇 개의 API 호출이
     * 이뤄지든(직렬·병렬 무관), 서킷 브레이커에는 단일 결과만 기록된다.
     *
     * - 시작 시 `tryAcquire()`로 차단 여부를 판정한다. 차단 시 block은 실행되지
     *   않고 즉시 `CircuitBreakerOpenError` 실패를 반환.
     * - 블록 내부 `call()` / `get()` / `analyze()`는 자신의 CB 처리를 건너뛴다
     *   ([isInRequestScope]가 true이므로).
     * - 블록이 정상 반환하면 `Result.success` + recordSuccess.
     * - 블록이 예외를 throw하면 `ApiError`로 매핑하여 `Result.failure`로 감싸고,
     *   에러 유형에 따라 필터링(비재시도성 실패는 브레이커에 반영되지 않음).
     * - `CancellationException`은 원형 그대로 다시 throw하며 CB는 갱신하지 않음.
     */
    suspend fun <T> executeRequest(block: suspend () -> T): Result<T> {
        if (!circuitBreaker.tryAcquire()) {
            return Result.failure(ApiError.CircuitBreakerOpenError())
        }

        val result: Result<T> = try {
            val value = withContext(requestScopeElement) { block() }
            Result.success(value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val apiErr = if (e is ApiError) e else ApiError.mapException(e)
            Result.failure(apiErr)
        }

        updateCircuitBreaker(result)
        return result
    }

    private class RequestScope(key: CoroutineContext.Key<RequestScope>) :
        AbstractCoroutineContextElement(key)
}
