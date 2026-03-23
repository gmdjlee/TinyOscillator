package com.tinyoscillator.presentation.consensus

import com.tinyoscillator.data.repository.ConsensusRepository
import com.tinyoscillator.domain.model.ConsensusChartData
import com.tinyoscillator.domain.model.ConsensusReport
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConsensusViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: ConsensusRepository
    private lateinit var viewModel: ConsensusViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        viewModel = ConsensusViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createReport(ticker: String = "005930", date: String = "2026-03-23") = ConsensusReport(
        writeDate = date,
        category = "IT",
        prevOpinion = "Hold",
        opinion = "Buy",
        title = "테스트($ticker)",
        stockTicker = ticker,
        author = "홍길동",
        institution = "미래에셋",
        targetPrice = 300000L,
        currentPrice = 212000L,
        divergenceRate = 41.51
    )

    private fun createChartData(ticker: String = "005930") = ConsensusChartData(
        ticker = ticker,
        stockName = "삼성전자",
        dates = listOf("2026-03-20", "2026-03-23"),
        marketCaps = listOf(500_000_000_000L, 520_000_000_000L),
        reportDates = listOf("2026-03-20", "2026-03-23"),
        reportTargetPrices = listOf(300000L, 310000L)
    )

    @Test
    fun `initial state - chartData is null and reports empty`() = runTest {
        assertNull(viewModel.chartData.value)
        assertTrue(viewModel.reports.value.isEmpty())
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `loadData - with ticker triggers data loading`() = runTest {
        val reports = listOf(createReport())
        val chartData = createChartData()
        coEvery { repository.getReportsByTicker("005930") } returns reports
        coEvery { repository.getConsensusChartData("005930", "삼성전자") } returns chartData

        viewModel.loadData("005930", "삼성전자")
        advanceUntilIdle()

        assertEquals(1, viewModel.reports.value.size)
        assertNotNull(viewModel.chartData.value)
        assertEquals("005930", viewModel.chartData.value!!.ticker)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `loadData - with same ticker does not reload`() = runTest {
        coEvery { repository.getReportsByTicker("005930") } returns listOf(createReport())
        coEvery { repository.getConsensusChartData("005930", "삼성전자") } returns createChartData()

        viewModel.loadData("005930", "삼성전자")
        advanceUntilIdle()

        viewModel.loadData("005930", "삼성전자")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getReportsByTicker("005930") }
    }

    @Test
    fun `loadData - with null ticker does nothing`() = runTest {
        viewModel.loadData(null, null)
        advanceUntilIdle()

        assertNull(viewModel.chartData.value)
        assertTrue(viewModel.reports.value.isEmpty())
        coVerify(exactly = 0) { repository.getReportsByTicker(any()) }
    }
}
