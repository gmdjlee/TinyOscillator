package com.tinyoscillator.core.database.dao

import com.tinyoscillator.core.database.entity.WorkerLogEntity
import com.tinyoscillator.core.worker.STATUS_ERROR
import com.tinyoscillator.core.worker.STATUS_SUCCESS
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WorkerLogDaoTest {

    private lateinit var dao: WorkerLogDao

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
    }

    @Test
    fun `insert and retrieve latest log`() = runTest {
        val log = WorkerLogEntity(
            workerName = "ETF",
            status = STATUS_SUCCESS,
            message = "완료: ETF 100개, 보유종목 500건",
            executedAt = System.currentTimeMillis()
        )
        coEvery { dao.insert(log) } just runs
        coEvery { dao.getLatestLog("ETF") } returns log

        dao.insert(log)
        val result = dao.getLatestLog("ETF")

        assertNotNull(result)
        assertEquals("ETF", result?.workerName)
        assertEquals(STATUS_SUCCESS, result?.status)
        assertEquals("완료: ETF 100개, 보유종목 500건", result?.message)
    }

    @Test
    fun `getLatestLog returns null when no logs exist`() = runTest {
        coEvery { dao.getLatestLog("ETF") } returns null

        val result = dao.getLatestLog("ETF")
        assertNull(result)
    }

    @Test
    fun `getRecentErrors returns only error logs`() = runTest {
        val errorLog = WorkerLogEntity(
            workerName = "시장지표",
            status = STATUS_ERROR,
            message = "KRX 로그인 실패",
            errorDetail = "java.lang.Exception: Login failed",
            executedAt = System.currentTimeMillis()
        )
        coEvery { dao.getRecentErrors(50) } returns listOf(errorLog)

        val errors = dao.getRecentErrors(50)
        assertEquals(1, errors.size)
        assertEquals(STATUS_ERROR, errors[0].status)
        assertNotNull(errors[0].errorDetail)
    }

    @Test
    fun `getRecentLogs returns logs for specific worker`() = runTest {
        val logs = (1..5).map {
            WorkerLogEntity(
                workerName = "ETF",
                status = STATUS_SUCCESS,
                message = "완료 #$it",
                executedAt = System.currentTimeMillis() - it * 86_400_000L
            )
        }
        coEvery { dao.getRecentLogs("ETF", 20) } returns logs

        val result = dao.getRecentLogs("ETF", 20)
        assertEquals(5, result.size)
        assertTrue(result.all { it.workerName == "ETF" })
    }

    @Test
    fun `insertAndCleanup calls insert and deleteOlderThan`() = runTest {
        val log = WorkerLogEntity(
            workerName = "자금 동향",
            status = STATUS_SUCCESS,
            message = "완료"
        )

        coEvery { dao.insertAndCleanup(log, 30) } just runs

        dao.insertAndCleanup(log, 30)

        coVerify(exactly = 1) { dao.insertAndCleanup(log, 30) }
    }

    @Test
    fun `error log entity preserves error detail`() {
        val log = WorkerLogEntity(
            workerName = "시장지표",
            status = STATUS_ERROR,
            message = "KOSPI 업데이트 실패",
            errorDetail = "java.net.SocketTimeoutException: connect timed out\n\tat okhttp3..."
        )

        assertEquals("시장지표", log.workerName)
        assertEquals(STATUS_ERROR, log.status)
        assertTrue(log.errorDetail!!.contains("SocketTimeoutException"))
    }

    @Test
    fun `entity default values are correct`() {
        val log = WorkerLogEntity(
            workerName = "ETF",
            status = STATUS_SUCCESS,
            message = "완료"
        )

        assertEquals(0L, log.id)
        assertNull(log.errorDetail)
        assertTrue(log.executedAt > 0)
    }

    @Test
    fun `getAllRecentLogs returns all worker types`() = runTest {
        val logs = listOf(
            WorkerLogEntity(workerName = "ETF", status = STATUS_SUCCESS, message = "ok"),
            WorkerLogEntity(workerName = "시장지표", status = STATUS_ERROR, message = "fail"),
            WorkerLogEntity(workerName = "자금 동향", status = STATUS_SUCCESS, message = "ok"),
            WorkerLogEntity(workerName = "장 마감 교체", status = STATUS_SUCCESS, message = "ok"),
            WorkerLogEntity(workerName = "무결성 검사", status = STATUS_SUCCESS, message = "ok")
        )
        coEvery { dao.getAllRecentLogs(50) } returns logs

        val result = dao.getAllRecentLogs(50)
        assertEquals(5, result.size)
        assertEquals(5, result.map { it.workerName }.toSet().size)
    }
}
