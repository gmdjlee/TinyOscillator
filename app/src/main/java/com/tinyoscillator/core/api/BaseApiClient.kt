package com.tinyoscillator.core.api

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
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

    /**
     * Kiwoom/KIS/AI 등 세 API 클라이언트에 중복되던 "서킷 브레이커 게이트 +
     * 단일 auth 재시도 + 재시도 간격 루프 + 서킷 브레이커 기록" 시퀀스를 한 곳에서
     * 처리하는 헬퍼. 각 클라이언트는 호출 함수(callOnce)만 람다로 넘긴다.
     *
     * 동작 순서:
     *  1. [isInRequestScope]가 false이면 [CircuitBreaker.tryAcquire]로 차단 여부 확인.
     *     차단 시 즉시 [ApiError.CircuitBreakerOpenError] 실패 반환.
     *  2. `call()` 1회 실행.
     *  3. [onAuthFailure]가 주어졌고 결과가 [ApiError.isAuthError]이면 토큰 갱신 콜백 호출 후
     *     1초 대기, `call()` 1회 재시도.
     *  4. 아직 실패이고 [retryableFilter]가 true이면 [retryDelaysMs] 간격으로
     *     최대 `retryDelaysMs.size`회 재시도. 성공하거나 [retryableFilter]가 false
     *     되는 순간 중단.
     *  5. [isInRequestScope]가 false인 경우에만 최종 결과를 브레이커에 기록.
     *
     * @param tag 로그 prefix (예: API ID, trId).
     * @param retryDelaysMs 각 재시도 전 대기 시간(ms). 크기가 재시도 최대 횟수.
     * @param onAuthFailure auth 오류 시 토큰 갱신 콜백. null이면 auth 재시도 생략 (AI API 등).
     * @param retryableFilter 예외가 재시도 대상인지 판정. 기본은 [ApiError.isRetriableError].
     * @param call 실제 API 호출 람다. 호출 시마다 rate-limit/parse 내부적으로 수행.
     */
    protected suspend fun <T> executeWithRetry(
        tag: String,
        retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
        onAuthFailure: (suspend () -> Unit)? = null,
        retryableFilter: (Throwable?) -> Boolean = { ApiError.isRetriableError(it) },
        call: suspend () -> Result<T>,
    ): Result<T> {
        val inRequestScope = isInRequestScope()
        if (!inRequestScope && !circuitBreaker.tryAcquire()) {
            Timber.w("%s 서킷 브레이커 차단 → 즉시 실패 (state=%s)", tag, circuitBreaker.currentState)
            return Result.failure(ApiError.CircuitBreakerOpenError())
        }

        var result = call()

        // Auth retry (1회, Kiwoom/KIS 전용)
        if (onAuthFailure != null) {
            val err = result.exceptionOrNull()
            if (err != null && ApiError.isAuthError(err)) {
                Timber.w("%s 인증 오류, 토큰 갱신 후 재시도", tag)
                onAuthFailure()
                delay(1000L)
                result = call()
            }
        }

        // Retriable retry loop
        if (result.isFailure && retryableFilter(result.exceptionOrNull())) {
            for (attempt in retryDelaysMs.indices) {
                delay(retryDelaysMs[attempt])
                Timber.d("%s 재시도 %d/%d", tag, attempt + 1, retryDelaysMs.size)
                result = call()
                if (result.isSuccess || !retryableFilter(result.exceptionOrNull())) break
            }
        }

        if (!inRequestScope) {
            updateCircuitBreaker(result)
        }
        return result
    }

    companion object {
        /**
         * 공통 재시도 간격 (ms). KIS/Kiwoom 등 대부분의 외부 API에 적용.
         * AI API처럼 더 긴 간격이 필요한 경우 호출자가 직접 리스트를 전달한다.
         */
        val DEFAULT_RETRY_DELAYS_MS: List<Long> = listOf(1000L, 2000L)

        /** AI API(Claude/Gemini) 전용 재시도 간격 — Rate limit policy상 더 긴 간격 필요. */
        val AI_RETRY_DELAYS_MS: List<Long> = listOf(2000L, 4000L)
    }
}
