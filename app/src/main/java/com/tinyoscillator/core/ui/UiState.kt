package com.tinyoscillator.core.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 앱 전역 공통 UiState.
 * staleData: 오류 발생 시 마지막 성공 데이터를 유지 (stale-while-revalidate)
 */
sealed interface UiState<out T> {
    data object Idle    : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class  Success<T>(val data: T) : UiState<T>
    data class  Error<T>(
        val message:   String,
        val staleData: T? = null,
        val retryable: Boolean = true,
    ) : UiState<T>
}

val <T> UiState<T>.isLoading: Boolean get() = this is UiState.Loading
val <T> UiState<T>.isError:   Boolean get() = this is UiState.Error
val <T> UiState<T>.dataOrNull: T? get() = when (this) {
    is UiState.Success -> data
    is UiState.Error   -> staleData
    else               -> null
}

/** Flow<T>를 UiState<T> Flow로 변환 */
fun <T> Flow<T>.asUiState(): Flow<UiState<T>> = flow {
    emit(UiState.Loading)
    try {
        collect { value -> emit(UiState.Success(value)) }
    } catch (e: Exception) {
        emit(UiState.Error(e.message ?: "알 수 없는 오류"))
    }
}

/** Stale-while-revalidate: 오류 시 이전 성공 데이터를 함께 전달 */
fun <T> Flow<T>.asUiStateWithStale(): Flow<UiState<T>> = flow {
    var lastSuccess: T? = null
    emit(UiState.Loading)
    try {
        collect { value ->
            lastSuccess = value
            emit(UiState.Success(value))
        }
    } catch (e: Exception) {
        emit(UiState.Error(e.message ?: "오류 발생", staleData = lastSuccess))
    }
}
