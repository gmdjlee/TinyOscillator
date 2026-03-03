package com.tinyoscillator.domain.usecase

import com.tinyoscillator.core.database.entity.StockMasterEntity
import com.tinyoscillator.data.repository.StockMasterRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SearchStocksUseCaseTest {

    private lateinit var stockMasterRepository: StockMasterRepository
    private lateinit var useCase: SearchStocksUseCase

    @Before
    fun setup() {
        stockMasterRepository = mockk(relaxed = true)
        useCase = SearchStocksUseCase(stockMasterRepository)
    }

    @Test
    fun `빈 문자열 쿼리는 빈 리스트 Flow를 반환한다`() = runTest {
        val result = useCase("").first()

        assertTrue(result.isEmpty())
        verify(exactly = 0) { stockMasterRepository.searchStocks(any()) }
    }

    @Test
    fun `공백만 있는 쿼리는 빈 리스트 Flow를 반환한다`() = runTest {
        val result = useCase("   ").first()

        assertTrue(result.isEmpty())
        verify(exactly = 0) { stockMasterRepository.searchStocks(any()) }
    }

    @Test
    fun `유효한 쿼리는 repository의 searchStocks에 위임한다`() = runTest {
        val expectedEntities = listOf(
            StockMasterEntity(
                ticker = "005930",
                name = "삼성전자",
                market = "KOSPI",
                lastUpdated = System.currentTimeMillis()
            ),
            StockMasterEntity(
                ticker = "009150",
                name = "삼성전기",
                market = "KOSPI",
                lastUpdated = System.currentTimeMillis()
            )
        )

        every { stockMasterRepository.searchStocks("삼성") } returns flowOf(expectedEntities)

        val result = useCase("삼성").first()

        assertEquals(2, result.size)
        assertEquals("005930", result[0].ticker)
        assertEquals("삼성전자", result[0].name)

        verify(exactly = 1) { stockMasterRepository.searchStocks("삼성") }
    }

    @Test
    fun `한글 검색어도 repository에 위임한다`() = runTest {
        every { stockMasterRepository.searchStocks("현대") } returns flowOf(emptyList())

        val result = useCase("현대").first()
        assertTrue(result.isEmpty())
        verify(exactly = 1) { stockMasterRepository.searchStocks("현대") }
    }

    @Test
    fun `숫자 ticker로 검색할 수 있다`() = runTest {
        val entity = StockMasterEntity(
            ticker = "005930",
            name = "삼성전자",
            market = "KOSPI",
            lastUpdated = System.currentTimeMillis()
        )
        every { stockMasterRepository.searchStocks("005930") } returns flowOf(listOf(entity))

        val result = useCase("005930").first()
        assertEquals(1, result.size)
        assertEquals("005930", result[0].ticker)
    }

    @Test
    fun `단일 문자 검색도 repository에 위임한다`() = runTest {
        every { stockMasterRepository.searchStocks("삼") } returns flowOf(emptyList())

        val result = useCase("삼").first()
        assertTrue(result.isEmpty())
        verify(exactly = 1) { stockMasterRepository.searchStocks("삼") }
    }

    @Test
    fun `탭 문자만 있는 쿼리는 빈 리스트를 반환한다`() = runTest {
        val result = useCase("\t").first()
        assertTrue(result.isEmpty())
        verify(exactly = 0) { stockMasterRepository.searchStocks(any()) }
    }

    @Test
    fun `개행 문자만 있는 쿼리는 빈 리스트를 반환한다`() = runTest {
        val result = useCase("\n").first()
        assertTrue(result.isEmpty())
        verify(exactly = 0) { stockMasterRepository.searchStocks(any()) }
    }
}
