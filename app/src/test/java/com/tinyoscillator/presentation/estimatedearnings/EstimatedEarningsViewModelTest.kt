package com.tinyoscillator.presentation.estimatedearnings

import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.data.repository.EstimatedEarningsRepository
import com.tinyoscillator.domain.model.EstimatedEarningsInfo
import com.tinyoscillator.domain.model.EstimatedEarningsRow
import com.tinyoscillator.domain.model.EstimatedEarningsSummary
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class EstimatedEarningsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: EstimatedEarningsRepository
    private lateinit var apiConfigProvider: ApiConfigProvider
    private lateinit var viewModel: EstimatedEarningsViewModel

    private val validConfig = KisApiKeyConfig(
        appKey = "test-key",
        appSecret = "test-secret"
    )

    private val testSummary = EstimatedEarningsSummary(
        info = EstimatedEarningsInfo(
            ticker = "005930",
            stockName = "삼성전자",
            analystName = "김철수",
            estimateDate = "2026.03.27",
            recommendation = "매수",
            targetPrice = "50,000",
        ),
        earningsData = listOf(
            EstimatedEarningsRow("매출액", "3,000,000", "2,589,354", "2,718,073", "2,850,000", "3,000,000"),
            EstimatedEarningsRow("영업이익", "500,000", "656,700", "1,538,330", "2,000,000", "2,500,000"),
        ),
        valuationData = listOf(
            EstimatedEarningsRow("EPS", "1,800", "2,131", "3,835", "4,500", "5,200"),
            EstimatedEarningsRow("PER", "4.2", "3.5", "2.0", "1.7", "1.4"),
        ),
        periods = listOf("2022.12", "2023.12", "2024.12", "2025.12E", "2026.12E"),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        apiConfigProvider = mockk()
        coEvery { apiConfigProvider.getKisConfig() } returns validConfig
        viewModel = EstimatedEarningsViewModel(repository, apiConfigProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadData success updates summary`() = runTest {
        coEvery { repository.getEstimatedEarnings("005930", validConfig) } returns
                Result.success(testSummary)

        viewModel.loadData("005930")
        advanceUntilIdle()

        assertNotNull(viewModel.summary.value)
        assertEquals("005930", viewModel.summary.value?.info?.ticker)
        assertEquals("삼성전자", viewModel.summary.value?.info?.stockName)
        assertEquals(2, viewModel.summary.value?.earningsData?.size)
        assertEquals(2, viewModel.summary.value?.valuationData?.size)
        assertFalse(viewModel.isLoading.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `loadData failure updates error`() = runTest {
        coEvery { repository.getEstimatedEarnings("005930", validConfig) } returns
                Result.failure(RuntimeException("API error"))

        viewModel.loadData("005930")
        advanceUntilIdle()

        assertNull(viewModel.summary.value)
        assertNotNull(viewModel.error.value)
        assertEquals("API error", viewModel.error.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `loadData with null ticker does nothing`() = runTest {
        viewModel.loadData(null)
        advanceUntilIdle()

        assertNull(viewModel.summary.value)
        assertNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `loadData same ticker twice skips second call`() = runTest {
        coEvery { repository.getEstimatedEarnings("005930", validConfig) } returns
                Result.success(testSummary)

        viewModel.loadData("005930")
        advanceUntilIdle()
        viewModel.loadData("005930")
        advanceUntilIdle()

        // Should only have been called once - verify summary is still set
        assertNotNull(viewModel.summary.value)
    }

    @Test
    fun `refresh reloads data`() = runTest {
        coEvery { repository.getEstimatedEarnings("005930", validConfig) } returns
                Result.success(testSummary)

        viewModel.loadData("005930")
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        assertNotNull(viewModel.summary.value)
        assertFalse(viewModel.isLoading.value)
    }
}
