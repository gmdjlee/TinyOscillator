package com.tinyoscillator.core.api

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OkHttp Call을 코루틴 suspend 함수로 변환.
 *
 * 동기 execute() 대신 비동기 enqueue()를 사용하여 스레드를 블로킹하지 않음.
 * 코루틴 취소 시 OkHttp Call도 자동 취소됨.
 */
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
        cancel()
    }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            // 취소 경합(resume과 cancel 동시) 시 Response가 소비되지 않고 누수될 수 있어
            // onCancellation 핸들러로 close를 보장한다(Phase 3-4).
            continuation.resume(response) { response.close() }
        }

        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) {
                continuation.resumeWithException(e)
            }
        }
    })
}
