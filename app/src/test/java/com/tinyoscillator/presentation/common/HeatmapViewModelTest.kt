package com.tinyoscillator.presentation.common

import com.tinyoscillator.domain.model.HeatmapData
import com.tinyoscillator.domain.usecase.BuildHeatmapUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * [HeatmapViewModel] 로딩·윈도우 변경·에러 처리 검증 (P8-5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HeatmapViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var buildHeatmapUseCase: BuildHeatmapUseCase

    private val sampleData = HeatmapData(
        tickers = listOf("005930"),
        tickerNames = mapOf("005930" to "삼성전자"),
        dates = listOf(1L, 2L),
        dateLabels = listOf("07.20", "07.21"),
        scores = mapOf("005930" to listOf(0.6f, 0.7f)),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        buildHeatmapUseCase = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init에서 20일 히트맵을 로드한다`() = runTest {
        coEvery { buildHeatmapUseCase(20) } returns sampleData

        val vm = HeatmapViewModel(buildHeatmapUseCase)
        advanceUntilIdle()

        val state = vm.heatmapState.value
        assertEquals(20, state.windowDays)
        assertEquals(sampleData, state.data)
        assertFalse(state.isLoading)
        coVerify { buildHeatmapUseCase(20) }
    }

    @Test
    fun `setWindowDays는 윈도우를 갱신하고 재로드한다`() = runTest {
        coEvery { buildHeatmapUseCase(any()) } returns sampleData

        val vm = HeatmapViewModel(buildHeatmapUseCase)
        advanceUntilIdle()

        vm.setWindowDays(10)
        advanceUntilIdle()

        assertEquals(10, vm.heatmapState.value.windowDays)
        coVerify { buildHeatmapUseCase(10) }
    }

    @Test
    fun `로드 실패 시 isLoading 해제·data는 null 유지`() = runTest {
        coEvery { buildHeatmapUseCase(any()) } throws RuntimeException("DB 오류")

        val vm = HeatmapViewModel(buildHeatmapUseCase)
        advanceUntilIdle()

        val state = vm.heatmapState.value
        assertFalse(state.isLoading)
        assertNull(state.data)
    }
}
