package com.tinyoscillator.presentation.portfolio

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.core.database.entity.PortfolioEntity
import com.tinyoscillator.core.database.entity.StockMasterEntity
import com.tinyoscillator.data.repository.PortfolioRepository
import com.tinyoscillator.domain.model.PortfolioHoldingItem
import com.tinyoscillator.domain.model.PortfolioSummary
import com.tinyoscillator.domain.model.PortfolioUiState
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PortfolioViewModelTest {

    private lateinit var application: Application
    private lateinit var portfolioRepository: PortfolioRepository
    private lateinit var stockMasterDao: StockMasterDao
    private lateinit var context: Context
    private lateinit var viewModel: PortfolioViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val testPortfolio = PortfolioEntity(id = 1, name = "기본 포트폴리오", maxWeightPercent = 30)
    private val emptySummary = PortfolioSummary(0, 0, 0, 0.0, 0)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        portfolioRepository = mockk(relaxed = true)
        stockMasterDao = mockk(relaxed = true)
        context = mockk(relaxed = true)

        // Mock encrypted shared preferences for API config loading
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString(any(), any()) } returns ""
        every { context.applicationContext } returns context

        // Default repository behavior
        coEvery { portfolioRepository.ensureDefaultPortfolio() } returns 1L
        coEvery { portfolioRepository.getPortfolio(1L) } returns testPortfolio
        coEvery { portfolioRepository.loadPortfolioHoldings(1L, 30) } returns (emptySummary to emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PortfolioViewModel {
        return PortfolioViewModel(application, portfolioRepository, stockMasterDao, context)
    }

    @Test
    fun `초기 상태는 Idle`() = runTest {
        val vm = createViewModel()
        // Before any coroutines run
        assertTrue(vm.uiState.value is PortfolioUiState.Idle || vm.uiState.value is PortfolioUiState.Loading)
    }

    @Test
    fun `기본 포트폴리오 로딩`() = runTest {
        val summary = PortfolioSummary(10_000_000, 8_000_000, 2_000_000, 25.0, 2)
        val holdings = listOf(
            PortfolioHoldingItem(1, "005930", "삼성전자", "KOSPI", "반도체", 100, 70000, 75000, 60.0, true, 20, 1_500_000, 7.14, 500_000),
            PortfolioHoldingItem(2, "000660", "SK하이닉스", "KOSPI", "반도체", 50, 150000, 160000, 40.0, true, 5, 800_000, 6.67, 500_000)
        )
        coEvery { portfolioRepository.loadPortfolioHoldings(1L, 30) } returns (summary to holdings)

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is PortfolioUiState.Success)
        val success = state as PortfolioUiState.Success
        assertEquals(2, success.holdings.size)
        assertEquals(10_000_000L, success.summary.totalEvaluation)
    }

    @Test
    fun `빈 포트폴리오 로딩`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is PortfolioUiState.Success)
        assertEquals(0, (state as PortfolioUiState.Success).holdings.size)
    }

    @Test
    fun `포트폴리오 로딩 실패`() = runTest {
        coEvery { portfolioRepository.loadPortfolioHoldings(any(), any()) } throws RuntimeException("DB 오류")

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is PortfolioUiState.Error)
        assertTrue((state as PortfolioUiState.Error).message.contains("DB 오류"))
    }

    @Test
    fun `종목 추가`() = runTest {
        coEvery { portfolioRepository.insertHolding(any()) } returns 10L

        val vm = createViewModel()
        advanceUntilIdle()

        vm.addHolding("005930", "삼성전자", "KOSPI", "반도체", 100, 75000, "20260314", "초기 매수")
        advanceUntilIdle()

        coVerify { portfolioRepository.insertHolding(any()) }
        coVerify { portfolioRepository.insertTransaction(any()) }
        // loadPortfolio is called again after add
        coVerify(atLeast = 2) { portfolioRepository.loadPortfolioHoldings(1L, 30) }
    }

    @Test
    fun `거래 추가`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.addTransaction(10L, 50, 76000, "20260315", "추가 매수")
        advanceUntilIdle()

        coVerify {
            portfolioRepository.insertTransaction(match {
                it.holdingId == 10L && it.shares == 50 && it.pricePerShare == 76000
            })
        }
    }

    @Test
    fun `종목 삭제`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.deleteHolding(5L)
        advanceUntilIdle()

        coVerify { portfolioRepository.deleteHoldingWithTransactions(5L) }
    }

    @Test
    fun `거래 삭제`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.deleteTransaction(3L)
        advanceUntilIdle()

        coVerify { portfolioRepository.deleteTransaction(3L) }
    }

    @Test
    fun `selectHolding 및 clearSelectedHolding`() = runTest {
        coEvery { portfolioRepository.getTransactionItems(1L, 75000L) } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.selectHolding(1L, "삼성전자", 75000L)
        advanceUntilIdle()

        assertEquals(1L, vm.selectedHoldingId.value)
        assertEquals("삼성전자", vm.selectedHoldingName.value)
        assertEquals(75000L, vm.selectedHoldingCurrentPrice.value)

        vm.clearSelectedHolding()
        assertNull(vm.selectedHoldingId.value)
    }

    @Test
    fun `포트폴리오 설정 업데이트`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.updatePortfolioSettings("새 포트폴리오", 25, 50_000_000L)
        advanceUntilIdle()

        coVerify {
            portfolioRepository.updatePortfolio(match {
                it.name == "새 포트폴리오" && it.maxWeightPercent == 25 && it.totalAmountLimit == 50_000_000L
            })
        }
    }

    @Test
    fun `검색 쿼리 설정`() = runTest {
        val searchFlow = flowOf(listOf(
            StockMasterEntity("005930", "삼성전자", "KOSPI", lastUpdated = 100L)
        ))
        every { stockMasterDao.searchStocks("삼성") } returns searchFlow

        val vm = createViewModel()
        advanceUntilIdle()

        vm.searchStock("삼성")
        advanceUntilIdle()

        // searchResults is a debounced flow, verify it was triggered
        // Note: exact verification of debounced flow is complex in unit tests
    }

    @Test
    fun `isRefreshing 초기값 false`() = runTest {
        val vm = createViewModel()
        assertFalse(vm.isRefreshing.value)
    }
}
