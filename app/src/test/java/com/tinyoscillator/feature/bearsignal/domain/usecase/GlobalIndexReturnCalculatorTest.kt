package com.tinyoscillator.feature.bearsignal.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlobalIndexReturnCalculatorTest {

    /** idx=0 기준 종가가 100인 지점부터 매일 +0.1 상승하는 등차 시계열(길이 [size]) */
    private fun linearCloses(size: Int, start: Double = 100.0, step: Double = 0.1): List<Double> =
        (0 until size).map { start + it * step }

    @Test
    fun `데이터 253건 이상이면 4기간 모두 계산`() {
        val closes = linearCloses(260)

        val result = GlobalIndexReturnCalculator.computeReturns(closes)

        assertEquals(4, result.size)
        result.forEach { assertEquals(true, it != null) }
    }

    @Test
    fun `단순 상승 시계열의 1M 수익률 검증`() {
        // 마지막 22개(오늘 포함, lookback=21) — base=idx(size-1-21), latest=idx(size-1)
        val closes = listOf(100.0, 110.0) + List(20) { 110.0 } // size=22, base=idx0=100, latest=idx21=110.0
        val result = GlobalIndexReturnCalculator.computeReturns(closes)

        // 12M/6M/3M는 데이터 부족(음수 인덱스)으로 null, 1M만 계산됨
        assertNull(result[0])
        assertNull(result[1])
        assertNull(result[2])
        assertEquals(10.0, result[3]!!, 1e-9)
    }

    @Test
    fun `데이터가 MIN_TRADING_DAYS 미만이면 전부 null`() {
        val closes = linearCloses(GlobalIndexReturnCalculator.MIN_TRADING_DAYS - 1)

        val result = GlobalIndexReturnCalculator.computeReturns(closes)

        assertEquals(listOf(null, null, null, null), result)
    }

    @Test
    fun `데이터가 정확히 MIN_TRADING_DAYS면 1M만 계산`() {
        val closes = linearCloses(GlobalIndexReturnCalculator.MIN_TRADING_DAYS)

        val result = GlobalIndexReturnCalculator.computeReturns(closes)

        assertNull(result[0])
        assertNull(result[1])
        assertNull(result[2])
        assertEquals(true, result[3] != null)
    }

    @Test
    fun `12M 6M 3M 1M 각각 lookback 경계에서 정확히 계산`() {
        // size = 253, index i의 종가 = 1000 + i (즉 idx0=1000, idx252=1252)
        val size = GlobalIndexReturnCalculator.LOOKBACK_12M + 1
        val closes = (0 until size).map { 1000.0 + it }

        val latest = closes.last() // 1000+252=1252
        val base12 = closes[closes.size - 1 - GlobalIndexReturnCalculator.LOOKBACK_12M] // idx0=1000
        val base6 = closes[closes.size - 1 - GlobalIndexReturnCalculator.LOOKBACK_6M]
        val base3 = closes[closes.size - 1 - GlobalIndexReturnCalculator.LOOKBACK_3M]
        val base1 = closes[closes.size - 1 - GlobalIndexReturnCalculator.LOOKBACK_1M]

        val result = GlobalIndexReturnCalculator.computeReturns(closes)

        assertEquals((latest - base12) / base12 * 100.0, result[0]!!, 1e-9)
        assertEquals((latest - base6) / base6 * 100.0, result[1]!!, 1e-9)
        assertEquals((latest - base3) / base3 * 100.0, result[2]!!, 1e-9)
        assertEquals((latest - base1) / base1 * 100.0, result[3]!!, 1e-9)
    }

    @Test
    fun `기준 종가가 0이면 해당 기간 null`() {
        val size = GlobalIndexReturnCalculator.LOOKBACK_1M + 1
        val closes = MutableList(size) { 100.0 }
        closes[0] = 0.0 // base(1M) = idx0

        val result = GlobalIndexReturnCalculator.computeReturns(closes)

        assertNull(result[3])
    }

    @Test
    fun `빈 리스트는 전부 null`() {
        assertEquals(listOf(null, null, null, null), GlobalIndexReturnCalculator.computeReturns(emptyList()))
    }
}
