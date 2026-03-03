package com.tinyoscillator.presentation.viewmodel

import android.app.Application
import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.core.database.dao.AnalysisHistoryDao
import com.tinyoscillator.data.repository.FinancialRepository
import com.tinyoscillator.data.repository.StockMasterRepository
import com.tinyoscillator.data.repository.StockRepository
import com.tinyoscillator.domain.model.OscillatorConfig
import com.tinyoscillator.domain.usecase.CalcOscillatorUseCase
import com.tinyoscillator.domain.usecase.SaveAnalysisHistoryUseCase
import com.tinyoscillator.domain.usecase.SearchStocksUseCase
import com.tinyoscillator.presentation.financial.FinancialInfoViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * ViewModel config Mutex tests.
 *
 * Verifies that both OscillatorViewModel and FinancialInfoViewModel
 * use Mutex for thread-safe config loading (double-checked locking pattern).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelConfigMutexTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)

        mockkStatic("com.tinyoscillator.presentation.settings.SettingsScreenKt")
        coEvery {
            com.tinyoscillator.presentation.settings.loadKiwoomConfig(any())
        } returns KiwoomApiKeyConfig(appKey = "test-key", secretKey = "test-secret")
        coEvery {
            com.tinyoscillator.presentation.settings.loadKisConfig(any())
        } returns KisApiKeyConfig(appKey = "kis-key", appSecret = "kis-secret")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ===== OscillatorViewModel =====

    @Test
    fun `OscillatorViewModel에 configMutex 필드가 존재한다`() {
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
            mockk(relaxed = true)
        )

        val field = OscillatorViewModel::class.java.getDeclaredField("configMutex")
        field.isAccessible = true
        val mutex = field.get(vm)
        assertNotNull(mutex)
        assertTrue(mutex is Mutex)
    }

    @Test
    fun `OscillatorViewModel cachedApiConfig는 @Volatile이다`() {
        val field = OscillatorViewModel::class.java.getDeclaredField("cachedApiConfig")
        assertTrue(java.lang.reflect.Modifier.isVolatile(field.modifiers))
    }

    @Test
    fun `OscillatorViewModel invalidateApiConfig은 캐시를 초기화한다`() {
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
            mockk(relaxed = true)
        )

        val field = OscillatorViewModel::class.java.getDeclaredField("cachedApiConfig")
        field.isAccessible = true

        // Set a cached config
        field.set(vm, KiwoomApiKeyConfig(appKey = "cached", secretKey = "cached"))
        assertNotNull(field.get(vm))

        // invalidateApiConfig should clear it
        vm.invalidateApiConfig()
        assertNull(field.get(vm))
    }

    // ===== FinancialInfoViewModel =====

    @Test
    fun `FinancialInfoViewModel에 configMutex 필드가 존재한다`() {
        val vm = FinancialInfoViewModel(application, mockk(relaxed = true))

        val field = FinancialInfoViewModel::class.java.getDeclaredField("configMutex")
        field.isAccessible = true
        val mutex = field.get(vm)
        assertNotNull(mutex)
        assertTrue(mutex is Mutex)
    }

    @Test
    fun `FinancialInfoViewModel cachedKisConfig는 @Volatile이다`() {
        val field = FinancialInfoViewModel::class.java.getDeclaredField("cachedKisConfig")
        assertTrue(java.lang.reflect.Modifier.isVolatile(field.modifiers))
    }

    @Test
    fun `FinancialInfoViewModel clearStock은 상태를 NoStock으로 리셋한다`() {
        val vm = FinancialInfoViewModel(application, mockk(relaxed = true))

        vm.clearStock()

        assertEquals(
            com.tinyoscillator.domain.model.FinancialState.NoStock,
            vm.state.value
        )
    }

    @Test
    fun `FinancialInfoViewModel selectTab은 탭을 변경한다`() {
        val vm = FinancialInfoViewModel(application, mockk(relaxed = true))

        vm.selectTab(com.tinyoscillator.domain.model.FinancialTab.STABILITY)

        assertEquals(
            com.tinyoscillator.domain.model.FinancialTab.STABILITY,
            vm.selectedTab.value
        )
    }
}
