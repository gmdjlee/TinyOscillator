package com.tinyoscillator.presentation.marketanalysis

import com.tinyoscillator.domain.model.FearGreedChartData
import com.tinyoscillator.domain.model.toFearGreedStatus
import org.junit.Assert.*
import org.junit.Test

/**
 * FearGreedState / FearGreedDateRange / toFearGreedStatus 검증 테스트
 */
class FearGreedViewModelTest {

    // ==========================================================
    // FearGreedState sealed class variants
    // ==========================================================

    @Test
    fun `FearGreedState sealed class variants`() {
        val idle: FearGreedState = FearGreedState.Idle
        val loading: FearGreedState = FearGreedState.Loading
        val success: FearGreedState = FearGreedState.Success(
            FearGreedChartData(market = "KOSPI", rows = emptyList())
        )
        val error: FearGreedState = FearGreedState.Error("오류 메시지")

        assertTrue(idle is FearGreedState.Idle)
        assertTrue(loading is FearGreedState.Loading)
        assertTrue(success is FearGreedState.Success)
        assertTrue(error is FearGreedState.Error)
        assertEquals("오류 메시지", (error as FearGreedState.Error).message)
    }

    // ==========================================================
    // FearGreedDateRange labels and days
    // ==========================================================

    @Test
    fun `FearGreedDateRange labels and days`() {
        assertEquals("1M", FearGreedDateRange.ONE_MONTH.label)
        assertEquals(30L, FearGreedDateRange.ONE_MONTH.days)

        assertEquals("3M", FearGreedDateRange.THREE_MONTHS.label)
        assertEquals(90L, FearGreedDateRange.THREE_MONTHS.days)

        assertEquals("6M", FearGreedDateRange.SIX_MONTHS.label)
        assertEquals(180L, FearGreedDateRange.SIX_MONTHS.days)

        assertEquals("1Y", FearGreedDateRange.ONE_YEAR.label)
        assertEquals(365L, FearGreedDateRange.ONE_YEAR.days)

        assertEquals("2Y", FearGreedDateRange.TWO_YEARS.label)
        assertEquals(730L, FearGreedDateRange.TWO_YEARS.days)

        assertEquals("전체", FearGreedDateRange.ALL.label)
        assertEquals(0L, FearGreedDateRange.ALL.days)

        // enum values 개수 확인
        assertEquals(6, FearGreedDateRange.entries.size)
    }

    // ==========================================================
    // toFearGreedStatus extension
    // ==========================================================

    @Test
    fun `toFearGreedStatus extension — 경계값 검증`() {
        // 0.0 → 극단적 공포 (< 0.2)
        assertEquals("극단적 공포", 0.0.toFearGreedStatus())

        // 0.2 → 공포 (>= 0.2, < 0.4)
        assertEquals("공포", 0.2.toFearGreedStatus())

        // 0.4 → 중립 (>= 0.4, < 0.6)
        assertEquals("중립", 0.4.toFearGreedStatus())

        // 0.6 → 탐욕 (>= 0.6, < 0.8)
        assertEquals("탐욕", 0.6.toFearGreedStatus())

        // 0.8 → 극단적 탐욕 (>= 0.8)
        assertEquals("극단적 탐욕", 0.8.toFearGreedStatus())

        // 1.0 → 극단적 탐욕
        assertEquals("극단적 탐욕", 1.0.toFearGreedStatus())
    }
}
