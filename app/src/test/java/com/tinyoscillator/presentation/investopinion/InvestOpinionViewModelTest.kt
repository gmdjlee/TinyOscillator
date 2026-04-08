package com.tinyoscillator.presentation.investopinion

import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.data.repository.InvestOpinionRepository
import com.tinyoscillator.domain.model.InvestOpinion
import com.tinyoscillator.domain.model.InvestOpinionSummary
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
class InvestOpinionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: InvestOpinionRepository
    private lateinit var apiConfigProvider: ApiConfigProvider
    private lateinit var viewModel: InvestOpinionViewModel

    private val validConfig = KisApiKeyConfig(
        appKey = "test-key",
        appSecret = "test-secret"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        apiConfigProvider = mockk(relaxed = true)
        coEvery { apiConfigProvider.getKisConfig() } returns validConfig
        viewModel = InvestOpinionViewModel(repository, apiConfigProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSummary(ticker: String = "005930") = InvestOpinionSummary(
        ticker = ticker,
        stockName = "삼성전자",
        opinions = listOf(
            InvestOpinion("20260408", "미래에셋", "매수", "02", 90000L, 75000L, "2", 1000L)
        ),
        buyCount = 1,
        holdCount = 0,
        sellCount = 0,
        avgTargetPrice = 90000L,
        currentPrice = 75000L
    )

    @Test
    fun `initial state - summary is null`() {
        assertNull(viewModel.summary.value)
        assertFalse(viewModel.isLoading.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `loadData - with ticker triggers API call`() = runTest {
        coEvery { repository.getInvestOpinions("005930", "삼성전자", validConfig) } returns
                Result.success(createSummary())

        viewModel.loadData("005930", "삼성전자")
        advanceUntilIdle()

        assertNotNull(viewModel.summary.value)
        assertEquals("005930", viewModel.summary.value!!.ticker)
        assertEquals(1, viewModel.summary.value!!.opinions.size)
        assertFalse(viewModel.isLoading.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `loadData - with null ticker does nothing`() = runTest {
        viewModel.loadData(null, null)
        advanceUntilIdle()

        assertNull(viewModel.summary.value)
        coVerify(exactly = 0) { repository.getInvestOpinions(any(), any(), any()) }
    }

    @Test
    fun `loadData - same ticker does not reload`() = runTest {
        coEvery { repository.getInvestOpinions("005930", "삼성전자", validConfig) } returns
                Result.success(createSummary())

        viewModel.loadData("005930", "삼성전자")
        advanceUntilIdle()

        viewModel.loadData("005930", "삼성전자")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getInvestOpinions(any(), any(), any()) }
    }

    @Test
    fun `loadData - different ticker loads new data`() = runTest {
        coEvery { repository.getInvestOpinions("005930", "삼성전자", validConfig) } returns
                Result.success(createSummary("005930"))
        coEvery { repository.getInvestOpinions("000660", "SK하이닉스", validConfig) } returns
                Result.success(createSummary("000660"))

        viewModel.loadData("005930", "삼성전자")
        advanceUntilIdle()
        assertEquals("005930", viewModel.summary.value!!.ticker)

        viewModel.loadData("000660", "SK하이닉스")
        advanceUntilIdle()
        assertEquals("000660", viewModel.summary.value!!.ticker)
    }

    @Test
    fun `loadData - API failure sets error`() = runTest {
        coEvery { repository.getInvestOpinions("005930", "삼성전자", validConfig) } returns
                Result.failure(RuntimeException("API error"))

        viewModel.loadData("005930", "삼성전자")
        advanceUntilIdle()

        assertNull(viewModel.summary.value)
        assertNotNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `refresh - reloads data for current ticker`() = runTest {
        coEvery { repository.getInvestOpinions("005930", "삼성전자", validConfig) } returns
                Result.success(createSummary())

        viewModel.loadData("005930", "삼성전자")
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        coVerify(exactly = 2) { repository.getInvestOpinions("005930", "삼성전자", validConfig) }
    }
}
