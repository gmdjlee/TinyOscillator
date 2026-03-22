package com.tinyoscillator.presentation.viewmodel

import android.app.Application
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.core.database.dao.AnalysisHistoryDao
import com.tinyoscillator.data.repository.FinancialRepository
import com.tinyoscillator.data.repository.StockMasterRepository
import com.tinyoscillator.data.repository.StockRepository
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.OscillatorConfig
import com.tinyoscillator.domain.usecase.CalcOscillatorUseCase
import com.tinyoscillator.domain.usecase.SaveAnalysisHistoryUseCase
import com.tinyoscillator.domain.usecase.SearchStocksUseCase
import com.tinyoscillator.core.config.ApiConfigProvider
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
class OscillatorViewModelTest {

    private lateinit var application: Application
    private lateinit var repository: StockRepository
    private lateinit var stockMasterRepository: StockMasterRepository
    private lateinit var searchStocksUseCase: SearchStocksUseCase
    private lateinit var saveAnalysisHistoryUseCase: SaveAnalysisHistoryUseCase
    private lateinit var calcOscillator: CalcOscillatorUseCase
    private lateinit var analysisHistoryDao: AnalysisHistoryDao
    private lateinit var financialRepository: FinancialRepository
    private lateinit var apiConfigProvider: ApiConfigProvider
    private lateinit var viewModel: OscillatorViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val validConfig = KiwoomApiKeyConfig(
        appKey = "test-key",
        secretKey = "test-secret"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        stockMasterRepository = mockk(relaxed = true)
        searchStocksUseCase = mockk(relaxed = true)
        saveAnalysisHistoryUseCase = mockk(relaxed = true)
        calcOscillator = CalcOscillatorUseCase(OscillatorConfig())
        analysisHistoryDao = mockk(relaxed = true)
        financialRepository = mockk(relaxed = true)
        apiConfigProvider = mockk(relaxed = true)

        // Mock static functions
        mockkStatic("com.tinyoscillator.presentation.settings.SettingsScreenKt")
        coEvery {
            com.tinyoscillator.presentation.settings.loadKiwoomConfig(any())
        } returns validConfig
        coEvery {
            com.tinyoscillator.presentation.settings.loadKisConfig(any())
        } returns com.tinyoscillator.core.api.KisApiKeyConfig(
            appKey = "kis-key",
            appSecret = "kis-secret"
        )

        // Mock DAO flows
        every { analysisHistoryDao.getRecent(any()) } returns flowOf(emptyList())

        // Mock search
        every { searchStocksUseCase(any()) } returns flowOf(emptyList())

        // Mock stock master
        coEvery { stockMasterRepository.populateIfEmpty(any()) } just Runs
        coEvery { stockMasterRepository.getCount() } returns 100

        viewModel = OscillatorViewModel(
            application,
            repository,
            stockMasterRepository,
            searchStocksUseCase,
            saveAnalysisHistoryUseCase,
            calcOscillator,
            analysisHistoryDao,
            financialRepository,
            apiConfigProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // -- Helpers --

    private fun createDailyTradingList(count: Int = 30): List<DailyTrading> {
        return (1..count).map { i ->
            DailyTrading(
                date = "2024${String.format("%02d", (i % 12) + 1)}${String.format("%02d", (i % 28) + 1)}",
                marketCap = 300_000_000_000_000L + i * 1_000_000_000L,
                foreignNetBuy = (i * 1_000_000L) * if (i % 3 == 0) -1 else 1,
                instNetBuy = (i * 500_000L) * if (i % 4 == 0) -1 else 1
            )
        }
    }

    // ==========================================================
    // 초기화 테스트
    // ==========================================================

    @Test
    fun `초기 상태는 Idle이다`() = runTest {
        advanceUntilIdle()
        assertEquals(OscillatorUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `init에서 종목 마스터를 초기화한다`() = runTest {
        advanceUntilIdle()
        coVerify(exactly = 1) { stockMasterRepository.populateIfEmpty(any()) }
    }

    @Test
    fun `종목 마스터 초기화 성공 시 Ready 상태가 된다`() = runTest {
        advanceUntilIdle()
        val status = viewModel.stockMasterStatus.value
        assertTrue("Expected Ready but got $status", status is StockMasterStatus.Ready)
        assertEquals(100, (status as StockMasterStatus.Ready).count)
    }

    @Test
    fun `종목 마스터 초기화 실패 시 Error 상태가 된다`() = runTest {
        // New ViewModel with failing stock master
        coEvery { stockMasterRepository.populateIfEmpty(any()) } throws RuntimeException("DB error")

        val vm = OscillatorViewModel(
            application, repository, stockMasterRepository,
            searchStocksUseCase, saveAnalysisHistoryUseCase,
            calcOscillator, analysisHistoryDao, financialRepository,
            apiConfigProvider
        )
        advanceUntilIdle()

        val status = vm.stockMasterStatus.value
        assertTrue("Expected Error but got $status", status is StockMasterStatus.Error)
    }

    // ==========================================================
    // refreshStockMaster 테스트
    // ==========================================================

    @Test
    fun `refreshStockMaster 호출 시 Loading에서 Ready로 전이된다`() = runTest {
        advanceUntilIdle()

        coEvery { stockMasterRepository.forceRefresh(any()) } just Runs
        coEvery { stockMasterRepository.getCount() } returns 2500

        viewModel.refreshStockMaster()
        advanceUntilIdle()

        val status = viewModel.stockMasterStatus.value
        assertTrue("Expected Ready but got $status", status is StockMasterStatus.Ready)
        assertEquals(2500, (status as StockMasterStatus.Ready).count)
        coVerify(exactly = 1) { stockMasterRepository.forceRefresh(any()) }
    }

    @Test
    fun `refreshStockMaster 실패 시 Error 상태가 된다`() = runTest {
        advanceUntilIdle()

        coEvery { stockMasterRepository.forceRefresh(any()) } throws RuntimeException("Refresh failed")

        viewModel.refreshStockMaster()
        advanceUntilIdle()

        val status = viewModel.stockMasterStatus.value
        assertTrue("Expected Error but got $status", status is StockMasterStatus.Error)
        assertTrue((status as StockMasterStatus.Error).message.contains("Refresh failed"))
    }

    // ==========================================================
    // 분석 테스트
    // ==========================================================

    @Test
    fun `유효하지 않은 ticker는 Error를 반환한다`() = runTest {
        advanceUntilIdle() // init

        viewModel.analyze("abc", "테스트")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Error but got $state", state is OscillatorUiState.Error)
        assertTrue((state as OscillatorUiState.Error).message.contains("유효하지 않은"))
    }

    @Test
    fun `5자리 ticker는 Error를 반환한다`() = runTest {
        advanceUntilIdle()

        viewModel.analyze("12345", "테스트")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is OscillatorUiState.Error)
    }

    @Test
    fun `7자리 ticker는 Error를 반환한다`() = runTest {
        advanceUntilIdle()

        viewModel.analyze("1234567", "테스트")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is OscillatorUiState.Error)
    }

    @Test
    fun `빈 데이터 반환 시 Error가 된다`() = runTest {
        advanceUntilIdle()

        coEvery {
            repository.getDailyTradingData(any(), any(), any(), any())
        } returns emptyList()

        viewModel.analyze("005930", "삼성전자")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Error but got $state", state is OscillatorUiState.Error)
        assertTrue((state as OscillatorUiState.Error).message.contains("데이터가 없습니다"))
    }

    @Test
    fun `정상 분석 시 Success가 된다`() = runTest {
        advanceUntilIdle()

        val dailyData = createDailyTradingList(30)
        coEvery {
            repository.getDailyTradingData(any(), any(), any(), any())
        } returns dailyData

        viewModel.analyze("005930", "삼성전자", analysisDays = 30, displayDays = 20)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Success but got $state", state is OscillatorUiState.Success)
        val success = state as OscillatorUiState.Success
        assertEquals("삼성전자", success.chartData.stockName)
        assertEquals("005930", success.chartData.ticker)
        assertTrue(success.chartData.rows.isNotEmpty())
        assertTrue(success.signals.isNotEmpty())
    }

    @Test
    fun `분석 성공 후 분석 기록이 저장된다`() = runTest {
        advanceUntilIdle()

        val dailyData = createDailyTradingList(30)
        coEvery {
            repository.getDailyTradingData(any(), any(), any(), any())
        } returns dailyData

        viewModel.analyze("005930", "삼성전자")
        advanceUntilIdle()

        coVerify(exactly = 1) { saveAnalysisHistoryUseCase("005930", "삼성전자") }
    }

    @Test
    fun `분석 성공 후 재무정보가 비동기로 수집된다`() = runTest {
        advanceUntilIdle()

        val dailyData = createDailyTradingList(30)
        coEvery {
            repository.getDailyTradingData(any(), any(), any(), any())
        } returns dailyData

        viewModel.analyze("005930", "삼성전자")
        advanceUntilIdle()

        coVerify(exactly = 1) { financialRepository.getFinancialData("005930", "삼성전자", any()) }
    }

    @Test
    fun `API 호출 예외 시 Error가 된다`() = runTest {
        advanceUntilIdle()

        coEvery {
            repository.getDailyTradingData(any(), any(), any(), any())
        } throws RuntimeException("Network failure")

        viewModel.analyze("005930", "삼성전자")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Error but got $state", state is OscillatorUiState.Error)
        assertTrue((state as OscillatorUiState.Error).message.contains("분석 실패"))
    }

    // ==========================================================
    // 검색 테스트
    // ==========================================================

    @Test
    fun `searchStock은 검색 쿼리를 업데이트한다`() = runTest {
        advanceUntilIdle()
        viewModel.searchStock("삼성")
        // Just verify no exception - the flow transformation happens asynchronously
        advanceUntilIdle()
    }

    // ==========================================================
    // API 설정 테스트
    // ==========================================================

    @Test
    fun `invalidateApiConfig 후 종목 마스터 재초기화를 시도한다`() = runTest {
        advanceUntilIdle()

        viewModel.invalidateApiConfig()
        advanceUntilIdle()

        // init에서 1번 + invalidate 후 1번 = 최소 2번
        coVerify(atLeast = 2) { stockMasterRepository.populateIfEmpty(any()) }
    }

    @Test
    fun `latestSignal이 결과에 포함된다`() = runTest {
        advanceUntilIdle()

        val dailyData = createDailyTradingList(30)
        coEvery {
            repository.getDailyTradingData(any(), any(), any(), any())
        } returns dailyData

        viewModel.analyze("005930", "삼성전자", displayDays = 20)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is OscillatorUiState.Success)
        val success = state as OscillatorUiState.Success
        assertNotNull(success.latestSignal)
        assertEquals(success.signals.last(), success.latestSignal)
    }

    @Test
    fun `재무정보 수집 실패는 분석 결과에 영향을 주지 않는다`() = runTest {
        advanceUntilIdle()

        val dailyData = createDailyTradingList(30)
        coEvery {
            repository.getDailyTradingData(any(), any(), any(), any())
        } returns dailyData
        coEvery {
            financialRepository.getFinancialData(any(), any(), any())
        } throws RuntimeException("Financial API failed")

        viewModel.analyze("005930", "삼성전자")
        advanceUntilIdle()

        // Analysis should still succeed despite financial fetch failure
        assertTrue(viewModel.uiState.value is OscillatorUiState.Success)
    }

    @Test
    fun `displayDays가 데이터보다 클 때 전체 데이터를 표시한다`() = runTest {
        advanceUntilIdle()

        val dailyData = createDailyTradingList(10)
        coEvery {
            repository.getDailyTradingData(any(), any(), any(), any())
        } returns dailyData

        viewModel.analyze("005930", "삼성전자", displayDays = 100)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Success but got $state", state is OscillatorUiState.Success)
        assertEquals(10, (state as OscillatorUiState.Success).chartData.rows.size)
    }

    // ==========================================================
    // 입력 유효성 검사 테스트
    // ==========================================================

    @Test
    fun `analysisDays가 0이면 Error를 반환한다`() = runTest {
        advanceUntilIdle()

        viewModel.analyze("005930", "삼성전자", analysisDays = 0)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Error but got $state", state is OscillatorUiState.Error)
        assertTrue((state as OscillatorUiState.Error).message.contains("1 이상"))
    }

    @Test
    fun `displayDays가 0이면 Error를 반환한다`() = runTest {
        advanceUntilIdle()

        viewModel.analyze("005930", "삼성전자", displayDays = 0)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Error but got $state", state is OscillatorUiState.Error)
        assertTrue((state as OscillatorUiState.Error).message.contains("1 이상"))
    }

    @Test
    fun `음수 analysisDays는 Error를 반환한다`() = runTest {
        advanceUntilIdle()

        viewModel.analyze("005930", "삼성전자", analysisDays = -10)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Error but got $state", state is OscillatorUiState.Error)
    }

    @Test
    fun `빈 ticker는 Error를 반환한다`() = runTest {
        advanceUntilIdle()

        viewModel.analyze("", "테스트")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is OscillatorUiState.Error)
    }

    @Test
    fun `영문 ticker는 Error를 반환한다`() = runTest {
        advanceUntilIdle()

        viewModel.analyze("AAPL00", "애플")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is OscillatorUiState.Error)
    }

    // ==========================================================
    // CancellationException 전파 테스트
    // ==========================================================

    @Test
    fun `API에서 CancellationException 발생 시 Error 상태가 되지 않는다`() = runTest {
        advanceUntilIdle()

        coEvery {
            repository.getDailyTradingData(any(), any(), any(), any())
        } throws kotlin.coroutines.cancellation.CancellationException("Coroutine cancelled")

        viewModel.analyze("005930", "삼성전자")
        advanceUntilIdle()

        // CancellationException은 전파되므로 Error 상태가 아닌 마지막 상태 유지
        // (viewModelScope가 처리하므로 Loading 또는 Idle)
        val state = viewModel.uiState.value
        assertFalse(
            "CancellationException should not produce Error state",
            state is OscillatorUiState.Error && (state as OscillatorUiState.Error).message.contains("분석 실패")
        )
    }

    // ==========================================================
    // warmupCount 경계 테스트
    // ==========================================================

    @Test
    fun `analysisDays와 displayDays가 같을 때 warmupCount는 0이다`() = runTest {
        advanceUntilIdle()

        val dailyData = createDailyTradingList(20)
        coEvery {
            repository.getDailyTradingData(any(), any(), any(), any())
        } returns dailyData

        viewModel.analyze("005930", "삼성전자", analysisDays = 20, displayDays = 20)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is OscillatorUiState.Success)
        // warmup=0이므로 전체 데이터가 표시됨
        assertEquals(20, (state as OscillatorUiState.Success).chartData.rows.size)
    }

    @Test
    fun `최소 데이터 크기 (1일)로 분석 시 정상 동작한다`() = runTest {
        advanceUntilIdle()

        val dailyData = listOf(
            DailyTrading(
                date = "20240101",
                marketCap = 300_000_000_000_000L,
                foreignNetBuy = 1_000_000L,
                instNetBuy = 500_000L
            )
        )
        coEvery {
            repository.getDailyTradingData(any(), any(), any(), any())
        } returns dailyData

        viewModel.analyze("005930", "삼성전자", analysisDays = 1, displayDays = 1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Success but got $state", state is OscillatorUiState.Success)
        assertEquals(1, (state as OscillatorUiState.Success).chartData.rows.size)
    }
}
