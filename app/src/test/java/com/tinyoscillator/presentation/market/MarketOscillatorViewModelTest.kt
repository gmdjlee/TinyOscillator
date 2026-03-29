package com.tinyoscillator.presentation.market

import com.tinyoscillator.data.repository.MarketIndicatorRepository
import com.tinyoscillator.domain.model.MarketOscillator
import com.tinyoscillator.domain.model.MarketOscillatorState
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MarketOscillatorViewModelTest {

    private lateinit var repository: MarketIndicatorRepository
    private lateinit var viewModel: MarketOscillatorViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val sampleOscillator = MarketOscillator(
        id = "KOSPI-2026-03-05",
        market = "KOSPI",
        date = "2026-03-05",
        indexValue = 2500.0,
        oscillator = 30.0,
        lastUpdated = System.currentTimeMillis()
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        repository = mockk(relaxed = true)

        // Default repository mocks
        coEvery { repository.getDataCount("KOSPI") } returns 0
        coEvery { repository.getDataCount("KOSDAQ") } returns 0
        coEvery { repository.getLatestData(any()) } returns null
        every { repository.getDataByDateRange(any(), any(), any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): MarketOscillatorViewModel {
        return MarketOscillatorViewModel(repository)
    }

    // ==========================================================
    // 초기 상태 테스트
    // ==========================================================

    @Test
    fun `초기 state는 Loading이다`() = runTest {
        viewModel = createViewModel()
        // 즉시 확인 (advanceUntilIdle 전)
        assertEquals(MarketOscillatorState.Loading, viewModel.state.value)
    }

    @Test
    fun `init 완료 후 데이터 없으면 Idle(hasData=false)가 된다`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Idle but got $state", state is MarketOscillatorState.Idle)
        assertFalse((state as MarketOscillatorState.Idle).hasData)
        assertNull(state.latestDate)
    }

    @Test
    fun `init 완료 후 데이터 있으면 Idle(hasData=true)가 된다`() = runTest {
        coEvery { repository.getDataCount("KOSPI") } returns 10
        coEvery { repository.getLatestData("KOSPI") } returns sampleOscillator

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Idle but got $state", state is MarketOscillatorState.Idle)
        assertTrue((state as MarketOscillatorState.Idle).hasData)
        assertEquals("2026-03-05", state.latestDate)
    }

    // ==========================================================
    // clearMessage 테스트
    // ==========================================================

    @Test
    fun `clearMessage 호출 시 Idle 상태에서는 변경되지 않는다`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is MarketOscillatorState.Idle)

        viewModel.clearMessage()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Idle but got $state", state is MarketOscillatorState.Idle)
    }

    // ==========================================================
    // 시장 선택 및 날짜 범위 테스트
    // ==========================================================

    @Test
    fun `초기 선택 시장은 KOSPI이다`() = runTest {
        viewModel = createViewModel()
        assertEquals("KOSPI", viewModel.selectedMarket.value)
    }

    @Test
    fun `onSelectedMarketChanged 호출 시 시장이 변경된다`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { repository.getLatestData("KOSDAQ") } returns null

        viewModel.onSelectedMarketChanged("KOSDAQ")
        advanceUntilIdle()

        assertEquals("KOSDAQ", viewModel.selectedMarket.value)
    }

    // ==========================================================
    // loadDataByRange 에러 처리 테스트
    // ==========================================================

    @Test
    fun `데이터 로드 중 예외 발생 시 Error 상태가 된다`() = runTest {
        every { repository.getDataByDateRange(any(), any(), any()) } returns flow {
            throw RuntimeException("DB 조회 실패")
        }

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Error but got $state", state is MarketOscillatorState.Error)
        assertTrue((state as MarketOscillatorState.Error).message.contains("데이터 로드 실패"))
    }

    @Test
    fun `데이터 로드 성공 시 marketData가 업데이트된다`() = runTest {
        every { repository.getDataByDateRange(any(), any(), any()) } returns flowOf(listOf(sampleOscillator))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.marketData.value.size)
        assertEquals("2026-03-05", viewModel.marketData.value[0].date)
    }
}
