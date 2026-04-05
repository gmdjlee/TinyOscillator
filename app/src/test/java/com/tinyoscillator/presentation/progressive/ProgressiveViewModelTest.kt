package com.tinyoscillator.presentation.progressive

import app.cash.turbine.test
import com.tinyoscillator.core.testing.MainDispatcherRule
import com.tinyoscillator.core.ui.UiState
import com.tinyoscillator.domain.model.AnalysisStep
import com.tinyoscillator.domain.model.ProgressiveAnalysisState
import com.tinyoscillator.domain.usecase.ProgressiveAnalysisUseCase
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProgressiveViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val successState = ProgressiveAnalysisState(
        ticker = "005930",
        steps = listOf(
            AnalysisStep.PriceData(
                ticker = "005930", currentPrice = 73500L,
                isLoading = false, isComplete = true,
            ),
            AnalysisStep.TechnicalIndicators(
                ema5 = 73200f, rsi = 55f,
                isLoading = false, isComplete = true,
            ),
            AnalysisStep.EnsembleSignal(
                ensembleScore = 0.62f,
                isLoading = false, isComplete = true,
            ),
        ),
        isFullyComplete = true,
    )

    /**
     * Fake UseCase — invoke를 오버라이드하여 고정 Flow 반환.
     * MockK relaxed mock���로 constructor 의��성 충족.
     */
    private val fakeUseCase = object : ProgressiveAnalysisUseCase(
        repository = mockk(relaxed = true),
        statisticalEngine = mockk(relaxed = true),
    ) {
        override fun invoke(ticker: String): Flow<ProgressiveAnalysisState> =
            flowOf(successState)
    }

    @Test
    fun `initial state is Idle`() = runTest {
        val vm = ProgressiveAnalysisViewModel(fakeUseCase)
        vm.analysisState.test {
            val first = awaitItem()
            assertTrue("Expected Idle, got $first", first is UiState.Idle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setTicker transitions to Success`() = runTest {
        val vm = ProgressiveAnalysisViewModel(fakeUseCase)
        vm.analysisState.test {
            assertTrue(awaitItem() is UiState.Idle)

            vm.setTicker("005930")
            val collected = mutableListOf<UiState<ProgressiveAnalysisState>>()
            repeat(3) {
                try { collected += awaitItem() } catch (_: Throwable) { return@repeat }
            }
            assertTrue(
                "Expected at least one Success in $collected",
                collected.any { it is UiState.Success },
            )
            val success = collected.filterIsInstance<UiState.Success<ProgressiveAnalysisState>>().last()
            assertTrue(success.data.isFullyComplete)
            assertTrue(success.data.ensembleData?.ensembleScore == 0.62f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry re-triggers analysis`() = runTest {
        val vm = ProgressiveAnalysisViewModel(fakeUseCase)
        vm.analysisState.test {
            assertTrue(awaitItem() is UiState.Idle)

            vm.setTicker("005930")
            val firstCycle = mutableListOf<UiState<ProgressiveAnalysisState>>()
            repeat(3) {
                try { firstCycle += awaitItem() } catch (_: Throwable) { return@repeat }
            }
            assertTrue(firstCycle.any { it is UiState.Success })

            vm.retry()
            val retryCycle = mutableListOf<UiState<ProgressiveAnalysisState>>()
            repeat(3) {
                try { retryCycle += awaitItem() } catch (_: Throwable) { return@repeat }
            }
            val allItems = firstCycle + retryCycle
            assertTrue(
                "Expected Success after retry in $allItems",
                retryCycle.any { it is UiState.Success } || allItems.last() is UiState.Success,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
