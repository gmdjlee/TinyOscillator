package com.tinyoscillator.core.database.entity

import org.junit.Assert.*
import org.junit.Test

/**
 * Room 엔티티 단위 테스트
 *
 * data class 동작, 프로퍼티 접근, equals/hashCode, copy 검증
 */
class DatabaseEntityTest {

    // ========== StockMasterEntity 테스트 ==========

    @Test
    fun `StockMasterEntity 생성 및 프로퍼티 접근`() {
        val entity = StockMasterEntity("005930", "삼성전자", "KOSPI", lastUpdated = 1700000000L)
        assertEquals("005930", entity.ticker)
        assertEquals("삼성전자", entity.name)
        assertEquals("KOSPI", entity.market)
        assertEquals(1700000000L, entity.lastUpdated)
    }

    @Test
    fun `StockMasterEntity equals - 같은 값`() {
        val e1 = StockMasterEntity("005930", "삼성전자", "KOSPI", lastUpdated = 100L)
        val e2 = StockMasterEntity("005930", "삼성전자", "KOSPI", lastUpdated = 100L)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    @Test
    fun `StockMasterEntity equals - 다른 ticker`() {
        val e1 = StockMasterEntity("005930", "삼성전자", "KOSPI", lastUpdated = 100L)
        val e2 = StockMasterEntity("000660", "삼성전자", "KOSPI", lastUpdated = 100L)
        assertNotEquals(e1, e2)
    }

    @Test
    fun `StockMasterEntity copy`() {
        val original = StockMasterEntity("005930", "삼성전자", "KOSPI", lastUpdated = 100L)
        val updated = original.copy(lastUpdated = 200L)
        assertEquals("005930", updated.ticker)
        assertEquals(200L, updated.lastUpdated)
    }

    @Test
    fun `StockMasterEntity 빈 market`() {
        val entity = StockMasterEntity("005930", "삼성전자", "", lastUpdated = 100L)
        assertEquals("", entity.market)
    }

    // ========== AnalysisCacheEntity 테스트 ==========

    @Test
    fun `AnalysisCacheEntity 생성 및 프로퍼티 접근`() {
        val entity = AnalysisCacheEntity(
            ticker = "005930",
            date = "20240101",
            marketCap = 300_000_000_000_000L,
            foreignNet = 50_000_000_000L,
            instNet = 30_000_000_000L
        )
        assertEquals("005930", entity.ticker)
        assertEquals("20240101", entity.date)
        assertEquals(300_000_000_000_000L, entity.marketCap)
        assertEquals(50_000_000_000L, entity.foreignNet)
        assertEquals(30_000_000_000L, entity.instNet)
    }

    @Test
    fun `AnalysisCacheEntity equals - 같은 PK`() {
        val e1 = AnalysisCacheEntity("005930", "20240101", 100L, 50L, 30L)
        val e2 = AnalysisCacheEntity("005930", "20240101", 100L, 50L, 30L)
        assertEquals(e1, e2)
    }

    @Test
    fun `AnalysisCacheEntity equals - 다른 date`() {
        val e1 = AnalysisCacheEntity("005930", "20240101", 100L, 50L, 30L)
        val e2 = AnalysisCacheEntity("005930", "20240102", 100L, 50L, 30L)
        assertNotEquals(e1, e2)
    }

    @Test
    fun `AnalysisCacheEntity 음수 순매수`() {
        val entity = AnalysisCacheEntity("005930", "20240101", 100L, -500L, -300L)
        assertEquals(-500L, entity.foreignNet)
        assertEquals(-300L, entity.instNet)
    }

    @Test
    fun `AnalysisCacheEntity copy`() {
        val original = AnalysisCacheEntity("005930", "20240101", 100L, 50L, 30L)
        val updated = original.copy(foreignNet = 999L)
        assertEquals(999L, updated.foreignNet)
        assertEquals("005930", updated.ticker)
    }

    // ========== AnalysisHistoryEntity 테스트 ==========

    @Test
    fun `AnalysisHistoryEntity 생성 및 프로퍼티 접근`() {
        val entity = AnalysisHistoryEntity(
            id = 1L,
            ticker = "005930",
            name = "삼성전자",
            lastAnalyzedAt = 1700000000000L
        )
        assertEquals(1L, entity.id)
        assertEquals("005930", entity.ticker)
        assertEquals("삼성전자", entity.name)
        assertEquals(1700000000000L, entity.lastAnalyzedAt)
    }

    @Test
    fun `AnalysisHistoryEntity 기본 id는 0`() {
        val entity = AnalysisHistoryEntity(
            ticker = "005930",
            name = "삼성전자",
            lastAnalyzedAt = 1700000000000L
        )
        assertEquals(0L, entity.id)
    }

    @Test
    fun `AnalysisHistoryEntity equals - id 포함`() {
        val e1 = AnalysisHistoryEntity(1L, "005930", "삼성전자", 100L)
        val e2 = AnalysisHistoryEntity(1L, "005930", "삼성전자", 100L)
        assertEquals(e1, e2)
    }

    @Test
    fun `AnalysisHistoryEntity equals - 다른 id`() {
        val e1 = AnalysisHistoryEntity(1L, "005930", "삼성전자", 100L)
        val e2 = AnalysisHistoryEntity(2L, "005930", "삼성전자", 100L)
        assertNotEquals(e1, e2)
    }

    @Test
    fun `AnalysisHistoryEntity copy`() {
        val original = AnalysisHistoryEntity(1L, "005930", "삼성전자", 100L)
        val updated = original.copy(lastAnalyzedAt = 200L)
        assertEquals(200L, updated.lastAnalyzedAt)
        assertEquals(1L, updated.id)
    }

    // ========== FinancialCacheEntity 테스트 ==========

    @Test
    fun `FinancialCacheEntity 생성 및 프로퍼티 접근`() {
        val entity = FinancialCacheEntity(
            ticker = "005930",
            name = "삼성전자",
            data = """{"ticker":"005930"}""",
            cachedAt = 1700000000000L
        )
        assertEquals("005930", entity.ticker)
        assertEquals("삼성전자", entity.name)
        assertTrue(entity.data.contains("005930"))
        assertEquals(1700000000000L, entity.cachedAt)
    }

    @Test
    fun `FinancialCacheEntity 기본 cachedAt는 현재 시간`() {
        val before = System.currentTimeMillis()
        val entity = FinancialCacheEntity(
            ticker = "005930",
            name = "삼성전자",
            data = "{}"
        )
        val after = System.currentTimeMillis()
        assertTrue(entity.cachedAt >= before)
        assertTrue(entity.cachedAt <= after)
    }

    @Test
    fun `FinancialCacheEntity equals`() {
        val e1 = FinancialCacheEntity("005930", "삼성전자", "{}", 100L)
        val e2 = FinancialCacheEntity("005930", "삼성전자", "{}", 100L)
        assertEquals(e1, e2)
    }

    @Test
    fun `FinancialCacheEntity copy`() {
        val original = FinancialCacheEntity("005930", "삼성전자", "{}", 100L)
        val updated = original.copy(cachedAt = 200L)
        assertEquals(200L, updated.cachedAt)
        assertEquals("005930", updated.ticker)
    }

    @Test
    fun `FinancialCacheEntity 빈 데이터`() {
        val entity = FinancialCacheEntity("005930", "삼성전자", "", 100L)
        assertEquals("", entity.data)
    }
}
