package com.tinyoscillator.core.api

/**
 * Converts a Throwable (typically an ApiError) to a user-facing Korean error message.
 */
fun Throwable.toUserMessage(): String = when (this) {
    is ApiError.NoApiKeyError -> "API 키가 설정되지 않았습니다. 설정에서 API 키를 입력해주세요."
    is ApiError.AuthError -> "API 인증에 실패했습니다. API 키를 확인해주세요."
    is ApiError.NetworkError -> "네트워크 연결을 확인해주세요."
    is ApiError.TimeoutError -> "서버 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요."
    is kotlinx.coroutines.TimeoutCancellationException -> "분석 시간이 초과되었습니다. 잠시 후 다시 시도해주세요."
    else -> "분석 실패: ${message ?: "알 수 없는 오류"}"
}
