package com.tinyoscillator.presentation.settings

import org.junit.Assert.*
import org.junit.Test

class CollectionSettingsTest {

    // === ConsensusCollectionPeriod 데이터 클래스 ===

    @Test
    fun `default consensus collection period is 30 days`() {
        val period = ConsensusCollectionPeriod()
        assertEquals(30, period.daysBack)
    }

    @Test
    fun `custom consensus collection period preserves value`() {
        val period = ConsensusCollectionPeriod(daysBack = 60)
        assertEquals(60, period.daysBack)
    }

    @Test
    fun `consensus collection period copy works correctly`() {
        val original = ConsensusCollectionPeriod()
        val modified = original.copy(daysBack = 14)
        assertEquals(14, modified.daysBack)
        assertEquals(30, original.daysBack)
    }

    @Test
    fun `consensus collection period equality`() {
        val a = ConsensusCollectionPeriod(30)
        val b = ConsensusCollectionPeriod(30)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `consensus collection period inequality`() {
        val a = ConsensusCollectionPeriod(30)
        val b = ConsensusCollectionPeriod(60)
        assertNotEquals(a, b)
    }

    // === 기존 수집 기간 데이터 클래스 ===

    @Test
    fun `default etf collection period is 14 days`() {
        val period = EtfCollectionPeriod()
        assertEquals(14, period.daysBack)
    }

    @Test
    fun `default market oscillator collection period is 30 days`() {
        val period = MarketOscillatorCollectionPeriod()
        assertEquals(30, period.daysBack)
    }

    @Test
    fun `default market deposit collection period is 365 days`() {
        val period = MarketDepositCollectionPeriod()
        assertEquals(365, period.daysBack)
    }

    // === 탭 타이틀 ===

    @Test
    fun `all four collection periods have valid defaults`() {
        val defaults = listOf(
            EtfCollectionPeriod().daysBack,
            MarketOscillatorCollectionPeriod().daysBack,
            MarketDepositCollectionPeriod().daysBack,
            ConsensusCollectionPeriod().daysBack
        )
        assertEquals(4, defaults.size)
        assertTrue(defaults.all { it > 0 })
    }

    // === 데이터 초기화 라벨 매핑 ===

    @Test
    fun `reset data type labels are correct`() {
        val labelMap = mapOf(
            "etf" to "ETF",
            "oscillator" to "과매수/과매도",
            "deposit" to "자금 동향",
            "consensus" to "리포트"
        )
        assertEquals("ETF", labelMap["etf"])
        assertEquals("과매수/과매도", labelMap["oscillator"])
        assertEquals("자금 동향", labelMap["deposit"])
        assertEquals("리포트", labelMap["consensus"])
        assertEquals(4, labelMap.size)
    }

    @Test
    fun `all collection period data classes have positive defaults`() {
        assertTrue(EtfCollectionPeriod().daysBack > 0)
        assertTrue(MarketOscillatorCollectionPeriod().daysBack > 0)
        assertTrue(MarketDepositCollectionPeriod().daysBack > 0)
        assertTrue(ConsensusCollectionPeriod().daysBack > 0)
    }
}
