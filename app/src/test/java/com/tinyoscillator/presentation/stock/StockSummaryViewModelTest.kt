package com.tinyoscillator.presentation.stock

import com.tinyoscillator.core.database.dao.AnalysisSnapshotDao
import com.tinyoscillator.core.database.dao.FundamentalCacheDao
import com.tinyoscillator.core.database.entity.AnalysisSnapshotEntity
import com.tinyoscillator.core.database.entity.FundamentalCacheEntity
import com.tinyoscillator.data.mapper.AnalysisResponseParser
import com.tinyoscillator.data.repository.ConsensusRepository
import com.tinyoscillator.domain.model.ConsensusReport
import com.tinyoscillator.domain.model.StockAnalysis
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StockSummaryViewModelTest {

    private lateinit var fundamentalCacheDao: FundamentalCacheDao
    private lateinit var consensusRepository: ConsensusRepository
    private lateinit var analysisSnapshotDao: AnalysisSnapshotDao
    private lateinit var parser: AnalysisResponseParser

    private val testDispatcher = StandardTestDispatcher()

    private val fundamental = FundamentalCacheEntity(
        ticker = "005930", date = "20260706", close = 70000,
        eps = 5000, per = 14.0, bps = 50000, pbr = 1.4, dps = 1400, dividendYield = 2.0
    )
    private val report = ConsensusReport(
        writeDate = "2026-07-01", category = "기업", prevOpinion = "매수", opinion = "매수",
        title = "리포트", stockTicker = "005930", stockName = "삼성전자",
        author = "김애널", institution = "한투", targetPrice = 90000, currentPrice = 70000,
        divergenceRate = 28.6
    )
    private val snapshot = AnalysisSnapshotEntity(
        id = 1, ticker = "005930", name = "삼성전자", analyzedAt = 1_000L,
        ensembleScore = 0.72, algoScores = "{}", algoRationales = "{}",
        aiInterpretation = "{\"json\":true}"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fundamentalCacheDao = mockk(relaxed = true)
        consensusRepository = mockk(relaxed = true)
        analysisSnapshotDao = mockk(relaxed = true)
        parser = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = StockSummaryViewModel(
        fundamentalCacheDao, consensusRepository, analysisSnapshotDao, parser
    )

    @Test
    fun `load populates extras from local caches`() = runTest(testDispatcher.scheduler) {
        val stockAnalysis = mockk<StockAnalysis>()
        coEvery { fundamentalCacheDao.getLatestByTicker("005930") } returns fundamental
        coEvery { consensusRepository.getReportsByTicker("005930") } returns listOf(report)
        coEvery { analysisSnapshotDao.getRecentByTicker("005930", 1) } returns listOf(snapshot)
        every { parser.parseOrNull("{\"json\":true}") } returns stockAnalysis

        val vm = createViewModel()
        vm.load("005930")
        advanceUntilIdle()

        val extras = vm.extras.value
        assertNotNull(extras)
        assertEquals(fundamental, extras!!.fundamental)
        assertEquals(report, extras.latestReport)
        assertEquals(0.72, extras.ensembleScore!!, 0.001)
        assertEquals(1_000L, extras.analyzedAt)
        assertEquals(stockAnalysis, extras.aiAnalysis)
    }

    @Test
    fun `load with empty caches yields null fields`() = runTest(testDispatcher.scheduler) {
        coEvery { fundamentalCacheDao.getLatestByTicker(any()) } returns null
        coEvery { consensusRepository.getReportsByTicker(any()) } returns emptyList()
        coEvery { analysisSnapshotDao.getRecentByTicker(any(), 1) } returns emptyList()

        val vm = createViewModel()
        vm.load("000660")
        advanceUntilIdle()

        val extras = vm.extras.value
        assertNotNull(extras)
        assertNull(extras!!.fundamental)
        assertNull(extras.latestReport)
        assertNull(extras.ensembleScore)
        assertNull(extras.aiAnalysis)
    }

    @Test
    fun `same ticker reload is skipped`() = runTest(testDispatcher.scheduler) {
        coEvery { analysisSnapshotDao.getRecentByTicker(any(), 1) } returns emptyList()

        val vm = createViewModel()
        vm.load("005930")
        advanceUntilIdle()
        vm.load("005930")
        advanceUntilIdle()

        coVerify(exactly = 1) { analysisSnapshotDao.getRecentByTicker("005930", 1) }
    }

    @Test
    fun `dao failure does not crash and leaves field null`() = runTest(testDispatcher.scheduler) {
        coEvery { fundamentalCacheDao.getLatestByTicker(any()) } throws RuntimeException("db error")
        coEvery { consensusRepository.getReportsByTicker(any()) } returns listOf(report)
        coEvery { analysisSnapshotDao.getRecentByTicker(any(), 1) } returns emptyList()

        val vm = createViewModel()
        vm.load("005930")
        advanceUntilIdle()

        val extras = vm.extras.value
        assertNotNull(extras)
        assertNull(extras!!.fundamental)
        assertEquals(report, extras.latestReport)
    }
}
