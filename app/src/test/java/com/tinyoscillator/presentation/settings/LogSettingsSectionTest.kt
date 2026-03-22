package com.tinyoscillator.presentation.settings

import com.tinyoscillator.core.database.entity.WorkerLogEntity
import com.tinyoscillator.core.worker.STATUS_ERROR
import com.tinyoscillator.core.worker.STATUS_SUCCESS
import org.junit.Assert.*
import org.junit.Test

class LogSettingsSectionTest {

    private fun createLog(
        id: Long = 1,
        workerName: String = "ETF",
        status: String = STATUS_SUCCESS,
        message: String = "완료",
        errorDetail: String? = null,
        executedAt: Long = System.currentTimeMillis()
    ) = WorkerLogEntity(
        id = id,
        workerName = workerName,
        status = status,
        message = message,
        errorDetail = errorDetail,
        executedAt = executedAt
    )

    // LogFilter tests

    @Test
    fun `LogFilter ALL has correct label`() {
        assertEquals("전체", LogFilter.ALL.label)
    }

    @Test
    fun `LogFilter SUCCESS has correct label`() {
        assertEquals("성공", LogFilter.SUCCESS.label)
    }

    @Test
    fun `LogFilter ERROR has correct label`() {
        assertEquals("에러", LogFilter.ERROR.label)
    }

    @Test
    fun `LogFilter entries count is 3`() {
        assertEquals(3, LogFilter.entries.size)
    }

    // Filtering logic tests

    @Test
    fun `ALL filter returns all logs`() {
        val logs = listOf(
            createLog(id = 1, status = STATUS_SUCCESS),
            createLog(id = 2, status = STATUS_ERROR),
            createLog(id = 3, status = "running")
        )
        val filtered = filterLogs(logs, LogFilter.ALL)
        assertEquals(3, filtered.size)
    }

    @Test
    fun `SUCCESS filter returns only success logs`() {
        val logs = listOf(
            createLog(id = 1, status = STATUS_SUCCESS),
            createLog(id = 2, status = STATUS_ERROR),
            createLog(id = 3, status = STATUS_SUCCESS)
        )
        val filtered = filterLogs(logs, LogFilter.SUCCESS)
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.status == STATUS_SUCCESS })
    }

    @Test
    fun `ERROR filter returns only error logs`() {
        val logs = listOf(
            createLog(id = 1, status = STATUS_SUCCESS),
            createLog(id = 2, status = STATUS_ERROR),
            createLog(id = 3, status = STATUS_ERROR)
        )
        val filtered = filterLogs(logs, LogFilter.ERROR)
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.status == STATUS_ERROR })
    }

    @Test
    fun `filter with empty logs returns empty list`() {
        val logs = emptyList<WorkerLogEntity>()
        assertEquals(0, filterLogs(logs, LogFilter.ALL).size)
        assertEquals(0, filterLogs(logs, LogFilter.SUCCESS).size)
        assertEquals(0, filterLogs(logs, LogFilter.ERROR).size)
    }

    @Test
    fun `SUCCESS filter with no success logs returns empty`() {
        val logs = listOf(
            createLog(id = 1, status = STATUS_ERROR),
            createLog(id = 2, status = STATUS_ERROR)
        )
        val filtered = filterLogs(logs, LogFilter.SUCCESS)
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `ERROR filter with no error logs returns empty`() {
        val logs = listOf(
            createLog(id = 1, status = STATUS_SUCCESS),
            createLog(id = 2, status = STATUS_SUCCESS)
        )
        val filtered = filterLogs(logs, LogFilter.ERROR)
        assertTrue(filtered.isEmpty())
    }

    // Export text generation tests

    @Test
    fun `export text contains header`() {
        val logs = listOf(createLog())
        val text = buildExportText(logs)
        assertTrue(text.contains("TinyOscillator 앱 로그"))
        assertTrue(text.contains("총 1건"))
    }

    @Test
    fun `export text contains log details`() {
        val logs = listOf(
            createLog(workerName = "ETF", status = STATUS_SUCCESS, message = "ETF 수집 완료")
        )
        val text = buildExportText(logs)
        assertTrue(text.contains("ETF"))
        assertTrue(text.contains("성공"))
        assertTrue(text.contains("ETF 수집 완료"))
    }

    @Test
    fun `export text contains error detail when present`() {
        val logs = listOf(
            createLog(
                status = STATUS_ERROR,
                message = "실패",
                errorDetail = "java.lang.RuntimeException: test"
            )
        )
        val text = buildExportText(logs)
        assertTrue(text.contains("에러"))
        assertTrue(text.contains("java.lang.RuntimeException: test"))
    }

    @Test
    fun `export text omits error detail when null`() {
        val logs = listOf(
            createLog(status = STATUS_SUCCESS, message = "완료", errorDetail = null)
        )
        val text = buildExportText(logs)
        assertFalse(text.contains("상세:"))
    }

    @Test
    fun `export text for empty logs`() {
        val text = buildExportText(emptyList())
        assertTrue(text.contains("총 0건"))
    }

    @Test
    fun `export text for multiple logs preserves order`() {
        val logs = listOf(
            createLog(id = 1, workerName = "ETF", message = "first"),
            createLog(id = 2, workerName = "시장지표", message = "second")
        )
        val text = buildExportText(logs)
        val firstIdx = text.indexOf("first")
        val secondIdx = text.indexOf("second")
        assertTrue(firstIdx < secondIdx)
    }

    // Helper functions that mirror the logic in LogSettingsSection

    private fun filterLogs(logs: List<WorkerLogEntity>, filter: LogFilter): List<WorkerLogEntity> {
        return when (filter) {
            LogFilter.ALL -> logs
            LogFilter.SUCCESS -> logs.filter { it.status == STATUS_SUCCESS }
            LogFilter.ERROR -> logs.filter { it.status == STATUS_ERROR }
        }
    }

    private fun buildExportText(logs: List<WorkerLogEntity>): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss", java.util.Locale.getDefault())
        return buildString {
            appendLine("TinyOscillator 앱 로그")
            appendLine("내보내기 시간: ${dateFormat.format(java.util.Date())}")
            appendLine("총 ${logs.size}건")
            appendLine("─".repeat(40))
            appendLine()
            logs.forEach { log ->
                val status = when (log.status) {
                    STATUS_SUCCESS -> "성공"
                    STATUS_ERROR -> "에러"
                    else -> log.status
                }
                appendLine("[${dateFormat.format(java.util.Date(log.executedAt))}] ${log.workerName} — $status")
                appendLine("  ${log.message}")
                if (!log.errorDetail.isNullOrBlank()) {
                    appendLine("  상세: ${log.errorDetail}")
                }
                appendLine()
            }
        }
    }
}
