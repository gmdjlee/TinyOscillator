package com.tinyoscillator.feature.bearsignal.data.mapper

import com.tinyoscillator.feature.bearsignal.data.local.BearSignalManualInputEntity
import com.tinyoscillator.feature.bearsignal.domain.model.InputSource
import com.tinyoscillator.feature.bearsignal.domain.model.IpoBigConsumption
import com.tinyoscillator.feature.bearsignal.domain.model.ManualIndicatorKey
import com.tinyoscillator.feature.bearsignal.domain.usecase.RateGateInputCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BearSignalManualInputMapperTest {

    // ── 인코딩/디코딩 왕복 ────────────────────────────────────

    @Test
    fun `boolean 인코딩 왕복`() {
        assertTrue(BearSignalManualInputMapper.decodeBoolean(BearSignalManualInputMapper.encodeBoolean(true)))
        assertFalse(BearSignalManualInputMapper.decodeBoolean(BearSignalManualInputMapper.encodeBoolean(false)))
    }

    @Test
    fun `dir 인코딩 왕복 - ease hold hike`() {
        listOf(RateGateInputCalculator.DIR_EASE, RateGateInputCalculator.DIR_HOLD, RateGateInputCalculator.DIR_HIKE)
            .forEach { dir ->
                val code = BearSignalManualInputMapper.encodeDir(dir)
                assertEquals(dir, BearSignalManualInputMapper.decodeDir(code))
            }
    }

    @Test
    fun `big 인코딩 왕복 - smooth pending failed`() {
        listOf(IpoBigConsumption.SMOOTH, IpoBigConsumption.PENDING, IpoBigConsumption.FAILED).forEach { big ->
            val code = BearSignalManualInputMapper.encodeBig(big)
            assertEquals(big, BearSignalManualInputMapper.decodeBig(code))
        }
    }

    // ── toDomain ──────────────────────────────────────────

    @Test
    fun `entities가 비어있으면 전 필드가 null인 ManualBearSignalInputs 반환`() {
        val domain = BearSignalManualInputMapper.toDomain(emptyList())

        assertNull(domain.loss)
        assertNull(domain.big)
        assertNull(domain.issueRatio)
        assertNull(domain.credit)
        assertNull(domain.margin)
        assertNull(domain.dir)
    }

    @Test
    fun `일부 키만 있으면 나머지는 null 유지`() {
        val entities = listOf(
            BearSignalManualInputEntity(ManualIndicatorKey.LOSS.key, 72.0, 100L),
            BearSignalManualInputEntity(ManualIndicatorKey.CREDIT.key, 45.0, 200L)
        )

        val domain = BearSignalManualInputMapper.toDomain(entities)

        assertEquals(72.0, domain.loss!!.value, 1e-9)
        assertEquals(InputSource.MANUAL, domain.loss!!.source)
        assertEquals(100L, domain.loss!!.updatedAt)
        assertEquals(45.0, domain.credit!!.value, 1e-9)
        assertNull(domain.big)
        assertNull(domain.issueRatio)
        assertNull(domain.margin)
        assertNull(domain.dir)
    }

    @Test
    fun `big margin dir는 디코딩되어 반환된다`() {
        val entities = listOf(
            BearSignalManualInputEntity(ManualIndicatorKey.BIG.key, BearSignalManualInputMapper.encodeBig("failed"), 1L),
            BearSignalManualInputEntity(ManualIndicatorKey.MARGIN.key, BearSignalManualInputMapper.encodeBoolean(true), 1L),
            BearSignalManualInputEntity(ManualIndicatorKey.DIR.key, BearSignalManualInputMapper.encodeDir("hike"), 1L)
        )

        val domain = BearSignalManualInputMapper.toDomain(entities)

        assertEquals("failed", domain.big!!.value)
        assertTrue(domain.margin!!.value)
        assertEquals("hike", domain.dir!!.value)
    }

    @Test
    fun `toEntity는 key를 indicatorKey 문자열로 저장한다`() {
        val entity = BearSignalManualInputMapper.toEntity(ManualIndicatorKey.CREDIT, 38.0, 500L)

        assertEquals(ManualIndicatorKey.CREDIT.key, entity.indicatorKey)
        assertEquals(38.0, entity.value, 1e-9)
        assertEquals(500L, entity.updatedAt)
    }

    @Test
    fun `fromKey는 알 수 없는 키에 null 반환`() {
        assertNull(ManualIndicatorKey.fromKey("unknown"))
        assertEquals(ManualIndicatorKey.LOSS, ManualIndicatorKey.fromKey("loss"))
    }
}
