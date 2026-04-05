package com.tinyoscillator.core.ui

import app.cash.turbine.test
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStateTest {

    // ── 확장 프로퍼티 ──────────────────────────────────────────
    @Test
    fun `isLoading true only for Loading state`() {
        assertTrue(UiState.Loading.isLoading)
        assertFalse(UiState.Idle.isLoading)
        assertFalse(UiState.Success("data").isLoading)
        assertFalse(UiState.Error<String>("error").isLoading)
    }

    @Test
    fun `isError true only for Error state`() {
        assertTrue(UiState.Error<String>("err").isError)
        assertFalse(UiState.Success("data").isError)
        assertFalse(UiState.Loading.isError)
    }

    @Test
    fun `dataOrNull returns data from Success`() {
        val state = UiState.Success("hello")
        assertEquals("hello", state.dataOrNull)
    }

    @Test
    fun `dataOrNull returns staleData from Error`() {
        val state = UiState.Error("err", staleData = "old_data")
        assertEquals("old_data", state.dataOrNull)
    }

    @Test
    fun `dataOrNull returns null from Loading`() =
        assertNull(UiState.Loading.dataOrNull)

    @Test
    fun `dataOrNull returns null from Idle`() =
        assertNull(UiState.Idle.dataOrNull)

    @Test
    fun `dataOrNull returns null from Error without stale`() {
        val state = UiState.Error<String>("err", staleData = null)
        assertNull(state.dataOrNull)
    }

    // ── asUiState 변환 ─────────────────────────────────────────
    @Test
    fun `asUiState emits Loading then Success`() = runTest {
        val f = flow { emit("result") }.asUiState()
        f.test {
            assertTrue(awaitItem() is UiState.Loading)
            assertEquals("result", (awaitItem() as UiState.Success).data)
            awaitComplete()
        }
    }

    @Test
    fun `asUiState emits Loading then Error on exception`() = runTest {
        val f = flow<String> { throw RuntimeException("fail") }.asUiState()
        f.test {
            assertTrue(awaitItem() is UiState.Loading)
            val error = awaitItem()
            assertTrue(error is UiState.Error)
            assertEquals("fail", (error as UiState.Error).message)
            awaitComplete()
        }
    }

    // ── asUiStateWithStale ─────────────────────────────────────
    @Test
    fun `asUiStateWithStale preserves last success on error`() = runTest {
        val f = flow {
            emit("first_data")
            throw RuntimeException("network error")
        }.asUiStateWithStale()

        f.test {
            assertTrue(awaitItem() is UiState.Loading)
            val success = awaitItem() as UiState.Success
            assertEquals("first_data", success.data)
            val error = awaitItem() as UiState.Error
            assertEquals("first_data", error.staleData)
            assertEquals("network error", error.message)
            awaitComplete()
        }
    }

    @Test
    fun `asUiStateWithStale staleData is null on first error`() = runTest {
        val f = flow<String> { throw RuntimeException("first fail") }
            .asUiStateWithStale()
        f.test {
            assertTrue(awaitItem() is UiState.Loading)
            val error = awaitItem() as UiState.Error
            assertNull(error.staleData)
            awaitComplete()
        }
    }

    // ── UiState 분기 로직 ────────────────────────────��────────
    @Test
    fun `error with staleData should show stale content`() {
        val state = UiState.Error("err", staleData = "old")
        val showStale = state is UiState.Error && state.staleData != null
        assertTrue(showStale)
    }

    @Test
    fun `error without staleData should show error content`() {
        val state = UiState.Error<String>("err", staleData = null)
        val showStale = state is UiState.Error && state.staleData != null
        assertFalse(showStale)
    }
}
