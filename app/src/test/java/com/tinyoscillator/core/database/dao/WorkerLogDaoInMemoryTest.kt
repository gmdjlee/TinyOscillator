package com.tinyoscillator.core.database.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tinyoscillator.core.database.AppDatabase
import com.tinyoscillator.core.database.entity.WorkerLogEntity
import com.tinyoscillator.core.worker.STATUS_ERROR
import com.tinyoscillator.core.worker.STATUS_SUCCESS
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * WorkerLogDao in-memory 테스트 — `insertAndCleanup` 트랜잭션 검증.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class WorkerLogDaoInMemoryTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: WorkerLogDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.workerLogDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insertAndCleanup은_keepDays_초과_로그를_삭제한다`() = runTest {
        val now = System.currentTimeMillis()
        val oldLog = WorkerLogEntity(
            workerName = "ETF",
            status = STATUS_SUCCESS,
            message = "오래된 로그",
            executedAt = now - 40L * 86_400_000L  // 40일 전
        )
        val recentLog = WorkerLogEntity(
            workerName = "ETF",
            status = STATUS_SUCCESS,
            message = "최근 로그",
            executedAt = now - 10L * 86_400_000L  // 10일 전
        )
        dao.insert(oldLog)
        dao.insert(recentLog)
        assertEquals(2, dao.getLogCount())

        // keepDays = 30 — 40일 전 로그는 삭제되어야 함
        val newLog = WorkerLogEntity(
            workerName = "ETF",
            status = STATUS_SUCCESS,
            message = "신규 로그"
        )
        dao.insertAndCleanup(newLog, keepDays = 30)

        val remaining = dao.getAllRecentLogs(50)
        assertEquals(2, remaining.size)
        assert(remaining.none { it.message == "오래된 로그" })
        assert(remaining.any { it.message == "최근 로그" })
        assert(remaining.any { it.message == "신규 로그" })
    }

    @Test
    fun `getRecentErrors는_status가_error인_로그만_반환한다`() = runTest {
        dao.insert(WorkerLogEntity(workerName = "ETF", status = STATUS_SUCCESS, message = "ok"))
        dao.insert(
            WorkerLogEntity(
                workerName = "시장지표",
                status = STATUS_ERROR,
                message = "실패",
                errorDetail = "network timeout"
            )
        )
        dao.insert(WorkerLogEntity(workerName = "자금 동향", status = STATUS_SUCCESS, message = "ok"))

        val errors = dao.getRecentErrors(50)

        assertEquals(1, errors.size)
        assertEquals("시장지표", errors[0].workerName)
        assertEquals("network timeout", errors[0].errorDetail)
    }

    @Test
    fun `getLatestLog는_특정_워커의_가장_최근_로그를_반환한다`() = runTest {
        val base = System.currentTimeMillis()
        dao.insert(WorkerLogEntity(workerName = "ETF", status = STATUS_SUCCESS, message = "old", executedAt = base - 1000))
        dao.insert(WorkerLogEntity(workerName = "ETF", status = STATUS_SUCCESS, message = "new", executedAt = base + 1000))
        dao.insert(WorkerLogEntity(workerName = "ETF", status = STATUS_SUCCESS, message = "mid", executedAt = base))

        val latest = dao.getLatestLog("ETF")

        assertEquals("new", latest?.message)
    }
}
