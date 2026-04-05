package com.tinyoscillator.data.repository

import com.tinyoscillator.core.database.dao.CalibrationDao
import com.tinyoscillator.core.database.entity.SignalHistoryEntity
import com.tinyoscillator.domain.model.AlgoAccuracyRow
import com.tinyoscillator.domain.model.AlgoResult
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SignalHistoryRepositoryTest {

    private lateinit var dao: CalibrationDao
    private lateinit var repo: SignalHistoryRepository

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        repo = SignalHistoryRepository(dao)
    }

    @Test
    fun `recordSignal creates entries for all algo results`() = runTest {
        val algoResults = mapOf(
            "NaiveBayes" to AlgoResult(algoName = "NaiveBayes", score = 0.72f),
            "HMM" to AlgoResult(algoName = "HMM", score = 0.45f),
        )

        repo.recordSignal("005930", algoResults, "20260405")

        coVerify {
            dao.insertSignalHistory(match { entries ->
                entries.size == 2 &&
                entries.any { it.algoName == "NaiveBayes" && kotlin.math.abs(it.rawScore - 0.72) < 0.001 } &&
                entries.any { it.algoName == "HMM" && kotlin.math.abs(it.rawScore - 0.45) < 0.001 } &&
                entries.all { it.ticker == "005930" && it.date == "20260405" && it.outcomeT1 == null }
            })
        }
    }

    @Test
    fun `getAccuracy returns mapped results by algoName`() = runTest {
        val rows = listOf(
            AlgoAccuracyRow("NaiveBayes", total = 50, hits = 35),
            AlgoAccuracyRow("HMM", total = 40, hits = 18),
        )
        coEvery { dao.getAccuracyByAlgo("005930", any()) } returns rows

        val result = repo.getAccuracy("005930", windowDays = 60)

        assertEquals(2, result.size)
        assertEquals(0.70f, result["NaiveBayes"]!!.accuracy, 0.01f)
        assertEquals(0.45f, result["HMM"]!!.accuracy, 0.01f)
    }

    @Test
    fun `getAccuracy returns empty map when no data`() = runTest {
        coEvery { dao.getAccuracyByAlgo(any(), any()) } returns emptyList()

        val result = repo.getAccuracy("005930")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getPendingTickers delegates to dao`() = runTest {
        coEvery { dao.getPendingTickers(any()) } returns listOf("005930", "000660")

        val tickers = repo.getPendingTickers()

        assertEquals(2, tickers.size)
        assertEquals("005930", tickers[0])
    }

    @Test
    fun `updateT1Outcomes updates matching entries`() = runTest {
        val pending = listOf(
            SignalHistoryEntity(id = 1, ticker = "005930", algoName = "NaiveBayes",
                rawScore = 0.7, date = "20260401"),
            SignalHistoryEntity(id = 2, ticker = "005930", algoName = "HMM",
                rawScore = 0.4, date = "20260401"),
        )
        coEvery { dao.getPendingT1Updates(any()) } returns pending

        val priceMap = mapOf(1L to 0.03f, 2L to -0.02f)
        repo.updateT1Outcomes(priceMap)

        coVerify { dao.updateT1(1, 0.03f) }
        coVerify { dao.updateT1(2, -0.02f) }
    }

    @Test
    fun `updateT1Outcomes skips entries not in priceMap`() = runTest {
        val pending = listOf(
            SignalHistoryEntity(id = 1, ticker = "005930", algoName = "NaiveBayes",
                rawScore = 0.7, date = "20260401"),
        )
        coEvery { dao.getPendingT1Updates(any()) } returns pending

        repo.updateT1Outcomes(emptyMap())

        coVerify(exactly = 0) { dao.updateT1(any(), any()) }
    }

    @Test
    fun `pruneOldData delegates to dao with correct cutoff`() = runTest {
        repo.pruneOldData(keepDays = 365)

        coVerify { dao.deleteOldHistory(any()) }
    }
}
