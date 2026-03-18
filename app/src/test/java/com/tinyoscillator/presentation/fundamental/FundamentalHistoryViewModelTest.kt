package com.tinyoscillator.presentation.fundamental

import android.app.Application
import app.cash.turbine.test
import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.data.repository.FundamentalHistoryRepository
import com.tinyoscillator.domain.model.FundamentalHistoryData
import com.tinyoscillator.domain.model.FundamentalHistoryState
import com.tinyoscillator.presentation.settings.KrxCredentials
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FundamentalHistoryViewModelTest {

    private lateinit var application: Application
    private lateinit var repository: FundamentalHistoryRepository
    private lateinit var krxApiClient: KrxApiClient
    private lateinit var viewModel: FundamentalHistoryViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testTicker = "005930"
    private val testName = "삼성전자"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        krxApiClient = mockk(relaxed = true)

        mockkStatic("com.tinyoscillator.presentation.settings.SettingsScreenKt")
        coEvery {
            com.tinyoscillator.presentation.settings.loadKrxCredentials(any())
        } returns KrxCredentials(id = "test", password = "test")

        every { krxApiClient.getKrxStock() } returns mockk()

        viewModel = FundamentalHistoryViewModel(application, repository, krxApiClient)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createTestData() = listOf(
        FundamentalHistoryData(
            date = "20260318", close = 70000L, eps = 5000L, per = 14.0,
            bps = 40000L, pbr = 1.75, dps = 1416L, dividendYield = 2.02
        )
    )

    @Test
    fun `initial state is NoStock`() {
        assertEquals(FundamentalHistoryState.NoStock, viewModel.state.value)
    }

    @Test
    fun `loadForStock transitions to Loading then Success`() = runTest {
        coEvery {
            repository.getFundamentalHistory(testTicker, any(), any())
        } returns createTestData()

        viewModel.state.test {
            assertEquals(FundamentalHistoryState.NoStock, awaitItem())

            viewModel.loadForStock(testTicker, testName)

            assertEquals(FundamentalHistoryState.Loading, awaitItem())

            val success = awaitItem() as FundamentalHistoryState.Success
            assertEquals(testTicker, success.ticker)
            assertEquals(testName, success.stockName)
            assertEquals(1, success.data.size)
            assertEquals(14.0, success.data[0].per, 0.001)
        }
    }

    @Test
    fun `loadForStock with empty data transitions to Error`() = runTest {
        coEvery {
            repository.getFundamentalHistory(testTicker, any(), any())
        } returns emptyList()

        viewModel.state.test {
            assertEquals(FundamentalHistoryState.NoStock, awaitItem())

            viewModel.loadForStock(testTicker, testName)

            assertEquals(FundamentalHistoryState.Loading, awaitItem())

            val error = awaitItem() as FundamentalHistoryState.Error
            assertTrue(error.message.contains("찾을 수 없습니다"))
        }
    }

    @Test
    fun `clearStock resets to NoStock`() = runTest {
        coEvery {
            repository.getFundamentalHistory(testTicker, any(), any())
        } returns createTestData()

        viewModel.loadForStock(testTicker, testName)
        advanceUntilIdle()

        viewModel.clearStock()
        assertEquals(FundamentalHistoryState.NoStock, viewModel.state.value)
    }

    @Test
    fun `NoKrxLogin when not logged in and no credentials`() = runTest {
        every { krxApiClient.getKrxStock() } returns null
        coEvery { krxApiClient.login(any(), any()) } returns false
        coEvery {
            com.tinyoscillator.presentation.settings.loadKrxCredentials(any())
        } returns KrxCredentials(id = "test", password = "test")

        viewModel = FundamentalHistoryViewModel(application, repository, krxApiClient)

        viewModel.state.test {
            assertEquals(FundamentalHistoryState.NoStock, awaitItem())

            viewModel.loadForStock(testTicker, testName)

            assertEquals(FundamentalHistoryState.Loading, awaitItem())
            assertEquals(FundamentalHistoryState.NoKrxLogin, awaitItem())
        }
    }
}
