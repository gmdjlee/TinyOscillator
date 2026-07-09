package com.tinyoscillator.feature.bearsignal.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IpoEtfDirectionCalculatorTest {

    @Test
    fun `고점 근접 유지시 up`() {
        // 최근고점 100, 현재 97 (-3%) >= -5.0
        val closes = listOf(90.0, 95.0, 100.0, 98.0, 97.0)

        assertEquals("up", IpoEtfDirectionCalculator.computeDirection(closes))
    }

    @Test
    fun `고점대비 뚜렷한 하락시 down`() {
        // 최근고점 100, 현재 80 (-20%) <= -15.0
        val closes = listOf(90.0, 95.0, 100.0, 90.0, 80.0)

        assertEquals("down", IpoEtfDirectionCalculator.computeDirection(closes))
    }

    @Test
    fun `중간 구간은 flat`() {
        // 최근고점 100, 현재 90 (-10%) — up/down 사이
        val closes = listOf(90.0, 95.0, 100.0, 95.0, 90.0)

        assertEquals("flat", IpoEtfDirectionCalculator.computeDirection(closes))
    }

    @Test
    fun `경계값 정확히 -5퍼센트는 up(gte)`() {
        val closes = listOf(100.0, 95.0) // -5.0%

        assertEquals("up", IpoEtfDirectionCalculator.computeDirection(closes))
    }

    @Test
    fun `경계값 -5퍼센트 초과 하락은 flat`() {
        val closes = listOf(100.0, 94.9) // -5.1%

        assertEquals("flat", IpoEtfDirectionCalculator.computeDirection(closes))
    }

    @Test
    fun `경계값 정확히 -15퍼센트는 down(lte)`() {
        val closes = listOf(100.0, 85.0) // -15.0%

        assertEquals("down", IpoEtfDirectionCalculator.computeDirection(closes))
    }

    @Test
    fun `경계값 -15퍼센트 미달은 flat`() {
        val closes = listOf(100.0, 85.1) // -14.9%

        assertEquals("flat", IpoEtfDirectionCalculator.computeDirection(closes))
    }

    @Test
    fun `조회창을 초과한 과거 고점은 무시(최근 60거래일만)`() {
        // 60거래일 이전에 훨씬 높은 고점(200)이 있었지만 조회창 밖 — 최근창의 고점(100) 기준 판정
        val oldHigh = List(70) { 100.0 }.toMutableList()
        oldHigh[0] = 200.0
        val closes = oldHigh + listOf(97.0) // 최근고점(창 내) 100 대비 -3%

        assertEquals("up", IpoEtfDirectionCalculator.computeDirection(closes))
    }

    @Test
    fun `빈 리스트는 null`() {
        assertNull(IpoEtfDirectionCalculator.computeDirection(emptyList()))
    }

    @Test
    fun `최근고점이 0이면 null`() {
        assertNull(IpoEtfDirectionCalculator.computeDirection(listOf(0.0, 0.0)))
    }
}
