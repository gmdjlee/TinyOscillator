package com.tinyoscillator.presentation.viewmodel

import android.app.Application
import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.core.database.dao.AnalysisHistoryDao
import com.tinyoscillator.data.repository.StockMasterRepository
import com.tinyoscillator.domain.model.OscillatorConfig
import com.tinyoscillator.domain.usecase.CalcOscillatorUseCase
import com.tinyoscillator.domain.usecase.SearchStocksUseCase
import com.tinyoscillator.presentation.financial.FinancialInfoViewModel
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
 * Tests for ApiConfigProvider integration with ViewModels.
 *
 * After extracting config loading to ApiConfigProvider, these tests verify
 * that ViewModels accept and use the centralized provider.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelConfigMutexTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var apiConfigProvider: ApiConfigProvider

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        apiConfigProvider = mockk(relaxed = true)

        mockkStatic("com.tinyoscillator.presentation.settings.SettingsScreenKt")
        coEvery {
            com.tinyoscillator.presentation.settings.loadKiwoomConfig(any())
        } returns KiwoomApiKeyConfig(appKey = "test-key", secretKey = "test-secret")
        coEvery {
            com.tinyoscillator.presentation.settings.loadKisConfig(any())
        } returns KisApiKeyConfig(appKey = "kis-key", appSecret = "kis-secret")

        coEvery { apiConfigProvider.getKiwoomConfig() } returns KiwoomApiKeyConfig(
            appKey = "test-key", secretKey = "test-secret"
        )
        coEvery { apiConfigProvider.getKisConfig() } returns KisApiKeyConfig(
            appKey = "kis-key", appSecret = "kis-secret"
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ===== OscillatorViewModel =====

    @Test
    fun `OscillatorViewModel은 ApiConfigProvider를 주입받아 생성된다`() {
        val stockMasterRepository = mockk<StockMasterRepository>(relaxed = true)
        coEvery { stockMasterRepository.populateIfEmpty(any()) } just Runs
        coEvery { stockMasterRepository.getCount() } returns 100

        val analysisHistoryDao = mockk<AnalysisHistoryDao>(relaxed = true)
        every { analysisHistoryDao.getRecent(any()) } returns flowOf(emptyList())

        val searchStocksUseCase = mockk<SearchStocksUseCase>(relaxed = true)
        every { searchStocksUseCase(any()) } returns flowOf(emptyList())

        val vm = OscillatorViewModel(
            application,
            mockk(relaxed = true),
            stockMasterRepository,
            searchStocksUseCase,
            mockk(relaxed = true),
            CalcOscillatorUseCase(OscillatorConfig()),
            analysisHistoryDao,
            mockk(relaxed = true),
            apiConfigProvider
        )

        assertNotNull(vm)
    }

    @Test
    fun `ApiConfigProvider에 cachedKiwoomConfig 필드가 존재하고 Volatile이다`() {
        val field = ApiConfigProvider::class.java.getDeclaredField("cachedKiwoomConfig")
        assertTrue(java.lang.reflect.Modifier.isVolatile(field.modifiers))
    }

    @Test
    fun `ApiConfigProvider invalidateAll은 모든 캐시를 초기화한다`() {
        val context = mockk<android.content.Context>(relaxed = true)
        val provider = ApiConfigProvider(context)

        provider.invalidateAll()

        // Verify all cached fields are null after invalidation
        val kiwoomField = ApiConfigProvider::class.java.getDeclaredField("cachedKiwoomConfig")
        kiwoomField.isAccessible = true
        assertNull(kiwoomField.get(provider))

        val kisField = ApiConfigProvider::class.java.getDeclaredField("cachedKisConfig")
        kisField.isAccessible = true
        assertNull(kisField.get(provider))
    }

    // ===== FinancialInfoViewModel =====

    @Test
    fun `FinancialInfoViewModel은 ApiConfigProvider를 주입받아 생성된다`() {
        val vm = FinancialInfoViewModel(application, mockk(relaxed = true), apiConfigProvider)
        assertNotNull(vm)
    }

    @Test
    fun `FinancialInfoViewModel clearStock은 상태를 NoStock으로 리셋한다`() {
        val vm = FinancialInfoViewModel(application, mockk(relaxed = true), apiConfigProvider)

        vm.clearStock()

        assertEquals(
            com.tinyoscillator.domain.model.FinancialState.NoStock,
            vm.state.value
        )
    }

    @Test
    fun `FinancialInfoViewModel selectTab은 탭을 변경한다`() {
        val vm = FinancialInfoViewModel(application, mockk(relaxed = true), apiConfigProvider)

        vm.selectTab(com.tinyoscillator.domain.model.FinancialTab.STABILITY)

        assertEquals(
            com.tinyoscillator.domain.model.FinancialTab.STABILITY,
            vm.selectedTab.value
        )
    }
}
