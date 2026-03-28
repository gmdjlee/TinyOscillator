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

/**
 * OscillatorViewModel edge case tests.
 *
 * Tests for boundary conditions, sequential operations, and error recovery.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OscillatorViewModelEdgeCaseTest {

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
        every { analysisHistoryDao.getRecent(any()) } returns flowOf(emptyList())
        every { searchStocksUseCase(any()) } returns flowOf(emptyList())
        coEvery { stockMasterRepository.populateIfEmpty(any()) } returns -1
        coEvery { stockMasterRepository.getCount() } returns 100

        viewModel = OscillatorViewModel(
            application, repository, stockMasterRepository,
            searchStocksUseCase, saveAnalysisHistoryUseCase,
            calcOscillator, analysisHistoryDao, financialRepository,
            apiConfigProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

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
    // 순차 분석 테스트
    // ==========================================================

    @Test
    fun `연속 분석 시 마지막 결과가 표시된다`() = runTest {
        advanceUntilIdle()

        val dailyData1 = createDailyTradingList(20)
        val dailyData2 = createDailyTradingList(25)

        coEvery {
            repository.getDailyTradingData("005930", any(), any(), any())
        } returns dailyData1
        coEvery {
            repository.getDailyTradingData("000660", any(), any(), any())
        } returns dailyData2

        viewModel.analyze("005930", "삼성전자")
        advanceUntilIdle()

        viewModel.analyze("000660", "SK하이닉스")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is OscillatorUiState.Success)
        assertEquals("SK하이닉스", (state as OscillatorUiState.Success).chartData.stockName)
    }

    @Test
    fun `분석 실패 후 다른 종목 분석 성공`() = runTest {
        advanceUntilIdle()

        coEvery {
            repository.getDailyTradingData("999999", any(), any(), any())
        } throws RuntimeException("Not found")
        coEvery {
            repository.getDailyTradingData("005930", any(), any(), any())
        } returns createDailyTradingList(30)

        viewModel.analyze("999999", "없는종목")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is OscillatorUiState.Error)

        viewModel.analyze("005930", "삼성전자")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is OscillatorUiState.Success)
    }

    // ==========================================================
    // 경계 조건 테스트
    // ==========================================================

    @Test
    fun `데이터가 정확히 analysisDays만큼 있을 때 정상 동작`() = runTest {
        advanceUntilIdle()

        val data = createDailyTradingList(30)
        coEvery {
            repository.getDailyTradingData(any(), any(), any(), any())
        } returns data

        viewModel.analyze("005930", "삼성전자", analysisDays = 30, displayDays = 30)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Success, got $state", state is OscillatorUiState.Success)
    }

    @Test
    fun `displayDays=1 최소 표시일수로 분석`() = runTest {
        advanceUntilIdle()

        coEvery {
            repository.getDailyTradingData(any(), any(), any(), any())
        } returns createDailyTradingList(30)

        viewModel.analyze("005930", "삼성전자", analysisDays = 30, displayDays = 1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Success, got $state", state is OscillatorUiState.Success)
        // displayDays=1이므로 마지막 1일만 표시
        assertEquals(1, (state as OscillatorUiState.Success).chartData.rows.size)
    }

    @Test
    fun `특수문자 포함 ticker는 Error를 반환한다`() = runTest {
        advanceUntilIdle()

        viewModel.analyze("005-30", "테스트")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is OscillatorUiState.Error)
    }

    @Test
    fun `공백 포함 ticker는 Error를 반환한다`() = runTest {
        advanceUntilIdle()

        viewModel.analyze(" 00593", "테스트")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is OscillatorUiState.Error)
    }

    // ==========================================================
    // searchStock 추가 테스트
    // ==========================================================

    @Test
    fun `빈 문자열 검색은 예외 없이 동작한다`() = runTest {
        advanceUntilIdle()
        viewModel.searchStock("")
        advanceUntilIdle()
        // No exception means success
    }

    @Test
    fun `특수문자 검색은 예외 없이 동작한다`() = runTest {
        advanceUntilIdle()
        viewModel.searchStock("!@#$%")
        advanceUntilIdle()
    }

    // ==========================================================
    // StockMasterStatus 테스트
    // ==========================================================

    @Test
    fun `StockMasterStatus Loading 상태 확인`() {
        val status = StockMasterStatus.Loading
        assertTrue(status is StockMasterStatus.Loading)
    }

    @Test
    fun `StockMasterStatus Ready 상태는 count를 포함한다`() {
        val status = StockMasterStatus.Ready(2500)
        assertEquals(2500, status.count)
    }

    @Test
    fun `StockMasterStatus Error 상태는 메시지를 포함한다`() {
        val status = StockMasterStatus.Error("DB error")
        assertEquals("DB error", status.message)
    }

    // ==========================================================
    // OscillatorUiState 테스트
    // ==========================================================

    @Test
    fun `OscillatorUiState Idle 상태`() {
        assertEquals(OscillatorUiState.Idle, OscillatorUiState.Idle)
    }

    @Test
    fun `OscillatorUiState Loading 상태는 메시지를 포함한다`() {
        val loading = OscillatorUiState.Loading("분석 중...")
        assertEquals("분석 중...", loading.message)
    }

    @Test
    fun `OscillatorUiState Error 메시지 포함`() {
        val error = OscillatorUiState.Error("Test error")
        assertEquals("Test error", error.message)
    }

    @Test
    fun `saveAnalysisHistoryUseCase 실패는 Error 상태가 된다`() = runTest {
        advanceUntilIdle()

        coEvery {
            repository.getDailyTradingData(any(), any(), any(), any())
        } returns createDailyTradingList(30)
        coEvery {
            saveAnalysisHistoryUseCase(any(), any())
        } throws RuntimeException("History save failed")

        viewModel.analyze("005930", "삼성전자")
        advanceUntilIdle()

        // saveAnalysisHistoryUseCase is called before Success is emitted,
        // so an exception propagates to the catch block
        assertTrue(viewModel.uiState.value is OscillatorUiState.Error)
    }
}
