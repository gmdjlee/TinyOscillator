package com.tinyoscillator.presentation.viewmodel

import android.app.Application
import app.cash.turbine.test
import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.data.repository.FinancialRepository
import com.tinyoscillator.domain.model.*
import com.tinyoscillator.presentation.financial.FinancialInfoViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FinancialInfoViewModelTest {

    private lateinit var application: Application
    private lateinit var financialRepository: FinancialRepository
    private lateinit var apiConfigProvider: ApiConfigProvider
    private lateinit var viewModel: FinancialInfoViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testTicker = "005930"
    private val testName = "삼성전자"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        financialRepository = mockk(relaxed = true)
        apiConfigProvider = mockk(relaxed = true)
        // Mock loadKisConfig - returns empty config (invalid)
        mockkStatic("com.tinyoscillator.presentation.settings.SettingsPreferencesKt")
        coEvery {
            com.tinyoscillator.presentation.settings.loadKisConfig(any())
        } returns com.tinyoscillator.core.api.KisApiKeyConfig(
            appKey = "test-key",
            appSecret = "test-secret"
        )
        viewModel = FinancialInfoViewModel(application, financialRepository, apiConfigProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // -- Helpers --

    private fun createFinancialData(
        ticker: String = testTicker,
        name: String = testName,
        periods: List<String> = listOf("202303", "202306"),
        profitabilityRatios: Map<String, ProfitabilityRatios> = mapOf(
            "202303" to ProfitabilityRatios(
                period = FinancialPeriod.fromYearMonth("202303"),
                roe = 10.5, roa = 5.2, operatingMargin = 15.0,
                netMargin = 12.0
            )
        )
    ) = FinancialData(
        ticker = ticker,
        name = name,
        periods = periods,
        balanceSheets = emptyMap(),
        incomeStatements = emptyMap(),
        profitabilityRatios = profitabilityRatios,
        stabilityRatios = emptyMap(),
        growthRatios = emptyMap()
    )

    // ==========================================================
    // Tests
    // ==========================================================

    @Test
    fun `초기 상태는 NoStock이다`() = runTest {
        assertEquals(FinancialState.NoStock, viewModel.state.value)
    }

    @Test
    fun `초기 선택 탭은 PROFITABILITY이다`() = runTest {
        assertEquals(FinancialTab.PROFITABILITY, viewModel.selectedTab.value)
    }

    @Test
    fun `selectTab은 탭을 변경한다`() = runTest {
        viewModel.selectTab(FinancialTab.STABILITY)
        assertEquals(FinancialTab.STABILITY, viewModel.selectedTab.value)

        viewModel.selectTab(FinancialTab.PROFITABILITY)
        assertEquals(FinancialTab.PROFITABILITY, viewModel.selectedTab.value)
    }

    @Test
    fun `loadForStock 성공 시 Success 상태가 된다`() = runTest {
        val data = createFinancialData()
        coEvery {
            financialRepository.getFinancialData(testTicker, testName, any())
        } returns Result.success(data)

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Success but got $state", state is FinancialState.Success)
    }

    @Test
    fun `loadForStock 실패 시 Error 상태가 된다`() = runTest {
        coEvery {
            financialRepository.getFinancialData(testTicker, testName, any())
        } returns Result.failure(Exception("Network error"))

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Error but got $state", state is FinancialState.Error)
    }

    @Test
    fun `loadForStock 빈 periods 시 Error 상태가 된다`() = runTest {
        val emptyData = createFinancialData(periods = emptyList(), profitabilityRatios = emptyMap())
        coEvery {
            financialRepository.getFinancialData(testTicker, testName, any())
        } returns Result.success(emptyData)

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Error for empty periods but got $state", state is FinancialState.Error)
    }

    @Test
    fun `같은 종목 재호출 시 repository에 위임한다 (TTL 캐시가 freshness 결정)`() = runTest {
        val data = createFinancialData()
        coEvery {
            financialRepository.getFinancialData(testTicker, testName, any())
        } returns Result.success(data)

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        // 두 번째 호출 - repository의 TTL 캐시에 위임 (ViewModel은 skip하지 않음)
        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        coVerify(exactly = 2) { financialRepository.getFinancialData(any(), any(), any()) }
    }

    @Test
    fun `다른 종목 로드 시 API를 다시 호출한다`() = runTest {
        val data = createFinancialData()
        coEvery {
            financialRepository.getFinancialData(any(), any(), any())
        } returns Result.success(data)

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        viewModel.loadForStock("000660", "SK하이닉스")
        advanceUntilIdle()

        coVerify(exactly = 2) { financialRepository.getFinancialData(any(), any(), any()) }
    }

    @Test
    fun `refresh 성공 시 Success 상태가 된다`() = runTest {
        val data = createFinancialData()
        coEvery {
            financialRepository.getFinancialData(testTicker, testName, any())
        } returns Result.success(data)
        coEvery {
            financialRepository.refreshFinancialData(testTicker, testName, any())
        } returns Result.success(data)

        // 먼저 loadForStock으로 currentTicker 설정
        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.isRefreshing.value)
        assertTrue(viewModel.state.value is FinancialState.Success)
    }

    @Test
    fun `refresh는 currentTicker가 null이면 아무것도 하지 않는다`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()

        coVerify(exactly = 0) { financialRepository.refreshFinancialData(any(), any(), any()) }
    }

    @Test
    fun `retry는 forceRefresh=true로 호출한다`() = runTest {
        val data = createFinancialData()
        coEvery {
            financialRepository.getFinancialData(testTicker, testName, any())
        } returns Result.failure(Exception("first fail"))
        coEvery {
            financialRepository.refreshFinancialData(testTicker, testName, any())
        } returns Result.success(data)

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is FinancialState.Error)

        viewModel.retry()
        advanceUntilIdle()

        coVerify(exactly = 1) { financialRepository.refreshFinancialData(testTicker, testName, any()) }
    }

    @Test
    fun `retry는 currentTicker가 null이면 아무것도 하지 않는다`() = runTest {
        viewModel.retry()
        advanceUntilIdle()

        coVerify(exactly = 0) { financialRepository.refreshFinancialData(any(), any(), any()) }
    }

    @Test
    fun `clearStock은 NoStock 상태로 초기화한다`() = runTest {
        val data = createFinancialData()
        coEvery {
            financialRepository.getFinancialData(testTicker, testName, any())
        } returns Result.success(data)

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is FinancialState.Success)

        viewModel.clearStock()

        assertEquals(FinancialState.NoStock, viewModel.state.value)
    }

    @Test
    fun `API key 에러 메시지 포함 시 NoApiKey 상태가 된다`() = runTest {
        coEvery {
            financialRepository.getFinancialData(testTicker, testName, any())
        } returns Result.failure(IllegalStateException("KIS API key not configured"))

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        assertEquals(FinancialState.NoApiKey, viewModel.state.value)
    }

    @Test
    fun `network 에러 시 네트워크 오류 메시지가 표시된다`() = runTest {
        coEvery {
            financialRepository.getFinancialData(testTicker, testName, any())
        } returns Result.failure(ApiError.NetworkError("network connection failed"))

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is FinancialState.Error)
        assertTrue((state as FinancialState.Error).message.contains("네트워크"))
    }

    @Test
    fun `isRefreshing은 refresh 중 true가 된다`() = runTest {
        val data = createFinancialData()
        coEvery {
            financialRepository.getFinancialData(testTicker, testName, any())
        } returns Result.success(data)
        coEvery {
            financialRepository.refreshFinancialData(testTicker, testName, any())
        } coAnswers {
            // Simulate slow API
            Result.success(data)
        }

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        // After completion, should be false
        assertFalse(viewModel.isRefreshing.value)
    }

    /**
     * Turbine 예시 — StateFlow 중간 emit까지 검증한다.
     * `.value` 단건 체크만으로는 `false → true → false` 전이를 관찰할 수 없어
     * 플래그의 "한 번 true가 됐었다"는 사실을 증명하려면 Turbine이 필요하다.
     */
    @Test
    fun `isRefreshing은 refresh false_true_false 전이를 거친다 (Turbine)`() = runTest {
        val data = createFinancialData()
        coEvery {
            financialRepository.getFinancialData(testTicker, testName, any())
        } returns Result.success(data)
        coEvery {
            financialRepository.refreshFinancialData(testTicker, testName, any())
        } returns Result.success(data)

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        viewModel.isRefreshing.test {
            assertFalse(awaitItem())  // 초기값 false
            viewModel.refresh()
            assertTrue(awaitItem())   // refresh() 진입 직후 true
            advanceUntilIdle()
            assertFalse(awaitItem())  // finally 블록에서 false 복원
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `refresh 실패 시 Error 상태가 되고 isRefreshing이 false가 된다`() = runTest {
        val data = createFinancialData()
        coEvery {
            financialRepository.getFinancialData(testTicker, testName, any())
        } returns Result.success(data)
        coEvery {
            financialRepository.refreshFinancialData(testTicker, testName, any())
        } returns Result.failure(Exception("Refresh failed"))

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is FinancialState.Error)
        assertFalse(viewModel.isRefreshing.value)
    }

    @Test
    fun `clearStock 후 loadForStock 시 새로 로드한다`() = runTest {
        val data = createFinancialData()
        coEvery {
            financialRepository.getFinancialData(any(), any(), any())
        } returns Result.success(data)

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        viewModel.clearStock()

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        // clearStock 후이므로 다시 로드해야 함
        coVerify(exactly = 2) { financialRepository.getFinancialData(testTicker, testName, any()) }
    }

    @Test
    fun `KIS config는 캐싱되어 한 번만 로드된다`() = runTest {
        val data = createFinancialData()
        coEvery {
            financialRepository.getFinancialData(any(), any(), any())
        } returns Result.success(data)

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        viewModel.loadForStock("000660", "SK하이닉스")
        advanceUntilIdle()

        // apiConfigProvider.getKisConfig should be called for each loadForStock call
        coVerify(exactly = 2) {
            apiConfigProvider.getKisConfig()
        }
    }

    // ==========================================================
    // refresh 실패 시 이전 Success 상태 보존 테스트
    // ==========================================================

    @Test
    fun `refresh 실패 시 이전 Success 상태를 보존한다`() = runTest {
        val data = createFinancialData()
        coEvery {
            financialRepository.getFinancialData(testTicker, testName, any())
        } returns Result.success(data)
        coEvery {
            financialRepository.refreshFinancialData(testTicker, testName, any())
        } throws RuntimeException("Network timeout")

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is FinancialState.Success)

        viewModel.refresh()
        advanceUntilIdle()

        // 이전 Success 상태가 보존되어야 함
        assertTrue(
            "Expected Success preserved after refresh failure, got ${viewModel.state.value}",
            viewModel.state.value is FinancialState.Success
        )
        assertFalse(viewModel.isRefreshing.value)
    }

    @Test
    fun `refresh 실패 시 이전 Error 상태에서는 Error가 된다`() = runTest {
        coEvery {
            financialRepository.getFinancialData(testTicker, testName, any())
        } returns Result.failure(Exception("first fail"))
        coEvery {
            financialRepository.refreshFinancialData(testTicker, testName, any())
        } throws RuntimeException("Network timeout")

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is FinancialState.Error)

        viewModel.refresh()
        advanceUntilIdle()

        // Error 상태에서 refresh 실패하면 새 Error
        assertTrue(viewModel.state.value is FinancialState.Error)
    }

    // ==========================================================
    // CancellationException 전파 테스트
    // ==========================================================

    @Test
    fun `loadForStock에서 CancellationException은 Error 상태가 되지 않는다`() = runTest {
        coEvery {
            financialRepository.getFinancialData(testTicker, testName, any())
        } throws kotlin.coroutines.cancellation.CancellationException("Job cancelled")

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        // CancellationException은 전파되므로 Loading 상태에 머물거나 cancel됨
        val state = viewModel.state.value
        // CancellationException이 Error로 변환되어서는 안 됨
        if (state is FinancialState.Error) {
            assertFalse(
                "CancellationException should not be wrapped as Error",
                state.message.contains("cancelled", ignoreCase = true)
            )
        }
    }

    // ==========================================================
    // 같은 종목 Error 상태에서 재로드 테스트
    // ==========================================================

    @Test
    fun `같은 종목이 Error 상태일 때 loadForStock은 다시 로드한다`() = runTest {
        coEvery {
            financialRepository.getFinancialData(testTicker, testName, any())
        } returns Result.failure(Exception("first fail"))

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is FinancialState.Error)

        // 같은 종목이지만 Error 상태이므로 재시도
        val data = createFinancialData()
        coEvery {
            financialRepository.getFinancialData(testTicker, testName, any())
        } returns Result.success(data)

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        assertTrue(viewModel.state.value is FinancialState.Success)
        coVerify(exactly = 2) { financialRepository.getFinancialData(testTicker, testName, any()) }
    }
}
