package com.tinyoscillator.presentation.etf

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.domain.model.DateRange
import com.tinyoscillator.domain.model.StockAggregatedTimePoint
import com.tinyoscillator.presentation.settings.EtfKeywordFilter
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AggregatedStockTrendViewModelTest {

    private lateinit var etfRepository: EtfRepository
    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()

    private val testStockTicker = "005930"

    private val defaultKeywordFilter = EtfKeywordFilter(
        includeKeywords = emptyList(),
        excludeKeywords = emptyList()
    )

    private val sampleData = listOf(
        StockAggregatedTimePoint(date = "20260320", totalAmount = 5000000, etfCount = 10, maxWeight = 15.0, avgWeight = 8.5),
        StockAggregatedTimePoint(date = "20260319", totalAmount = 4800000, etfCount = 10, maxWeight = 14.8, avgWeight = 8.3),
        StockAggregatedTimePoint(date = "20260318", totalAmount = 4600000, etfCount = 9, maxWeight = 14.5, avgWeight = 8.1),
        StockAggregatedTimePoint(date = "20260101", totalAmount = 3000000, etfCount = 8, maxWeight = 12.0, avgWeight = 7.0),
        StockAggregatedTimePoint(date = "20250601", totalAmount = 2500000, etfCount = 7, maxWeight = 11.0, avgWeight = 6.5)
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        etfRepository = mockk(relaxed = true)

        mockkStatic("com.tinyoscillator.presentation.settings.SettingsScreenKt")
        coEvery { com.tinyoscillator.presentation.settings.loadEtfKeywordFilter(any()) } returns defaultKeywordFilter
        coEvery { etfRepository.getExcludedTickers(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createSavedStateHandle(
        stockTicker: String = testStockTicker
    ): SavedStateHandle {
        return SavedStateHandle(mapOf("stockTicker" to stockTicker))
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = createSavedStateHandle()
    ): AggregatedStockTrendViewModel {
        return AggregatedStockTrendViewModel(etfRepository, context, savedStateHandle)
    }

    // ==========================================================
    // ViewModel 생성 테스트
    // ==========================================================

    @Test
    fun `생성 - SavedStateHandle에서 stockTicker를 올바르게 읽는다`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(testStockTicker, viewModel.getStockTicker())
    }

    @Test
    fun `생성 - SavedStateHandle에 stockTicker가 없으면 빈 문자열이다`() = runTest {
        val viewModel = createViewModel(SavedStateHandle())
        advanceUntilIdle()

        assertEquals("", viewModel.getStockTicker())
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
        coEvery { etfRepository.getStockAggregatedTrend(testStockTicker, any()) } returns sampleData

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("삼성전자", viewModel.stockName.value)
    }

    @Test
    fun `loadData - 정상 호출 시 filteredData가 전체 데이터로 설정된다`() = runTest {
        coEvery { etfRepository.getStockAggregatedTrend(testStockTicker, any()) } returns sampleData

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(sampleData.size, viewModel.filteredData.value.size)
        assertEquals(sampleData, viewModel.filteredData.value)
    }

    @Test
    fun `loadData - excludeKeywords가 getExcludedTickers에 전달된다`() = runTest {
        val keywords = EtfKeywordFilter(
            includeKeywords = emptyList(),
            excludeKeywords = listOf("인버스", "레버리지")
        )
        coEvery { com.tinyoscillator.presentation.settings.loadEtfKeywordFilter(any()) } returns keywords
        coEvery { etfRepository.getExcludedTickers(listOf("인버스", "레버리지")) } returns listOf("252670")
        coEvery { etfRepository.getStockAggregatedTrend(testStockTicker, listOf("252670")) } returns sampleData

        val viewModel = createViewModel()
        advanceUntilIdle()

        coVerify { etfRepository.getExcludedTickers(listOf("인버스", "레버리지")) }
        coVerify { etfRepository.getStockAggregatedTrend(testStockTicker, listOf("252670")) }
    }

    @Test
    fun `loadData - stockName이 null이면 null로 유지된다`() = runTest {
        coEvery { etfRepository.getStockName(testStockTicker) } returns null
        coEvery { etfRepository.getStockAggregatedTrend(testStockTicker, any()) } returns sampleData

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertNull(viewModel.stockName.value)
    }

    // ==========================================================
    // loadData 에러 처리 테스트
    // ==========================================================

    @Test
    fun `loadData - 일반 예외 발생 시 크래시 없이 처리된다`() = runTest {
        coEvery { etfRepository.getStockName(any()) } throws RuntimeException("DB error")

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.filteredData.value.isEmpty())
    }

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun `loadData - CancellationException은 다시 던져진다`() = runTest {
        coEvery { etfRepository.getStockName(any()) } throws kotlinx.coroutines.CancellationException("cancelled")

        val viewModel = createViewModel()
        advanceUntilIdle()
    }

    // ==========================================================
    // selectRange 테스트
    // ==========================================================

    @Test
    fun `selectRange - 범위 변경 시 selectedRange가 업데이트된다`() = runTest {
        coEvery { etfRepository.getStockAggregatedTrend(any(), any()) } returns sampleData

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectRange(DateRange.MONTH_3)

        assertEquals(DateRange.MONTH_3, viewModel.selectedRange.value)
    }

    @Test
    fun `selectRange - ALL 선택 시 전체 데이터가 반환된다`() = runTest {
        coEvery { etfRepository.getStockAggregatedTrend(any(), any()) } returns sampleData

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectRange(DateRange.WEEK_1)
        viewModel.selectRange(DateRange.ALL)

        assertEquals(sampleData.size, viewModel.filteredData.value.size)
    }

    @Test
    fun `selectRange - 기간 범위 선택 시 데이터가 필터링된다`() = runTest {
        coEvery { etfRepository.getStockAggregatedTrend(any(), any()) } returns sampleData

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectRange(DateRange.WEEK_1)

        assertTrue(viewModel.filteredData.value.size <= sampleData.size)
    }

    // ==========================================================
    // applyFilter 테스트
    // ==========================================================

    @Test
    fun `applyFilter - DateRange ALL이면 전체 데이터가 반환된다`() = runTest {
        coEvery { etfRepository.getStockAggregatedTrend(any(), any()) } returns sampleData

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(DateRange.ALL, viewModel.selectedRange.value)
        assertEquals(sampleData.size, viewModel.filteredData.value.size)
    }

    @Test
    fun `applyFilter - cutoff 이전 데이터는 필터링된다`() = runTest {
        coEvery { etfRepository.getStockAggregatedTrend(any(), any()) } returns sampleData

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectRange(DateRange.MONTH_1)

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
}
