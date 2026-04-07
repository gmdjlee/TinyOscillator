package com.tinyoscillator.presentation.marketanalysis

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.tinyoscillator.data.repository.MarketPerRepository
import com.tinyoscillator.domain.model.MarketPerChartData
import com.tinyoscillator.domain.model.MarketPerDateRange
import com.tinyoscillator.domain.model.MarketPerRow
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MarketPerViewModelTest {

    private lateinit var application: Application
    private lateinit var repository: MarketPerRepository
    private lateinit var viewModel: MarketPerViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val sampleRows = listOf(
        MarketPerRow(date = "20260401", closeIndex = 2650.0, per = 12.5, pbr = 1.1, dividendYield = 1.8),
        MarketPerRow(date = "20260402", closeIndex = 2660.0, per = 12.6, pbr = 1.12, dividendYield = 1.79)
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        application = mockk(relaxed = true)

        // Mock EncryptedSharedPreferences for loadKrxCredentials
        val mockPrefs = mockk<SharedPreferences>(relaxed = true)
        every { mockPrefs.getString("krx_id", "") } returns "testUser"
        every { mockPrefs.getString("krx_password", "") } returns "testPass"
        every { application.getSharedPreferences(any(), any()) } returns mockPrefs

        // Mock applicationContext for EncryptedSharedPreferences
        every { application.applicationContext } returns application

        // EncryptedSharedPreferences uses MasterKey which needs a real context,
        // so we mock at a higher level — the getEncryptedPrefs call will be mocked
        mockkStatic("com.tinyoscillator.presentation.settings.SettingsScreenKt")
        coEvery {
            com.tinyoscillator.presentation.settings.loadKrxCredentials(any())
        } returns com.tinyoscillator.presentation.settings.KrxCredentials("testUser", "testPass")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `init triggers data load with default KOSPI 1Y`() = runTest {
        coEvery {
            repository.getMarketPerHistory(eq("KOSPI"), any(), any(), any(), any())
        } returns MarketPerChartData(market = "KOSPI", rows = sampleRows)

        viewModel = MarketPerViewModel(application, repository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is MarketPerState.Success)
        assertEquals(2, (state as MarketPerState.Success).chartData.rows.size)
        assertEquals("KOSPI", viewModel.selectedMarket.value)
        assertEquals(MarketPerDateRange.ONE_YEAR, viewModel.selectedRange.value)
    }

    @Test
    fun `selectMarket changes market and reloads data`() = runTest {
        coEvery {
            repository.getMarketPerHistory(any(), any(), any(), any(), any())
        } returns MarketPerChartData(market = "KOSPI", rows = sampleRows)

        viewModel = MarketPerViewModel(application, repository)
        advanceUntilIdle()

        coEvery {
            repository.getMarketPerHistory(eq("KOSDAQ"), any(), any(), any(), any())
        } returns MarketPerChartData(market = "KOSDAQ", rows = sampleRows)

        viewModel.selectMarket("KOSDAQ")
        advanceUntilIdle()

        assertEquals("KOSDAQ", viewModel.selectedMarket.value)
        coVerify { repository.getMarketPerHistory(eq("KOSDAQ"), any(), any(), any(), any()) }
    }

    @Test
    fun `selectDateRange changes range and reloads data`() = runTest {
        coEvery {
            repository.getMarketPerHistory(any(), any(), any(), any(), any())
        } returns MarketPerChartData(market = "KOSPI", rows = sampleRows)

        viewModel = MarketPerViewModel(application, repository)
        advanceUntilIdle()

        viewModel.selectDateRange(MarketPerDateRange.THREE_MONTHS)
        advanceUntilIdle()

        assertEquals(MarketPerDateRange.THREE_MONTHS, viewModel.selectedRange.value)
    }

    @Test
    fun `empty data results in Idle state`() = runTest {
        coEvery {
            repository.getMarketPerHistory(any(), any(), any(), any(), any())
        } returns MarketPerChartData(market = "KOSPI", rows = emptyList())

        viewModel = MarketPerViewModel(application, repository)
        advanceUntilIdle()

        assertTrue(viewModel.state.value is MarketPerState.Idle)
    }

    @Test
    fun `api error results in Error state`() = runTest {
        coEvery {
            repository.getMarketPerHistory(any(), any(), any(), any(), any())
        } throws RuntimeException("Network error")

        viewModel = MarketPerViewModel(application, repository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is MarketPerState.Error)
        assertEquals("Network error", (state as MarketPerState.Error).message)
    }

    @Test
    fun `missing credentials results in Error state`() = runTest {
        coEvery {
            com.tinyoscillator.presentation.settings.loadKrxCredentials(any())
        } returns com.tinyoscillator.presentation.settings.KrxCredentials("", "")

        viewModel = MarketPerViewModel(application, repository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is MarketPerState.Error)
        assertTrue((state as MarketPerState.Error).message.contains("자격증명"))
    }

    @Test
    fun `refresh reloads data`() = runTest {
        coEvery {
            repository.getMarketPerHistory(any(), any(), any(), any(), any())
        } returns MarketPerChartData(market = "KOSPI", rows = sampleRows)

        viewModel = MarketPerViewModel(application, repository)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        coVerify(atLeast = 2) { repository.getMarketPerHistory(any(), any(), any(), any(), any()) }
    }
}
