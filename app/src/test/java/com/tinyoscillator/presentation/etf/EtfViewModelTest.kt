package com.tinyoscillator.presentation.etf

import android.content.Context
import com.tinyoscillator.core.database.entity.EtfEntity
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.domain.model.EtfDataProgress
import com.tinyoscillator.domain.model.EtfUiState
import com.tinyoscillator.presentation.settings.EtfCollectionPeriod
import com.tinyoscillator.presentation.settings.EtfKeywordFilter
import com.tinyoscillator.presentation.settings.KrxCredentials
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
    private val defaultCollectionPeriod = EtfCollectionPeriod(daysBack = 14)

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
        coEvery { com.tinyoscillator.presentation.settings.loadEtfCollectionPeriod(any()) } returns defaultCollectionPeriod

        // Default: getAllEtfs returns empty flow
        every { etfRepository.getAllEtfs() } returns flowOf(emptyList())

        // Default: no existing data
        coEvery { etfRepository.getLatestDate() } returns null
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
    // 초기 상태 테스트
    // ==========================================================

    @Test
    fun `초기 uiState는 Idle이다`() = runTest {
        // getLatestDate returns a date so collectInitialData is not called
        coEvery { etfRepository.getLatestDate() } returns "20260305"

        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(EtfUiState.Idle, viewModel.uiState.value)
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
        coEvery { etfRepository.getLatestDate() } returns "20260305"

        viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.needsCredentials.value)
    }

    // ==========================================================
    // refreshData 테스트
    // ==========================================================

    @Test
    fun `refreshData 호출 시 인증정보가 비어있으면 needsCredentials가 true가 된다`() = runTest {
        // init에서는 유효한 creds, getLatestDate 있음 → collectInitialData 안 함
        coEvery { etfRepository.getLatestDate() } returns "20260305"
        viewModel = createViewModel()
        advanceUntilIdle()

        // refreshData 시에는 blank creds 반환
        coEvery { com.tinyoscillator.presentation.settings.loadKrxCredentials(any()) } returns blankCredentials

        viewModel.refreshData()
        advanceUntilIdle()

        assertTrue(viewModel.needsCredentials.value)
    }

    // ==========================================================
    // 데이터 수집 플로우 테스트
    // ==========================================================

    @Test
    fun `인증정보가 있고 데이터가 없으면 collectInitialData가 호출된다`() = runTest {
        coEvery { etfRepository.getLatestDate() } returns null
        coEvery {
            etfRepository.updateData(any(), any(), any())
        } returns flowOf(
            EtfDataProgress.Loading("수집 중...", 0.5f),
            EtfDataProgress.Success(etfCount = 10, holdingCount = 100)
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Success but got $state", state is EtfUiState.Success)
        assertEquals(10, (state as EtfUiState.Success).etfCount)
    }

    @Test
    fun `refreshData 성공 시 Success 상태가 된다`() = runTest {
        coEvery { etfRepository.getLatestDate() } returns "20260305"
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery {
            etfRepository.updateData(any(), any(), any())
        } returns flowOf(
            EtfDataProgress.Loading("업데이트 중...", 0.5f),
            EtfDataProgress.Success(etfCount = 5, holdingCount = 50)
        )

        viewModel.refreshData(daysBack = 3)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Success but got $state", state is EtfUiState.Success)
        assertEquals(5, (state as EtfUiState.Success).etfCount)
    }

    @Test
    fun `데이터 수집 중 Error 발생 시 Error 상태가 된다`() = runTest {
        coEvery { etfRepository.getLatestDate() } returns null
        coEvery {
            etfRepository.updateData(any(), any(), any())
        } returns flowOf(
            EtfDataProgress.Loading("수집 중...", 0.1f),
            EtfDataProgress.Error("KRX 로그인 실패")
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Error but got $state", state is EtfUiState.Error)
        assertTrue((state as EtfUiState.Error).message.contains("로그인 실패"))
    }

    @Test
    fun `onCredentialsSaved 호출 시 needsCredentials가 false가 되고 데이터 수집이 시작된다`() = runTest {
        coEvery { com.tinyoscillator.presentation.settings.loadKrxCredentials(any()) } returns blankCredentials
        viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.needsCredentials.value)

        // onCredentialsSaved 후에는 유효한 creds 반환
        coEvery { com.tinyoscillator.presentation.settings.loadKrxCredentials(any()) } returns validCredentials
        coEvery {
            etfRepository.updateData(any(), any(), any())
        } returns flowOf(
            EtfDataProgress.Success(etfCount = 8, holdingCount = 80)
        )

        viewModel.onCredentialsSaved()
        advanceUntilIdle()

        assertFalse(viewModel.needsCredentials.value)
        val state = viewModel.uiState.value
        assertTrue("Expected Success but got $state", state is EtfUiState.Success)
    }
}
