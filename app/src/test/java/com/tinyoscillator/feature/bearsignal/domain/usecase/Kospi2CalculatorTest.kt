package com.tinyoscillator.feature.bearsignal.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [Kospi2Calculator] 시총 합산·백분율 산술 테스트 (TASK.md §3.5, Phase 1). */
class Kospi2CalculatorTest {

    @Test
    fun `삼성전자+SK하이닉스 시총 비중 정상 계산`() {
        val marketCaps = mapOf(
            "005930" to 500_000_000_000L, // 삼성전자
            "000660" to 300_000_000_000L, // SK하이닉스
            "035420" to 200_000_000_000L, // 그 외 종목
            "051910" to 1_000_000_000_000L
        )
        // total = 2_000_000_000_000, 삼성+SK = 800_000_000_000 → 40%
        val result = Kospi2Calculator.compute(marketCaps)

        assertEquals(40.0, result!!, 1e-9)
    }

    @Test
    fun `빈 맵은 null`() {
        assertNull(Kospi2Calculator.compute(emptyMap()))
    }

    @Test
    fun `삼성전자 데이터 누락 시 null`() {
        val marketCaps = mapOf(
            "000660" to 300_000_000_000L,
            "035420" to 200_000_000_000L
        )
        assertNull(Kospi2Calculator.compute(marketCaps))
    }

    @Test
    fun `SK하이닉스 데이터 누락 시 null`() {
        val marketCaps = mapOf(
            "005930" to 500_000_000_000L,
            "035420" to 200_000_000_000L
        )
        assertNull(Kospi2Calculator.compute(marketCaps))
    }

    @Test
    fun `전체 시가총액 0 이하이면 null`() {
        val marketCaps = mapOf(
            "005930" to 0L,
            "000660" to 0L
        )
        assertNull(Kospi2Calculator.compute(marketCaps))
    }

    @Test
    fun `삼성전자와 SK하이닉스만 있는 경우 100퍼센트`() {
        val marketCaps = mapOf(
            "005930" to 700_000_000_000L,
            "000660" to 300_000_000_000L
        )
        val result = Kospi2Calculator.compute(marketCaps)

        assertEquals(100.0, result!!, 1e-9)
    }

    @Test
    fun `리포트 기준값 근사 재현 - 56 퍼센트대`() {
        // TASK.md 부록 C 기준값: kospi2=56 (2026.6.30 리포트). 비중만 재현 가능한 임의 시총으로 구성.
        val marketCaps = mapOf(
            "005930" to 392_000_000_000L,
            "000660" to 168_000_000_000L,
            "005380" to 440_000_000_000L
        )
        // total=1_000_000_000_000, 삼성+SK=560_000_000_000 → 56%
        val result = Kospi2Calculator.compute(marketCaps)

        assertEquals(56.0, result!!, 1e-9)
    }
}
