package com.tinyoscillator.domain.usecase

import com.tinyoscillator.core.database.dao.AnalysisHistoryDao
import com.tinyoscillator.core.database.dao.CalibrationDao
import com.tinyoscillator.core.database.entity.AnalysisHistoryEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BuildHeatmapUseCase] 관심 종목 일별 앙상블 히트맵 구축 검증 (P8-5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuildHeatmapUseCaseTest {

    private val analysisHistoryDao: AnalysisHistoryDao = mockk()
    private val calibrationDao: CalibrationDao = mockk()
    private val useCase = BuildHeatmapUseCase(analysisHistoryDao, calibrationDao)

    private fun history(ticker: String, name: String): AnalysisHistoryEntity {
        val e = mockk<AnalysisHistoryEntity>()
        every { e.ticker } returns ticker
        every { e.name } returns name
        return e
    }

    @Test
    fun `이력 없으면 빈 히트맵`() = runTest {
        coEvery { analysisHistoryDao.getAll() } returns emptyList()

        val data = useCase(windowDays = 20)

        assertTrue(data.tickers.isEmpty())
        assertTrue(data.dates.isEmpty())
        assertTrue(data.dateLabels.isEmpty())
        assertTrue(data.scores.isEmpty())
        assertTrue(data.tickerNames.isEmpty())
    }

    @Test
    fun `종목·이름·윈도우 크기가 반영된다`() = runTest {
        coEvery { analysisHistoryDao.getAll() } returns listOf(
            history("005930", "삼성전자"),
            history("035720", "카카오")
        )
        coEvery { calibrationDao.getAverageScoreForDay(any(), any()) } returns 0.8

        val data = useCase(windowDays = 5)

        assertEquals(listOf("005930", "035720"), data.tickers)
        assertEquals("삼성전자", data.tickerNames["005930"])
        assertEquals("카카오", data.tickerNames["035720"])
        assertEquals(5, data.dates.size)
        assertEquals(5, data.dateLabels.size)
        // 각 종목의 점수 리스트 길이 = windowDays
        data.scores.values.forEach { assertEquals(5, it.size) }
    }

    @Test
    fun `일별 평균 점수가 그대로 채워진다`() = runTest {
        coEvery { analysisHistoryDao.getAll() } returns listOf(history("005930", "삼성전자"))
        coEvery { calibrationDao.getAverageScoreForDay("005930", any()) } returns 0.73

        val data = useCase(windowDays = 3)

        data.scores["005930"]!!.forEach { assertEquals(0.73f, it, 0.0001f) }
    }

    @Test
    fun `평균 점수 null인 날은 0_5로 폴백`() = runTest {
        coEvery { analysisHistoryDao.getAll() } returns listOf(history("005930", "삼성전자"))
        coEvery { calibrationDao.getAverageScoreForDay(any(), any()) } returns null

        val data = useCase(windowDays = 4)

        data.scores["005930"]!!.forEach { assertEquals(0.5f, it, 0.0001f) }
    }
}
