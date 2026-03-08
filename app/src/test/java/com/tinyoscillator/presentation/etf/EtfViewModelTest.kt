package com.tinyoscillator.presentation.etf

import android.content.Context
import com.tinyoscillator.core.database.entity.EtfEntity
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.presentation.settings.EtfKeywordFilter
import com.tinyoscillator.presentation.settings.KrxCredentials
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EtfViewModelTest {

    private lateinit var etfRepository: EtfRepository
    private lateinit var context: Context
    private lateinit var viewModel: EtfViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val blankCredentials = KrxCredentials(id = "", password = "")
    private val validCredentials = KrxCredentials(id = "testId", password = "testPw")
    private val defaultKeywordFilter = EtfKeywordFilter(
        includeKeywords = emptyList(),
        excludeKeywords = emptyList()
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        context = mockk(relaxed = true)
        etfRepository = mockk(relaxed = true)

        // Mock static settings functions
        mockkStatic("com.tinyoscillator.presentation.settings.SettingsScreenKt")

        // Default: valid credentials, no keywords
        coEvery { com.tinyoscillator.presentation.settings.loadKrxCredentials(any()) } returns validCredentials
        coEvery { com.tinyoscillator.presentation.settings.loadEtfKeywordFilter(any()) } returns defaultKeywordFilter

        // Default: getAllEtfs returns empty flow
        every { etfRepository.getAllEtfs() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): EtfViewModel {
        return EtfViewModel(etfRepository, context)
    }

    // ==========================================================
    // needsCredentials 테스트
    // ==========================================================

    @Test
    fun `인증정보가 비어있으면 needsCredentials가 true가 된다`() = runTest {
        coEvery { com.tinyoscillator.presentation.settings.loadKrxCredentials(any()) } returns blankCredentials

        viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.needsCredentials.value)
    }

    @Test
    fun `인증정보가 있으면 needsCredentials는 false이다`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.needsCredentials.value)
    }

    // ==========================================================
    // ETF 리스트 테스트
    // ==========================================================

    @Test
    fun `ETF 리스트가 정상적으로 로드된다`() = runTest {
        val etfs = listOf(
            EtfEntity(ticker = "069500", name = "KODEX 200", isinCode = "KR7069500007"),
            EtfEntity(ticker = "252670", name = "KODEX 200선물인버스2X", isinCode = "KR7252670005")
        )
        every { etfRepository.getAllEtfs() } returns flowOf(etfs)

        viewModel = createViewModel()
        val collectJob = launch { viewModel.etfList.collect {} }
        advanceUntilIdle()

        assertEquals(2, viewModel.etfList.value.size)
        collectJob.cancel()
    }

    @Test
    fun `제외 키워드가 적용되면 해당 ETF가 필터링된다`() = runTest {
        val etfs = listOf(
            EtfEntity(ticker = "069500", name = "KODEX 200", isinCode = "KR7069500007"),
            EtfEntity(ticker = "252670", name = "KODEX 200선물인버스2X", isinCode = "KR7252670005"),
            EtfEntity(ticker = "114800", name = "KODEX 인버스", isinCode = "KR7114800004")
        )
        every { etfRepository.getAllEtfs() } returns flowOf(etfs)
        coEvery { com.tinyoscillator.presentation.settings.loadEtfKeywordFilter(any()) } returns EtfKeywordFilter(
            includeKeywords = emptyList(),
            excludeKeywords = listOf("인버스")
        )

        viewModel = createViewModel()
        val collectJob = launch { viewModel.etfList.collect {} }
        advanceUntilIdle()

        assertEquals(1, viewModel.etfList.value.size)
        assertEquals("KODEX 200", viewModel.etfList.value[0].name)
        collectJob.cancel()
    }

    @Test
    fun `onCredentialsSaved 호출 시 needsCredentials가 false가 된다`() = runTest {
        coEvery { com.tinyoscillator.presentation.settings.loadKrxCredentials(any()) } returns blankCredentials
        viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.needsCredentials.value)

        viewModel.onCredentialsSaved()

        assertFalse(viewModel.needsCredentials.value)
    }
}
