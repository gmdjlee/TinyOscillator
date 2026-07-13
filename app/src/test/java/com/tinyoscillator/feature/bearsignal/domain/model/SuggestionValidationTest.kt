package com.tinyoscillator.feature.bearsignal.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * [SuggestionValidation] 순수 함수 테스트 — 열거형 화이트리스트·신선도(STALE)·급변 재확인 트리거
 * 경계값 (TASK_bear_signal_console.md §4.5).
 */
class SuggestionValidationTest {

    // ── 열거형 화이트리스트 ────────────────────────────────────────────

    @Test
    fun `isValidDir는 ease hold hike만 허용한다`() {
        assertTrue(SuggestionValidation.isValidDir("ease"))
        assertTrue(SuggestionValidation.isValidDir("hold"))
        assertTrue(SuggestionValidation.isValidDir("hike"))
        assertFalse(SuggestionValidation.isValidDir("tightening"))
        assertFalse(SuggestionValidation.isValidDir(""))
    }

    @Test
    fun `isValidBigDeal는 smooth pending failed만 허용한다`() {
        assertTrue(SuggestionValidation.isValidBigDeal("smooth"))
        assertTrue(SuggestionValidation.isValidBigDeal("pending"))
        assertTrue(SuggestionValidation.isValidBigDeal("failed"))
        assertFalse(SuggestionValidation.isValidBigDeal("withdrawn"))
    }

    // ── 신선도(STALE) 경계 ────────────────────────────────────────────

    @Test
    fun `isStale 정확히 maxAgeDays이면 STALE 아님(초과일 때만 STALE)`() {
        val today = LocalDate.of(2026, 8, 1)
        val asOf = today.minusDays(45)
        assertFalse(SuggestionValidation.isStale(asOf, today, 45L))
    }

    @Test
    fun `isStale maxAgeDays를 1일 초과하면 STALE`() {
        val today = LocalDate.of(2026, 8, 1)
        val asOf = today.minusDays(46)
        assertTrue(SuggestionValidation.isStale(asOf, today, 45L))
    }

    @Test
    fun `isStale 오늘 날짜면 STALE 아님`() {
        val today = LocalDate.of(2026, 8, 1)
        assertFalse(SuggestionValidation.isStale(today, today, 0L))
    }

    // ── 급변 재확인 트리거 — 금리 ±0.5%p ────────────────────────────────

    @Test
    fun `isVolatileRateChange 정확히 0점5는 초과가 아니므로 급변 아님`() {
        assertFalse(SuggestionValidation.isVolatileRateChange(current = 4.0, proposed = 4.5))
    }

    @Test
    fun `isVolatileRateChange 0점5 초과면 급변`() {
        assertTrue(SuggestionValidation.isVolatileRateChange(current = 4.0, proposed = 4.51))
    }

    @Test
    fun `isVolatileRateChange 하락 방향도 절대값 기준으로 판정한다`() {
        assertTrue(SuggestionValidation.isVolatileRateChange(current = 4.5, proposed = 3.9))
        assertFalse(SuggestionValidation.isVolatileRateChange(current = 4.5, proposed = 4.0))
    }

    @Test
    fun `isVolatileRateChange current가 없으면(기준 없음) 급변 아님`() {
        assertFalse(SuggestionValidation.isVolatileRateChange(current = null, proposed = 10.0))
    }

    // ── 급변 재확인 트리거 — 신용잔고 ±30% ────────────────────────────────

    @Test
    fun `isVolatileCreditChange 정확히 30퍼센트는 초과가 아니므로 급변 아님`() {
        assertFalse(SuggestionValidation.isVolatileCreditChange(current = 40.0, proposed = 52.0))
    }

    @Test
    fun `isVolatileCreditChange 30퍼센트 초과면 급변`() {
        assertTrue(SuggestionValidation.isVolatileCreditChange(current = 40.0, proposed = 52.1))
    }

    @Test
    fun `isVolatileCreditChange current가 0이면 비율 계산 불가로 급변 아님`() {
        assertFalse(SuggestionValidation.isVolatileCreditChange(current = 0.0, proposed = 50.0))
    }

    @Test
    fun `isVolatileCreditChange current가 없으면 급변 아님`() {
        assertFalse(SuggestionValidation.isVolatileCreditChange(current = null, proposed = 50.0))
    }
}
