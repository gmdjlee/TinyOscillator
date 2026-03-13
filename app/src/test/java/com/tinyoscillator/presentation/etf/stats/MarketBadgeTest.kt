package com.tinyoscillator.presentation.etf.stats

import org.junit.Assert.*
import org.junit.Test

/**
 * MarketBadge 유틸리티 함수 단위 테스트
 *
 * marketDisplayName, normalizeMarketCode 검증
 */
class MarketBadgeTest {

    // ==========================================================
    // normalizeMarketCode 테스트
    // ==========================================================

    @Test
    fun `normalizeMarketCode - KOSPI는 KOSPI를 반환한다`() {
        assertEquals("KOSPI", normalizeMarketCode("KOSPI"))
    }

    @Test
    fun `normalizeMarketCode - 거래소는 KOSPI를 반환한다`() {
        assertEquals("KOSPI", normalizeMarketCode("거래소"))
    }

    @Test
    fun `normalizeMarketCode - KOSDAQ는 KOSDAQ를 반환한다`() {
        assertEquals("KOSDAQ", normalizeMarketCode("KOSDAQ"))
    }

    @Test
    fun `normalizeMarketCode - 코스닥은 KOSDAQ를 반환한다`() {
        assertEquals("KOSDAQ", normalizeMarketCode("코스닥"))
    }

    @Test
    fun `normalizeMarketCode - 알 수 없는 값은 그대로 반환한다`() {
        assertEquals("KONEX", normalizeMarketCode("KONEX"))
    }

    @Test
    fun `normalizeMarketCode - null은 null을 반환한다`() {
        assertNull(normalizeMarketCode(null))
    }

    // ==========================================================
    // marketDisplayName 테스트
    // ==========================================================

    @Test
    fun `marketDisplayName - KOSPI는 코스피를 반환한다`() {
        assertEquals("코스피", marketDisplayName("KOSPI"))
    }

    @Test
    fun `marketDisplayName - 거래소는 코스피를 반환한다`() {
        assertEquals("코스피", marketDisplayName("거래소"))
    }

    @Test
    fun `marketDisplayName - KOSDAQ는 코스닥을 반환한다`() {
        assertEquals("코스닥", marketDisplayName("KOSDAQ"))
    }

    @Test
    fun `marketDisplayName - 코스닥은 코스닥을 반환한다`() {
        assertEquals("코스닥", marketDisplayName("코스닥"))
    }

    @Test
    fun `marketDisplayName - 알 수 없는 값은 null을 반환한다`() {
        assertNull(marketDisplayName("KONEX"))
    }

    @Test
    fun `marketDisplayName - null은 null을 반환한다`() {
        assertNull(marketDisplayName(null))
    }

    @Test
    fun `marketDisplayName - 빈 문자열은 null을 반환한다`() {
        assertNull(marketDisplayName(""))
    }
}
