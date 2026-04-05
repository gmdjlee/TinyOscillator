package com.tinyoscillator.core.ui

import app.cash.turbine.test
import com.tinyoscillator.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UiStateViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial state is Idle`() {
        val scope = TestScope()
        val vm = emptyFlow<String>()
            .asUiStateWithStale()
            .stateIn(scope, SharingStarted.Eagerly, UiState.Idle)
        assertTrue(vm.value is UiState.Idle)
    }

    @Test
    fun `successful flow transitions to Success`() = runTest {
        val f = flow { emit("data") }.asUiStateWithStale()
        f.test {
            assertTrue(awaitItem() is UiState.Loading)
            assertTrue(awaitItem() is UiState.Success)
            awaitComplete()
        }
    }

    @Test
    fun `failed flow after success carries stale data`() = runTest {
        val f = flow {
            emit("good_data")
            throw Exception("err")
        }.asUiStateWithStale()

        f.test {
            assertTrue(awaitItem() is UiState.Loading)
            assertTrue(awaitItem() is UiState.Success)
            val error = awaitItem() as UiState.Error<String>
            assertEquals("good_data", error.staleData)
            awaitComplete()
        }
    }
}
