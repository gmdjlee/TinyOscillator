package com.tinyoscillator.presentation.demark

import android.app.Application
import com.tinyoscillator.data.repository.StockRepository
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.DemarkPeriodType
import com.tinyoscillator.domain.usecase.CalcDemarkTDUseCase
import com.tinyoscillator.core.config.ApiConfigProvider
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemarkTDViewModelTest {

    private lateinit var application: Application
    private lateinit var repository: StockRepository
    private lateinit var calcDemarkTD: CalcDemarkTDUseCase
    private lateinit var apiConfigProvider: ApiConfigProvider
    private lateinit var viewModel: DemarkTDViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testTicker = "005930"
    private val testName = "삼성전자"

    private fun generateDailyData(days: Int, startPrice: Int = 1000): List<DailyTrading> {
        return (0 until days).map { i ->
            DailyTrading(
                date = String.format("2024%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = 100_000_000_000_000L,
                foreignNetBuy = 0L,
                instNetBuy = 0L,
                closePrice = startPrice + i * 10
            )
        }
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        calcDemarkTD = CalcDemarkTDUseCase() // 실제 계산 로직 사용
        apiConfigProvider = mockk(relaxed = true)

        mockkStatic("com.tinyoscillator.presentation.settings.SettingsScreenKt")
        coEvery {
            com.tinyoscillator.presentation.settings.loadKiwoomConfig(any())
        } returns com.tinyoscillator.core.api.KiwoomApiKeyConfig(
            appKey = "test-key",
            secretKey = "test-secret"
        )

        // Mock NetworkUtils (Kotlin object → mockkObject)
        mockkObject(com.tinyoscillator.core.network.NetworkUtils)
        every {
            com.tinyoscillator.core.network.NetworkUtils.isNetworkAvailable(any())
        } returns true

        viewModel = DemarkTDViewModel(application, repository, calcDemarkTD, apiConfigProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `초기 상태는 NoStock이다`() = runTest {
        assertEquals(DemarkTDState.NoStock, viewModel.state.value)
    }

    @Test
    fun `초기 선택 기간은 DAILY이다`() = runTest {
        assertEquals(DemarkPeriodType.DAILY, viewModel.selectedPeriod.value)
    }

    @Test
    fun `loadForStock 성공 시 Success 상태가 된다`() = runTest {
        val dailyData = generateDailyData(30)
        coEvery {
            repository.getDailyTradingData(testTicker, any(), any(), any())
        } returns dailyData

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Success but got $state", state is DemarkTDState.Success)
        val success = state as DemarkTDState.Success
        assertEquals(testName, success.chartData.stockName)
        assertEquals(testTicker, success.chartData.ticker)
        assertEquals(DemarkPeriodType.DAILY, success.chartData.periodType)
    }

    @Test
    fun `loadForStock 빈 데이터 시 Error 상태가 된다`() = runTest {
        coEvery {
            repository.getDailyTradingData(testTicker, any(), any(), any())
        } returns emptyList()

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Error but got $state", state is DemarkTDState.Error)
    }

    @Test
    fun `loadForStock 예외 시 Error 상태가 된다`() = runTest {
        coEvery {
            repository.getDailyTradingData(testTicker, any(), any(), any())
        } throws RuntimeException("API error")

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Error but got $state", state is DemarkTDState.Error)
    }

    @Test
    fun `일봉에서 주봉 전환 시 재계산된다`() = runTest {
        // 6주 이상의 데이터 (주봉 리샘플링 후 5개 이상 보장)
        val dailyData = mutableListOf<DailyTrading>()
        val dates = listOf(
            // Week 2: Jan 8-12
            "20240108", "20240109", "20240110", "20240111", "20240112",
            // Week 3: Jan 15-19
            "20240115", "20240116", "20240117", "20240118", "20240119",
            // Week 4: Jan 22-26
            "20240122", "20240123", "20240124", "20240125", "20240126",
            // Week 5: Jan 29 - Feb 2
            "20240129", "20240130", "20240131", "20240201", "20240202",
            // Week 6: Feb 5-9
            "20240205", "20240206", "20240207", "20240208", "20240209",
            // Week 7: Feb 12-16
            "20240212", "20240213", "20240214", "20240215", "20240216",
        )
        dates.forEachIndexed { i, date ->
            dailyData.add(
                DailyTrading(
                    date = date,
                    marketCap = 100_000_000_000_000L,
                    foreignNetBuy = 0L,
                    instNetBuy = 0L,
                    closePrice = 1000 + i * 10
                )
            )
        }

        coEvery {
            repository.getDailyTradingData(testTicker, any(), any(), any())
        } returns dailyData

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is DemarkTDState.Success)
        val dailyResult = (viewModel.state.value as DemarkTDState.Success).chartData
        assertEquals(DemarkPeriodType.DAILY, dailyResult.periodType)

        // 주봉으로 전환
        viewModel.selectPeriod(DemarkPeriodType.WEEKLY)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Success after weekly switch but got $state", state is DemarkTDState.Success)
        val weeklyResult = (state as DemarkTDState.Success).chartData
        assertEquals(DemarkPeriodType.WEEKLY, weeklyResult.periodType)
        assertEquals(6, weeklyResult.rows.size) // 6주
    }

    @Test
    fun `clearStock은 NoStock 상태로 초기화한다`() = runTest {
        val dailyData = generateDailyData(30)
        coEvery {
            repository.getDailyTradingData(testTicker, any(), any(), any())
        } returns dailyData

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is DemarkTDState.Success)

        viewModel.clearStock()
        assertEquals(DemarkTDState.NoStock, viewModel.state.value)
    }

    @Test
    fun `같은 기간 선택 시 재계산하지 않는다`() = runTest {
        val dailyData = generateDailyData(30)
        coEvery {
            repository.getDailyTradingData(testTicker, any(), any(), any())
        } returns dailyData

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        val firstState = viewModel.state.value

        // 같은 기간 재선택
        viewModel.selectPeriod(DemarkPeriodType.DAILY)
        advanceUntilIdle()

        // 상태가 변경되지 않아야 함 (같은 참조)
        assertSame(firstState, viewModel.state.value)
    }

    @Test
    fun `네트워크 미연결 시 Error 상태가 된다`() = runTest {
        every {
            com.tinyoscillator.core.network.NetworkUtils.isNetworkAvailable(any())
        } returns false

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Error but got $state", state is DemarkTDState.Error)
        assertTrue((state as DemarkTDState.Error).message.contains("네트워크"))
    }

    @Test
    fun `5개 미만 데이터 시 계산 오류 Error가 된다`() = runTest {
        val shortData = generateDailyData(3)
        coEvery {
            repository.getDailyTradingData(testTicker, any(), any(), any())
        } returns shortData

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Error for short data but got $state", state is DemarkTDState.Error)
        assertTrue((state as DemarkTDState.Error).message.contains("5개"))
    }
}
