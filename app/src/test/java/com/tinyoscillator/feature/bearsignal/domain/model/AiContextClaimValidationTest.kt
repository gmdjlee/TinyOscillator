package com.tinyoscillator.feature.bearsignal.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * [AiContextClaimValidation] 검증 파이프라인 골든·경계 테스트 (TASK_bear_signal_console.md §4.7
 * "검증 파이프라인" 라인 350~355 — 클레임 단위 폐기, 그룹 폐기 아님).
 */
class AiContextClaimValidationTest {

    private val today = LocalDate.of(2026, 7, 17)
    private val verifiedUrls = listOf("https://example.com/report", "https://news.example.com/article")

    private fun factDraft(
        sectionKey: AiContextSectionKey = AiContextSectionKey.TYPE0_MONITOR,
        sourceUrl: String? = "https://example.com/report",
        sourceDate: LocalDate? = today,
        quote: String? = "원문 인용"
    ): AiContextClaimDraft = AiContextClaimDraft(
        sectionKey = sectionKey,
        text = "체크리스트 항목 텍스트",
        type = ClaimType.FACT,
        sourceUrl = sourceUrl,
        sourceTitle = "리포트 제목",
        sourceDate = sourceDate,
        quote = quote
    )

    // ── URL 교차검증 ─────────────────────────────────────────────

    @Test
    fun `URL이 검색결과 목록에 있으면 검증 통과한다`() {
        assertTrue(AiContextClaimValidation.isUrlVerified("https://example.com/report", verifiedUrls))
    }

    @Test
    fun `URL이 검색결과 목록에 없으면 검증 실패한다`() {
        assertFalse(AiContextClaimValidation.isUrlVerified("https://malicious.example.com/fake", verifiedUrls))
    }

    @Test
    fun `URL이 목록에 없는 클레임은 URL_NOT_VERIFIED로 폐기된다`() {
        val result = AiContextClaimValidation.validate(
            factDraft(sourceUrl = "https://malicious.example.com/fake"),
            verifiedUrls,
            today
        )
        assertEquals(
            AiContextClaimValidationResult.Rejected(AiContextClaimRejection.URL_NOT_VERIFIED),
            result
        )
    }

    @Test
    fun `source_url이 null이면 URL_NOT_VERIFIED로 폐기된다`() {
        val result = AiContextClaimValidation.validate(factDraft(sourceUrl = null), verifiedUrls, today)
        assertEquals(
            AiContextClaimValidationResult.Rejected(AiContextClaimRejection.URL_NOT_VERIFIED),
            result
        )
    }

    // ── source_date 부재 ─────────────────────────────────────────

    @Test
    fun `source_date가 없으면 SOURCE_DATE_MISSING으로 폐기된다`() {
        val result = AiContextClaimValidation.validate(factDraft(sourceDate = null), verifiedUrls, today)
        assertEquals(
            AiContextClaimValidationResult.Rejected(AiContextClaimRejection.SOURCE_DATE_MISSING),
            result
        )
    }

    // ── FACT quote 부재 ──────────────────────────────────────────

    @Test
    fun `fact 클레임에 quote가 없으면 FACT_QUOTE_MISSING으로 폐기된다`() {
        val result = AiContextClaimValidation.validate(factDraft(quote = null), verifiedUrls, today)
        assertEquals(
            AiContextClaimValidationResult.Rejected(AiContextClaimRejection.FACT_QUOTE_MISSING),
            result
        )
    }

    @Test
    fun `fact 클레임에 quote가 공백뿐이면 FACT_QUOTE_MISSING으로 폐기된다`() {
        val result = AiContextClaimValidation.validate(factDraft(quote = "   "), verifiedUrls, today)
        assertEquals(
            AiContextClaimValidationResult.Rejected(AiContextClaimRejection.FACT_QUOTE_MISSING),
            result
        )
    }

    // ── monitor·cases는 fact만 허용 ───────────────────────────────

    @Test
    fun `monitor 섹션의 interpretation 클레임은 INTERPRETATION_NOT_ALLOWED로 폐기된다`() {
        val draft = factDraft(sectionKey = AiContextSectionKey.TYPE0_MONITOR, quote = null)
            .copy(type = ClaimType.INTERPRETATION)
        val result = AiContextClaimValidation.validate(draft, verifiedUrls, today)
        assertEquals(
            AiContextClaimValidationResult.Rejected(AiContextClaimRejection.INTERPRETATION_NOT_ALLOWED),
            result
        )
    }

    @Test
    fun `cases 섹션의 interpretation 클레임은 INTERPRETATION_NOT_ALLOWED로 폐기된다`() {
        val draft = factDraft(sectionKey = AiContextSectionKey.TYPE2_CASES, quote = null)
            .copy(type = ClaimType.INTERPRETATION)
        val result = AiContextClaimValidation.validate(draft, verifiedUrls, today)
        assertEquals(
            AiContextClaimValidationResult.Rejected(AiContextClaimRejection.INTERPRETATION_NOT_ALLOWED),
            result
        )
    }

    @Test
    fun `history_current 섹션의 interpretation 클레임은 quote 없이도 통과한다`() {
        val draft = AiContextClaimDraft(
            sectionKey = AiContextSectionKey.HISTORY_CURRENT,
            text = "AI 해석 문단",
            type = ClaimType.INTERPRETATION,
            sourceUrl = "https://example.com/report",
            sourceTitle = "리포트 제목",
            sourceDate = today,
            quote = null
        )
        val result = AiContextClaimValidation.validate(draft, verifiedUrls, today)
        assertTrue(result is AiContextClaimValidationResult.Accepted)
        val accepted = result as AiContextClaimValidationResult.Accepted
        assertEquals(ClaimType.INTERPRETATION, accepted.claim.type)
        assertFalse(accepted.stale)
    }

    @Test
    fun `history_current 섹션의 fact 클레임은 quote 부재 시 여전히 폐기된다`() {
        val draft = AiContextClaimDraft(
            sectionKey = AiContextSectionKey.HISTORY_CURRENT,
            text = "사실 클레임",
            type = ClaimType.FACT,
            sourceUrl = "https://example.com/report",
            sourceTitle = "리포트 제목",
            sourceDate = today,
            quote = null
        )
        val result = AiContextClaimValidation.validate(draft, verifiedUrls, today)
        assertEquals(
            AiContextClaimValidationResult.Rejected(AiContextClaimRejection.FACT_QUOTE_MISSING),
            result
        )
    }

    // ── 전체 통과 (골든) ──────────────────────────────────────────

    @Test
    fun `모든 검증을 통과한 fact 클레임은 Accepted로 반환된다`() {
        val result = AiContextClaimValidation.validate(factDraft(), verifiedUrls, today)
        assertTrue(result is AiContextClaimValidationResult.Accepted)
        val accepted = result as AiContextClaimValidationResult.Accepted
        assertEquals(AiContextSectionKey.TYPE0_MONITOR, accepted.claim.sectionKey)
        assertEquals("https://example.com/report", accepted.claim.sourceUrl)
        assertFalse(accepted.stale)
    }

    // ── STALE 경계값 — monitor 45d/46d ─────────────────────────────

    @Test
    fun `monitor 섹션 45일 경과는 STALE이 아니다`() {
        val sourceDate = today.minusDays(45)
        val result = AiContextClaimValidation.validate(
            factDraft(sectionKey = AiContextSectionKey.TYPE1_MONITOR, sourceDate = sourceDate),
            verifiedUrls,
            today
        )
        assertTrue(result is AiContextClaimValidationResult.Accepted)
        assertFalse((result as AiContextClaimValidationResult.Accepted).stale)
    }

    @Test
    fun `monitor 섹션 46일 경과는 STALE이다(폐기 아님)`() {
        val sourceDate = today.minusDays(46)
        val result = AiContextClaimValidation.validate(
            factDraft(sectionKey = AiContextSectionKey.TYPE1_MONITOR, sourceDate = sourceDate),
            verifiedUrls,
            today
        )
        assertTrue(result is AiContextClaimValidationResult.Accepted)
        assertTrue((result as AiContextClaimValidationResult.Accepted).stale)
    }

    // ── STALE 경계값 — cases 30d/31d ────────────────────────────────

    @Test
    fun `cases 섹션 30일 경과는 STALE이 아니다`() {
        val sourceDate = today.minusDays(30)
        val result = AiContextClaimValidation.validate(
            factDraft(sectionKey = AiContextSectionKey.TYPE2_CASES, sourceDate = sourceDate),
            verifiedUrls,
            today
        )
        assertTrue(result is AiContextClaimValidationResult.Accepted)
        assertFalse((result as AiContextClaimValidationResult.Accepted).stale)
    }

    @Test
    fun `cases 섹션 31일 경과는 STALE이다(폐기 아님)`() {
        val sourceDate = today.minusDays(31)
        val result = AiContextClaimValidation.validate(
            factDraft(sectionKey = AiContextSectionKey.TYPE2_CASES, sourceDate = sourceDate),
            verifiedUrls,
            today
        )
        assertTrue(result is AiContextClaimValidationResult.Accepted)
        assertTrue((result as AiContextClaimValidationResult.Accepted).stale)
    }

    // ── STALE 경계값 — history_current 30d/31d ──────────────────────

    @Test
    fun `history_current 섹션 30일 경과는 STALE이 아니다`() {
        val sourceDate = today.minusDays(30)
        val result = AiContextClaimValidation.validate(
            factDraft(sectionKey = AiContextSectionKey.HISTORY_CURRENT, sourceDate = sourceDate),
            verifiedUrls,
            today
        )
        assertTrue(result is AiContextClaimValidationResult.Accepted)
        assertFalse((result as AiContextClaimValidationResult.Accepted).stale)
    }

    @Test
    fun `history_current 섹션 31일 경과는 STALE이다(폐기 아님)`() {
        val sourceDate = today.minusDays(31)
        val result = AiContextClaimValidation.validate(
            factDraft(sectionKey = AiContextSectionKey.HISTORY_CURRENT, sourceDate = sourceDate),
            verifiedUrls,
            today
        )
        assertTrue(result is AiContextClaimValidationResult.Accepted)
        assertTrue((result as AiContextClaimValidationResult.Accepted).stale)
    }

    // ── section_key 문자열 매핑 ──────────────────────────────────

    @Test
    fun `section_key 문자열은 스펙 값과 1대1 대응한다`() {
        assertEquals("type0_monitor", AiContextSectionKey.TYPE0_MONITOR.key)
        assertEquals("type1_monitor", AiContextSectionKey.TYPE1_MONITOR.key)
        assertEquals("type2_monitor", AiContextSectionKey.TYPE2_MONITOR.key)
        assertEquals("type0_cases", AiContextSectionKey.TYPE0_CASES.key)
        assertEquals("type1_cases", AiContextSectionKey.TYPE1_CASES.key)
        assertEquals("type2_cases", AiContextSectionKey.TYPE2_CASES.key)
        assertEquals("history_current", AiContextSectionKey.HISTORY_CURRENT.key)
    }

    @Test
    fun `fromKey는 알 수 없는 문자열에 null을 반환한다`() {
        assertEquals(AiContextSectionKey.TYPE0_MONITOR, AiContextSectionKey.fromKey("type0_monitor"))
        assertEquals(null, AiContextSectionKey.fromKey("unknown_key"))
    }
}
