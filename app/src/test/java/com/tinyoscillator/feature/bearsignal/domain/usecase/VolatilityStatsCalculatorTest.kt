package com.tinyoscillator.feature.bearsignal.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VolatilityStatsCalculator] 골든·경계 케이스 테스트 (TASK.md §3.2, Phase 1).
 *
 * 결정적 종가 픽스처(Fixture A/B)는 사전에 Python으로 오프라인 계산해 확보한 기대값을 사용한다
 * (mean/std/±σ 카운트가 정확히 재현되는지 검증). 경계(정확히 σ) 검증은 자기참조(값 자체가
 * 통계량에 영향을 주는 순환)를 피하기 위해 `internal fun countBreaches`를 mean/stdDev를 고정한
 * 채로 직접 호출한다.
 */
class VolatilityStatsCalculatorTest {

    // Fixture A: 28개 완만한 상승(0.05%) + 상승 스파이크(+6%) + 하락 스파이크(-6%) = 30 수익률.
    // Python(ddof=1, 아래 종가를 그대로 사용해 재계산)으로 사전 검증한 기대값:
    // mean=0.04666666614193874, std=1.575728244931639, up3=1, down3=1, up4=0, down4=0
    // (스파이크 z: 약 +3.778/-3.837 — 3σ는 초과, 4σ는 미달).
    private val fixtureACloses = listOf(
        1000.000000, 1000.500000, 1001.000250, 1001.500750, 1002.001501,
        1002.502501, 1003.003753, 1003.505254, 1004.007007, 1004.509011,
        1005.011265, 1005.513771, 1006.016528, 1006.519536, 1007.022796,
        1007.526307, 1008.030070, 1008.534085, 1009.038352, 1009.542871,
        1010.047643, 1010.552667, 1011.057943, 1011.563472, 1012.069254,
        1012.575288, 1013.081576, 1013.588117, 1014.094911, 1074.940605,
        1010.444169
    )

    // Fixture B: 30개 소폭 등락(±0.02~0.1%) 반복 — 스파이크 없음. 기대값: mean=0.0, up3=down3=0,
    // 최대 |z|≈1.499 (3σ 미달).
    private val fixtureBCloses = listOf(
        2500.000000, 2501.250000, 2499.999375, 2502.499374, 2499.996875,
        2500.496874, 2499.996775, 2501.246773, 2499.996150, 2502.496146,
        2499.993650, 2500.493649, 2499.993550, 2501.243547, 2499.992925,
        2502.492918, 2499.990425, 2500.490423, 2499.990325, 2501.240320,
        2499.989700, 2502.489690, 2499.987200, 2500.487197, 2499.987100,
        2501.237094, 2499.986475, 2502.486462, 2499.983975, 2500.483972,
        2499.983875
    )

    // ── 골든 케이스 ─────────────────────────────────────────────

    @Test
    fun `compute Fixture A 상승·하락 스파이크 각 1건 카운트`() {
        val result = VolatilityStatsCalculator.compute(fixtureACloses)

        assertNotNull(result)
        result!!
        assertEquals(1, result.up3)
        assertEquals(1, result.down3)
        assertEquals(0, result.up4)
        assertEquals(0, result.down4)
        assertEquals(30, result.sampleCount)
        assertEquals(0.04666666614193874, result.mean, 1e-6)
        assertEquals(1.575728244931639, result.stdDev, 1e-6)
    }

    @Test
    fun `compute Fixture B 스파이크 없음 up down 모두 0`() {
        val result = VolatilityStatsCalculator.compute(fixtureBCloses)

        assertNotNull(result)
        result!!
        assertEquals(0, result.up3)
        assertEquals(0, result.down3)
        assertEquals(0, result.up4)
        assertEquals(0, result.down4)
        assertEquals(30, result.sampleCount)
    }

    // ── 데이터 부족 경계 ─────────────────────────────────────────

    @Test
    fun `compute 종가 2개 미만은 null`() {
        assertNull(VolatilityStatsCalculator.compute(emptyList()))
        assertNull(VolatilityStatsCalculator.compute(listOf(1000.0)))
    }

    @Test
    fun `computeFromReturns MIN_RETURNS 미만은 null 경계`() {
        val belowMin = List(VolatilityStatsCalculator.MIN_RETURNS - 1) { 0.1 }
        val atMin = List(VolatilityStatsCalculator.MIN_RETURNS) { 0.1 }

        assertNull(VolatilityStatsCalculator.computeFromReturns(belowMin))
        assertNotNull(VolatilityStatsCalculator.computeFromReturns(atMin))
    }

    @Test
    fun `compute 데이터 부족 시나리오 - 10개 종가(9수익률) null`() {
        val shortCloses = (0 until 10).map { 1000.0 + it }
        assertNull(VolatilityStatsCalculator.compute(shortCloses))
    }

    // ── 표준편차 0(무변동) 경계 ───────────────────────────────────

    @Test
    fun `모든 수익률 동일(표준편차 0)이면 up down 모두 0`() {
        val flat = List(25) { 0.2 }
        val result = VolatilityStatsCalculator.computeFromReturns(flat)

        assertNotNull(result)
        result!!
        assertEquals(0.0, result.stdDev, 1e-12)
        assertEquals(0, result.up3)
        assertEquals(0, result.down3)
        assertEquals(0, result.up4)
        assertEquals(0, result.down4)
    }

    // ── 경계: 정확히 σ 도달은 미포함(strict >), 구현 정의 고정 ──────

    @Test
    fun `countBreaches 정확히 3시그마는 미포함 초과분만 카운트`() {
        val values = listOf(3.0, 3.0000001, -3.0, -3.0000001, 0.0, 2.9999999, -2.9999999)

        val (up, down) = VolatilityStatsCalculator.countBreaches(values, mean = 0.0, stdDev = 1.0, sigmaThreshold = 3.0)

        // 정확히 3.0/-3.0인 값은 카운트되지 않고, 3.0000001/-3.0000001만 카운트된다.
        assertEquals(1, up)
        assertEquals(1, down)
    }

    @Test
    fun `countBreaches 정확히 4시그마는 미포함 초과분만 카운트`() {
        val values = listOf(4.0, 4.0000001, -4.0, -4.0000001, 0.0)

        val (up, down) = VolatilityStatsCalculator.countBreaches(values, mean = 0.0, stdDev = 1.0, sigmaThreshold = 4.0)

        assertEquals(1, up)
        assertEquals(1, down)
    }

    @Test
    fun `countBreaches up 0 케이스 - 하락만 존재`() {
        val values = listOf(-5.0, -4.5, 0.0, 0.5, 1.0, 2.0)

        val (up, down) = VolatilityStatsCalculator.countBreaches(values, mean = 0.0, stdDev = 1.0, sigmaThreshold = 3.0)

        assertEquals(0, up)
        assertTrue(down >= 1)
    }
}
