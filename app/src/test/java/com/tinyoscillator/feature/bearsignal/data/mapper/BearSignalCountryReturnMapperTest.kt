package com.tinyoscillator.feature.bearsignal.data.mapper

import com.tinyoscillator.feature.bearsignal.data.local.BearSignalCountryReturnEntity
import com.tinyoscillator.feature.bearsignal.domain.model.AutoMarketReturn
import com.tinyoscillator.feature.bearsignal.domain.model.MarketCoverage
import com.tinyoscillator.feature.bearsignal.domain.model.MarketReturnsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BearSignalCountryReturnMapperTest {

    private fun sampleSnapshot() = MarketReturnsSnapshot(
        markets = listOf(
            AutoMarketReturn("코스피", listOf(173.1, 103.7, 54.0, 4.5), lead = true, coverage = MarketCoverage.AUTO, updatedAt = 1_000L),
            AutoMarketReturn("닛케이", listOf(75.2, 36.7, 29.4, 6.7), lead = false, coverage = MarketCoverage.AUTO, updatedAt = 1_000L),
            AutoMarketReturn("RTS", listOf(null, null, null, null), lead = false, coverage = MarketCoverage.MANUAL_REQUIRED, updatedAt = 1_000L)
        )
    )

    @Test
    fun `toEntities 마켓 수만큼 엔티티 생성`() {
        val entities = BearSignalCountryReturnMapper.toEntities(sampleSnapshot())

        assertEquals(3, entities.size)
        val kospi = entities.first { it.countryName == "코스피" }
        assertEquals(173.1, kospi.r12m!!, 1e-9)
        assertEquals(103.7, kospi.r6m!!, 1e-9)
        assertEquals(54.0, kospi.r3m!!, 1e-9)
        assertEquals(4.5, kospi.r1m!!, 1e-9)
        assertTrue(kospi.lead)
        assertEquals("AUTO", kospi.coverage)
    }

    @Test
    fun `toEntities MANUAL_REQUIRED는 r 값 전부 null`() {
        val entities = BearSignalCountryReturnMapper.toEntities(sampleSnapshot())

        val rts = entities.first { it.countryName == "RTS" }
        assertNull(rts.r12m)
        assertNull(rts.r6m)
        assertNull(rts.r3m)
        assertNull(rts.r1m)
        assertEquals("MANUAL_REQUIRED", rts.coverage)
    }

    @Test
    fun `toEntities toDomain 왕복 변환 일치`() {
        val original = sampleSnapshot()

        val roundTripped = BearSignalCountryReturnMapper.toDomain(BearSignalCountryReturnMapper.toEntities(original))

        assertEquals(original.markets.toSet(), roundTripped!!.markets.toSet())
    }

    @Test
    fun `toDomain 빈 리스트는 null`() {
        assertNull(BearSignalCountryReturnMapper.toDomain(emptyList()))
    }

    @Test
    fun `toDomain 알 수 없는 coverage 문자열은 MANUAL_REQUIRED로 폴백`() {
        val entities = listOf(
            BearSignalCountryReturnEntity("테스트", 1.0, 2.0, 3.0, 4.0, false, "UNKNOWN", 1_000L)
        )

        val result = BearSignalCountryReturnMapper.toDomain(entities)

        assertEquals(MarketCoverage.MANUAL_REQUIRED, result!!.markets[0].coverage)
    }

    @Test
    fun `manualRequiredNames는 MANUAL_REQUIRED 마켓만 포함`() {
        val snapshot = BearSignalCountryReturnMapper.toDomain(BearSignalCountryReturnMapper.toEntities(sampleSnapshot()))!!

        assertEquals(listOf("RTS"), snapshot.manualRequiredNames)
    }

    @Test
    fun `toMarketReturnsList는 coverage와 무관하게 전량 포함`() {
        val snapshot = sampleSnapshot()

        val list = snapshot.toMarketReturnsList()

        assertEquals(3, list.size)
        assertEquals(listOf(null, null, null, null), list.first { it.name == "RTS" }.r)
    }
}
