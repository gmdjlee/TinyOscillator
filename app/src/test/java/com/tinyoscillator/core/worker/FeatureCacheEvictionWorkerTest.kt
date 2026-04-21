package com.tinyoscillator.core.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.FeatureCacheDao
import com.tinyoscillator.core.database.dao.FinancialCacheDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureCacheEvictionWorkerTest {

    private val context: Context = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)
    private val featureCacheDao: FeatureCacheDao = mockk(relaxed = true)
    private val financialCacheDao: FinancialCacheDao = mockk(relaxed = true)
    private val analysisCacheDao: AnalysisCacheDao = mockk(relaxed = true)

    private fun worker() = FeatureCacheEvictionWorker(
        context = context,
        workerParams = workerParams,
        featureCacheDao = featureCacheDao,
        financialCacheDao = financialCacheDao,
        analysisCacheDao = analysisCacheDao,
    )

    @Test
    fun `doWork evicts three cache tables and returns success`() = runTest {
        coEvery { featureCacheDao.evictExpired(any()) } just runs
        coEvery { financialCacheDao.deleteExpired(any()) } just runs
        coEvery { analysisCacheDao.deleteAllOlderThan(any()) } returns 0

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 1) { featureCacheDao.evictExpired(any()) }
        coVerify(exactly = 1) { financialCacheDao.deleteExpired(any()) }
        coVerify(exactly = 1) { analysisCacheDao.deleteAllOlderThan(any()) }
    }

    @Test
    fun `doWork uses 7-day cutoff for financial cache`() = runTest {
        val capturedCutoff = slotLong()
        coEvery { featureCacheDao.evictExpired(any()) } just runs
        coEvery { financialCacheDao.deleteExpired(capture(capturedCutoff)) } just runs
        coEvery { analysisCacheDao.deleteAllOlderThan(any()) } returns 0

        val before = System.currentTimeMillis()
        worker().doWork()
        val after = System.currentTimeMillis()

        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000L
        val cutoff = capturedCutoff.captured
        assertTrue("cutoff should be ~7 days before now", cutoff in (before - sevenDaysMs)..(after - sevenDaysMs))
    }

    @Test
    fun `doWork uses 730-day cutoff for analysis cache`() = runTest {
        val capturedCutoff = slotString()
        coEvery { featureCacheDao.evictExpired(any()) } just runs
        coEvery { financialCacheDao.deleteExpired(any()) } just runs
        coEvery { analysisCacheDao.deleteAllOlderThan(capture(capturedCutoff)) } returns 0

        worker().doWork()

        val expected = LocalDate.now().minusDays(730L).toString()
        assertEquals(expected, capturedCutoff.captured)
    }

    @Test
    fun `doWork retries on DAO failure below attempt threshold`() = runTest {
        coEvery { featureCacheDao.evictExpired(any()) } throws RuntimeException("db locked")
        coEvery { workerParams.runAttemptCount } returns 0

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `doWork fails after exhausting retry attempts`() = runTest {
        coEvery { featureCacheDao.evictExpired(any()) } throws RuntimeException("db locked")
        coEvery { workerParams.runAttemptCount } returns 3

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun `companion constants are stable`() {
        assertEquals("feature_cache_eviction", FeatureCacheEvictionWorker.WORK_NAME)
        assertEquals("feature_cache_eviction_manual", FeatureCacheEvictionWorker.MANUAL_WORK_NAME)
        assertEquals("feature_cache_eviction_tag", FeatureCacheEvictionWorker.TAG)
    }

    private fun slotLong(): io.mockk.CapturingSlot<Long> = io.mockk.slot()
    private fun slotString(): io.mockk.CapturingSlot<String> = io.mockk.slot()
}
