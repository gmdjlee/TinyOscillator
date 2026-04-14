package com.tinyoscillator.core.api

import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Assert.*
import org.junit.Test

/**
 * ApiError.toUserMessage() 확장함수 테스트.
 *
 * 각 ApiError 서브타입에 대해 올바른 한국어 메시지를 반환하는지 검증.
 */
class ApiErrorExtensionsTest {

    @Test
    fun `NoApiKeyError는 API 키 설정 안내 메시지 반환`() {
        val error = ApiError.NoApiKeyError()
        val message = error.toUserMessage()

        assertTrue("API 키 관련 메시지", message.contains("API 키가 설정되지 않았습니다"))
        assertTrue("설정 안내 포함", message.contains("설정에서 API 키를 입력"))
    }

    @Test
    fun `AuthError는 인증 실패 메시지 반환`() {
        val error = ApiError.AuthError("인증 실패")
        val message = error.toUserMessage()

        assertTrue("인증 실패 메시지", message.contains("API 인증에 실패했습니다"))
        assertTrue("API 키 확인 안내", message.contains("API 키를 확인"))
    }

    @Test
    fun `NetworkError는 네트워크 확인 메시지 반환`() {
        val error = ApiError.NetworkError("연결 불가")
        val message = error.toUserMessage()

        assertTrue("네트워크 확인 메시지", message.contains("네트워크 연결을 확인"))
    }

    @Test
    fun `TimeoutError는 서버 응답 시간 초과 메시지 반환`() {
        val error = ApiError.TimeoutError("타임아웃")
        val message = error.toUserMessage()

        assertTrue("서버 응답 시간 초과", message.contains("서버 응답 시간이 초과"))
        assertTrue("재시도 안내", message.contains("잠시 후 다시 시도"))
    }

    @Test
    fun `TimeoutCancellationException은 분석 시간 초과 메시지 반환`() {
        // TimeoutCancellationException은 internal 생성자라 특별히 처리
        val error: Throwable = try {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeout(1) {
                    kotlinx.coroutines.delay(1000)
                }
            }
            throw AssertionError("TimeoutCancellationException이 발생해야 함")
        } catch (e: TimeoutCancellationException) {
            e
        }

        val message = error.toUserMessage()

        assertTrue("분석 시간 초과 메시지", message.contains("분석 시간이 초과"))
        assertTrue("재시도 안내", message.contains("잠시 후 다시 시도"))
    }

    @Test
    fun `알 수 없는 예외는 기본 실패 메시지 반환`() {
        val error = RuntimeException("예상치 못한 오류")
        val message = error.toUserMessage()

        assertTrue("분석 실패 메시지", message.contains("분석 실패"))
        assertTrue("원본 메시지 포함", message.contains("예상치 못한 오류"))
    }

    @Test
    fun `null 메시지의 알 수 없는 예외는 알 수 없는 오류 메시지`() {
        val error = RuntimeException(null as String?)
        val message = error.toUserMessage()

        assertTrue("분석 실패 메시지", message.contains("분석 실패"))
        assertTrue("알 수 없는 오류", message.contains("알 수 없는 오류"))
    }

    @Test
    fun `ApiCallError는 else 분기로 기본 메시지 반환`() {
        val error = ApiError.ApiCallError(500, "서버 오류")
        val message = error.toUserMessage()

        assertTrue("분석 실패 메시지", message.contains("분석 실패"))
    }

    @Test
    fun `ParseError는 else 분기로 기본 메시지 반환`() {
        val error = ApiError.ParseError("JSON 파싱 실패")
        val message = error.toUserMessage()

        assertTrue("분석 실패 메시지", message.contains("분석 실패"))
    }

    @Test
    fun `CircuitBreakerOpenError는 일시 중단 메시지 반환`() {
        val error = ApiError.CircuitBreakerOpenError()
        val message = error.toUserMessage()

        assertTrue("일시 중단 메시지", message.contains("일시 중단"))
        assertTrue("재시도 안내", message.contains("잠시 후 다시 시도"))
    }

    @Test
    fun `CircuitBreakerOpenError는 NetworkError와 다른 메시지를 반환`() {
        val cbMessage = ApiError.CircuitBreakerOpenError().toUserMessage()
        val netMessage = ApiError.NetworkError("net").toUserMessage()

        assertNotEquals("서킷 브레이커와 네트워크 에러는 다른 메시지", cbMessage, netMessage)
    }

    @Test
    fun `각 에러 타입의 메시지가 서로 다르다`() {
        val messages = listOf(
            ApiError.NoApiKeyError().toUserMessage(),
            ApiError.AuthError("auth").toUserMessage(),
            ApiError.NetworkError("net").toUserMessage(),
            ApiError.CircuitBreakerOpenError().toUserMessage(),
            ApiError.TimeoutError("timeout").toUserMessage(),
            RuntimeException("unknown").toUserMessage()
        )

        // 모든 메시지가 고유해야 함
        assertEquals("모든 메시지 고유", messages.size, messages.toSet().size)
    }
}
