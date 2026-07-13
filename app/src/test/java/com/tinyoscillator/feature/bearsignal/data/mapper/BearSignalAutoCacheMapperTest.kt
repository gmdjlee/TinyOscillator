package com.tinyoscillator.feature.bearsignal.data.mapper

import com.tinyoscillator.feature.bearsignal.data.local.BearSignalAutoCacheEntity
import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.AutoIndicator
import com.tinyoscillator.feature.bearsignal.domain.model.BearIndicatorKey
import com.tinyoscillator.feature.bearsignal.domain.model.InputSource
import com.tinyoscillator.feature.bearsignal.domain.model.SuggestionField
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

    // ── Phase 4(§4.5) — 웹/LLM 제안 승인 전용 필드(credit/lossRatio/bigDeal) ────

    private fun sampleInputsWithSuggestionFields() = sampleInputs().copy(
        credit = AutoIndicator(42.0, InputSource.AUTO, 3_000L),
        lossRatio = AutoIndicator(65.0, InputSource.AUTO, 3_000L),
        bigDeal = AutoIndicator("pending", InputSource.AUTO, 3_000L)
    )

    @Test
    fun `toEntities Phase4 3개 필드까지 채우면 8개 엔티티 생성`() {
        val entities = BearSignalAutoCacheMapper.toEntities(sampleInputsWithSuggestionFields())

        assertEquals(8, entities.size)
        val byKey = entities.associateBy { it.indicatorKey }
        assertEquals(42.0, byKey[BearIndicatorKey.GATE_CREDIT.key]!!.value, 1e-9)
        assertEquals(65.0, byKey[BearIndicatorKey.S3_LOSS_RATIO.key]!!.value, 1e-9)
        assertEquals(1.0, byKey[BearIndicatorKey.S3_BIG_DEAL.key]!!.value, 1e-9) // pending → 1.0
    }

    @Test
    fun `toEntities toDomain Phase4 포함 왕복 변환 일치`() {
        val original = sampleInputsWithSuggestionFields()

        val roundTripped = BearSignalAutoCacheMapper.toDomain(BearSignalAutoCacheMapper.toEntities(original))

        assertEquals(original, roundTripped)
    }

    @Test
    fun `toDomain Phase4 키가 없으면 해당 필드는 null(구버전 캐시 호환)`() {
        val entities = BearSignalAutoCacheMapper.toEntities(sampleInputs())

        val result = BearSignalAutoCacheMapper.toDomain(entities)

        assertEquals(null, result!!.credit)
        assertEquals(null, result.lossRatio)
        assertEquals(null, result.bigDeal)
    }

    @Test
    fun `bigDeal smooth failed도 각각 0점0 2점0으로 인코딩되고 왕복 복원된다`() {
        val smoothInputs = sampleInputs().copy(bigDeal = AutoIndicator("smooth", InputSource.AUTO, 1_000L))
        val smoothEntities = BearSignalAutoCacheMapper.toEntities(smoothInputs)
        assertEquals(0.0, smoothEntities.associateBy { it.indicatorKey }[BearIndicatorKey.S3_BIG_DEAL.key]!!.value, 1e-9)
        assertEquals("smooth", BearSignalAutoCacheMapper.toDomain(smoothEntities)!!.bigDeal!!.value)

        val failedInputs = sampleInputs().copy(bigDeal = AutoIndicator("failed", InputSource.AUTO, 1_000L))
        val failedEntities = BearSignalAutoCacheMapper.toEntities(failedInputs)
        assertEquals(2.0, failedEntities.associateBy { it.indicatorKey }[BearIndicatorKey.S3_BIG_DEAL.key]!!.value, 1e-9)
        assertEquals("failed", BearSignalAutoCacheMapper.toDomain(failedEntities)!!.bigDeal!!.value)
    }

    // ── suggestionEntity(field, rawValue, updatedAt) — §4.5 제안 승인 개별 upsert 경로 ─────

    @Test
    fun `suggestionEntity RATE는 숫자 문자열을 그대로 Double로 인코딩한다`() {
        val entity = BearSignalAutoCacheMapper.suggestionEntity(SuggestionField.RATE, "4.50", 5_000L)

        assertEquals(BearIndicatorKey.GATE_RATE.key, entity.indicatorKey)
        assertEquals(4.50, entity.value, 1e-9)
        assertEquals(InputSource.AUTO.name, entity.source)
        assertEquals(5_000L, entity.updatedAt)
    }

    @Test
    fun `suggestionEntity DIR은 ease hold hike를 -1 0 1로 인코딩한다`() {
        assertEquals(-1.0, BearSignalAutoCacheMapper.suggestionEntity(SuggestionField.DIR, "ease", 1L).value, 1e-9)
        assertEquals(0.0, BearSignalAutoCacheMapper.suggestionEntity(SuggestionField.DIR, "hold", 1L).value, 1e-9)
        assertEquals(1.0, BearSignalAutoCacheMapper.suggestionEntity(SuggestionField.DIR, "hike", 1L).value, 1e-9)
    }

    @Test
    fun `suggestionEntity BIG_DEAL은 smooth pending failed를 0 1 2로 인코딩한다`() {
        assertEquals(0.0, BearSignalAutoCacheMapper.suggestionEntity(SuggestionField.BIG_DEAL, "smooth", 1L).value, 1e-9)
        assertEquals(1.0, BearSignalAutoCacheMapper.suggestionEntity(SuggestionField.BIG_DEAL, "pending", 1L).value, 1e-9)
        assertEquals(2.0, BearSignalAutoCacheMapper.suggestionEntity(SuggestionField.BIG_DEAL, "failed", 1L).value, 1e-9)
    }

    @Test
    fun `suggestionEntity CREDIT LOSS_RATIO는 숫자 문자열을 그대로 인코딩하고 키가 다르다`() {
        val credit = BearSignalAutoCacheMapper.suggestionEntity(SuggestionField.CREDIT, "50.00", 1L)
        val loss = BearSignalAutoCacheMapper.suggestionEntity(SuggestionField.LOSS_RATIO, "65.00", 1L)

        assertEquals(BearIndicatorKey.GATE_CREDIT.key, credit.indicatorKey)
        assertEquals(50.0, credit.value, 1e-9)
        assertEquals(BearIndicatorKey.S3_LOSS_RATIO.key, loss.indicatorKey)
        assertEquals(65.0, loss.value, 1e-9)
    }

    @Test
    fun `suggestionEntity source는 항상 AUTO다(승인 반영 경로)`() {
        val entity = BearSignalAutoCacheMapper.suggestionEntity(SuggestionField.RATE, "4.50", 1L)
        assertEquals(InputSource.AUTO.name, entity.source)
    }
}
