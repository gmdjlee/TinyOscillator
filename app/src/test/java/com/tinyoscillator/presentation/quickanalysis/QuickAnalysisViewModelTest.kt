package com.tinyoscillator.presentation.quickanalysis

import android.app.Application
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.data.repository.StockRepository
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.usecase.CalcDemarkTDUseCase
import com.tinyoscillator.domain.usecase.CalcOscillatorUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickAnalysisViewModelTest {

    private lateinit var application: Application
    private lateinit var repository: StockRepository
    private lateinit var apiConfigProvider: ApiConfigProvider
    private lateinit var viewModel: QuickAnalysisViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testTicker = "005930"
    private val testName = "삼성전자"

    private fun generateDailyData(days: Int, startPrice: Int = 1000): List<DailyTrading> {
        return (0 until days).map { i ->
            DailyTrading(
                date = String.format("2024%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = 100_000_000_000_000L,
                foreignNetBuy = 1_000_000L * (i % 5),
                instNetBuy = 500_000L * (i % 3),
                closePrice = startPrice + i * 10
            )
        }
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        apiConfigProvider = mockk(relaxed = true)

        // Mock NetworkUtils (Kotlin object → mockkObject)
        mockkObject(com.tinyoscillator.core.network.NetworkUtils)
        every {
            com.tinyoscillator.core.network.NetworkUtils.isNetworkAvailable(any())
        } returns true

        viewModel = QuickAnalysisViewModel(
            application = application,
            repository = repository,
            calcOscillator = CalcOscillatorUseCase(),   // 실제 계산 로직 사용
            calcDemarkTD = CalcDemarkTDUseCase(),       // 실제 계산 로직 사용
            apiConfigProvider = apiConfigProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `초기 상태는 Loading이다`() = runTest {
        assertTrue(viewModel.state.value is QuickAnalysisState.Loading)
    }

    @Test
    fun `load 성공 시 Success 상태와 요약이 생성된다`() = runTest {
        val dailyData = generateDailyData(30)
        coEvery {
            repository.getDailyTradingData(testTicker, any(), any(), any())
        } returns dailyData

        viewModel.load(testTicker, testName)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Success but got $state", state is QuickAnalysisState.Success)
        val summary = (state as QuickAnalysisState.Success).summary
        assertEquals(testTicker, summary.ticker)
        assertEquals(testName, summary.stockName)
        assertEquals(dailyData.last().closePrice, summary.closePrice)
        assertEquals(dailyData.last().date, summary.date)
    }

    @Test
    fun `등락률은 마지막 이틀 종가로 계산된다`() = runTest {
        // 종가 1000 → 1290 (30일, +10/일): 마지막 이틀 1280 → 1290
        val dailyData = generateDailyData(30)
        coEvery {
            repository.getDailyTradingData(testTicker, any(), any(), any())
        } returns dailyData

        viewModel.load(testTicker, testName)
        advanceUntilIdle()

        val summary = (viewModel.state.value as QuickAnalysisState.Success).summary
        val expected = (1290 - 1280) * 100.0 / 1280
        assertNotNull(summary.changePct)
        assertEquals(expected, summary.changePct!!, 1e-9)
    }

    @Test
    fun `빈 데이터 시 Error 상태가 된다`() = runTest {
        coEvery {
            repository.getDailyTradingData(testTicker, any(), any(), any())
        } returns emptyList()

        viewModel.load(testTicker, testName)
        advanceUntilIdle()

        assertTrue(viewModel.state.value is QuickAnalysisState.Error)
    }

    @Test
    fun `예외 발생 시 Error 상태가 된다`() = runTest {
        coEvery {
            repository.getDailyTradingData(testTicker, any(), any(), any())
        } throws RuntimeException("API error")

        viewModel.load(testTicker, testName)
        advanceUntilIdle()

        assertTrue(viewModel.state.value is QuickAnalysisState.Error)
    }

    @Test
    fun `네트워크 미연결 시 Error 상태가 된다`() = runTest {
        every {
            com.tinyoscillator.core.network.NetworkUtils.isNetworkAvailable(any())
        } returns false

        viewModel.load(testTicker, testName)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Error but got $state", state is QuickAnalysisState.Error)
        assertTrue((state as QuickAnalysisState.Error).message.contains("네트워크"))
    }

    @Test
    fun `같은 종목 재요청 시 재로드하지 않는다`() = runTest {
        val dailyData = generateDailyData(30)
        coEvery {
            repository.getDailyTradingData(testTicker, any(), any(), any())
        } returns dailyData

        viewModel.load(testTicker, testName)
        advanceUntilIdle()
        viewModel.load(testTicker, testName)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.getDailyTradingData(testTicker, any(), any(), any())
        }
    }

    @Test
    fun `다른 종목 요청 시 재로드한다`() = runTest {
        val otherTicker = "000660"
        coEvery {
            repository.getDailyTradingData(any(), any(), any(), any())
        } returns generateDailyData(30)

        viewModel.load(testTicker, testName)
        advanceUntilIdle()
        viewModel.load(otherTicker, "SK하이닉스")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.getDailyTradingData(testTicker, any(), any(), any())
        }
        coVerify(exactly = 1) {
            repository.getDailyTradingData(otherTicker, any(), any(), any())
        }
        val summary = (viewModel.state.value as QuickAnalysisState.Success).summary
        assertEquals(otherTicker, summary.ticker)
    }

    @Test
    fun `Error 상태에서 같은 종목 재요청 시 재시도한다`() = runTest {
        coEvery {
            repository.getDailyTradingData(testTicker, any(), any(), any())
        } throws RuntimeException("API error")

        viewModel.load(testTicker, testName)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is QuickAnalysisState.Error)

        coEvery {
            repository.getDailyTradingData(testTicker, any(), any(), any())
        } returns generateDailyData(30)

        viewModel.load(testTicker, testName)
        advanceUntilIdle()

        assertTrue(viewModel.state.value is QuickAnalysisState.Success)
        coVerify(exactly = 2) {
            repository.getDailyTradingData(testTicker, any(), any(), any())
        }
    }
}
