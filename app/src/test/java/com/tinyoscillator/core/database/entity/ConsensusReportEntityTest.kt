package com.tinyoscillator.core.database.entity

import org.junit.Assert.*
import org.junit.Test

class ConsensusReportEntityTest {

    private fun createEntity(
        targetPrice: Long = 300000L,
        currentPrice: Long = 212000L
    ): ConsensusReportEntity {
        val divergenceRate = if (currentPrice > 0) {
            (targetPrice - currentPrice).toDouble() / currentPrice * 100.0
        } else {
            0.0
        }
        return ConsensusReportEntity(
            writeDate = "2026-03-23",
            category = "IT",
            prevOpinion = "Hold",
            opinion = "Buy",
            title = "목표가 상향",
            stockTicker = "005930",
            stockName = "삼성전자",
            author = "홍길동",
            institution = "미래에셋",
            targetPrice = targetPrice,
            currentPrice = currentPrice,
            divergenceRate = divergenceRate
        )
    }

    @Test
    fun `entity creation - all fields are set correctly`() {
        val entity = createEntity()
        assertEquals("2026-03-23", entity.writeDate)
        assertEquals("IT", entity.category)
        assertEquals("Hold", entity.prevOpinion)
        assertEquals("Buy", entity.opinion)
        assertEquals("목표가 상향", entity.title)
        assertEquals("삼성전자", entity.stockName)
        assertEquals("005930", entity.stockTicker)
        assertEquals("홍길동", entity.author)
        assertEquals("미래에셋", entity.institution)
        assertEquals(300000L, entity.targetPrice)
        assertEquals(212000L, entity.currentPrice)
    }

    @Test
    fun `divergence rate - positive case 300000 vs 212000`() {
        val entity = createEntity(targetPrice = 300000L, currentPrice = 212000L)
        val expected = (300000L - 212000L).toDouble() / 212000.0 * 100.0
        assertEquals(expected, entity.divergenceRate, 0.01)
        assertTrue(entity.divergenceRate > 41.0)
        assertTrue(entity.divergenceRate < 42.0)
    }

    @Test
    fun `divergence rate - negative case target less than current`() {
        val entity = createEntity(targetPrice = 180000L, currentPrice = 212000L)
        assertTrue(entity.divergenceRate < 0.0)
        val expected = (180000L - 212000L).toDouble() / 212000.0 * 100.0
        assertEquals(expected, entity.divergenceRate, 0.01)
    }

    @Test
    fun `divergence rate - zero when currentPrice is 0`() {
        val entity = createEntity(targetPrice = 300000L, currentPrice = 0L)
        assertEquals(0.0, entity.divergenceRate, 0.001)
    }
}
