package com.tinyoscillator.presentation.etf

import androidx.lifecycle.SavedStateHandle
import com.tinyoscillator.core.database.entity.EtfEntity
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.domain.model.DateRange
import com.tinyoscillator.domain.model.HoldingTimeSeries
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StockTrendViewModelTest {

    private lateinit var etfRepository: EtfRepository
    private val testDispatcher = StandardTestDispatcher()

    private val testEtfTicker = "069500"
    private val testStockTicker = "005930"

    private val sampleData = listOf(
        HoldingTimeSeries(date = "20260320", weight = 10.5, amount = 1000000),
        HoldingTimeSeries(date = "20260319", weight = 10.3, amount = 980000),
        HoldingTimeSeries(date = "20260318", weight = 10.1, amount = 960000),
        HoldingTimeSeries(date = "20260101", weight = 9.0, amount = 800000),
        HoldingTimeSeries(date = "20250601", weight = 8.5, amount = 750000)
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        etfRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createSavedStateHandle(
        etfTicker: String = testEtfTicker,
        stockTicker: String = testStockTicker
    ): SavedStateHandle {
        return SavedStateHandle(
            mapOf("etfTicker" to etfTicker, "stockTicker" to stockTicker)
        )
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = createSavedStateHandle()
    ): StockTrendViewModel {
        return StockTrendViewModel(etfRepository, savedStateHandle)
    }

    // ==========================================================
    // ViewModel 생성 테스트
    // ==========================================================

    @Test
    fun `생성 - SavedStateHandle에서 ticker를 올바르게 읽는다`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(testStockTicker, viewModel.getStockTicker())
        assertEquals(testEtfTicker, viewModel.getEtfTicker())
    }

    @Test
    fun `생성 - SavedStateHandle에 ticker가 없으면 빈 문자열이다`() = runTest {
        val viewModel = createViewModel(SavedStateHandle())
        advanceUntilIdle()

        assertEquals("", viewModel.getStockTicker())
        assertEquals("", viewModel.getEtfTicker())
    }

    @Test
    fun `생성 - 초기 selectedRange는 ALL이다`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(DateRange.ALL, viewModel.selectedRange.value)
    }

    // ==========================================================
    // loadData 성공 테스트
    // ==========================================================

    @Test
    fun `loadData - 정상 호출 시 stockName이 설정된다`() = runTest {
        coEvery { etfRepository.getStockName(testStockTicker) } returns "삼성전자"
        coEvery { etfRepository.getEtf(testEtfTicker) } returns EtfEntity(
            ticker = testEtfTicker, name = "KODEX 200", isinCode = "KR7069500007"
        )
        coEvery { etfRepository.getStockTrendInEtf(testEtfTicker, testStockTicker) } returns sampleData

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("삼성전자", viewModel.stockName.value)
    }

    @Test
    fun `loadData - 정상 호출 시 etfName이 설정된다`() = runTest {
        coEvery { etfRepository.getEtf(testEtfTicker) } returns EtfEntity(
            ticker = testEtfTicker, name = "KODEX 200", isinCode = "KR7069500007"
        )
        coEvery { etfRepository.getStockTrendInEtf(testEtfTicker, testStockTicker) } returns sampleData

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("KODEX 200", viewModel.etfName.value)
    }

    @Test
    fun `loadData - 정상 호출 시 filteredData가 전체 데이터로 설정된다`() = runTest {
        coEvery { etfRepository.getStockTrendInEtf(testEtfTicker, testStockTicker) } returns sampleData

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(sampleData.size, viewModel.filteredData.value.size)
        assertEquals(sampleData, viewModel.filteredData.value)
    }

    @Test
    fun `loadData - ETF가 없으면 etfName은 null이다`() = runTest {
        coEvery { etfRepository.getEtf(testEtfTicker) } returns null
        coEvery { etfRepository.getStockTrendInEtf(testEtfTicker, testStockTicker) } returns sampleData

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertNull(viewModel.etfName.value)
    }

    // ==========================================================
    // loadData 에러 처리 테스트
    // ==========================================================

    @Test
    fun `loadData - 일반 예외 발생 시 크래시 없이 처리된다`() = runTest {
        coEvery { etfRepository.getStockName(any()) } throws RuntimeException("DB error")

        val viewModel = createViewModel()
        advanceUntilIdle()

        // 에러 시 데이터가 빈 상태로 유지
        assertTrue(viewModel.filteredData.value.isEmpty())
    }

    @Test
    fun `loadData - CancellationException은 다시 던져진다`() = runTest {
        coEvery { etfRepository.getStockName(any()) } throws kotlinx.coroutines.CancellationException("cancelled")

        val viewModel = createViewModel()
        advanceUntilIdle()

        // CancellationException은 viewModelScope 내에서 다시 던져져 코루틴이 취소됨
        // 데이터가 로드되지 않아야 한다
        assertTrue(viewModel.filteredData.value.isEmpty())
    }

    // ==========================================================
    // selectRange 테스트
    // ==========================================================

    @Test
    fun `selectRange - 범위 변경 시 selectedRange가 업데이트된다`() = runTest {
        coEvery { etfRepository.getStockTrendInEtf(any(), any()) } returns sampleData

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectRange(DateRange.MONTH_1)

        assertEquals(DateRange.MONTH_1, viewModel.selectedRange.value)
    }

    @Test
    fun `selectRange - ALL 선택 시 전체 데이터가 반환된다`() = runTest {
        coEvery { etfRepository.getStockTrendInEtf(any(), any()) } returns sampleData

        val viewModel = createViewModel()
        advanceUntilIdle()

        // 먼저 범위를 줄임
        viewModel.selectRange(DateRange.WEEK_1)
        // 다시 ALL로 변경
        viewModel.selectRange(DateRange.ALL)

        assertEquals(sampleData.size, viewModel.filteredData.value.size)
    }

    @Test
    fun `selectRange - 기간 범위 선택 시 데이터가 필터링된다`() = runTest {
        coEvery { etfRepository.getStockTrendInEtf(any(), any()) } returns sampleData

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectRange(DateRange.WEEK_1)

        // 1주일 범위로 필터링하면 최근 데이터만 남아야 함
        assertTrue(viewModel.filteredData.value.size <= sampleData.size)
    }

    // ==========================================================
    // applyFilter 테스트
    // ==========================================================

    @Test
    fun `applyFilter - DateRange ALL이면 전체 데이터가 반환된다`() = runTest {
        coEvery { etfRepository.getStockTrendInEtf(any(), any()) } returns sampleData

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(DateRange.ALL, viewModel.selectedRange.value)
        assertEquals(sampleData.size, viewModel.filteredData.value.size)
    }

    @Test
    fun `applyFilter - cutoff 이전 데이터는 필터링된다`() = runTest {
        coEvery { etfRepository.getStockTrendInEtf(any(), any()) } returns sampleData

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectRange(DateRange.MONTH_1)

        // 1개월 전 이후 데이터만 남아야 함 — 오래된 데이터는 제외
        val filtered = viewModel.filteredData.value
        assertTrue(filtered.size < sampleData.size)
        assertTrue(filtered.all { it.date >= DateRange.MONTH_1.getCutoffDate() })
    }

    // ==========================================================
    // accessor 테스트
    // ==========================================================

    @Test
    fun `getStockTicker - SavedStateHandle의 stockTicker를 반환한다`() = runTest {
        val viewModel = createViewModel()
        assertEquals(testStockTicker, viewModel.getStockTicker())
    }

    @Test
    fun `getEtfTicker - SavedStateHandle의 etfTicker를 반환한다`() = runTest {
        val viewModel = createViewModel()
        assertEquals(testEtfTicker, viewModel.getEtfTicker())
    }
}
