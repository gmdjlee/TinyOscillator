package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.CustomsTradeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class CustomsTradeCalculatorTest {

    // ── computeSemiShare ──────────────────────────────────────────────

    private fun reportBaselineItems(yearMonth: String = "202605") = listOf(
        CustomsTradeItem("반도체", "854239", 23_100.0, 5_000.0, yearMonth),
        CustomsTradeItem("자동차", "870323", 15_000.0, 1_000.0, yearMonth),
        CustomsTradeItem("일반기계", "845011", 10_000.0, 2_000.0, yearMonth),
        CustomsTradeItem("석유제품", "271019", 8_000.0, 3_000.0, yearMonth),
        CustomsTradeItem("선박", "890120", 5_000.0, 0.0, yearMonth),
        CustomsTradeItem("철강제품", "720839", 4_000.0, 500.0, yearMonth),
        CustomsTradeItem("무선통신기기", "851712", 12_000.0, 1_000.0, yearMonth),
        CustomsTradeItem("컴퓨터", "847130", 3_000.0, 500.0, yearMonth),
        CustomsTradeItem("디스플레이", "852852", 6_000.0, 500.0, yearMonth),
        CustomsTradeItem("가전", "845011", 3_000.0, 200.0, yearMonth),
        CustomsTradeItem("섬유류", "500710", 2_000.0, 100.0, yearMonth),
        CustomsTradeItem("정밀기기", "901380", 1_500.0, 100.0, yearMonth),
        CustomsTradeItem("합성수지", "390110", 3_400.0, 200.0, yearMonth),
        CustomsTradeItem("고무제품", "401110", 1_000.0, 100.0, yearMonth),
        CustomsTradeItem("플라스틱제품", "392690", 2_000.0, 100.0, yearMonth)
    )

    @Test
    fun `computeSemiShare 전 품목 수출 합계 대비 반도체 비중`() {
        val items = reportBaselineItems()
        val total = items.sumOf { it.exportUsd }
        val expected = 23_100.0 / total * 100.0

        val result = CustomsTradeCalculator.computeSemiShare(items)

        assertEquals(expected, result!!, 1e-9)
    }

    @Test
    fun `computeSemiShare HS 코드만 있고 품목명이 다르면 접두사로 매칭`() {
        val items = listOf(
            CustomsTradeItem("Semiconductors", "854231", 2_000.0, 0.0, "202605"),
            CustomsTradeItem("기타", "999999", 8_000.0, 0.0, "202605")
        )

        val result = CustomsTradeCalculator.computeSemiShare(items)

        assertEquals(20.0, result!!, 1e-9)
    }

    @Test
    fun `computeSemiShare 빈 리스트는 null`() {
        assertNull(CustomsTradeCalculator.computeSemiShare(emptyList()))
    }

    @Test
    fun `computeSemiShare 합계 0이면 null`() {
        val items = listOf(CustomsTradeItem("반도체", "854239", 0.0, 0.0, "202605"))
        assertNull(CustomsTradeCalculator.computeSemiShare(items))
    }

    @Test
    fun `computeSemiShare 반도체 항목이 없으면 0`() {
        val items = listOf(
            CustomsTradeItem("자동차", "870323", 1_000.0, 0.0, "202605"),
            CustomsTradeItem("선박", "890120", 1_000.0, 0.0, "202605")
        )

        val result = CustomsTradeCalculator.computeSemiShare(items)

        assertEquals(0.0, result!!, 1e-9)
    }

    // ── computeBufferIntact ───────────────────────────────────────────

    @Test
    fun `computeBufferIntact YoY 증감 0퍼센트면 건재`() {
        val current = reportBaselineItems("202605")
        val prior = reportBaselineItems("202505")

        val result = CustomsTradeCalculator.computeBufferIntact(current, prior)

        assertTrue(result!!)
    }

    @Test
    fun `computeBufferIntact YoY 급감(경계 미만)이면 붕괴`() {
        // 완충산업 합계(자동차+일반기계+석유) = 15000+10000+8000 = 33000
        val prior = reportBaselineItems("202505")
        // 현재를 -25% 급감시킴(임계 -20% 미만)
        val current = listOf(
            CustomsTradeItem("반도체", "854239", 23_100.0, 5_000.0, "202605"),
            CustomsTradeItem("자동차", "870323", 15_000.0 * 0.75, 1_000.0, "202605"),
            CustomsTradeItem("일반기계", "845011", 10_000.0 * 0.75, 2_000.0, "202605"),
            CustomsTradeItem("석유제품", "271019", 8_000.0 * 0.75, 3_000.0, "202605")
        )

        val result = CustomsTradeCalculator.computeBufferIntact(current, prior)

        assertFalse(result!!)
    }

    @Test
    fun `computeBufferIntact 경계값 정확히 -20퍼센트는 건재(gte)`() {
        val prior = listOf(
            CustomsTradeItem("자동차", "870323", 100.0, 0.0, "202505"),
            CustomsTradeItem("일반기계", "845011", 100.0, 0.0, "202505"),
            CustomsTradeItem("석유제품", "271019", 100.0, 0.0, "202505")
        )
        // 합계 300 → -20% = 240
        val current = listOf(
            CustomsTradeItem("자동차", "870323", 80.0, 0.0, "202605"),
            CustomsTradeItem("일반기계", "845011", 80.0, 0.0, "202605"),
            CustomsTradeItem("석유제품", "271019", 80.0, 0.0, "202605")
        )

        val result = CustomsTradeCalculator.computeBufferIntact(current, prior)

        assertTrue(result!!)
    }

    @Test
    fun `computeBufferIntact 경계값 바로 아래(-20-eps)는 붕괴`() {
        val prior = listOf(
            CustomsTradeItem("자동차", "870323", 1000.0, 0.0, "202505"),
            CustomsTradeItem("일반기계", "845011", 0.0, 0.0, "202505"),
            CustomsTradeItem("석유제품", "271019", 0.0, 0.0, "202505")
        )
        val current = listOf(
            CustomsTradeItem("자동차", "870323", 799.0, 0.0, "202605") // -20.1%
        )

        val result = CustomsTradeCalculator.computeBufferIntact(current, prior)

        assertFalse(result!!)
    }

    @Test
    fun `computeBufferIntact 완충산업 품목 전무하면 null`() {
        val current = listOf(CustomsTradeItem("반도체", "854239", 100.0, 0.0, "202605"))
        val prior = listOf(CustomsTradeItem("반도체", "854239", 100.0, 0.0, "202505"))

        assertNull(CustomsTradeCalculator.computeBufferIntact(current, prior))
    }

    @Test
    fun `computeBufferIntact 빈 리스트는 null`() {
        assertNull(CustomsTradeCalculator.computeBufferIntact(emptyList(), reportBaselineItems()))
        assertNull(CustomsTradeCalculator.computeBufferIntact(reportBaselineItems(), emptyList()))
    }
}
