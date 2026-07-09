package com.tinyoscillator.feature.bearsignal.data.mapper

import com.tinyoscillator.feature.bearsignal.data.local.BearSignalManualCountryReturnEntity
import com.tinyoscillator.feature.bearsignal.domain.model.ManualMarketReturn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BearSignalManualCountryReturnMapperTest {

    @Test
    fun `toEntity toDomain 왕복 변환`() {
        val manual = ManualMarketReturn("RTS", listOf(-10.0, -5.0, -2.0, -1.0), 999L)

        val entity = BearSignalManualCountryReturnMapper.toEntity(manual)
        assertEquals("RTS", entity.countryName)
        assertEquals(-10.0, entity.r12m!!, 1e-9)
        assertEquals(-1.0, entity.r1m!!, 1e-9)
        assertEquals(999L, entity.updatedAt)

        val roundTrip = BearSignalManualCountryReturnMapper.toDomain(entity)
        assertEquals(manual, roundTrip)
    }

    @Test
    fun `일부 기간이 null이어도 보존된다`() {
        val manual = ManualMarketReturn("다우", listOf(null, null, null, -50.0), 1L)

        val entity = BearSignalManualCountryReturnMapper.toEntity(manual)
        assertNull(entity.r12m)
        assertNull(entity.r6m)
        assertNull(entity.r3m)
        assertEquals(-50.0, entity.r1m!!, 1e-9)

        val roundTrip = BearSignalManualCountryReturnMapper.toDomain(entity)
        assertEquals(listOf(null, null, null, -50.0), roundTrip.r)
    }

    @Test
    fun `리스트 변환은 순서를 유지한다`() {
        val entities = listOf(
            BearSignalManualCountryReturnEntity("RTS", -1.0, -2.0, -3.0, -4.0, 1L),
            BearSignalManualCountryReturnEntity("인니", -5.0, -6.0, -7.0, -8.0, 2L)
        )

        val list = BearSignalManualCountryReturnMapper.toDomain(entities)
        assertEquals(2, list.size)
        assertEquals("RTS", list[0].name)
        assertEquals("인니", list[1].name)
        assertTrue(list.all { it.r.size == 4 })
    }
}
