package com.tinyoscillator.feature.bearsignal.data.mapper

import com.tinyoscillator.feature.bearsignal.data.local.BearSignalAutoCacheEntity
import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.AutoIndicator
import com.tinyoscillator.feature.bearsignal.domain.model.BearIndicatorKey
import com.tinyoscillator.feature.bearsignal.domain.model.InputSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BearSignalAutoCacheMapperTest {

    private fun sampleInputs() = AutoBearSignalInputs(
        up3 = AutoIndicator(14, InputSource.AUTO, 1_000L),
        down3 = AutoIndicator(12, InputSource.AUTO, 1_000L),
        up4 = AutoIndicator(3, InputSource.AUTO, 1_000L),
        down4 = AutoIndicator(2, InputSource.AUTO, 1_000L),
        kospi2 = AutoIndicator(56.0, InputSource.AUTO, 1_000L)
    )

    @Test
    fun `toEntities 5개 지표 키 생성`() {
        val entities = BearSignalAutoCacheMapper.toEntities(sampleInputs())

        assertEquals(5, entities.size)
        val byKey = entities.associateBy { it.indicatorKey }
        assertEquals(14.0, byKey[BearIndicatorKey.S2_UP3.key]!!.value, 1e-9)
        assertEquals(12.0, byKey[BearIndicatorKey.S2_DOWN3.key]!!.value, 1e-9)
        assertEquals(3.0, byKey[BearIndicatorKey.S2_UP4.key]!!.value, 1e-9)
        assertEquals(2.0, byKey[BearIndicatorKey.S2_DOWN4.key]!!.value, 1e-9)
        assertEquals(56.0, byKey[BearIndicatorKey.AMP_KOSPI2.key]!!.value, 1e-9)
        entities.forEach { assertEquals(InputSource.AUTO.name, it.source) }
    }

    @Test
    fun `toEntities toDomain 왕복 변환 일치`() {
        val original = sampleInputs()
        val roundTripped = BearSignalAutoCacheMapper.toDomain(BearSignalAutoCacheMapper.toEntities(original))

        assertEquals(original, roundTripped)
    }

    @Test
    fun `toDomain 필수 키 누락 시 null`() {
        val incomplete = listOf(
            BearSignalAutoCacheEntity(BearIndicatorKey.S2_UP3.key, 14.0, InputSource.AUTO.name, 1_000L)
            // down3/up4/down4/kospi2 누락
        )

        assertNull(BearSignalAutoCacheMapper.toDomain(incomplete))
    }

    @Test
    fun `toDomain 빈 리스트는 null`() {
        assertNull(BearSignalAutoCacheMapper.toDomain(emptyList()))
    }

    @Test
    fun `toDomain 알 수 없는 source 문자열은 AUTO로 폴백`() {
        val entities = listOf(
            BearSignalAutoCacheEntity(BearIndicatorKey.S2_UP3.key, 14.0, "UNKNOWN", 1_000L),
            BearSignalAutoCacheEntity(BearIndicatorKey.S2_DOWN3.key, 12.0, InputSource.AUTO.name, 1_000L),
            BearSignalAutoCacheEntity(BearIndicatorKey.S2_UP4.key, 3.0, InputSource.AUTO.name, 1_000L),
            BearSignalAutoCacheEntity(BearIndicatorKey.S2_DOWN4.key, 2.0, InputSource.AUTO.name, 1_000L),
            BearSignalAutoCacheEntity(BearIndicatorKey.AMP_KOSPI2.key, 56.0, InputSource.AUTO.name, 1_000L)
        )

        val result = BearSignalAutoCacheMapper.toDomain(entities)

        assertEquals(InputSource.AUTO, result!!.up3.source)
    }
}
