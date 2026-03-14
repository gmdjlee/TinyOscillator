package com.tinyoscillator.core.database.entity

import org.junit.Assert.*
import org.junit.Test

class PortfolioEntityTest {

    // ========== PortfolioEntity 테스트 ==========

    @Test
    fun `PortfolioEntity 생성 및 기본값`() {
        val entity = PortfolioEntity()
        assertEquals(0L, entity.id)
        assertEquals("기본 포트폴리오", entity.name)
        assertEquals(30, entity.maxWeightPercent)
        assertNull(entity.totalAmountLimit)
        assertTrue(entity.createdAt > 0)
    }

    @Test
    fun `PortfolioEntity 커스텀 값`() {
        val entity = PortfolioEntity(
            id = 1L,
            name = "테스트 포트폴리오",
            maxWeightPercent = 20,
            totalAmountLimit = 100_000_000L,
            createdAt = 1000L
        )
        assertEquals(1L, entity.id)
        assertEquals("테스트 포트폴리오", entity.name)
        assertEquals(20, entity.maxWeightPercent)
        assertEquals(100_000_000L, entity.totalAmountLimit)
        assertEquals(1000L, entity.createdAt)
    }

    @Test
    fun `PortfolioEntity equals`() {
        val e1 = PortfolioEntity(id = 1, name = "A", maxWeightPercent = 30, createdAt = 100L)
        val e2 = PortfolioEntity(id = 1, name = "A", maxWeightPercent = 30, createdAt = 100L)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    @Test
    fun `PortfolioEntity copy`() {
        val original = PortfolioEntity(id = 1, name = "원본", maxWeightPercent = 30, createdAt = 100L)
        val updated = original.copy(name = "수정됨", maxWeightPercent = 25)
        assertEquals("수정됨", updated.name)
        assertEquals(25, updated.maxWeightPercent)
        assertEquals(1L, updated.id)
    }

    // ========== PortfolioHoldingEntity 테스트 ==========

    @Test
    fun `PortfolioHoldingEntity 생성 및 기본값`() {
        val entity = PortfolioHoldingEntity(
            portfolioId = 1L,
            ticker = "005930",
            stockName = "삼성전자",
            market = "KOSPI",
            sector = "반도체"
        )
        assertEquals(0L, entity.id)
        assertEquals(1L, entity.portfolioId)
        assertEquals("005930", entity.ticker)
        assertEquals("삼성전자", entity.stockName)
        assertEquals("KOSPI", entity.market)
        assertEquals("반도체", entity.sector)
        assertEquals(0, entity.lastPrice)
        assertEquals(0L, entity.priceUpdatedAt)
    }

    @Test
    fun `PortfolioHoldingEntity lastPrice 값 설정`() {
        val entity = PortfolioHoldingEntity(
            portfolioId = 1L,
            ticker = "005930",
            stockName = "삼성전자",
            market = "KOSPI",
            sector = "반도체",
            lastPrice = 75000,
            priceUpdatedAt = 1000L
        )
        assertEquals(75000, entity.lastPrice)
        assertEquals(1000L, entity.priceUpdatedAt)
    }

    @Test
    fun `PortfolioHoldingEntity equals`() {
        val e1 = PortfolioHoldingEntity(id = 1, portfolioId = 1, ticker = "005930", stockName = "삼성전자", market = "KOSPI", sector = "반도체")
        val e2 = PortfolioHoldingEntity(id = 1, portfolioId = 1, ticker = "005930", stockName = "삼성전자", market = "KOSPI", sector = "반도체")
        assertEquals(e1, e2)
    }

    @Test
    fun `PortfolioHoldingEntity equals - 다른 ticker`() {
        val e1 = PortfolioHoldingEntity(id = 1, portfolioId = 1, ticker = "005930", stockName = "삼성전자", market = "KOSPI", sector = "반도체")
        val e2 = PortfolioHoldingEntity(id = 1, portfolioId = 1, ticker = "000660", stockName = "SK하이닉스", market = "KOSPI", sector = "반도체")
        assertNotEquals(e1, e2)
    }

    // ========== PortfolioTransactionEntity 테스트 ==========

    @Test
    fun `PortfolioTransactionEntity 매수 거래`() {
        val entity = PortfolioTransactionEntity(
            holdingId = 1L,
            date = "20260314",
            shares = 100,
            pricePerShare = 75000
        )
        assertEquals(0L, entity.id)
        assertEquals(1L, entity.holdingId)
        assertEquals("20260314", entity.date)
        assertEquals(100, entity.shares)
        assertEquals(75000, entity.pricePerShare)
        assertEquals("", entity.memo)
        assertTrue(entity.createdAt > 0)
    }

    @Test
    fun `PortfolioTransactionEntity 매도 거래 - 음수 shares`() {
        val entity = PortfolioTransactionEntity(
            holdingId = 1L,
            date = "20260314",
            shares = -50,
            pricePerShare = 80000,
            memo = "일부 매도"
        )
        assertEquals(-50, entity.shares)
        assertEquals("일부 매도", entity.memo)
    }

    @Test
    fun `PortfolioTransactionEntity equals`() {
        val e1 = PortfolioTransactionEntity(id = 1, holdingId = 1, date = "20260314", shares = 100, pricePerShare = 75000, createdAt = 100L)
        val e2 = PortfolioTransactionEntity(id = 1, holdingId = 1, date = "20260314", shares = 100, pricePerShare = 75000, createdAt = 100L)
        assertEquals(e1, e2)
    }

    @Test
    fun `PortfolioTransactionEntity copy`() {
        val original = PortfolioTransactionEntity(holdingId = 1, date = "20260314", shares = 100, pricePerShare = 75000, createdAt = 100L)
        val updated = original.copy(memo = "추가 매수")
        assertEquals("추가 매수", updated.memo)
        assertEquals(100, updated.shares)
    }
}
