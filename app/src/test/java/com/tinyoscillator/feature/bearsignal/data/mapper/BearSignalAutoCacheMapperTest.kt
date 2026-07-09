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

    // ── Phase 2: [B] 등급 스칼라 지표 (semi/buffer/rate/dir/etf) ──────────────

    private fun sampleInputsWithExternal() = sampleInputs().copy(
        semi = AutoIndicator(23.1, InputSource.AUTO, 2_000L),
        buffer = AutoIndicator(true, InputSource.AUTO, 2_000L),
        rate = AutoIndicator(3.75, InputSource.AUTO, 2_000L),
        dir = AutoIndicator("hike", InputSource.AUTO, 2_000L),
        etf = AutoIndicator("up", InputSource.AUTO, 2_000L)
    )

    @Test
    fun `toEntities Phase2 5개 필드까지 채우면 10개 엔티티 생성`() {
        val entities = BearSignalAutoCacheMapper.toEntities(sampleInputsWithExternal())

        assertEquals(10, entities.size)
        val byKey = entities.associateBy { it.indicatorKey }
        assertEquals(23.1, byKey[BearIndicatorKey.AMP_SEMI.key]!!.value, 1e-9)
        assertEquals(1.0, byKey[BearIndicatorKey.AMP_BUFFER.key]!!.value, 1e-9) // true → 1.0
        assertEquals(3.75, byKey[BearIndicatorKey.GATE_RATE.key]!!.value, 1e-9)
        assertEquals(1.0, byKey[BearIndicatorKey.GATE_DIR.key]!!.value, 1e-9) // hike → 1.0
        assertEquals(1.0, byKey[BearIndicatorKey.S3_ETF.key]!!.value, 1e-9) // up → 1.0
    }

    @Test
    fun `toEntities Phase2 필드가 null이면 기존 5개만 생성(하위 호환)`() {
        val entities = BearSignalAutoCacheMapper.toEntities(sampleInputs())

        assertEquals(5, entities.size)
    }

    @Test
    fun `toEntities toDomain Phase2 포함 왕복 변환 일치`() {
        val original = sampleInputsWithExternal()

        val roundTripped = BearSignalAutoCacheMapper.toDomain(BearSignalAutoCacheMapper.toEntities(original))

        assertEquals(original, roundTripped)
    }

    @Test
    fun `toDomain Phase2 키가 없으면 해당 필드는 null(구버전 캐시 호환)`() {
        val entities = BearSignalAutoCacheMapper.toEntities(sampleInputs())

        val result = BearSignalAutoCacheMapper.toDomain(entities)

        assertEquals(null, result!!.semi)
        assertEquals(null, result.buffer)
        assertEquals(null, result.rate)
        assertEquals(null, result.dir)
        assertEquals(null, result.etf)
    }

    @Test
    fun `buffer false는 0점0으로 인코딩되고 왕복 시 false 복원`() {
        val inputs = sampleInputs().copy(buffer = AutoIndicator(false, InputSource.AUTO, 1_000L))

        val entities = BearSignalAutoCacheMapper.toEntities(inputs)
        val byKey = entities.associateBy { it.indicatorKey }
        assertEquals(0.0, byKey[BearIndicatorKey.AMP_BUFFER.key]!!.value, 1e-9)

        val result = BearSignalAutoCacheMapper.toDomain(entities)
        assertEquals(false, result!!.buffer!!.value)
    }

    @Test
    fun `dir ease는 -1점0으로 인코딩되고 왕복 시 ease 복원`() {
        val inputs = sampleInputs().copy(dir = AutoIndicator("ease", InputSource.AUTO, 1_000L))

        val entities = BearSignalAutoCacheMapper.toEntities(inputs)
        val byKey = entities.associateBy { it.indicatorKey }
        assertEquals(-1.0, byKey[BearIndicatorKey.GATE_DIR.key]!!.value, 1e-9)

        val result = BearSignalAutoCacheMapper.toDomain(entities)
        assertEquals("ease", result!!.dir!!.value)
    }

    @Test
    fun `dir hold는 0점0으로 인코딩되고 왕복 시 hold 복원`() {
        val inputs = sampleInputs().copy(dir = AutoIndicator("hold", InputSource.AUTO, 1_000L))

        val entities = BearSignalAutoCacheMapper.toEntities(inputs)
        val byKey = entities.associateBy { it.indicatorKey }
        assertEquals(0.0, byKey[BearIndicatorKey.GATE_DIR.key]!!.value, 1e-9)

        val result = BearSignalAutoCacheMapper.toDomain(entities)
        assertEquals("hold", result!!.dir!!.value)
    }

    @Test
    fun `etf down은 -1점0으로 인코딩되고 왕복 시 down 복원`() {
        val inputs = sampleInputs().copy(etf = AutoIndicator("down", InputSource.AUTO, 1_000L))

        val entities = BearSignalAutoCacheMapper.toEntities(inputs)
        val byKey = entities.associateBy { it.indicatorKey }
        assertEquals(-1.0, byKey[BearIndicatorKey.S3_ETF.key]!!.value, 1e-9)

        val result = BearSignalAutoCacheMapper.toDomain(entities)
        assertEquals("down", result!!.etf!!.value)
    }

    @Test
    fun `etf flat은 0점0으로 인코딩되고 왕복 시 flat 복원`() {
        val inputs = sampleInputs().copy(etf = AutoIndicator("flat", InputSource.AUTO, 1_000L))

        val entities = BearSignalAutoCacheMapper.toEntities(inputs)
        val byKey = entities.associateBy { it.indicatorKey }
        assertEquals(0.0, byKey[BearIndicatorKey.S3_ETF.key]!!.value, 1e-9)

        val result = BearSignalAutoCacheMapper.toDomain(entities)
        assertEquals("flat", result!!.etf!!.value)
    }
}
