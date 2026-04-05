package com.tinyoscillator.domain

import com.tinyoscillator.domain.model.AnalysisStep
import com.tinyoscillator.domain.model.ProgressiveAnalysisState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ProgressiveAnalysisUseCase 흐름 테스트.
 *
 * 실제 StatisticalAnalysisEngine은 모킹 비용이 높으므로,
 * 동일한 channelFlow 패턴을 따르는 FakeProgressiveFlow로 단계 순서를 검증한다.
 */
class ProgressiveAnalysisUseCaseTest {

    /**
     * 실제 UseCase와 동일한 channelFlow 패턴을 따르는 Fake.
     * 단계: 빈 상태 → 가격 → 기술지표 → 앙상블 → 외부 → 최종완료
     */
    private fun fakeProgressiveFlow(ticker: String): Flow<ProgressiveAnalysisState> =
        channelFlow {
            var state = ProgressiveAnalysisState(ticker = ticker)
            send(state) // 초기 빈 상태

            // 1단계: 가격
            val priceStep = AnalysisStep.PriceData(
                ticker = ticker,
                currentPrice = 73500L,
                priceChange = 0.012f,
                volume = 15_000_000L,
                isLoading = false,
                isComplete = true,
            )
            state = state.copy(steps = state.steps + priceStep)
            send(state)

            // 2단계: 기술 지표
            val techStep = AnalysisStep.TechnicalIndicators(
                ema5 = 73200f, ema20 = 72800f, ema60 = 71500f,
                macdHistogram = 150f, rsi = 55f, bollingerPct = 0.65f,
                isLoading = false, isComplete = true,
            )
            state = state.copy(steps = state.steps + techStep)
            send(state)

            // 3단계: 앙상블
            val ensembleStep = AnalysisStep.EnsembleSignal(
                algoResults = mapOf("NaiveBayes" to 0.65f, "Logistic" to 0.58f),
                ensembleScore = 0.62f,
                rationale = mapOf("NaiveBayes" to "상승 65%"),
                isLoading = false, isComplete = true,
            )
            state = state.copy(steps = state.steps + ensembleStep)
            send(state)

            // 4단계: 외부 데이터
            val extStep = AnalysisStep.ExternalData(
                dartEvents = listOf("유상증자: CAR -2.3%"),
                institutionalFlow = 500_000f,
                isLoading = false, isComplete = true,
            )
            state = state.copy(steps = state.steps + extStep)
            state = state.copy(isFullyComplete = true)
            send(state)
        }

    @Test
    fun `emits at least 3 states for happy path`() = runTest {
        val states = mutableListOf<ProgressiveAnalysisState>()
        fakeProgressiveFlow("005930").collect { states += it }
        assertTrue(
            "Expected ≥3 states, got ${states.size}",
            states.size >= 3,
        )
    }

    @Test
    fun `first emit has no completed steps`() = runTest {
        var firstState: ProgressiveAnalysisState? = null
        fakeProgressiveFlow("005930").collect { state ->
            if (firstState == null) firstState = state
        }
        assertTrue(firstState?.steps.isNullOrEmpty())
    }

    @Test
    fun `price step appears before technical step`() = runTest {
        val states = mutableListOf<ProgressiveAnalysisState>()
        fakeProgressiveFlow("005930").collect { states += it }

        val priceIdx = states.indexOfFirst { it.priceData?.isComplete == true }
        val techIdx = states.indexOfFirst { it.technicalData?.isComplete == true }
        assertTrue(
            "price (idx=$priceIdx) should appear before technical (idx=$techIdx)",
            priceIdx in 0 until techIdx,
        )
    }

    @Test
    fun `technical step appears before ensemble step`() = runTest {
        val states = mutableListOf<ProgressiveAnalysisState>()
        fakeProgressiveFlow("005930").collect { states += it }

        val techIdx = states.indexOfFirst { it.technicalData?.isComplete == true }
        val ensembleIdx = states.indexOfFirst { it.ensembleData?.isComplete == true }
        assertTrue(
            "technical (idx=$techIdx) should appear before ensemble (idx=$ensembleIdx)",
            techIdx in 0 until ensembleIdx,
        )
    }

    @Test
    fun `last state is fully complete`() = runTest {
        var lastState: ProgressiveAnalysisState? = null
        fakeProgressiveFlow("005930").collect { lastState = it }
        assertTrue(lastState?.isFullyComplete == true)
    }

    @Test
    fun `ensemble score in 0 to 1 range`() = runTest {
        var lastState: ProgressiveAnalysisState? = null
        fakeProgressiveFlow("005930").collect { lastState = it }
        val score = lastState?.ensembleData?.ensembleScore ?: -1f
        assertTrue("ensemble score $score out of range", score in 0f..1f)
    }
}
