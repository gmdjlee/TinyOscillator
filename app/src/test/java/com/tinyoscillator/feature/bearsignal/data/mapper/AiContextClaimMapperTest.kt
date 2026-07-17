package com.tinyoscillator.feature.bearsignal.data.mapper

import com.tinyoscillator.feature.bearsignal.data.local.BearSignalAiContextEntity
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextClaim
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextSectionKey
import com.tinyoscillator.feature.bearsignal.domain.model.ClaimType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** [AiContextClaimMapper] 왕복 변환·방어적 디코딩 테스트 (TASK_bear_signal_console.md §4.7, Phase 7-2). */
class AiContextClaimMapperTest {

    private fun claim(
        sectionKey: AiContextSectionKey = AiContextSectionKey.TYPE0_MONITOR,
        text: String = "체크리스트 항목",
        type: ClaimType = ClaimType.FACT,
        quote: String? = "원문 인용"
    ) = AiContextClaim(
        sectionKey = sectionKey,
        text = text,
        type = type,
        sourceUrl = "https://example.com/report",
        sourceTitle = "제목",
        sourceDate = LocalDate.of(2026, 7, 17),
        quote = quote
    )

    @Test
    fun `toEntity는 클레임 목록을 content_json 배열로 직렬화한다`() {
        val entity = AiContextClaimMapper.toEntity(
            sectionKey = AiContextSectionKey.TYPE0_MONITOR,
            claims = listOf(claim()),
            provider = "claude",
            asOf = LocalDate.of(2026, 7, 17),
            approvedAt = 100L
        )

        assertEquals("type0_monitor", entity.sectionKey)
        assertEquals("2026-07-17", entity.asOf)
        assertEquals("claude", entity.provider)
        assertEquals(100L, entity.approvedAt)
        assertTrue(entity.contentJson.contains("\"section_key\":\"type0_monitor\""))
        assertTrue(entity.contentJson.contains("\"source_date\":\"2026-07-17\""))
    }

    @Test
    fun `toDomain 왕복 변환은 값을 보존한다`() {
        val original = listOf(
            claim(text = "항목1"),
            claim(text = "항목2", type = ClaimType.INTERPRETATION, quote = null)
        )
        val entity = AiContextClaimMapper.toEntity(
            AiContextSectionKey.HISTORY_CURRENT,
            original,
            provider = "gemini",
            asOf = LocalDate.of(2026, 7, 17),
            approvedAt = 1L
        )

        val roundTripped = AiContextClaimMapper.toDomain(entity)

        assertEquals(original, roundTripped)
    }

    @Test
    fun `content_json이 손상된 JSON이면 빈 리스트를 반환한다`() {
        val entity = BearSignalAiContextEntity(
            sectionKey = "type0_monitor",
            contentJson = "이것은 JSON이 아니다",
            asOf = "2026-07-17",
            provider = "claude",
            approvedAt = 1L
        )

        val result = AiContextClaimMapper.toDomain(entity)

        assertEquals(emptyList<AiContextClaim>(), result)
    }

    @Test
    fun `알 수 없는 section_key 항목은 건너뛰고 나머지는 정상 복원한다`() {
        val json = """[
            {"section_key":"type9_unknown","text":"모름","type":"fact","source_url":"https://a.com","source_title":"t","source_date":"2026-07-17","quote":"q"},
            {"section_key":"type0_monitor","text":"정상","type":"fact","source_url":"https://a.com","source_title":"t","source_date":"2026-07-17","quote":"q"}
        ]""".trimIndent()
        val entity = BearSignalAiContextEntity(
            sectionKey = "type0_monitor",
            contentJson = json,
            asOf = "2026-07-17",
            provider = "claude",
            approvedAt = 1L
        )

        val result = AiContextClaimMapper.toDomain(entity)

        assertEquals(1, result.size)
        assertEquals("정상", result.first().text)
    }

    @Test
    fun `알 수 없는 source_date 형식의 항목은 건너뛴다`() {
        val json = """[
            {"section_key":"type0_monitor","text":"손상","type":"fact","source_url":"https://a.com","source_title":"t","source_date":"날짜아님","quote":"q"}
        ]""".trimIndent()
        val entity = BearSignalAiContextEntity(
            sectionKey = "type0_monitor",
            contentJson = json,
            asOf = "2026-07-17",
            provider = "claude",
            approvedAt = 1L
        )

        val result = AiContextClaimMapper.toDomain(entity)

        assertEquals(emptyList<AiContextClaim>(), result)
    }
}
