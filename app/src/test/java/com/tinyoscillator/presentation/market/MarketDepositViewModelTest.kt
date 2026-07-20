package com.tinyoscillator.presentation.market

import com.tinyoscillator.data.repository.MarketIndicatorRepository
import com.tinyoscillator.domain.model.DateRangeOption
import com.tinyoscillator.domain.model.MarketDeposit
import com.tinyoscillator.domain.model.MarketDepositChartData
import com.tinyoscillator.domain.model.MarketDepositState
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MarketDepositViewModelTest {

    private lateinit var repository: MarketIndicatorRepository
    private lateinit var viewModel: MarketDepositViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val sampleDeposits = listOf(
        MarketDeposit(
            date = "2026-03-01",
            depositAmount = 50000.0,
            depositChange = 100.0,
            creditAmount = 20000.0,
            creditChange = -50.0,
            lastUpdated = System.currentTimeMillis()
        ),
        MarketDeposit(
            date = "2026-03-02",
            depositAmount = 50100.0,
            depositChange = 100.0,
            creditAmount = 19950.0,
            creditChange = -50.0,
            lastUpdated = System.currentTimeMillis()
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): MarketDepositViewModel {
        return MarketDepositViewModel(repository)
    }

    // ==========================================================
    // 초기 상태 테스트
    // ==========================================================

    @Test
    fun `초기 state는 Idle이다`() = runTest {
        every { repository.getDepositsByDateRange(any(), any()) } returns flowOf(emptyList())

        viewModel = createViewModel()

        assertEquals(MarketDepositState.Idle, viewModel.state.value)
    }

    @Test
    fun `초기 selectedRange는 DEFAULT이다`() = runTest {
        every { repository.getDepositsByDateRange(any(), any()) } returns flowOf(emptyList())

        viewModel = createViewModel()

        assertEquals(DateRangeOption.DEFAULT, viewModel.selectedRange.value)
    }

    @Test
    fun `초기 depositData는 empty이다`() = runTest {
        every { repository.getDepositsByDateRange(any(), any()) } returns flowOf(emptyList())

        viewModel = createViewModel()

        assertEquals(MarketDepositChartData.empty(), viewModel.depositData.value)
    }

    // ==========================================================
    // loadData 성공 테스트
    // ==========================================================

    @Test
    fun `데이터 로드 성공 시 Success 상태가 된다`() = runTest {
        every { repository.getDepositsByDateRange(any(), any()) } returns flowOf(sampleDeposits)

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Success but got $state", state is MarketDepositState.Success)

        val depositData = viewModel.depositData.value
        assertEquals(2, depositData.dates.size)
        assertEquals("2026-03-01", depositData.dates[0])
        assertEquals("2026-03-02", depositData.dates[1])
        assertEquals(50000.0, depositData.depositAmounts[0], 0.01)
        assertEquals(20000.0, depositData.creditAmounts[0], 0.01)
    }

    // ==========================================================
    // loadData 실패 테스트
    // ==========================================================

    @Test
    fun `데이터가 비어있으면 Error 상태가 된다`() = runTest {
        every { repository.getDepositsByDateRange(any(), any()) } returns flowOf(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Error but got $state", state is MarketDepositState.Error)
        assertTrue((state as MarketDepositState.Error).message.contains("저장된 데이터가 없습니다"))
    }

    // ==========================================================
    // refreshData 테스트
    // ==========================================================

    @Test
    fun `refreshData 호출 시 재로드되어 Success 상태가 유지된다`() = runTest {
        every { repository.getDepositsByDateRange(any(), any()) } returns flowOf(sampleDeposits)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is MarketDepositState.Success)

        // refreshData는 refreshTrigger를 증가시켜 재로드를 강제한다(동일 데이터 → Success 유지)
        viewModel.refreshData()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Success after refresh but got $state", state is MarketDepositState.Success)
    }

    @Test
    fun `Error 상태에서 refreshData 호출 시 재로드되어 Success로 전환된다`() = runTest {
        every { repository.getDepositsByDateRange(any(), any()) } returns flowOf(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is MarketDepositState.Error)

        // 재시도 시점에 데이터가 채워진 상태로 repository 응답이 바뀐 경우를 재현
        every { repository.getDepositsByDateRange(any(), any()) } returns flowOf(sampleDeposits)

        viewModel.refreshData()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Success after retry but got $state", state is MarketDepositState.Success)
        assertEquals(2, viewModel.depositData.value.dates.size)
    }

    @Test
    fun `Error 상태에서 refreshData 호출 직후 Loading 상태로 전환된다`() = runTest {
        every { repository.getDepositsByDateRange(any(), any()) } returns flowOf(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is MarketDepositState.Error)

        viewModel.refreshData()

        val state = viewModel.state.value
        assertTrue("Expected Loading immediately after refreshData but got $state", state is MarketDepositState.Loading)
    }

    @Test
    fun `Error 상태에서 refreshData 호출 시 repository가 재호출된다`() = runTest {
        every { repository.getDepositsByDateRange(any(), any()) } returns flowOf(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.refreshData()
        advanceUntilIdle()

        verify(exactly = 2) { repository.getDepositsByDateRange(any(), any()) }
    }

    // ==========================================================
    // updateDateRange 테스트
    // ==========================================================

    @Test
    fun `updateDateRange 호출 시 selectedRange가 변경된다`() = runTest {
        every { repository.getDepositsByDateRange(any(), any()) } returns flowOf(sampleDeposits)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateDateRange(DateRangeOption.MONTH)
        advanceUntilIdle()

        assertEquals(DateRangeOption.MONTH, viewModel.selectedRange.value)
    }

    // ==========================================================
    // clearMessage 테스트
    // ==========================================================

    @Test
    fun `clearMessage 호출 시 Success 상태에서 Idle로 전환된다`() = runTest {
        every { repository.getDepositsByDateRange(any(), any()) } returns flowOf(sampleDeposits)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is MarketDepositState.Success)

        viewModel.clearMessage()

        assertEquals(MarketDepositState.Idle, viewModel.state.value)
    }

    @Test
    fun `clearMessage 호출 시 Error 상태에서는 변경되지 않는다`() = runTest {
        every { repository.getDepositsByDateRange(any(), any()) } returns flowOf(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is MarketDepositState.Error)

        viewModel.clearMessage()

        // clearMessage는 Success 상태에서만 Idle로 전환
        assertTrue(viewModel.state.value is MarketDepositState.Error)
    }
}
