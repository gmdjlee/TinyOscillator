package com.tinyoscillator.presentation.report

import android.content.Context
import com.tinyoscillator.data.repository.ConsensusRepository
import com.tinyoscillator.domain.model.ConsensusFilter
import com.tinyoscillator.domain.model.ConsensusFilterOptions
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
class ReportViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: ConsensusRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        context = mockk(relaxed = true)

        coEvery { repository.seedFromJson(any()) } returns Unit
        coEvery { repository.getFilterOptions() } returns ConsensusFilterOptions()
        coEvery { repository.getReports(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ReportViewModel {
        return ReportViewModel(repository, context)
    }

    private fun createReport(ticker: String = "005930") = ConsensusReport(
        writeDate = "2026-03-23",
        category = "IT",
        prevOpinion = "Hold",
        opinion = "Buy",
        title = "테스트",
        stockTicker = ticker,
        stockName = "테스트종목",
        author = "홍길동",
        institution = "미래에셋",
        targetPrice = 300000L,
        currentPrice = 212000L,
        divergenceRate = 41.51
    )

    @Test
    fun `initial state - isLoading starts true then becomes false after init`() = runTest {
        val vm = createViewModel()
        assertTrue(vm.isLoading.value)

        advanceUntilIdle()
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun `updateFilter - triggers reload with new filter`() = runTest {
        val reports = listOf(createReport())
        coEvery { repository.getReports(any()) } returns emptyList()
        val vm = createViewModel()
        advanceUntilIdle()

        val newFilter = ConsensusFilter(category = "IT")
        coEvery { repository.getReports(newFilter) } returns reports

        vm.updateFilter(newFilter)
        advanceUntilIdle()

        assertEquals(newFilter, vm.filter.value)
        assertEquals(1, vm.reports.value.size)
    }

    @Test
    fun `clearFilter - resets filter to empty`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.updateFilter(ConsensusFilter(category = "IT"))
        advanceUntilIdle()

        vm.clearFilter()
        advanceUntilIdle()

        assertEquals(ConsensusFilter(), vm.filter.value)
    }

    @Test
    fun `reports state - reflects repository results`() = runTest {
        val reports = listOf(createReport("005930"), createReport("068270"))
        coEvery { repository.getReports(any()) } returns reports

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(2, vm.reports.value.size)
        assertEquals(2, vm.reportCount.value)
    }
}
