package com.tinyoscillator.presentation.investorprofile

import android.app.Application
import com.tinyoscillator.core.api.InvestmentMode
import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.core.network.NetworkUtils
import com.tinyoscillator.data.repository.InvestorFlowRepository
import com.tinyoscillator.domain.model.InvestorType
import com.tinyoscillator.domain.model.ProfileBasis
import com.tinyoscillator.domain.model.ProfileDisplayMode
import com.tinyoscillator.domain.model.ProfilePeriod
import com.tinyoscillator.domain.usecase.CalcInvestorVolumeProfileUseCase
import com.tinyoscillator.domain.usecase.InvestorProfileFixture
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * [InvestorProfileViewModel] 단위 테스트. 명세: `docs/TASK_investor_volume_profile.md` §9.3.
 *
 * [InvestorProfileViewModel.recompute]는 `Dispatchers.Default`(실 스레드)에서 도는데다 [InvestorProfileViewModel.load]도
 * 리포지토리 호출을 `Dispatchers.IO`로 감싼다. `Dispatchers.setMain`이 대체하는 것은 `Dispatchers.Main`뿐이라
 * `advanceUntilIdle()`만으로는 그 이후의 실 스레드 작업 완료를 보장할 수 없다 — [awaitTerminal]로 종료 상태를 기다린다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InvestorProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var application: Application
    private lateinit var repository: InvestorFlowRepository
    private lateinit var apiConfigProvider: ApiConfigProvider
    private lateinit var viewModel: InvestorProfileViewModel

    private val testTicker = "005930"
    private val testName = "삼성전자"
    private val validConfig = KisApiKeyConfig(
        appKey = "test-key",
        appSecret = "test-secret",
        investmentMode = InvestmentMode.PRODUCTION
    )

    private fun todayKst(): LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))

    /**
     * Loading 상태를 지나 진짜 종료 상태(Success/Empty/Error/NoApiKey)까지 기다린다.
     * `advanceUntilIdle()`로 Main 큐를 먼저 비워(동기 구간 + Loading 설정을 확정) 그 다음 `Dispatchers.IO`/
     * `Dispatchers.Default`(실 스레드) 작업이 끝날 때까지 `state.first { }`로 대기한다.
     */
    private suspend fun TestScope.awaitTerminal(): InvestorProfileState {
        advanceUntilIdle()
        return viewModel.state.first { it !is InvestorProfileState.Loading }
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        apiConfigProvider = mockk(relaxed = true)

        mockkObject(NetworkUtils)
        every { NetworkUtils.isNetworkAvailable(any()) } returns true
        coEvery { apiConfigProvider.getKisConfig() } returns validConfig
        coEvery {
            repository.getFlows(any(), any(), any(), any(), any())
        } returns Result.success(InvestorProfileFixture.rows())

        viewModel = InvestorProfileViewModel(application, repository, CalcInvestorVolumeProfileUseCase(), apiConfigProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `초기 상태는 NoStock이다`() = runTest {
        assertEquals(InvestorProfileState.NoStock, viewModel.state.value)
    }

    @Test
    fun `loadForStock 성공 시 Success 상태가 된다`() = runTest {
        viewModel.loadForStock(testTicker, testName)
        val state = awaitTerminal()

        assertTrue("Expected Success but got $state", state is InvestorProfileState.Success)
    }

    @Test
    fun `API 키 미설정 시 NoApiKey 상태가 된다`() = runTest {
        coEvery { apiConfigProvider.getKisConfig() } returns KisApiKeyConfig()

        viewModel.loadForStock(testTicker, testName)
        val state = awaitTerminal()

        assertTrue("Expected NoApiKey but got $state", state is InvestorProfileState.NoApiKey)
        coVerify(exactly = 0) { repository.getFlows(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `모의투자 모드에서는 NoApiKey 상태가 된다`() = runTest {
        coEvery { apiConfigProvider.getKisConfig() } returns validConfig.copy(investmentMode = InvestmentMode.MOCK)

        viewModel.loadForStock(testTicker, testName)
        val state = awaitTerminal()

        assertTrue("Expected NoApiKey but got $state", state is InvestorProfileState.NoApiKey)
        assertTrue((state as InvestorProfileState.NoApiKey).message.contains("모의투자"))
        coVerify(exactly = 0) { repository.getFlows(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `리포지토리 실패 시 Error, retry 성공 시 Success가 된다`() = runTest {
        coEvery {
            repository.getFlows(any(), any(), any(), any(), any())
        } returns Result.failure(RuntimeException("KIS API 오류 [ABC]: 실패"))

        viewModel.loadForStock(testTicker, testName)
        val errorState = awaitTerminal()
        assertTrue("Expected Error but got $errorState", errorState is InvestorProfileState.Error)

        coEvery {
            repository.getFlows(any(), any(), any(), any(), any())
        } returns Result.success(InvestorProfileFixture.rows())

        viewModel.retry()
        val successState = awaitTerminal()
        assertTrue("Expected Success but got $successState", successState is InvestorProfileState.Success)
    }

    @Test
    fun `toggleInvestor·setDisplayMode·setBinCount·setBasis는 초기 로드 이후 리포지토리를 다시 호출하지 않는다`() = runTest {
        viewModel.loadForStock(testTicker, testName)
        awaitTerminal()

        // 표시 필터: 재계산·네트워크 둘 다 없음(동기 반영이므로 대기 불필요)
        viewModel.toggleInvestor(InvestorType.FOREIGN)
        assertEquals(setOf(InvestorType.INDIVIDUAL, InvestorType.INSTITUTION), viewModel.selectedInvestors.value)
        viewModel.setDisplayMode(ProfileDisplayMode.STACKED)
        assertEquals(ProfileDisplayMode.STACKED, viewModel.displayMode.value)

        // 구간 수 변경: 재계산만(Dispatchers.Default). 이전 상태도 Success라 `!is Loading`으로는 구분 못 하므로
        // 변경이 실제 반영된 값(bins.size)을 predicate로 잡아 새 방출을 기다린다.
        viewModel.setBinCount(10)
        val afterBinCount = viewModel.state.first { it is InvestorProfileState.Success && it.profile.bins.size == 10 }
        assertEquals(10, (afterBinCount as InvestorProfileState.Success).profile.bins.size)

        // 산출 기준 변경: 마찬가지로 autoBasis가 false로 바뀐 새 방출을 기다린다(자동값과 우연히 같아도 안전).
        viewModel.setBasis(ProfileBasis.CUMULATIVE_BUY)
        val afterBasis = viewModel.state.first { it is InvestorProfileState.Success && !it.profile.autoBasis }
        assertEquals(ProfileBasis.CUMULATIVE_BUY, (afterBasis as InvestorProfileState.Success).profile.basis)

        coVerify(exactly = 1) { repository.getFlows(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `오프라인이고 캐시가 없으면 Error가 된다`() = runTest {
        every { NetworkUtils.isNetworkAvailable(any()) } returns false
        coEvery {
            repository.getFlows(any(), any(), any(), any(), any())
        } returns Result.success(emptyList())

        viewModel.loadForStock(testTicker, testName)
        val state = awaitTerminal()

        assertTrue("Expected Error but got $state", state is InvestorProfileState.Error)
        assertTrue((state as InvestorProfileState.Error).message.contains("네트워크"))
        coVerify { repository.getFlows(testTicker, any(), any(), false, any()) }
    }

    @Test
    fun `오프라인이어도 캐시가 있으면 Success가 된다`() = runTest {
        every { NetworkUtils.isNetworkAvailable(any()) } returns false

        viewModel.loadForStock(testTicker, testName)
        val state = awaitTerminal()

        assertTrue("Expected Success but got $state", state is InvestorProfileState.Success)
        coVerify { repository.getFlows(testTicker, any(), any(), false, any()) }
    }

    @Test
    fun `setCustomRange는 start가 end보다 늦으면 무시한다`() = runTest {
        val queryBefore = viewModel.query.value

        viewModel.setCustomRange(todayKst(), todayKst().minusDays(1))

        assertEquals(queryBefore, viewModel.query.value)
        coVerify(exactly = 0) { repository.getFlows(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `setCustomRange는 end가 today보다 미래면 무시한다`() = runTest {
        val queryBefore = viewModel.query.value

        viewModel.setCustomRange(todayKst().minusDays(10), todayKst().plusDays(1))

        assertEquals(queryBefore, viewModel.query.value)
        coVerify(exactly = 0) { repository.getFlows(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `유효한 setCustomRange는 CUSTOM 기간으로 재조회한다`() = runTest {
        viewModel.loadForStock(testTicker, testName)
        awaitTerminal()

        val start = todayKst().minusDays(30)
        val end = todayKst()
        viewModel.setCustomRange(start, end)
        val state = awaitTerminal()

        assertEquals(ProfilePeriod.CUSTOM, viewModel.query.value.period)
        assertTrue("Expected Success but got $state", state is InvestorProfileState.Success)
        coVerify { repository.getFlows(testTicker, start, any(), any(), any()) }
    }

    @Test
    fun `마지막 남은 주체는 해제되지 않는다`() {
        viewModel.toggleInvestor(InvestorType.INDIVIDUAL)
        viewModel.toggleInvestor(InvestorType.FOREIGN)
        assertEquals(setOf(InvestorType.INSTITUTION), viewModel.selectedInvestors.value)

        viewModel.toggleInvestor(InvestorType.INSTITUTION)
        assertEquals(setOf(InvestorType.INSTITUTION), viewModel.selectedInvestors.value)
    }

    @Test
    fun `clearStock은 NoStock 상태로 초기화한다`() = runTest {
        viewModel.loadForStock(testTicker, testName)
        awaitTerminal()

        viewModel.clearStock()

        assertEquals(InvestorProfileState.NoStock, viewModel.state.value)
    }
}
